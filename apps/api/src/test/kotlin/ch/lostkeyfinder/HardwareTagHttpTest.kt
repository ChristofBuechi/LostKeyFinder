package ch.lostkeyfinder

import ch.lostkeyfinder.tags.InventoryStatus
import ch.lostkeyfinder.tags.PublicRequestLimits
import ch.lostkeyfinder.tags.Tag
import ch.lostkeyfinder.tags.TagAudit
import ch.lostkeyfinder.tags.TagBatch
import ch.lostkeyfinder.tags.TagBatchService
import ch.lostkeyfinder.tags.TagProperties
import ch.lostkeyfinder.tags.TagRegistrationDraft
import ch.lostkeyfinder.tags.TagStatus
import ch.lostkeyfinder.tags.TagTransactions
import ch.lostkeyfinder.users.User
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
import org.springframework.test.web.servlet.post
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("ci")
@Import(InMemoryMongoConfiguration::class, TestIdentityConfiguration::class)
class HardwareTagHttpTest {
    @Autowired lateinit var mvc: MockMvc

    @Autowired lateinit var template: MongoTemplate

    @Autowired lateinit var identity: TestIdentityProvider

    @Autowired lateinit var limits: PublicRequestLimits

    @Autowired lateinit var transactions: TagTransactions
    private val mapper = JsonMapper.builder().build()

    @BeforeEach fun reset() {
        for (collection in listOf(
            "users",
            "tags",
            "tagBatches",
            "tagRegistrationDrafts",
            "auditLog",
            "publicRequestLimits",
        )) {
            template.remove(Query(), collection)
        }
        template.indexOps(User::class.java).createIndex(Index().on("firebaseUserId", Sort.Direction.ASC).unique())
        template.indexOps(Tag::class.java).createIndex(Index().on("publicId", Sort.Direction.ASC).unique())
        template
            .indexOps(
                Tag::class.java,
            ).createIndex(
                Index()
                    .on("batchId", Sort.Direction.ASC)
                    .on("position", Sort.Direction.ASC)
                    .unique()
                    .sparse(),
            )
        template.indexOps(TagBatch::class.java).createIndex(Index().on("creationKey", Sort.Direction.ASC).unique())
        identity.reset()
    }

    private fun login(
        admin: Boolean = false,
        email: String = "owner@example.com",
    ): String {
        identity.responseEmail = email
        val token = identity.token(email = email)
        val response =
            mvc
                .get("/api/v1/me") {
                    header("Authorization", "Bearer $token")
                }.andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        if (admin) {
            template.updateFirst(
                query(where("_id").`is`(mapper.readTree(response)["id"].asString())),
                Update.update("isAdmin", true),
                User::class.java,
            )
        }
        return token
    }

    private fun batch(
        token: String,
        count: Int = 3,
        key: String = UUID.randomUUID().toString(),
    ): String =
        mapper
            .readTree(
                mvc
                    .post("/api/v1/admin/tag-batches") {
                        header("Authorization", "Bearer $token")
                        header("Idempotency-Key", key)
                        contentType = MediaType.APPLICATION_JSON
                        content = "{\"count\":$count,\"label\":\"Sample\"}"
                    }.andExpect { status { isOk() } }
                    .andReturn()
                    .response.contentAsString,
            )["id"]
            .asString()

    private fun release(
        token: String,
        id: String,
    ) {
        mvc.post("/api/v1/admin/tag-batches/$id/release") { header("Authorization", "Bearer $token") }.andExpect { status { isOk() } }
    }

    private fun firstTag(batchId: String): Tag = template.findOne(query(where("batchId").`is`(batchId)), Tag::class.java)!!

    private fun start(
        publicId: String,
        email: String = "owner@example.com",
    ): String =
        mapper
            .readTree(
                mvc
                    .post("/api/v1/public/tags/$publicId/registration-drafts") {
                        contentType = MediaType.APPLICATION_JSON
                        content = mapper.writeValueAsString(mapOf("email" to email))
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.expiresAt") { exists() }
                        jsonPath("$.email") { doesNotExist() }
                    }.andReturn()
                    .response.contentAsString,
            )["flowId"]
            .asString()

