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
    val confirmedAt: Instant? = null,
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
    private val confirmations: DeletionConfirmations,
) {
    fun sendConfirmation(jwt: Jwt): Boolean {
        requireWorker()
        val user = users.profile(jwt)
        val job = template.findById(deletionKey(requireNotNull(jwt.subject)), AccountDeletion::class.java)
        if (job != null && job.userId != user.id) unavailable()
        if (job?.confirmedAt != null) return false
        if (!user.canAccess && !(user.status == UserStatus.DELETING && job?.userId == user.id)) unavailable()
        val authTime = (jwt.getClaim<Any>("auth_time") as? Number)?.toLong() ?: 0
        if (Instant.now().epochSecond - authTime > 300) throw ResponseStatusException(HttpStatus.CONFLICT, "Recent sign-in required")
        confirmations.send(user, identity.verifiedEmail(jwt))
        return true
    }

    fun request(
        jwt: Jwt,
        token: String,
    ) {
        val key = deletionKey(requireNotNull(jwt.subject))
        val user = users.profile(jwt)
        val proof = confirmations.proof(user, token)
        val job = template.findById(key, AccountDeletion::class.java)
        if (proof.consumedAt != null && job?.confirmedAt != null && job.userId == user.id) return
        requireWorker()
        val email = identity.verifiedEmail(jwt)
        transactions.run { ops ->
            val current = ops.findById(user.id, User::class.java) ?: unavailable()
            if (current.firebaseUserId != jwt.subject) unavailable()
            val existing = ops.findById(key, AccountDeletion::class.java)
            if (existing != null && existing.userId != current.id) unavailable()
            val currentProof = confirmations.proof(current, token)
            if (currentProof.consumedAt != null && existing?.confirmedAt != null && existing.userId == current.id) return@run true
            if (!current.canAccess &&
                !(current.status == UserStatus.DELETING && existing?.userId == current.id && existing.confirmedAt == null)
            ) {
                unavailable()
            }
            confirmations.requireCurrent(current, token, email)
            val now = Instant.now()
            val changed =
                ops.updateFirst(
                    query(
                        where("_id")
                            .`is`(current.id)
                            .and("status")
                            .`is`(current.status)
                            .and("deletionConfirmation.tokenHash")
                            .`is`(currentProof.tokenHash)
                            .and("deletionConfirmation.consumedAt")
                            .`is`(null),
                    ),
                    Update
                        .update("status", UserStatus.DELETING)
                        .set("isAdmin", false)
                        .set("updatedAt", now)
                        .set("deletionConfirmation.consumedAt", now),
                    User::class.java,
                )
            if (changed.matchedCount != 1L) unavailable()
            if (existing == null) {
                ops.insert(AccountDeletion(key, current.id, now, confirmedAt = now))
            } else {
                ops.updateFirst(
                    query(where("_id").`is`(key)),
                    Update.update("confirmedAt", now).set("nextAttemptAt", Instant.EPOCH),
                    AccountDeletion::class.java,
                )
            }
            true
        }
    }

    private fun requireWorker() {
        if (jobProperties.audience.isBlank() || jobProperties.serviceAccount.isBlank()) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Account deletion worker unavailable")
        }
    }

    private fun unavailable(): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND, "Confirmation unavailable")
}
