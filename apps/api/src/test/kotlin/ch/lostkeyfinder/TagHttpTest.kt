package ch.lostkeyfinder

import ch.lostkeyfinder.tags.InventoryStatus
import ch.lostkeyfinder.tags.Tag
import ch.lostkeyfinder.tags.TagOrigin
import ch.lostkeyfinder.tags.TagService
import ch.lostkeyfinder.tags.TagStatus
import ch.lostkeyfinder.users.User
import org.bson.Document
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("ci")
@Import(InMemoryMongoConfiguration::class, TestIdentityConfiguration::class)
class TagHttpTest {
    @Autowired lateinit var mvc: MockMvc

    @Autowired lateinit var template: MongoTemplate

    @Autowired lateinit var identity: TestIdentityProvider

    @Autowired lateinit var tags: TagService
    private val mapper = JsonMapper.builder().build()

    @BeforeEach fun reset() {
        template.remove(Query(), Tag::class.java)
        template.remove(Query(), User::class.java)
        template.remove(Query(), "tagCreationLimits")
        template.indexOps(User::class.java).createIndex(Index().on("firebaseUserId", Sort.Direction.ASC).unique())
        template.indexOps(Tag::class.java).createIndex(Index().on("publicId", Sort.Direction.ASC).unique())
        template.indexOps(Tag::class.java).createIndex(Index().on("creationKey", Sort.Direction.ASC).unique().sparse())
        identity.reset()
    }

    private fun login(): String {
        val token = identity.token()
        mvc.get("/api/v1/me") { header("Authorization", "Bearer $token") }.andExpect {
            status { isOk() }
            jsonPath("$.isAdmin") { value(false) }
        }
        return token
    }

