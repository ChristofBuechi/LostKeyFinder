package ch.lostkeyfinder.chat

import ch.lostkeyfinder.tags.TagAudit
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.MongoOperations
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.data.mongodb.core.query.Update
import java.time.Instant
import java.util.UUID

@Document("lossCases")
data class LossCase(
    @field:Id val id: String,
    val tagId: String,
    val ownerUserId: String,
    val createdAt: Instant,
    val activeKey: String? = null,
    val closedAt: Instant? = null,
)

@Document("chats")
data class Chat(
    @field:Id val id: String,
    val caseId: String,
    val tagId: String,
    val ownerUserId: String,
    val finderUserId: String,
    val participantIds: List<String>,
    val createdAt: Instant,
    val lastMessageAt: Instant,
    val sequence: Long = 0,
    val ownerReadThrough: Long = 0,
    val finderReadThrough: Long = 0,
    val closedAt: Instant? = null,
)

@Document("messages")
@Schema(requiredProperties = ["id", "chatId", "senderUserId", "sequence", "text", "createdAt", "imageId"])
data class ChatMessage(
    @field:Id val id: String,
    val chatId: String,
    val senderUserId: String,
    val sequence: Long,
    val text: String,
    val createdAt: Instant,
    val imageId: String? = null,
)

@Document("messageImages")
data class ChatImage(
    @field:Id val id: String,
    val draftId: String?,
    val messageId: String?,
    val bytes: ByteArray,
    val expiresAt: Instant? = null,
)

@Document("finderReportDrafts")
data class FinderDraft(
    @field:Id val id: String,
    val tagId: String,
    val ownerUserId: String,
    val email: String?,
    val text: String,
    val expiresAt: Instant,
    val imageId: String? = null,
    val chatId: String? = null,
    val completedUserId: String? = null,
)

@Schema(requiredProperties = ["flowId", "expiresAt"])
data class FinderStarted(
    val flowId: String,
    val expiresAt: Instant,
)

@Schema(
    requiredProperties = [
        "id", "caseId", "tagId", "createdAt", "lastMessageAt", "closedAt",
        "writable", "participant", "canClose", "unread", "sequence",
    ],
)
data class ChatView(
    val id: String,
    val caseId: String,
    val tagId: String,
    val createdAt: Instant,
    val lastMessageAt: Instant,
    val closedAt: Instant?,
    val writable: Boolean,
    val participant: Boolean,
    val canClose: Boolean,
    val unread: Long,
    val sequence: Long,
)

@Schema(requiredProperties = ["items", "page", "totalPages"])
data class ChatPage(
    val items: List<ChatView>,
    val page: Int,
    val totalPages: Int,
)

@Schema(requiredProperties = ["items", "before"])
data class MessagePage(
    val items: List<ChatMessage>,
    val before: Long?,
)

fun chatAudit(
    ops: MongoOperations,
    chat: Chat,
    actorId: String,
    action: String,
    now: Instant,
    messageId: String? = null,
) {
    ops.insert(
        TagAudit(
            UUID.randomUUID().toString(),
            actorId,
            action,
            chat.tagId,
            now,
            chatId = chat.id,
            messageId = messageId,
            chatParticipantIds = chat.participantIds,
        ),
    )
}

fun closeTagChats(
    ops: MongoOperations,
    tagId: String,
    actorId: String,
    now: Instant,
) {
    val open = query(where("tagId").`is`(tagId).and("closedAt").`is`(null))
    ops.find(open, Chat::class.java).forEach {
        chatAudit(ops, it, actorId, "CHAT_CLOSED", now)
    }
    ops.updateMulti(open, Update.update("closedAt", now), Chat::class.java)
    ops.updateMulti(open, Update.update("closedAt", now).unset("activeKey"), LossCase::class.java)
}
