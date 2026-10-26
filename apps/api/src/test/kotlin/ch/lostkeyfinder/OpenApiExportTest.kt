package ch.lostkeyfinder

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("ci")
@Import(InMemoryMongoConfiguration::class)
class OpenApiExportTest {
    @Autowired lateinit var mvc: MockMvc

    @Test fun `export real contract without opening an HTTP port`() {
        val json =
            mvc
                .get("/v3/api-docs")
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        // Springdoc's server URL comes from MockMvc and is not our public API base URL.
        val mapper =
            tools.jackson.databind.json.JsonMapper
                .builder()
                .build()
        val document = mapper.readTree(json) as tools.jackson.databind.node.ObjectNode
        document.remove("servers")
        val me = document["paths"]["/api/v1/me"]["get"]
        assertEquals("UserController_me", me["operationId"].asString())
        assertTrue(me["security"][0].has("bearerAuth"))
        assertFalse(me.has("parameters"))
        assertEquals<Set<String>>(
            setOf("id", "email", "status", "createdAt", "updatedAt", "isAdmin"),
            document["components"]["schemas"]["CurrentUserResponse"]["required"].values().map { it.asString() }.toSet(),
        )
        assertEquals(
            setOf(
                "/api/v1/health/live",
                "/api/v1/health/ready",
                "/api/v1/version",
                "/api/v1/me",
                "/api/v1/tags",
                "/api/v1/tags/{tagId}",
                "/api/v1/tags/{tagId}/status",
                "/api/v1/tags/{tagId}/label",
                "/api/v1/admin/tags/{tagId}/label",
                "/api/v1/tags/{tagId}/release",
                "/api/v1/tags/{tagId}/history",
                "/api/v1/admin/tags/{tagId}/history",
                "/api/v1/admin/tags",
                "/api/v1/admin/tags/summary",
                "/api/v1/admin/tags/{tagId}",
                "/api/v1/tags/by-public/{publicId}",
                "/api/v1/admin/tag-batches",
                "/api/v1/admin/tag-batches/{batchId}",
                "/api/v1/admin/tag-batches/{batchId}/release",
                "/api/v1/admin/tag-batches/{batchId}/export",
                "/api/v1/public/tags/{publicId}",
                "/api/v1/public/tags/{publicId}/registration-drafts",
                "/api/v1/tag-registrations/{flowId}",
                "/api/v1/tag-registrations/{flowId}/complete",
                "/api/v1/tags/{publicId}/register",
            ),
            document["paths"].propertyNames().toSet(),
        )
        Files.writeString(Path.of("openapi.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(document))
    }
}