    private fun create(
        token: String,
        key: String = UUID.randomUUID().toString(),
        body: String = "{\"label\":\"Keys\"}",
    ): String =
        mvc
            .post("/api/v1/tags") {
                header("Authorization", "Bearer $token")
                header("Idempotency-Key", key)
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect {
                status { isOk() }
                jsonPath("$.origin") { value("SELF_CREATED") }
                jsonPath("$.status") { value("ACTIVE") }
                jsonPath("$.creationKey") { doesNotExist() }
                jsonPath("$._id") { doesNotExist() }
                header { string("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate") }
            }.andReturn()
            .response.contentAsString

    @Test fun `creation is atomic and retries preserve public identity and owner`() {
        val token = login()
        val key = UUID.randomUUID().toString()
        val first = create(token, key)
        assertEquals(first, create(token, key.uppercase()))
        val node = mapper.readTree(first)
        assertEquals(4, UUID.fromString(node["publicId"].asString()).version())
        assertEquals(4, UUID.fromString(node["id"].asString()).version())
        assertEquals(1L, template.count(Query(), Tag::class.java))
        assertEquals(template.findAll(User::class.java).single().id, node["primaryOwnerUserId"].asString())
        mvc
            .post("/api/v1/tags") {
                header("Authorization", "Bearer $token")
                header("Idempotency-Key", key)
                contentType = MediaType.APPLICATION_JSON
                content = "{\"label\":\"Other\"}"
            }.andExpect { status { isConflict() } }
    }

    @Test fun `concurrent retries create exactly one tag and keys are scoped by owner`() {
        val token = login()
        val key = UUID.randomUUID().toString()
        val results =
            Executors.newFixedThreadPool(4).use { pool ->
                (1..8).map { pool.submit<String> { create(token, key) } }.map { it.get(10, TimeUnit.SECONDS) }
            }
        assertEquals(1, results.toSet().size)
        val other = login()
        create(other, key)
        assertEquals(2L, template.count(Query(), Tag::class.java))
    }

    @Test fun `owner reads and changes only their own available tags`() {
        val token = login()
        val id = mapper.readTree(create(token))["id"].asString()
        val other = login()
        mvc.get("/api/v1/tags") { header("Authorization", "Bearer $other") }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(0) }
        }
        mvc.get("/api/v1/tags/$id") { header("Authorization", "Bearer $other") }.andExpect { status { isNotFound() } }
        for ((actor, state, expected) in listOf(
            Triple(other, "INACTIVE", 404),
            Triple(token, "BLOCKED", 400),
            Triple(token, "INACTIVE", 200),
        )) {
            mvc
                .patch("/api/v1/tags/$id/status") {
                    header("Authorization", "Bearer $actor")
                    contentType = MediaType.APPLICATION_JSON
                    content = "{\"status\":\"$state\"}"
                }.andExpect { status { isEqualTo(expected) } }
        }
        mvc.get("/api/v1/tags/$id") { header("Authorization", "Bearer $token") }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("INACTIVE") }
        }
        template.updateFirst(query(where("_id").`is`(id)), Update.update("status", TagStatus.RETIRED), Tag::class.java)
        mvc
            .patch("/api/v1/tags/$id/status") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = "{\"status\":\"ACTIVE\"}"
            }.andExpect { status { isNotFound() } }
    }

    @Test fun `admin is database controlled and revocation applies to the next request`() {
        val token = login()
        create(token)
        mvc.get("/api/v1/admin/tags") { header("Authorization", "Bearer $token") }.andExpect { status { isForbidden() } }
        template.updateFirst(Query(), Update.update("isAdmin", true), User::class.java)
        mvc.get("/api/v1/me") { header("Authorization", "Bearer $token") }.andExpect { jsonPath("$.isAdmin") { value(true) } }
        mvc.get("/api/v1/admin/tags") { header("Authorization", "Bearer $token") }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(1) }
        }
        template.updateFirst(Query(), Update.update("isAdmin", false), User::class.java)
        mvc.get("/api/v1/admin/tags") { header("Authorization", "Bearer $token") }.andExpect { status { isForbidden() } }
        template.updateFirst(Query(), Update.update("isAdmin", true).set("status", "DISABLED"), User::class.java)
        mvc.get("/api/v1/admin/tags") { header("Authorization", "Bearer $token") }.andExpect { status { isForbidden() } }
    }

    @Test fun `legacy documents default to non admin and requests cannot grant themselves rights`() {
        val token = login()
        template.updateFirst(Query(), Update().unset("isAdmin"), User::class.java)
        mvc.get("/api/v1/me") { header("Authorization", "Bearer $token") }.andExpect { jsonPath("$.isAdmin") { value(false) } }
        mvc
            .post("/api/v1/tags") {
                header("Authorization", "Bearer $token")
                header("Idempotency-Key", UUID.randomUUID())
                contentType = MediaType.APPLICATION_JSON
                content = "{\"label\":\"X\",\"isAdmin\":true,\"primaryOwnerUserId\":\"foreign\"}"
            }.andExpect { status { isBadRequest() } }
        assertEquals(0L, template.count(Query(), Tag::class.java))
    }

    @Test fun `admin filters and cursor pagination handle both origins with duplicate timestamps`() {
        val token = login()
        val owner = template.findAll(User::class.java).single()
        template.updateFirst(Query(), Update.update("isAdmin", true), User::class.java)
        val time = Instant.parse("2026-01-01T00:00:00Z")
        repeat(28) {
            template.insert(
                Tag(
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    TagOrigin.SELF_CREATED,
                    owner.id,
                    TagStatus.ACTIVE,
                    "",
                    time,
                    time,
                ),
            )
        }
        val unassigned =
            Tag(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                TagOrigin.PREPRODUCED,
                null,
                TagStatus.INACTIVE,
                "",
                time.minusMillis(1),
                time,
                batchId = UUID.randomUUID().toString(),
                inventoryStatus = InventoryStatus.PREPARED,
            )
        template.insert(unassigned)

        fun page(cursor: String? = null): String =
            mvc
                .get("/api/v1/admin/tags") {
                    header("Authorization", "Bearer $token")
                    cursor?.let { param("cursor", it) }
                }.andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        val first = mapper.readTree(page())
        assertEquals(25, first["items"].size())
        val second = mapper.readTree(page(first["nextCursor"].asString()))
        assertEquals(4, second["items"].size())
        val ids = (first["items"].toList() + second["items"].toList()).map { it["id"].asString() }
        assertEquals(29, ids.toSet().size)
        mvc
            .get("/api/v1/admin/tags?origin=PREPRODUCED&status=INACTIVE&assigned=false&publicId=${unassigned.publicId.uppercase()}") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.items.length()") { value(1) }
                jsonPath("$.items[0].primaryOwnerUserId") { doesNotExist() }
            }
        assertEquals(
            28,
            tags.list(ownerId = owner.id, origin = TagOrigin.SELF_CREATED, status = TagStatus.ACTIVE, assigned = true).let {
                it.items.size +
                    tags.list(ownerId = owner.id, cursor = it.nextCursor).items.size
            },
        )
    }

    @Test fun `malformed input is rejected and anonymous requests cannot access tags`() {
        for (path in listOf("/api/v1/tags", "/api/v1/admin/tags")) mvc.get(path).andExpect { status { isUnauthorized() } }
        val token = login()
        for (body in listOf("{\"label\":\"${"x".repeat(81)}\"}", "{\"label\":null}")) {
            mvc
                .post("/api/v1/tags") {
                    header("Authorization", "Bearer $token")
                    header("Idempotency-Key", UUID.randomUUID())
                    contentType = MediaType.APPLICATION_JSON
                    content = body
                }.andExpect { status { isBadRequest() } }
        }
        mvc
            .post("/api/v1/tags") {
                header("Authorization", "Bearer $token")
                header("Idempotency-Key", "bad-key")
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect { status { isBadRequest() } }
        for (cursor in listOf("bad", "no_${UUID.randomUUID()}", "-1_${UUID.randomUUID()}", "1_bad")) {
            mvc
                .get("/api/v1/tags") {
                    header("Authorization", "Bearer $token")
                    param("cursor", cursor)
                }.andExpect { status { isBadRequest() } }
        }
        mvc.get("/api/v1/tags/bad") { header("Authorization", "Bearer $token") }.andExpect { status { isBadRequest() } }
        val legacy = template.getCollection("users").find().first()!!
        assertEquals(false, legacy["isAdmin"])
        assertEquals(0L, template.count(Query(), Tag::class.java))
    }

    @Test fun `daily creation limit cannot prevent readback of a successful request`() {
        val token = login()
        val key = UUID.randomUUID().toString()
        val created = create(token, key)
        val owner = template.findAll(User::class.java).single()
        val limitId = "${owner.id}:${LocalDate.now(ZoneOffset.UTC)}"
        template.updateFirst(query(where("_id").`is`(limitId)), Update.update("count", 100), "tagCreationLimits")
        assertEquals(created, create(token, key))
        mvc
            .post("/api/v1/tags") {
                header("Authorization", "Bearer $token")
                header("Idempotency-Key", UUID.randomUUID())
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect { status { isTooManyRequests() } }
        assertEquals(1L, template.count(Query(), Tag::class.java))
    }
}
