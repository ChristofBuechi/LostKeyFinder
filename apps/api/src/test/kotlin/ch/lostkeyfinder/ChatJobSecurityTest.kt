package ch.lostkeyfinder

import ch.lostkeyfinder.chat.ChatMailProperties
import ch.lostkeyfinder.chat.notificationJobValidator
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatJobSecurityTest {
    @Test fun `only a live Google token for the exact scheduler audience and verified service account passes`() {
        val properties =
            ChatMailProperties(jobAudience = "https://example.test", jobServiceAccount = "scheduler@example.iam.gserviceaccount.com")
        val valid =
            mapOf<String, Any>(
                "iss" to "https://accounts.google.com",
                "aud" to listOf(properties.jobAudience),
                "email" to properties.jobServiceAccount,
                "email_verified" to true,
                "exp" to Instant.now().plusSeconds(600),
            )

        fun rejected(changes: Map<String, Any?>): Boolean {
            val claims = valid.toMutableMap()
            changes.forEach { (key, value) -> if (value == null) claims.remove(key) else claims[key] = value }
            val jwt =
                Jwt
                    .withTokenValue("verified-by-decoder")
                    .header("alg", "RS256")
                    .claims { it.putAll(claims) }
                    .build()
            return notificationJobValidator(properties).validate(jwt).hasErrors()
        }
        assertFalse(rejected(emptyMap()))
        for (changes in listOf(
            mapOf("iss" to "https://securetoken.google.com/project"),
            mapOf("aud" to listOf("https://other.example")),
            mapOf("email" to "other@example.iam.gserviceaccount.com"),
            mapOf("email_verified" to false),
            mapOf("exp" to Instant.now().minusSeconds(600)),
            mapOf("exp" to null),
            mapOf("email" to null),
        )) {
            assertTrue(rejected(changes))
        }
    }
}