    @Test fun `batch creation and release are idempotent with one audit each and CSV uses canonical URLs`() {
        val token = login(true)
        val key = UUID.randomUUID().toString()
        val id = batch(token, 100, key)
        assertEquals(id, batch(token, 100, key))
        assertEquals(100L, template.count(Query(), Tag::class.java))
        assertEquals(
            100,
            template
                .findAll(Tag::class.java)
                .map { it.publicId }
                .toSet()
                .size,
        )
        assertEquals(
            100,
            template
                .findAll(Tag::class.java)
                .map { it.position }
                .toSet()
                .size,
        )
        assertEquals(1L, template.count(Query(), TagAudit::class.java))
        val csv =
            mvc
                .get("/api/v1/admin/tag-batches/$id/export") { header("Authorization", "Bearer $token") }
                .andExpect {
                    status { isOk() }
                    content { contentTypeCompatibleWith("text/csv") }
                    header { string("Content-Disposition", "attachment; filename=tags-$id.csv") }
                }.andReturn()
                .response.contentAsString
        assertEquals(101, csv.trim().lines().size)
        for (tag in template.findAll(Tag::class.java)) {
            assertEquals(null, tag.primaryOwnerUserId)
            assertEquals(InventoryStatus.PREPARED, tag.inventoryStatus)
            assertEquals(true, csv.contains("https://example.test/f/${tag.publicId}"))
        }
        release(token, id)
        release(token, id)
        assertEquals(100L, template.count(query(where("inventoryStatus").`is`(InventoryStatus.RELEASED)), Tag::class.java))
        assertEquals(1L, template.count(query(where("action").`is`("BATCH_RELEASED")), TagAudit::class.java))
        mvc.get("/api/v1/admin/tag-batches/$id") { header("Authorization", "Bearer $token") }.andExpect {
            jsonPath("$.releasedAt") { exists() }
            jsonPath("$.creationKey") { doesNotExist() }
        }
    }

    @Test fun `global inventory summary and filters include assignments and both lifecycle dimensions`() {
        val token = login(true)
        val id = batch(token)
        release(token, id)
        val tag = firstTag(id)
        mvc.post("/api/v1/tags/${tag.publicId}/register") { header("Authorization", "Bearer $token") }.andExpect { status { isOk() } }
        template.updateFirst(
            query(where("batchId").`is`(id).and("primaryOwnerUserId").`is`(null)),
            Update.update("status", TagStatus.BLOCKED),
            Tag::class.java,
        )
        mvc.get("/api/v1/admin/tags/summary?batchId=$id") { header("Authorization", "Bearer $token") }.andExpect {
            status { isOk() }
            jsonPath("$.total") { value(3) }
            jsonPath("$.assigned") { value(1) }
            jsonPath("$.unassigned") { value(2) }
            jsonPath("$.blocked") { value(1) }
            jsonPath("$.released") { value(3) }
        }
        mvc
            .get("/api/v1/admin/tags?batchId=$id&inventoryStatus=RELEASED&assigned=true") {
                header("Authorization", "Bearer $token")
            }.andExpect { jsonPath("$.items.length()") { value(1) } }
        mvc
            .get(
                "/api/v1/admin/tags/${tag.id}",
            ) { header("Authorization", "Bearer $token") }
            .andExpect { jsonPath("$.position") { exists() } }
        mvc.get("/api/v1/tags/by-public/${tag.publicId}") { header("Authorization", "Bearer $token") }.andExpect { status { isOk() } }
        val other = login()
        mvc.get("/api/v1/tags/by-public/${tag.publicId}") { header("Authorization", "Bearer $other") }.andExpect { status { isNotFound() } }
        mvc.get("/api/v1/admin/tags/summary") { header("Authorization", "Bearer $other") }.andExpect { status { isForbidden() } }
    }

