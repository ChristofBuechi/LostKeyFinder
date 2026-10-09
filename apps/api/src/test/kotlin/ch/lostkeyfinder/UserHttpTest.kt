package ch.lostkeyfinder

import ch.lostkeyfinder.users.User
import ch.lostkeyfinder.users.UserStatus
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.SignedJWT
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
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("ci")
@Import(InMemoryMongoConfiguration::class, TestIdentityConfiguration::class)
class UserHttpTest {
    @Autowired lateinit var mvc: MockMvc

    @Autowired lateinit var template: MongoTemplate

    @Autowired lateinit var identity: TestIdentityProvider
    private val mapper = JsonMapper.builder().build()

    @BeforeEach fun reset() {
        template.remove(Query(), User::class.java)
        template.indexOps(User::class.java).createIndex(Index().on("supabaseUserId", Sort.Direction.ASC).unique())
        identity.reset()
    }

    @Test fun `missing credentials return stateless problem details with correlation ID`() {
        mvc.get("/api/v1/me?token=secret").andExpect {
            status { isUnauthorized() }
            header { string("WWW-Authenticate", "Bearer") }
            header { exists("X-Correlation-ID") }
            header { doesNotExist("Set-Cookie") }
            jsonPath("$.type") { value("/problems/unauthorized") }
            jsonPath("$.instance") { value("/api/v1/me") }
            jsonPath("$.correlationId") { exists() }
        }
    }

    @Test fun `signature issuer audience time and subject are all enforced`() {
        val otherKey = ECKeyGenerator(Curve.P_256).generate()
        val valid = SignedJWT.parse(identity.token())
        val symmetric =
            SignedJWT(
                JWSHeader(JWSAlgorithm.HS256),
                valid.jwtClaimsSet,
            ).apply { sign(MACSigner(ByteArray(32) { 1 })) }.serialize()
        val invalid =
            listOf(
                "not-a-token",
                symmetric,
                identity.token(signingKey = otherKey),
                identity.token(issuer = "https://foreign.example/auth/v1"),
                identity.token(audience = "other"),
                identity.token(audience = null),
                identity.token(expires = Instant.now().minusSeconds(120)),
                identity.token(expires = null),
                identity.token(notBefore = Instant.now().plusSeconds(120)),
                identity.token(subject = null),
                identity.token(subject = "invalid-uuid"),
            )
        for (token in invalid) mvc.get("/api/v1/me") { header("Authorization", "Bearer $token") }.andExpect { status { isUnauthorized() } }
        assertEquals(0, identity.userRequests.get())
        assertEquals(0L, template.count(Query(), User::class.java))
    }

    @Test fun `first access provisions a normalized user and repeated access preserves identity and timestamps`() {
        val token = identity.token()
        val first =
            mvc
                .get("/api/v1/me") { header("Authorization", "Bearer $token") }
                .andExpect {
                    status { isOk() }
                    jsonPath("$.email") { value("owner@example.com") }
                    jsonPath("$.status") { value("ACTIVE") }
                    jsonPath("$._id") { doesNotExist() }
                    jsonPath("$.supabaseUserId") { doesNotExist() }
                    header { doesNotExist("Set-Cookie") }
                }.andReturn()
                .response.contentAsString
        val second =
            mvc
                .get("/api/v1/me") { header("Authorization", "Bearer $token") }
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        assertEquals(first, second)
        assertEquals(4, UUID.fromString(mapper.readTree(first)["id"].asString()).version())
        assertEquals(1, identity.userRequests.get())
        assertEquals(1L, template.count(Query(), User::class.java))
    }

    @Test fun `local disabled and deleting status revoke the next request despite an admin role claim`() {
        val token = identity.token()
        mvc.get("/api/v1/me") { header("Authorization", "Bearer $token") }.andExpect { status { isOk() } }
        for (status in listOf(UserStatus.DISABLED, UserStatus.DELETING)) {
            template.updateFirst(Query(), Update.update("status", status), User::class.java)
            mvc.get("/api/v1/me") { header("Authorization", "Bearer $token") }.andExpect { status { isForbidden() } }
        }
        assertEquals(1, identity.userRequests.get())
    }

    @Test fun `email alone never joins two identities`() {
        val responses =
            (1..2).map {
                val token = identity.token()
                mvc
                    .get("/api/v1/me") { header("Authorization", "Bearer $token") }
                    .andExpect { status { isOk() } }
                    .andReturn()
                    .response.contentAsString
            }
        assertNotEquals(mapper.readTree(responses[0])["id"].asString(), mapper.readTree(responses[1])["id"].asString())
        assertEquals(2L, template.count(Query(), User::class.java))
    }

    @Test fun `editable metadata cannot substitute for verified provider email`() {
        identity.confirmed = false
        val token = identity.token()
        mvc.get("/api/v1/me") { header("Authorization", "Bearer $token") }.andExpect { status { isForbidden() } }
        assertEquals(0L, template.count(Query(), User::class.java))
    }

    @Test fun `provider subject and email must match the signed identity`() {
        val token = identity.token()
        identity.responseSubject = UUID.randomUUID().toString()
        mvc.get("/api/v1/me") { header("Authorization", "Bearer $token") }.andExpect { status { isUnauthorized() } }
        identity.responseSubject = null
        identity.responseEmail = "different@example.com"
        mvc.get("/api/v1/me") { header("Authorization", "Bearer $token") }.andExpect { status { isUnauthorized() } }
        assertEquals(0L, template.count(Query(), User::class.java))
    }

    @Test fun `provider failures are safe and cannot create a user`() {
        val token = identity.token()
        for ((upstream, expected) in listOf(401 to 401, 403 to 401, 500 to 503)) {
            identity.responseStatus = upstream
            val response =
                mvc
                    .get("/api/v1/me?token=secret") { header("Authorization", "Bearer $token") }
                    .andExpect { status { isEqualTo(expected) } }
                    .andReturn()
                    .response.contentAsString
            assertFalse(response.contains("secret"))
            assertFalse(response.contains(token))
        }
        assertEquals(0L, template.count(Query(), User::class.java))
    }

    @Test fun `existing users do not need a provider call on every request`() {
        val token = identity.token()
        mvc.get("/api/v1/me") { header("Authorization", "Bearer $token") }.andExpect { status { isOk() } }
        identity.responseStatus = 500
        mvc.get("/api/v1/me") { header("Authorization", "Bearer $token") }.andExpect { status { isOk() } }
        assertEquals(1, identity.userRequests.get())
    }

    @Test fun `concurrent first access produces exactly one local user`() {
        val token = identity.token()
        val ids =
            Executors.newFixedThreadPool(4).use { executor ->
                (1..8)
                    .map {
                        executor.submit<String> {
                            val response =
                                mvc
                                    .get("/api/v1/me") { header("Authorization", "Bearer $token") }
                                    .andExpect { status { isOk() } }
                                    .andReturn()
                                    .response.contentAsString
                            mapper.readTree(response)["id"].asString()
                        }
                    }.map { it.get(10, TimeUnit.SECONDS) }
            }
        assertEquals(1, ids.toSet().size)
        assertEquals(1L, template.count(Query(), User::class.java))
    }
}
