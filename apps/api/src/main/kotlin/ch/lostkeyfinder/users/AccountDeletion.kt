package ch.lostkeyfinder.users

import ch.lostkeyfinder.chat.Chat
import ch.lostkeyfinder.chat.ChatImage
import ch.lostkeyfinder.chat.ChatNotice
import ch.lostkeyfinder.chat.FinderDraft
import ch.lostkeyfinder.chat.chatAudit
import ch.lostkeyfinder.chat.closeTagChats
import ch.lostkeyfinder.identity.FirebaseIdentity
import ch.lostkeyfinder.tags.Tag
import ch.lostkeyfinder.tags.TagAudit
import ch.lostkeyfinder.tags.TagOrigin
import ch.lostkeyfinder.tags.TagRegistrationDraft
import ch.lostkeyfinder.tags.TagStatus
import ch.lostkeyfinder.tags.TagTransactions
import ch.lostkeyfinder.tags.tagChanges
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@Document("accountDeletions")
data class AccountDeletion(
    @field:Id val id: String,
    val userId: String,
    val createdAt: Instant,
    val localCleaned: Boolean = false,
    val completedAt: Instant? = null,
)

fun deletionKey(subject: String): String =
    MessageDigest.getInstance("SHA-256").digest(subject.toByteArray()).joinToString("") {
        "%02x".format(it)
    }

@Service
class AccountDeletionService(
    private val template: MongoTemplate,
    private val transactions: TagTransactions,
    private val identity: FirebaseIdentity,
    private val users: UserService,
) {
    fun delete(jwt: Jwt): Boolean {
        val key = deletionKey(requireNotNull(jwt.subject))
        var job = template.findById(key, AccountDeletion::class.java)
        if (job?.completedAt != null) return true
        val authTime = (jwt.getClaim<Any>("auth_time") as? Number)?.toLong() ?: 0
        if (job == null &&
            Instant.now().epochSecond - authTime > 300
        ) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Recent sign-in required")
        }
        if (job == null) {
            val user = users.currentUser(jwt)
            identity.verifiedEmail(jwt)
            job =
                transactions.run { ops ->
                    ops.findById(key, AccountDeletion::class.java) ?: run {
                        ops.lockActiveUser(user.id)
                        ops.updateFirst(
                            query(where("_id").`is`(user.id)),
                            Update.update("status", UserStatus.DELETING).set("isAdmin", false),
                            User::class.java,
                        )
                        ops.insert(AccountDeletion(key, user.id, Instant.now()))
                    }
                }
        }
        val userId = job.userId
        // Bounded requests can be repeated after timeout; each tag and its history commit together.
        val owned = template.find(query(where("primaryOwnerUserId").`is`(userId)).limit(25), Tag::class.java)
        owned.forEach { tag ->
            transactions.run<Boolean> { ops ->
                val before =
                    ops.findOne(query(where("_id").`is`(tag.id).and("primaryOwnerUserId").`is`(userId)), Tag::class.java) ?: return@run true
                val now = Instant.now()
                val retire = before.origin == TagOrigin.SELF_CREATED
                val status =
                    if (retire) {
                        TagStatus.RETIRED
                    } else if (before.status in
                        listOf(TagStatus.ACTIVE, TagStatus.INACTIVE)
                    ) {
                        TagStatus.ACTIVE
                    } else {
                        before.status
                    }
                val after =
                    before.copy(
                        primaryOwnerUserId = null,
                        label = "",
                        registeredAt = null,
                        status = status,
                        deletedAt = if (retire) before.deletedAt ?: now else before.deletedAt,
                        updatedAt = now,
                    )
                ops.updateFirst(
                    query(where("_id").`is`(tag.id)),
                    Update
                        .update("primaryOwnerUserId", null)
                        .set("label", "")
                        .set("registeredAt", null)
                        .set("status", after.status)
                        .set("deletedAt", after.deletedAt)
                        .set("updatedAt", now),
                    Tag::class.java,
                )
                closeTagChats(ops, tag.id, userId, now)
                ops.updateMulti(
                    query(where("tagId").`is`(tag.id)),
                    Update.update("expiresAt", now).unset("email"),
                    TagRegistrationDraft::class.java,
                )
                ops.insert(
                    TagAudit(
                        UUID.randomUUID().toString(),
                        userId,
                        if (retire) "TAG_DELETED" else "TAG_RELEASED",
                        tag.id,
                        now,
                        tagChanges(before, after),
                    ),
                )
                true
            }
        }
        if (template.exists(query(where("primaryOwnerUserId").`is`(userId)), Tag::class.java)) return false
        if (!job.localCleaned) {
            val user = template.findById(userId, User::class.java) ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)
            val openChats = query(where("finderUserId").`is`(userId).and("closedAt").`is`(null))
            template.find(openChats.limit(25), Chat::class.java).forEach { chat ->
                transactions.run { ops ->
                    val current = ops.findById(chat.id, Chat::class.java)!!
                    if (current.closedAt == null) {
                        val now = Instant.now()
                        ops.updateFirst(query(where("_id").`is`(chat.id)), Update.update("closedAt", now), Chat::class.java)
                        chatAudit(ops, current, userId, "CHAT_CLOSED", now)
                    }
                    true
                }
            }
            if (template.exists(openChats, Chat::class.java)) return false
            val finderDrafts =
                query(
                    Criteria().orOperator(
                        where("email").`is`(user.email),
                        where("completedUserId").`is`(userId),
                        where("ownerUserId").`is`(userId),
                    ),
                )
            template.find(finderDrafts.limit(25), FinderDraft::class.java).forEach { draft ->
                transactions.run { ops ->
                    ops.remove(query(where("draftId").`is`(draft.id).and("messageId").`is`(null)), ChatImage::class.java)
                    ops.remove(draft)
                    true
                }
            }
            if (template.exists(finderDrafts, FinderDraft::class.java)) return false
            val registrations = query(Criteria().orOperator(where("email").`is`(user.email), where("completedUserId").`is`(userId)))
            template.find(registrations.limit(25), TagRegistrationDraft::class.java).forEach { template.remove(it) }
            if (template.exists(registrations, TagRegistrationDraft::class.java)) return false
            val notices = query(where("userId").`is`(userId))
            template.find(notices.limit(25), ChatNotice::class.java).forEach { template.remove(it) }
            if (template.exists(notices, ChatNotice::class.java)) return false
            transactions.run { ops ->
                ops.updateFirst(
                    query(where("_id").`is`(userId)),
                    Update.update("email", "$userId@deleted.invalid").set("language", "de").unset("tagCreationLimit"),
                    User::class.java,
                )
                ops.updateFirst(query(where("_id").`is`(key)), Update.update("localCleaned", true), AccountDeletion::class.java)
                true
            }
        }
        // External side effect after local cleanup; USER_NOT_FOUND is safe on retries.
        identity.deleteAccount(jwt)
        transactions.run { ops ->
            ops.remove(query(where("_id").`is`(userId)), User::class.java)
            ops.updateFirst(query(where("_id").`is`(key)), Update.update("completedAt", Instant.now()), AccountDeletion::class.java)
            true
        }
        return true
    }
}