    @Test fun `public resolution is minimal and unknown unavailable and unprepared tags cannot be claimed`() {
        val token = login(true)
        val id = batch(token, 1)
        val tag = firstTag(id)
        for (publicId in listOf(UUID.randomUUID().toString(), tag.publicId)) {
            mvc.get("/api/v1/public/tags/$publicId").andExpect {
                status { isOk() }
                content { json("{\"action\":\"UNAVAILABLE\"}") }
            }
            mvc
                .post(
                    "/api/v1/tags/$publicId/register",
                ) { header("Authorization", "Bearer $token") }
                .andExpect { status { is4xxClientError() } }
        }
        release(token, id)
        mvc.get("/api/v1/public/tags/${tag.publicId}").andExpect { content { json("{\"action\":\"REGISTER\"}") } }
        for (state in listOf(TagStatus.INACTIVE, TagStatus.BLOCKED, TagStatus.RETIRED)) {
            template.updateFirst(query(where("_id").`is`(tag.id)), Update.update("status", state), Tag::class.java)
            mvc.get("/api/v1/public/tags/${tag.publicId}").andExpect { content { json("{\"action\":\"UNAVAILABLE\"}") } }
        }
        assertEquals(1L, template.count(Query(), Tag::class.java))
    }

    @Test fun `email registration requires exact verified identity and removes email on idempotent completion`() {
        val admin = login(true)
        val id = batch(admin, 1)
        release(admin, id)
        val tag = firstTag(id)
        val flow = start(tag.publicId, " OWNER@EXAMPLE.COM ")
        val wrong = login(email = "wrong@example.com")
        for (suffix in listOf("", "/complete")) {
            if (suffix.isEmpty()) {
                mvc
                    .get(
                        "/api/v1/tag-registrations/$flow",
                    ) { header("Authorization", "Bearer $wrong") }
                    .andExpect { status { isNotFound() } }
            } else {
                mvc
                    .post(
                        "/api/v1/tag-registrations/$flow/complete",
                    ) { header("Authorization", "Bearer $wrong") }
                    .andExpect { status { isNotFound() } }
            }
        }
        identity.responseEmail = "owner@example.com"
        mvc.get("/api/v1/tag-registrations/$flow").andExpect { status { isUnauthorized() } }
        mvc.get("/api/v1/tag-registrations/$flow") { header("Authorization", "Bearer $admin") }.andExpect {
            status { isOk() }
            jsonPath("$.completed") { value(false) }
            jsonPath("$.email") { doesNotExist() }
        }
        identity.confirmed = false
        mvc
            .post(
                "/api/v1/tag-registrations/$flow/complete",
            ) { header("Authorization", "Bearer $admin") }
            .andExpect { status { isForbidden() } }
        identity.confirmed = true
        repeat(2) {
            mvc.post("/api/v1/tag-registrations/$flow/complete") { header("Authorization", "Bearer $admin") }.andExpect {
                status { isOk() }
                jsonPath("$.registeredAt") { exists() }
            }
        }
        mvc
            .get("/api/v1/tag-registrations/$flow") {
                header("Authorization", "Bearer $admin")
            }.andExpect { jsonPath("$.completed") { value(true) } }
        assertEquals(null, template.findById(flow, TagRegistrationDraft::class.java)!!.email)
        assertEquals(1L, template.count(query(where("action").`is`("TAG_REGISTERED")), TagAudit::class.java))
        mvc.get("/api/v1/public/tags/${tag.publicId}").andExpect { content { json("{\"action\":\"REPORT_FOUND\"}") } }
        identity.responseEmail = "wrong@example.com"
        mvc
            .post(
                "/api/v1/tag-registrations/$flow/complete",
            ) { header("Authorization", "Bearer $wrong") }
            .andExpect { status { isNotFound() } }
    }

    @Test fun `expiry suspension and another winning owner are rechecked at completion`() {
        val admin = login(true)
        val id = batch(admin)
        release(admin, id)
        val tags = template.findAll(Tag::class.java)
        val expired = start(tags[0].publicId)
        template.updateFirst(
            query(where("_id").`is`(expired)),
            Update.update("expiresAt", Instant.now().minusSeconds(1)),
            TagRegistrationDraft::class.java,
        )
        mvc.get("/api/v1/tag-registrations/$expired") { header("Authorization", "Bearer $admin") }.andExpect { status { isNotFound() } }
        mvc
            .post(
                "/api/v1/tag-registrations/$expired/complete",
            ) { header("Authorization", "Bearer $admin") }
            .andExpect { status { isNotFound() } }
        val blocked = start(tags[1].publicId)
        template.updateFirst(query(where("_id").`is`(tags[1].id)), Update.update("status", TagStatus.BLOCKED), Tag::class.java)
        mvc
            .post(
                "/api/v1/tag-registrations/$blocked/complete",
            ) { header("Authorization", "Bearer $admin") }
            .andExpect { status { isConflict() } }
        val lost = start(tags[2].publicId)
        val other = login()
        mvc.post("/api/v1/tags/${tags[2].publicId}/register") { header("Authorization", "Bearer $other") }.andExpect { status { isOk() } }
        mvc
            .post(
                "/api/v1/tag-registrations/$lost/complete",
            ) { header("Authorization", "Bearer $admin") }
            .andExpect { status { isConflict() } }
        mvc.post("/api/v1/tags/${tags[2].publicId}/register") { header("Authorization", "Bearer $other") }.andExpect { status { isOk() } }
    }

