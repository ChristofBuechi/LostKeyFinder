package ch.lostkeyfinder

import org.bson.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Instant
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.springframework.data.mongodb.core.mapping.Document as MongoDocument

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("inmemory")
@Import(InMemoryMongoConfiguration::class)
class MongoPersistenceTest {
    @Autowired lateinit var template: MongoTemplate

    @Autowired lateinit var mvc: MockMvc

    @BeforeEach
    @AfterEach
    fun clearDocuments() {
        template.remove(Query(), PersistenceProbe::class.java)
    }

    @Test fun `Spring Data maps Kotlin dates enums and binary data to BSON and back`() {
        val probe = PersistenceProbe("mapping", ProbeState.DRAFT, Instant.parse("2026-01-02T03:04:05Z"), byteArrayOf(1, 2, 3))
        template.insert(probe)

        val restored = assertNotNull(template.findById(probe.id, PersistenceProbe::class.java))
        assertEquals(probe.id, restored.id)
        assertEquals(probe.state, restored.state)
        assertEquals(probe.createdAt, restored.createdAt)
        assertContentEquals(probe.bytes, restored.bytes)

        val stored = assertNotNull(template.findById(probe.id, Document::class.java, "persistenceProbes"))
        assertEquals("DRAFT", stored.getString("state"))
        assertEquals(probe.createdAt, stored.getDate("createdAt").toInstant())
    }

    @Test fun `filtered queries return only matching documents`() {
        template.insert(probe("draft", ProbeState.DRAFT))
        template.insert(probe("active", ProbeState.ACTIVE))

        val result = template.find(query(where("state").`is`(ProbeState.ACTIVE)), PersistenceProbe::class.java)

        assertEquals(listOf("active"), result.map { it.id })
    }

    @Test fun `conditional updates do not overwrite documents with a different state`() {
        template.insert(probe("update", ProbeState.DRAFT))
        val condition = query(where("_id").`is`("update").and("state").`is`(ProbeState.DRAFT))
        val update = Update.update("state", ProbeState.ACTIVE)

        val changed = template.updateFirst(condition, update, PersistenceProbe::class.java)
        val repeated = template.updateFirst(condition, update, PersistenceProbe::class.java)

        assertEquals(1L, changed.modifiedCount)
        assertEquals(0L, repeated.matchedCount)
        assertEquals(ProbeState.ACTIVE, template.findById("update", PersistenceProbe::class.java)?.state)
    }

    @Test fun `deleted documents cannot be read back`() {
        template.insert(probe("delete", ProbeState.DRAFT))

        val result = template.remove(query(where("_id").`is`("delete")), PersistenceProbe::class.java)

        assertEquals(1L, result.deletedCount)
        assertNull(template.findById("delete", PersistenceProbe::class.java))
    }

    @Test fun `readiness uses the real Actuator Mongo health contributor`() {
        mvc.get("/api/v1/health/ready").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("ok") }
        }
    }

    private fun probe(
        id: String,
        state: ProbeState,
    ) = PersistenceProbe(id, state, Instant.parse("2026-01-02T03:04:05Z"), byteArrayOf())
}

enum class ProbeState { DRAFT, ACTIVE }

@MongoDocument("persistenceProbes")
data class PersistenceProbe(
    @field:Id val id: String,
    val state: ProbeState,
    val createdAt: Instant,
    val bytes: ByteArray,
)
