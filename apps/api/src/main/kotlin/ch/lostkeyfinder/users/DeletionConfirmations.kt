package ch.lostkeyfinder.users

import ch.lostkeyfinder.mail.MailSender
import ch.lostkeyfinder.tags.PublicRequestLimits
import ch.lostkeyfinder.tags.TagProperties
import ch.lostkeyfinder.translations.Translations
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

data class DeletionConfirmation(
    val tokenHash: String,
    val emailHash: String,
    val expiresAt: Instant,
    val consumedAt: Instant? = null,
)

@Service
class DeletionConfirmations(
    private val template: MongoTemplate,
    private val mail: MailSender,
    private val translations: Translations,
    private val urls: TagProperties,
    private val limits: PublicRequestLimits,
) {
    private val random = SecureRandom()

    fun send(
        user: User,
        email: String,
    ) {
        if (!mail.configured ||
            urls.publicBaseUrl.isBlank()
        ) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Confirmation delivery unavailable")
        }
        limits.reserve("deletion-email", user.id, 3)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
        val proof = DeletionConfirmation(hash(token), hash("$token:$email"), Instant.now().plusSeconds(900))
        val changed =
            template.updateFirst(
                query(
                    where("_id")
                        .`is`(user.id)
                        .and("firebaseUserId")
                        .`is`(user.firebaseUserId)
                        .and("status")
                        .`is`(user.status),
                ),
                Update.update("deletionConfirmation", proof),
                User::class.java,
            )
        if (changed.matchedCount != 1L) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Account unavailable")
        try {
            val link = "${urls.publicBaseUrl.trimEnd('/')}/account/deletion-confirm#$token"
            mail.send(
                email,
                translations.text(user.language, "emails.accountDeletion.subject"),
                translations.text(user.language, "emails.accountDeletion.body", mapOf("confirmationUrl" to link)),
            )
        } catch (ex: Exception) {
            template.updateFirst(
                query(where("_id").`is`(user.id).and("deletionConfirmation.tokenHash").`is`(proof.tokenHash)),
                Update().unset("deletionConfirmation"),
                User::class.java,
            )
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Confirmation delivery unavailable")
        }
    }

    fun proof(
        user: User,
        token: String,
    ): DeletionConfirmation {
        if (!Regex("[A-Za-z0-9_-]{43}").matches(token)) unavailable()
        val proof = user.deletionConfirmation ?: unavailable()
        if (!equal(proof.tokenHash, hash(token))) unavailable()
        return proof
    }

    fun requireCurrent(
        user: User,
        token: String,
        email: String,
    ): DeletionConfirmation =
        proof(user, token).also {
            if (it.consumedAt != null || !it.expiresAt.isAfter(Instant.now()) || !equal(it.emailHash, hash("$token:$email"))) unavailable()
        }

    private fun hash(value: String) =
        Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.toByteArray()))

    private fun equal(
        first: String,
        second: String,
    ) = MessageDigest.isEqual(first.toByteArray(), second.toByteArray())

    private fun unavailable(): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND, "Confirmation unavailable")
}