    @Test fun `batches require admin invalid inputs fail and unknown resources stay unavailable`() {
        val owner = login()
        mvc
            .post("/api/v1/admin/tag-batches") {
                header("Authorization", "Bearer $owner")
                header("Idempotency-Key", UUID.randomUUID())
                contentType =
                    MediaType.APPLICATION_JSON
                content = "{\"count\":1}"
            }.andExpect { status { isForbidden() } }
        val admin = login(true)
        for (body in listOf("{\"count\":0}", "{\"count\":101}", "{\"count\":1.5}", "{}")) {
            mvc
                .post("/api/v1/admin/tag-batches") {
                    header("Authorization", "Bearer $admin")
                    header("Idempotency-Key", UUID.randomUUID())
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        body
                }.andExpect { status { isBadRequest() } }
        }
        val key = UUID.randomUUID().toString()
        batch(admin, 1, key)
        mvc
            .post("/api/v1/admin/tag-batches") {
                header("Authorization", "Bearer $admin")
                header("Idempotency-Key", key)
                contentType =
                    MediaType.APPLICATION_JSON
                content = "{\"count\":2}"
            }.andExpect { status { isConflict() } }
        val missing = UUID.randomUUID()
        for (path in listOf(
            "/api/v1/admin/tag-batches/$missing",
            "/api/v1/admin/tags/$missing",
            "/api/v1/tag-registrations/$missing",
        )) {
            mvc
                .get(path) {
                    header("Authorization", "Bearer $admin")
                }.andExpect { status { isNotFound() } }
        }
        mvc
            .post(
                "/api/v1/admin/tag-batches/$missing/release",
            ) { header("Authorization", "Bearer $admin") }
            .andExpect { status { isNotFound() } }
        mvc.get("/api/v1/admin/tags?batchId=bad") { header("Authorization", "Bearer $admin") }.andExpect { status { isBadRequest() } }
        mvc
            .post("/api/v1/public/tags/$missing/registration-drafts") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    "{\"email\":\"bad\"}"
            }.andExpect { status { isBadRequest() } }
        assertFailsWith<IllegalArgumentException> { TagProperties("http://example.test/path") }
        val noExport = TagBatchService(template, transactions, TagProperties())
        assertEquals(
            503,
            assertFailsWith<ResponseStatusException> {
                noExport.export(template.findAll(User::class.java).first(), missing.toString())
            }.statusCode.value(),
        )
    }

    @Test fun `batches paginate and daily public windows enforce limits without raw contexts`() {
        val token = login(true)
        repeat(26) { batch(token, 1) }
        val page =
            mvc
                .get("/api/v1/admin/tag-batches") {
                    header("Authorization", "Bearer $token")
                }.andExpect { jsonPath("$.items.length()") { value(25) } }
                .andReturn()
                .response.contentAsString
        val cursor = mapper.readTree(page)["nextCursor"].asString()
        mvc
            .get("/api/v1/admin/tag-batches") {
                header("Authorization", "Bearer $token")
                param("cursor", cursor)
            }.andExpect { jsonPath("$.items.length()") { value(1) } }
        limits.reserve("test", "private@example.com", 1)
        assertEquals(429, assertFailsWith<ResponseStatusException> { limits.reserve("test", "private@example.com", 1) }.statusCode.value())
        assertFalse(
            template
                .getCollection("publicRequestLimits")
                .find()
                .first()
                .toString()
                .contains("private@example.com"),
        )
        mvc.get("/api/v1/public/tags/bad").andExpect { status { isBadRequest() } }
    }
}
