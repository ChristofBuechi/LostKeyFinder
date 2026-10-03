package ch.lostkeyfinder

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestComponent
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("ci")
@Import(InMemoryMongoConfiguration::class, HealthFakeConfiguration::class, FailureController::class)
class FoundationHttpTest {
    @Autowired lateinit var mvc: MockMvc

    @Autowired lateinit var mongo: MongoHealthFake

    @Autowired lateinit var context: ApplicationContext

    @BeforeEach fun reset() {
        mongo.ready = true
    }

    @Test fun `CI context uses the embedded Mongo server`() {
        val client = context.getBean(com.mongodb.client.MongoClient::class.java)
        val ping = client.getDatabase("foundation").runCommand(org.bson.Document("ping", 1))
        kotlin.test.assertEquals(1.0, ping.getDouble("ok"))
    }

    @Test fun `valid correlation IDs are retained and MDC is cleared after the request`() {
        val id = "cb60e48e-166b-4d95-a9b1-8b0b6d023e99"
        mvc.get("/api/v1/health/live") { header("X-Correlation-ID", id) }.andExpect {
            header { string("X-Correlation-ID", id) }
        }
        kotlin.test.assertNull(MDC.get("correlationId"))
    }

    @Test fun `readiness uses real Actuator health group and fake Mongo contributor`() {
        mvc.get("/api/v1/health/ready").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("ok") }
            header { exists("X-Correlation-ID") }
        }
        mongo.ready = false
        mvc.get("/api/v1/health/live").andExpect { status { isOk() } }
        mvc.get("/api/v1/health/ready").andExpect {
            status { isServiceUnavailable() }
            content { contentType("application/problem+json") }
            jsonPath("$.type") { value("/problems/dependency-unavailable") }
        }
    }

    @Test fun `version and unknown routes preserve versioned contract`() {
        mvc.get("/api/v1/version").andExpect {
            status { isOk() }
            jsonPath("$.version") { value("0.1.0") }
            jsonPath("$.environment") { value("test") }
        }
        mvc.get("/health/live").andExpect { status { isNotFound() } }
    }

    @Test fun `CORS accepts normalized origins and rejects unknown origins`() {
        mvc.get("/api/v1/health/live") { header("Origin", "https://second.example") }.andExpect {
            status { isOk() }
            header { string("Access-Control-Allow-Origin", "https://second.example") }
        }
        mvc.get("/api/v1/health/live") { header("Origin", "https://attacker.example") }.andExpect {
            status { isForbidden() }
            header { doesNotExist("Access-Control-Allow-Origin") }
        }
    }

    @Test fun `unexpected errors do not leak messages stacks or query strings`() {
        val result =
            mvc
                .get("/api/v1/test/failure?token=secret") { header("X-Correlation-ID", "untrusted-value") }
                .andExpect {
                    status { isInternalServerError() }
                    jsonPath("$.instance") { value("/api/v1/test/failure") }
                    jsonPath("$.detail") { doesNotExist() }
                    jsonPath("$.stack") { doesNotExist() }
                }.andReturn()
        assertFalse(result.response.contentAsString.contains("secret"))
        assertTrue(result.response.getHeader("X-Correlation-ID") != "untrusted-value")
    }

    @Test fun `DTO validation returns safe problem details`() {
        mvc
            .post("/api/v1/test/validate") {
                contentType = org.springframework.http.MediaType.APPLICATION_JSON
                content = "{\"name\":\"\"}"
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.detail") { value("Request validation failed") }
                jsonPath("$.errors") { isArray() }
            }
    }

    @Test fun `client errors retain explicit safe details and standard Allow headers`() {
        mvc.get("/api/v1/test/conflict").andExpect {
            status { isConflict() }
            jsonPath("$.type") { value("/problems/conflict") }
            jsonPath("$.detail") { value("Operation already exists") }
        }
        mvc.get("/api/v1/test/validate").andExpect {
            status { isMethodNotAllowed() }
            header { string("Allow", "POST") }
            jsonPath("$.type") { value("/problems/request-failed") }
        }
    }

    @Test fun `unknown DTO fields are rejected`() {
        mvc
            .post("/api/v1/test/validate") {
                contentType = org.springframework.http.MediaType.APPLICATION_JSON
                content = "{\"name\":\"valid\",\"unexpected\":true}"
            }.andExpect { status { isBadRequest() } }
    }
}

data class TestCommand(
    @field:NotBlank val name: String,
)

@RestController
@TestComponent
class FailureController {
    @GetMapping("/api/v1/test/conflict")
    fun conflict(): Nothing = throw ResponseStatusException(HttpStatus.CONFLICT, "Operation already exists")

    @GetMapping("/api/v1/test/failure")
    fun failure(): Nothing = error("database password=secret")

    @PostMapping("/api/v1/test/validate")
    fun validate(
        @Valid @RequestBody command: TestCommand,
    ) = command
}
