package ch.lostkeyfinder

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpServer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistrar
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.time.Instant
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@TestConfiguration(proxyBeanMethods = false)
class TestIdentityConfiguration {
    @Bean(destroyMethod = "close")
    fun testIdentity() = TestIdentityProvider()

    @Bean
    fun identityProperties(identity: TestIdentityProvider) =
        DynamicPropertyRegistrar { registry ->
            registry.add("identity.jwk-set-uri") { "${identity.origin}/jwks" }
            registry.add("identity.accounts-url") { identity.origin }
            registry.add("identity.project-id") { "test-project" }
            registry.add("identity.api-key") { "test-publishable-key" }
        }
}

class TestIdentityProvider : AutoCloseable {
    val key: RSAKey = RSAKeyGenerator(2048).keyID("test-key").generate()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val origin: String get() = "http://127.0.0.1:${server.address.port}"
    val issuer: String get() = "https://securetoken.google.com/test-project"
    var confirmed = true
    var responseSubject: String? = null
    var responseEmail = "Owner@Example.com"
    var responseStatus = 200
    var deleteStatus = 200
    val deleteRequests = AtomicInteger()
    private val deleted =
        java.util.concurrent.ConcurrentHashMap
            .newKeySet<String>()
    val userRequests = AtomicInteger()

    init {
        server.createContext("/jwks") { exchange ->
            val body = JWKSet(key.toPublicJWK()).toString().toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/v1/accounts:lookup") { exchange ->
            userRequests.incrementAndGet()
            val token =
                JsonMapper
                    .builder()
                    .build()
                    .readTree(exchange.requestBody)["idToken"]
                    .asString()
            val subject = runCatching { SignedJWT.parse(token).jwtClaimsSet.subject }.getOrNull()
            val status =
                if (exchange.requestURI.query == "key=test-publishable-key" &&
                    subject != null
                ) {
                    if (subject in deleted) 400 else responseStatus
                } else {
                    401
                }
            val body =
                JsonMapper.builder().build().writeValueAsBytes(
                    mapOf(
                        "users" to
                            listOf(
                                mapOf(
                                    "localId" to (responseSubject ?: subject),
                                    "email" to responseEmail,
                                    "emailVerified" to confirmed,
                                    "user_metadata" to mapOf("email_verified" to true),
                                    "message" to "upstream-secret",
                                ),
                            ),
                    ),
                )
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/v1/accounts:delete") { exchange ->
            deleteRequests.incrementAndGet()
            val token =
                JsonMapper
                    .builder()
                    .build()
                    .readTree(exchange.requestBody)["idToken"]
                    .asString()
            val subject = SignedJWT.parse(token).jwtClaimsSet.subject
            if (deleteStatus == 200) deleted.add(subject)
            val body = if (deleteStatus == 200) "{}" else """{"error":{"message":"UNAVAILABLE"}}"""
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(deleteStatus, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        server.start()
    }

    fun token(
        subject: String? = UUID.randomUUID().toString(),
        email: String = "Owner@Example.com",
        issuer: String = this.issuer,
        audience: String? = "test-project",
        expires: Instant? = Instant.now().plusSeconds(600),
        notBefore: Instant? = null,
        issuedAt: Instant? = Instant.now().minusSeconds(10),
        authTime: Long? = Instant.now().minusSeconds(10).epochSecond,
        signingKey: RSAKey = key,
    ): String {
        val claims =
            JWTClaimsSet
                .Builder()
                .issuer(issuer)
                .subject(subject)
                .claim("email", email)
                .claim("auth_time", authTime)
                .claim("role", "admin")
                .claim("user_metadata", mapOf("email_verified" to true))
        issuedAt?.let { claims.issueTime(Date.from(it)) }
        audience?.let { claims.audience(it) }
        expires?.let { claims.expirationTime(Date.from(it)) }
        notBefore?.let { claims.notBeforeTime(Date.from(it)) }
        return SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build(), claims.build())
            .apply {
                sign(RSASSASigner(signingKey))
            }.serialize()
    }

    fun reset() {
        deleteStatus = 200
        deleteRequests.set(0)
        deleted.clear()
        confirmed = true
        responseSubject = null
        responseEmail = "Owner@Example.com"
        responseStatus = 200
        userRequests.set(0)
    }

    override fun close() {
        server.stop(0)
    }
}
