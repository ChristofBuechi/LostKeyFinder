package ch.lostkeyfinder

import ch.lostkeyfinder.config.ApiProperties
import ch.lostkeyfinder.config.MongoConfiguration
import com.mongodb.MongoClientSettings
import jakarta.validation.Validation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConfigurationTest {
    private val factory = Validation.buildDefaultValidatorFactory()
    private val validator = factory.validator

    @AfterEach fun close() {
        factory.close()
    }

    @Test fun `defaults allow local development`() {
        assertTrue(validator.validate(ApiProperties()).isEmpty())
    }

    @Test fun `unknown environments and unsafe origins fail validation`() {
        assertTrue(validator.validate(ApiProperties(environment = "preview")).isNotEmpty())
        for (origin in listOf(
            "https://example.com/path",
            "https://example.com/",
            "https://example.com?secret=1",
            "ftp://example.com",
            "https://user@example.com",
            "not-a-url",
        )) {
            assertTrue(validator.validate(ApiProperties(allowedOrigins = listOf(origin))).isNotEmpty(), origin)
        }
    }

    @Test fun `comma separated origins can contain surrounding whitespace`() {
        assertTrue(validator.validate(ApiProperties(allowedOrigins = listOf("https://one.example", " https://two.example"))).isEmpty())
    }

    @Test fun `production connection accepts driver parsed OIDC configuration`() {
        validateMongo(
            "mongodb://example.com:443/dev1?tls=true&loadBalanced=true&retryWrites=false&authMechanism=MONGODB-OIDC&authMechanismProperties=ENVIRONMENT:gcp,TOKEN_RESOURCE:FIRESTORE",
        )
    }

    @Test fun `production connection rejects missing transport and spoofed OIDC properties`() {
        for (uri in listOf(
            "mongodb://example.com/dev1",
            "mongodb://example.com/dev1?authMechanism=MONGODB-OIDC&ENVIRONMENT%3Agcp&TOKEN_RESOURCE%3AFIRESTORE",
            "mongodb://example.com/dev1?tls=true&loadBalanced=true&retryWrites=false&authMechanism=MONGODB-OIDC&authMechanismProperties=ENVIRONMENT:azure,TOKEN_RESOURCE:FIRESTORE",
        )) {
            assertFailsWith<IllegalArgumentException> { validateMongo(uri) }
        }
    }

    @Test fun `production profile requires a connection URI`() {
        val environment = MockEnvironment().apply { setActiveProfiles("prod") }
        assertFailsWith<IllegalArgumentException> {
            MongoConfiguration().mongoSettings(environment).customize(MongoClientSettings.builder())
        }
    }

    @Test fun `production profile enforces OIDC even when legacy environment says development`() {
        val environment =
            MockEnvironment()
                .withProperty("spring.mongodb.uri", "mongodb://localhost:27017/dev1?tls=true&loadBalanced=true&retryWrites=false")
                .withProperty("api.environment", "development")
                .withProperty("NODE_ENV", "development")
                .apply { setActiveProfiles("prod") }
        assertFailsWith<IllegalArgumentException> {
            MongoConfiguration().mongoSettings(environment).customize(MongoClientSettings.builder())
        }
    }

    private fun validateMongo(uri: String) {
        val environment = MockEnvironment().withProperty("spring.mongodb.uri", uri).apply { setActiveProfiles("prod") }
        MongoConfiguration()
            .mongoSettings(environment)
            .customize(MongoClientSettings.builder())
    }
}
