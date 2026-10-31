package ch.lostkeyfinder.users

import ch.lostkeyfinder.identity.FirebaseIdentity
import ch.lostkeyfinder.tags.TagTransactions
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest
import java.time.Instant

@Document("accountDeletions")
data class AccountDeletion(
    @field:Id val id: String,
    val userId: String,
    val createdAt: Instant,
    val localCleaned: Boolean = false,
    val completedAt: Instant? = null,
    val nextAttemptAt: Instant = Instant.EPOCH,
    val leaseUntil: Instant = Instant.EPOCH,
    val leaseToken: String? = null,
    val failures: Int = 0,
    val lastFailureAt: Instant? = null,
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
    private val jobProperties: AccountDeletionJobProperties,
) {
    fun request(jwt: Jwt) {
        val key = deletionKey(requireNotNull(jwt.subject))
        val job = template.findById(key, AccountDeletion::class.java)
        if (job != null) return
        if (jobProperties.audience.isBlank() || jobProperties.serviceAccount.isBlank()) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Account deletion worker unavailable")
        }
        val authTime = (jwt.getClaim<Any>("auth_time") as? Number)?.toLong() ?: 0
        if (Instant.now().epochSecond - authTime > 300) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Recent sign-in required")
        }
        val user = users.currentUser(jwt)
        identity.verifiedEmail(jwt)
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
}
