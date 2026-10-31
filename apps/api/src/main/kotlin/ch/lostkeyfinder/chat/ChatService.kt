package ch.lostkeyfinder.chat

import ch.lostkeyfinder.identity.normalizeEmail
import ch.lostkeyfinder.tags.InventoryStatus
import ch.lostkeyfinder.tags.PublicRequestLimits
import ch.lostkeyfinder.tags.Tag
import ch.lostkeyfinder.tags.TagOrigin
import ch.lostkeyfinder.tags.TagService
import ch.lostkeyfinder.tags.TagStatus
import ch.lostkeyfinder.tags.TagTransactions
import ch.lostkeyfinder.users.User
import ch.lostkeyfinder.users.lockActiveUser
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoOperations
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class ChatService(
    private val template: MongoTemplate,
    private val transactions: TagTransactions,
    private val limits: PublicRequestLimits,
    private val notices: ChatNotifications,
) {
    fun start(
        publicId: String,
        email: String,
        text: String,
        image: ByteArray?,
        key: String,
    ): FinderStarted {
        TagService.uuid(publicId)
        TagService.uuid(key)
        val address =
            try {
                normalizeEmail(email)
            } catch (ex: IllegalArgumentException) {
                bad("Invalid email")
            }
        limits.reserve("finder-email", address, 10)
        limits.reserve("finder-tag", publicId.lowercase(), 30)
        val value = text.trim()
        validateText(value, true)
        return transactions.run { ops ->
            val existing = ops.findById(key.lowercase(), FinderDraft::class.java)
            if (existing != null) {
                val sameTag = ops.findOne(query(where("publicId").`is`(publicId.lowercase())), Tag::class.java)?.id == existing.tagId
                val sameImage =
                    existing.imageId?.let { ops.findById(it, ChatImage::class.java)?.bytes }?.contentEquals(image ?: byteArrayOf())
                        ?: (image == null)
                if (!sameTag || !sameImage || existing.email != address || existing.text != value ||
                    !existing.expiresAt.isAfter(Instant.now())
                ) {
                    conflict()
                }
                FinderStarted(existing.id, existing.expiresAt)
            } else {
                val tag = ops.findOne(query(where("publicId").`is`(publicId.lowercase())), Tag::class.java) ?: unavailable()
                if (!reportable(tag)) unavailable()
                val draft =
                    FinderDraft(
                        key.lowercase(),
                        tag.id,
                        tag.primaryOwnerUserId!!,
                        address,
                        value,
                        now().plusSeconds(14400),
                        imageId = image?.let { UUID.randomUUID().toString() },
                    )
                ops.insert(draft)
                image?.let { ops.insert(ChatImage(draft.imageId!!, draft.id, null, it, draft.expiresAt)) }
                FinderStarted(draft.id, draft.expiresAt)
            }
        }
    }

    fun complete(
        flowId: String,
        finder: User,
        verifiedEmail: String,
    ): ChatView {
        TagService.uuid(flowId)
        limits.reserve("finder-complete", finder.id, 30)
        val chat =
            transactions.run { ops ->
                ops.lockActiveUser(finder.id)
                val draft = ops.findById(flowId.lowercase(), FinderDraft::class.java) ?: unavailable()
                if (draft.completedUserId != null) {
                    if (draft.completedUserId != finder.id) unavailable()
                    return@run ops.findById(draft.chatId!!, Chat::class.java) ?: unavailable()
                }
                if (!draft.expiresAt.isAfter(Instant.now()) || draft.email != verifiedEmail) unavailable()
                val tag = ops.findById(draft.tagId, Tag::class.java) ?: unavailable()
                if (!reportable(tag) || tag.primaryOwnerUserId != draft.ownerUserId) conflict()
                if (tag.primaryOwnerUserId == finder.id) bad("Cannot report your own tag")
                ops.lockActiveUser(draft.ownerUserId)
                // Serialize against release, deletion and parallel first reports on this tag.
                ops.updateFirst(query(where("_id").`is`(tag.id)), Update().inc("chatVersion", 1), Tag::class.java)
                val now = now()
                val lossCase =
                    ops.findOne(query(where("activeKey").`is`(tag.id)), LossCase::class.java)
                        ?: ops.insert(LossCase(UUID.randomUUID().toString(), tag.id, draft.ownerUserId, now, tag.id))
                val previous = ops.findOne(query(where("caseId").`is`(lossCase.id).and("finderUserId").`is`(finder.id)), Chat::class.java)
                val chat =
                    if (previous == null) {
                        ops.insert(
                            Chat(
                                UUID.randomUUID().toString(),
                                lossCase.id,
                                tag.id,
                                draft.ownerUserId,
                                finder.id,
                                listOf(draft.ownerUserId, finder.id),
                                now,
                                now,
                                1,
                                finderReadThrough = 1,
                            ),
                        )
                    } else {
                        previous
                            .copy(
                                sequence = previous.sequence + 1,
                                lastMessageAt = now,
                                finderReadThrough = previous.sequence + 1,
                            ).also {
                                ops.updateFirst(
                                    query(where("_id").`is`(it.id)),
                                    Update
                                        .update("sequence", it.sequence)
                                        .set("lastMessageAt", now)
                                        .set("finderReadThrough", it.sequence),
                                    Chat::class.java,
                                )
                            }
                    }
                val message = ChatMessage(UUID.randomUUID().toString(), chat.id, finder.id, chat.sequence, draft.text, now, draft.imageId)
                ops.insert(message)
                draft.imageId?.let {
                    ops.updateFirst(
                        query(where("_id").`is`(it)),
                        Update
                            .update("messageId", message.id)
                            .unset("draftId")
                            .unset("expiresAt"),
                        ChatImage::class.java,
                    )
                }
                if (previous == null) chatAudit(ops, chat, finder.id, "CHAT_CREATED", now)
                chatAudit(ops, chat, finder.id, "CHAT_MESSAGE_SENT", now, message.id)
                notices.schedule(ops, chat, chat.ownerUserId, if (previous == null) now else now.plusSeconds(900))
                ops.updateFirst(
                    query(where("_id").`is`(draft.id)),
                    Update
                        .update("chatId", chat.id)
                        .set("completedUserId", finder.id)
                        .set("text", "")
                        .unset("email"),
                    FinderDraft::class.java,
                )
                chat
            }
        notices.deliverInitial(chat.id, chat.ownerUserId)
        return view(chat, finder)
    }

    fun detail(
        id: String,
        user: User,
    ): ChatView = view(authorized(id, user), user)

    fun list(
        user: User,
        page: Int,
        tagId: String?,
    ): ChatPage {
        if (page < 1) bad("Invalid page")
        val criteria =
            if (tagId != null) {
                TagService.uuid(tagId)
                if (user.isAdmin) where("tagId").`is`(tagId) else where("tagId").`is`(tagId).and("participantIds").`is`(user.id)
            } else {
                where("participantIds").`is`(user.id)
            }
        val pages = maxOf(1, ((template.count(query(criteria), Chat::class.java) + 24) / 25).toInt())
        val current = minOf(page, pages)
        val chats =
            template.find(
                query(criteria)
                    .with(Sort.by(Sort.Direction.DESC, "lastMessageAt", "_id"))
                    .skip((current - 1L) * 25)
                    .limit(25),
                Chat::class.java,
            )
        return ChatPage(chats.map { view(it, user) }, current, pages)
    }

    fun messages(
        id: String,
        user: User,
        before: Long?,
    ): MessagePage {
        authorized(id, user)
        if (before != null && before <= 0) bad("Invalid cursor")
        val selection = where("chatId").`is`(id)
        if (before != null) selection.and("sequence").lt(before)
        val messages = template.find(query(selection).with(Sort.by(Sort.Direction.DESC, "sequence")).limit(51), ChatMessage::class.java)
        return MessagePage(messages.take(50).reversed(), if (messages.size > 50) messages[49].sequence else null)
    }

    fun send(
        id: String,
        user: User,
        text: String,
        image: ByteArray?,
        key: String,
    ): ChatMessage {
        TagService.uuid(id)
        TagService.uuid(key)
        limits.reserve("chat-send", user.id, 120)
        val value = text.trim()
        validateText(value, image == null)
        return transactions.run { ops ->
            ops.lockActiveUser(user.id)
            val chat = authorized(id, user, ops)
            if (user.id !in chat.participantIds) unavailable()
            val existing = ops.findById(key.lowercase(), ChatMessage::class.java)
            if (existing != null) {
                val storedImage = existing.imageId?.let { ops.findById(it, ChatImage::class.java)?.bytes }
                if (existing.chatId != id || existing.senderUserId != user.id || existing.text != value ||
                    !(storedImage?.contentEquals(image ?: byteArrayOf()) ?: (image == null))
                ) {
                    conflict()
                }
                return@run existing
            }
            val tag = ops.findById(chat.tagId, Tag::class.java) ?: unavailable()
            if (!writable(chat, tag)) conflict()
            ops.updateFirst(query(where("_id").`is`(tag.id)), Update().inc("chatVersion", 1), Tag::class.java)
            val now = now()
            val message =
                ChatMessage(
                    key.lowercase(),
                    chat.id,
                    user.id,
                    chat.sequence + 1,
                    value,
                    now,
                    imageId = image?.let { UUID.randomUUID().toString() },
                )
            ops.insert(message)
            image?.let { ops.insert(ChatImage(message.imageId!!, null, message.id, it)) }
            val readField = if (user.id == chat.ownerUserId) "ownerReadThrough" else "finderReadThrough"
            ops.updateFirst(
                query(where("_id").`is`(chat.id)),
                Update
                    .update("sequence", message.sequence)
                    .set("lastMessageAt", now)
                    .set(readField, message.sequence),
                Chat::class.java,
            )
            chatAudit(ops, chat, user.id, "CHAT_MESSAGE_SENT", now, message.id)
            val recipient = if (user.id == chat.ownerUserId) chat.finderUserId else chat.ownerUserId
            notices.schedule(ops, chat.copy(sequence = message.sequence), recipient, now.plusSeconds(900))
            message
        }
    }

    fun read(
        id: String,
        user: User,
        through: Long,
    ) {
        if (through < 0) bad("Invalid read position")
        transactions.run { ops ->
            ops.lockActiveUser(user.id)
            val chat = authorized(id, user, ops)
            if (user.id !in chat.participantIds) unavailable()
            if (through > chat.sequence) bad("Invalid read position")
            val field = if (user.id == chat.ownerUserId) "ownerReadThrough" else "finderReadThrough"
            ops.updateFirst(query(where("_id").`is`(id)), Update().max(field, through), Chat::class.java)
            true
        }
    }

    fun close(
        id: String,
        owner: User,
    ) {
        transactions.run { ops ->
            ops.lockActiveUser(owner.id)
            val chat = authorized(id, owner, ops)
            if (chat.ownerUserId != owner.id) throw ResponseStatusException(HttpStatus.FORBIDDEN)
            val now = now()
            val tag = ops.findById(chat.tagId, Tag::class.java) ?: unavailable()
            ops.updateFirst(query(where("_id").`is`(tag.id)), Update().inc("chatVersion", 1), Tag::class.java)
            // One case contains all finder chats; completing the return closes this case together.
            val open = query(where("caseId").`is`(chat.caseId).and("closedAt").`is`(null))
            ops.find(open, Chat::class.java).forEach { chatAudit(ops, it, owner.id, "CHAT_CLOSED", now) }
            ops.updateMulti(open, Update.update("closedAt", now), Chat::class.java)
            ops.updateFirst(query(where("_id").`is`(chat.caseId)), Update.update("closedAt", now).unset("activeKey"), LossCase::class.java)
            true
        }
    }

    fun image(
        id: String,
        user: User,
    ): ByteArray {
        TagService.uuid(id)
        val image = template.findById(id, ChatImage::class.java) ?: unavailable()
        val message = image.messageId?.let { template.findById(it, ChatMessage::class.java) } ?: unavailable()
        authorized(message.chatId, user)
        return image.bytes
    }

    private fun authorized(
        id: String,
        user: User,
        ops: MongoOperations = template,
    ): Chat {
        TagService.uuid(id)
        val chat = ops.findById(id.lowercase(), Chat::class.java) ?: unavailable()
        if (!user.isAdmin && user.id !in chat.participantIds) unavailable()
        return chat
    }

    private fun view(
        chat: Chat,
        user: User,
    ): ChatView {
        val tag = template.findById(chat.tagId, Tag::class.java)
        val through = if (user.id == chat.ownerUserId) chat.ownerReadThrough else chat.finderReadThrough
        return ChatView(
            chat.id,
            chat.caseId,
            chat.tagId,
            chat.createdAt,
            chat.lastMessageAt,
            chat.closedAt,
            user.id in chat.participantIds && tag != null && writable(chat, tag),
            user.id in chat.participantIds,
            chat.ownerUserId == user.id && chat.closedAt == null,
            if (user.id in chat.participantIds) maxOf(0, chat.sequence - through) else 0,
            chat.sequence,
        )
    }

    private fun writable(
        chat: Chat,
        tag: Tag,
    ) = chat.closedAt == null && tag.deletedAt == null &&
        tag.status in listOf(TagStatus.ACTIVE, TagStatus.INACTIVE) && tag.primaryOwnerUserId == chat.ownerUserId

    private fun reportable(tag: Tag) =
        tag.deletedAt == null && tag.status == TagStatus.ACTIVE && tag.primaryOwnerUserId != null &&
            (tag.origin == TagOrigin.SELF_CREATED || tag.inventoryStatus == InventoryStatus.RELEASED)

    private fun validateText(
        text: String,
        required: Boolean,
    ) {
        if ((required && text.isBlank()) || text.codePointCount(0, text.length) > 2000) bad("Message must contain 1 to 2000 characters")
    }

    private fun now() = Instant.now().truncatedTo(ChronoUnit.MILLIS)

    private fun unavailable(): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND, "Chat unavailable")

    private fun conflict(): Nothing = throw ResponseStatusException(HttpStatus.CONFLICT, "Chat state changed")

    private fun bad(reason: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, reason)
}
