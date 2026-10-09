package ch.lostkeyfinder

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
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
            registry.add("identity.supabase-url") { identity.origin }
            registry.add("identity.publishable-key") { "test-publishable-key" }
        }
}

class TestIdentityProvider : AutoCloseable {
    val key: ECKey = ECKeyGenerator(Curve.P_256).keyID("test-key").generate()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val origin: String get() = "http://127.0.0.1:${server.address.port}"
    val issuer: String get() = "$origin/auth/v1"
    var confirmed = true
    var responseSubject: String? = null
    var responseEmail = "Owner@Example.com"
    var responseStatus = 200
    val userRequests = AtomicInteger()

    init {
        server.createContext("/auth/v1/.well-known/jwks.json") { exchange ->
            val body = JWKSet(key.toPublicJWK()).toString().toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/auth/v1/user") { exchange ->
            userRequests.incrementAndGet()
            val token = exchange.requestHeaders.getFirst("Authorization")?.removePrefix("Bearer ")
            val subject = runCatching { SignedJWT.parse(token).jwtClaimsSet.subject }.getOrNull()
            val status =
                if (exchange.requestHeaders.getFirst("apikey") == "test-publishable-key" &&
                    subject != null
                ) {
                    responseStatus
                } else {
                    401
                }
            val body =
                JsonMapper.builder().build().writeValueAsBytes(
                    mapOf(
                        "id" to (responseSubject ?: subject),
                        "email" to responseEmail,
                        "email_confirmed_at" to if (confirmed) "2026-01-01T00:00:00Z" else null,
                        "user_metadata" to mapOf("email_verified" to true),
                        "message" to "upstream-secret",
                    ),
                )
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
    }

    fun token(
        subject: String? = UUID.randomUUID().toString(),
        email: String = "Owner@Example.com",
        issuer: String = this.issuer,
        audience: String? = "authenticated",
        expires: Instant? = Instant.now().plusSeconds(600),
        notBefore: Instant? = null,
        signingKey: ECKey = key,
    ): String {
        val claims =
            JWTClaimsSet
                .Builder()
                .issuer(issuer)
                .subject(subject)
                .claim("email", email)
                .claim("role", "admin")
                .claim("user_metadata", mapOf("email_verified" to true))
        audience?.let { claims.audience(it) }
        expires?.let { claims.expirationTime(Date.from(it)) }
        notBefore?.let { claims.notBeforeTime(Date.from(it)) }
        return SignedJWT(JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.keyID).build(), claims.build())
            .apply {
                sign(ECDSASigner(signingKey))
            }.serialize()
    }

    fun reset() {
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
