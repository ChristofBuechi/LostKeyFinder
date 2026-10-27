package ch.lostkeyfinder.users

import ch.lostkeyfinder.identity.FirebaseIdentity
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class UserService(
    private val template: MongoTemplate,
    private val identity: FirebaseIdentity,
) {
    fun currentUser(jwt: Jwt): User {
        val query = query(where("firebaseUserId").`is`(jwt.subject))
        val user = template.findOne(query, User::class.java) ?: provision(query, jwt)
        if (!user.canAccess) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Account unavailable")
        return user
    }

    fun requireAdmin(jwt: Jwt): User =
        currentUser(jwt).also {
            if (!it.isAdmin) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access required")
        }

    fun changeLanguage(
        jwt: Jwt,
        language: String,
    ): User {
        if (language !in setOf("de", "en")) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported language")
        val user = currentUser(jwt)
        val changed =
            template.updateFirst(
                query(where("_id").`is`(user.id).and("status").`is`(UserStatus.ACTIVE)),
                Update.update("language", language).set("updatedAt", Instant.now()),
                User::class.java,
            )
        if (changed.matchedCount != 1L) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Account unavailable")
        return currentUser(jwt)
    }

    private fun provision(
        query: Query,
        jwt: Jwt,
    ): User {
        val email = identity.verifiedEmail(jwt)
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val update =
            Update()
                .setOnInsert("_id", UUID.randomUUID().toString())
                .setOnInsert("firebaseUserId", jwt.subject)
                .setOnInsert("email", email)
                .setOnInsert("status", UserStatus.ACTIVE)
                .setOnInsert("isAdmin", false)
                .setOnInsert("language", "de")
                .setOnInsert("createdAt", now)
                .setOnInsert("updatedAt", now)
        try {
            template.upsert(query, update, User::class.java)
        } catch (ex: DuplicateKeyException) {
            // ponytail: the unique subject index chooses one concurrent first-login winner; read it below.
        }
        return template.findOne(query, User::class.java) ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)
    }
}
