package ch.lostkeyfinder.users

import ch.lostkeyfinder.identity.SupabaseIdentity
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
    private val identity: SupabaseIdentity,
) {
    fun currentUser(jwt: Jwt): User {
        val query = query(where("supabaseUserId").`is`(jwt.subject))
        val user = template.findOne(query, User::class.java) ?: provision(query, jwt)
        if (!user.canAccess) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Account unavailable")
        return user
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
                .setOnInsert("supabaseUserId", jwt.subject)
                .setOnInsert("email", email)
                .setOnInsert("status", UserStatus.ACTIVE)
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
