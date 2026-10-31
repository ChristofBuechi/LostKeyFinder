package ch.lostkeyfinder.chat

import ch.lostkeyfinder.tags.PublicRequestLimits
import ch.lostkeyfinder.tags.TagService
import ch.lostkeyfinder.tags.TagTransactions
import ch.lostkeyfinder.users.User
import ch.lostkeyfinder.users.lockActiveUser
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

enum class ReportReason { SPAM, HARASSMENT, OTHER }

@Document("abuseReports")
data class ChatReport(
    @field:Id val id: String,
    val chatId: String,
    val messageId: String,
    val reporterUserId: String,
    val reason: ReportReason,
    val createdAt: Instant,
    val status: String = "OPEN",
)

@Service
class ChatReports(
    private val template: MongoTemplate,
    private val transactions: TagTransactions,
    private val limits: PublicRequestLimits,
) {
    fun report(
        user: User,
        chatId: String,
        messageId: String,
        reason: ReportReason,
    ) {
        TagService.uuid(chatId)
        TagService.uuid(messageId)
        val key = "${user.id}:${messageId.lowercase()}"
        if (!template.exists(query(where("_id").`is`(key)), ChatReport::class.java)) limits.reserve("chat-report", user.id, 20)
        transactions.run { ops ->
            ops.lockActiveUser(user.id)
            val chat = ops.findById(chatId.lowercase(), Chat::class.java)
            val message = ops.findById(messageId.lowercase(), ChatMessage::class.java)
            if (chat == null || user.id !in chat.participantIds || message?.chatId != chat.id) {
                throw ResponseStatusException(HttpStatus.NOT_FOUND, "Message unavailable")
            }
            ops.upsert(
                query(where("_id").`is`(key)),
                Update()
                    .setOnInsert("chatId", chat.id)
                    .setOnInsert("messageId", message.id)
                    .setOnInsert("reporterUserId", user.id)
                    .setOnInsert("reason", reason)
                    .setOnInsert("createdAt", Instant.now())
                    .setOnInsert("status", "OPEN"),
                ChatReport::class.java,
            )
            true
        }
    }
}
