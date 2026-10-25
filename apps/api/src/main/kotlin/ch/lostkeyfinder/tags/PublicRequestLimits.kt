package ch.lostkeyfinder.tags

import org.bson.Document
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class PublicRequestLimits(
    private val template: MongoTemplate,
) {
    // One private per-database key, shared across replicas. Raw IPs/emails never enter bucket documents.
    private val key: ByteArray by lazy {
        val selection = query(where("_id").`is`("public-v1"))
        try {
            val generated = Base64.getEncoder().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
            template.upsert(selection, Update().setOnInsert("key", generated), "rateLimitKeys")
        } catch (ex: DuplicateKeyException) {
            // Another replica initialized the same key.
        }
        Base64.getDecoder().decode(requireNotNull(template.findOne(selection, Document::class.java, "rateLimitKeys")).getString("key"))
    }

    fun reserve(
        scope: String,
        context: String,
        maximum: Int,
        seconds: Long = 3600,
    ) {
        val window = Instant.now().epochSecond / seconds
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
        val id = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal("$scope:$context:$window".toByteArray()))
        val selection = query(where("_id").`is`(id))
        try {
            template.upsert(
                selection,
                Update().setOnInsert("count", 0).setOnInsert(
                    "expiresAt",
                    Instant.ofEpochSecond((window + 2) * seconds),
                ),
                "publicRequestLimits",
            )
        } catch (ex: DuplicateKeyException) {
            // Another request initialized the same fixed window.
        }
        val result =
            template.updateFirst(
                query(where("_id").`is`(id).and("count").lt(maximum)),
                Update().inc("count", 1),
                "publicRequestLimits",
            )
        if (result.modifiedCount != 1L) throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests")
    }
}
