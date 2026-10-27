package ch.lostkeyfinder.chat

import ch.lostkeyfinder.mail.MailSender
import ch.lostkeyfinder.tags.TagProperties
import ch.lostkeyfinder.users.User
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoOperations
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import java.time.Instant

@Document("chatNotifications")
data class ChatNotice(
    @field:Id val id: String,
    val chatId: String,
    val userId: String,
    val targetSequence: Long,
    val deliveredSequence: Long = 0,
    val dueAt: Instant,
    val leaseUntil: Instant = Instant.EPOCH,
    val status: String = "PENDING",
)

@Service
class ChatNotifications(
    private val template: MongoTemplate,
    private val mail: MailSender,
    private val tags: TagProperties,
) {
    fun schedule(
        ops: MongoOperations,
        chat: Chat,
        userId: String,
        due: Instant,
    ) {
        val id = "${chat.id}:$userId"
        val before = ops.findById(id, ChatNotice::class.java)
        if (before == null) {
            ops.insert(ChatNotice(id, chat.id, userId, chat.sequence, dueAt = due))
        } else {
            val change = Update.update("targetSequence", chat.sequence).set("status", "PENDING")
            if (before.targetSequence <= before.deliveredSequence) change.set("dueAt", due)
            ops.updateFirst(query(where("_id").`is`(id)), change, ChatNotice::class.java)
        }
    }

    fun deliverInitial(
        chatId: String,
        userId: String,
    ) {
        val notice = template.findById("$chatId:$userId", ChatNotice::class.java) ?: return
        if (notice.deliveredSequence == 0L && notice.targetSequence == 1L) deliver(notice.id)
    }

    fun dispatch(): Int {
        val due =
            template.find(
                query(
                    where("dueAt")
                        .lte(Instant.now())
                        .and("leaseUntil")
                        .lte(Instant.now())
                        .and("status")
                        .`in`("PENDING", "FAILED"),
                ).limit(10),
                ChatNotice::class.java,
            )
        due.forEach { deliver(it.id) }
        return due.size
    }

    private fun deliver(id: String) {
        val now = Instant.now()
        val notice =
            template.findAndModify(
                query(
                    where("_id")
                        .`is`(id)
                        .and("dueAt")
                        .lte(now)
                        .and("leaseUntil")
                        .lte(now)
                        .and("status")
                        .`in`("PENDING", "FAILED"),
                ),
                Update.update("leaseUntil", now.plusSeconds(180)),
                FindAndModifyOptions.options().returnNew(true),
                ChatNotice::class.java,
            )
                ?: return
        var status = "SENT"
        try {
            val chat = template.findById(notice.chatId, Chat::class.java)
            val user = template.findById(notice.userId, User::class.java)
            val readThrough = if (notice.userId == chat?.ownerUserId) chat.ownerReadThrough else chat?.finderReadThrough ?: 0
            if (!mail.configured || chat == null || user == null || !user.canAccess || notice.userId !in chat.participantIds ||
                readThrough >= notice.targetSequence
            ) {
                status = "SKIPPED"
            } else {
                check(tags.publicBaseUrl.isNotBlank())
                val subject =
                    if (notice.deliveredSequence == 0L && notice.targetSequence == 1L) {
                        "Lost Key Finder: Neue Fundmeldung / Item found"
                    } else {
                        "Lost Key Finder: Ungelesene Nachrichten / Unread messages"
                    }
                val link = "${tags.publicBaseUrl.trimEnd('/')}/chats/${chat.id}"
                mail.send(
                    user.email,
                    subject,
                    "Es gibt neue Nachrichten. / You have new messages.\n\n$link\n\nMelde dich mit deiner E-Mail-Adresse an. / Sign in with your email address.",
                )
            }
        } catch (ex: Exception) {
            // SMTP can have accepted a message before a timeout; retry may rarely duplicate delivery.
            template.updateFirst(
                query(where("_id").`is`(id)),
                Update
                    .update("status", "FAILED")
                    .set("leaseUntil", Instant.EPOCH)
                    .set("dueAt", now.plusSeconds(900)),
                ChatNotice::class.java,
            )
            return
        }
        // Preserve messages arriving during SMTP: acknowledge only the sequence we actually sent.
        val same = query(where("_id").`is`(id).and("targetSequence").`is`(notice.targetSequence))
        val result =
            template.updateFirst(
                same,
                Update
                    .update("status", status)
                    .set("deliveredSequence", notice.targetSequence)
                    .set("leaseUntil", Instant.EPOCH),
                ChatNotice::class.java,
            )
        if (result.matchedCount == 0L) {
            template.updateFirst(
                query(where("_id").`is`(id)),
                Update
                    .update("status", "PENDING")
                    .set("deliveredSequence", notice.targetSequence)
                    .set("leaseUntil", Instant.EPOCH)
                    .set("dueAt", now.plusSeconds(900)),
                ChatNotice::class.java,
            )
        }
    }
}
