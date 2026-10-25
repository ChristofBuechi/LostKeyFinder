package ch.lostkeyfinder

import ch.lostkeyfinder.operations.SchemaMigration
import ch.lostkeyfinder.tags.PublicRequestLimits
import ch.lostkeyfinder.tags.RateLimitProperties
import ch.lostkeyfinder.tags.TagCreationLimit
import ch.lostkeyfinder.users.User
import ch.lostkeyfinder.users.UserStatus
import org.bson.Document
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Base64
import java.util.Date
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("ci")
@Import(InMemoryMongoConfiguration::class, TestIdentityConfiguration::class)
class SchemaSimplificationTest {
    @Autowired lateinit var template: MongoTemplate
    private val day get() = LocalDate.now(ZoneOffset.UTC).toString()
    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { 7 })

    @BeforeEach fun reset() {
        for (name in listOf(
            "users",
            "tagCreationLimits",
            "publicRequestLimits",
            "rateLimitKeys",
            "rateLimits",
        )) {
            template.remove(Query(), name)
        }
    }

    private fun user(): User {
        val now = Instant.now()
        return template.insert(
            User(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "probe@example.com", UserStatus.ACTIVE, now, now),
        )
    }

    private fun window(id: String): Document =
        template.findById(id, Document::class.java, "users")!!.get("tagCreationLimit", Document::class.java)

    @Test fun `embedded reset and reservations are atomic and do not create phantom users`() {
        val owner = user()
        template.updateFirst(
            query(where("_id").`is`(owner.id)),
            Update.update(
                "tagCreationLimit",
                mapOf(
                    "day" to "2020-01-01",
                    "count" to 100,
                ),
            ),
            "users",
        )
        val limiter = TagCreationLimit(template)
        Executors.newFixedThreadPool(4).use { pool ->
            (1..20).map { pool.submit { limiter.reserve(owner.id) } }.forEach { it.get(10, TimeUnit.SECONDS) }
        }
        assertEquals(day, window(owner.id).getString("day"))
        assertEquals(20, window(owner.id).getInteger("count"))
        template.updateFirst(query(where("_id").`is`(owner.id)), Update.update("tagCreationLimit.count", 98), "users")
        val results =
            Executors.newFixedThreadPool(4).use { pool ->
                (1..8)
                    .map {
                        pool.submit<Int> {
                            try {
                                limiter.reserve(owner.id)
                                200
                            } catch (
                                ex: ResponseStatusException,
                            ) {
                                ex.statusCode.value()
                            }
                        }
                    }.map { it.get(10, TimeUnit.SECONDS) }
            }
        assertEquals(2, results.count { it == 200 })
        assertEquals(6, results.count { it == 429 })
        assertEquals(100, window(owner.id).getInteger("count"))
        assertEquals(0L, template.getCollection("tagCreationLimits").countDocuments())
        assertEquals(429, assertFailsWith<ResponseStatusException> { limiter.reserve(UUID.randomUUID().toString()) }.statusCode.value())
        assertEquals(1L, template.count(Query(), User::class.java))
        val future = LocalDate.now(ZoneOffset.UTC).plusDays(1).toString()
        template.updateFirst(query(where("_id").`is`(owner.id)), Update.update("tagCreationLimit.day", future), "users")
        assertEquals(429, assertFailsWith<ResponseStatusException> { limiter.reserve(owner.id) }.statusCode.value())
        assertEquals(future, window(owner.id).getString("day"))
        assertEquals(100, window(owner.id).getInteger("count"))
    }

    @Test fun `external keys are validated redacted and never written to the database`() {
        assertFailsWith<IllegalArgumentException> { RateLimitProperties("invalid") }
        assertFailsWith<IllegalArgumentException> { RateLimitProperties(Base64.getEncoder().encodeToString(ByteArray(8))) }
        assertFalse(RateLimitProperties(key).toString().contains(key))
        assertEquals(
            503,
            assertFailsWith<ResponseStatusException> {
                PublicRequestLimits(template, RateLimitProperties()).reserve("test", "peer", 1)
            }.statusCode.value(),
        )
        val limiter = PublicRequestLimits(template, RateLimitProperties(key))
        limiter.reserve("test", "private@example.com", 1)
        assertEquals(429, assertFailsWith<ResponseStatusException> { limiter.reserve("test", "private@example.com", 1) }.statusCode.value())
        assertEquals(1L, template.getCollection("rateLimits").countDocuments())
        assertEquals(0L, template.getCollection("rateLimitKeys").countDocuments())
        assertFalse(
            template
                .getCollection("rateLimits")
                .find()
                .first()
                .toString()
                .contains("private@example.com"),
        )
    }

    @Test fun `migration preserves new counts imports old counts once and reconciles late deltas`() {
        val owner = user()
        repeat(2) { TagCreationLimit(template).reserve(owner.id) }
        val legacyId = "${owner.id}:$day"
        template.insert(Document("_id", legacyId).append("count", 3), "tagCreationLimits")
        template.insert(
            Document("_id", "bucket").append("count", 4).append("expiresAt", Date.from(Instant.now().plusSeconds(3600))),
            "publicRequestLimits",
        )
        template.insert(Document("_id", "bucket").append("count", 2), "rateLimits")
        val migration = SchemaMigration(template)
        migration.importCounters()
        migration.importCounters()
        assertEquals(5, (window(owner.id)["count"] as Number).toInt())
        assertEquals(6, (template.findById("bucket", Document::class.java, "rateLimits")!!["count"] as Number).toInt())
        template.updateFirst(query(where("_id").`is`(legacyId)), Update().inc("count", 1), "tagCreationLimits")
        template.updateFirst(query(where("_id").`is`("bucket")), Update().inc("count", 1), "publicRequestLimits")
        migration.importCounters()
        assertEquals(6, (window(owner.id)["count"] as Number).toInt())
        assertEquals(7, (template.findById("bucket", Document::class.java, "rateLimits")!!["count"] as Number).toInt())
        assertEquals(1L, migration.inspect().getValue("tagCreationLimits"))
    }

    @Test fun `cleanup requires stopped writers and matching key and keeps business collections`() {
        val owner = user()
        template.insert(Document("_id", "public-v1").append("key", key), "rateLimitKeys")
        val migration = SchemaMigration(template)
        assertFailsWith<IllegalArgumentException> { migration.cleanup(key, false) }
        assertFailsWith<IllegalArgumentException> { migration.cleanup(Base64.getEncoder().encodeToString(ByteArray(32) { 1 }), true) }
        migration.cleanup(key, true)
        migration.cleanup(key, true)
        assertTrue(template.collectionExists("users"))
        assertEquals(owner.id, template.findById(owner.id, User::class.java)!!.id)
        assertFalse(template.collectionExists("rateLimitKeys"))
        assertFalse(template.collectionExists("tagCreationLimits"))
        assertFalse(template.collectionExists("publicRequestLimits"))
    }
}
