package ch.lostkeyfinder.chat

import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
class ChatNotificationJob(
    private val notices: ChatNotifications,
    private val properties: ChatMailProperties,
) {
    private val decoder by lazy {
        NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build().also {
            it.setJwtValidator(notificationJobValidator(properties))
        }
    }

    @PostMapping("/api/v1/internal/chat-notifications/dispatch")
    @Operation(hidden = true)
    fun dispatch(
        @RequestHeader("Authorization", required = false) authorization: String?,
    ): Map<String, Int> {
        if (properties.jobAudience.isBlank() ||
            properties.jobServiceAccount.isBlank()
        ) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)
        }
        try {
            require(authorization?.startsWith("Bearer ") == true)
            decoder.decode(authorization.substring(7))
        } catch (ex: Exception) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        }
        return mapOf("processed" to notices.dispatch())
    }
}

internal fun notificationJobValidator(properties: ChatMailProperties) =
    DelegatingOAuth2TokenValidator(
        JwtValidators.createDefaultWithIssuer("https://accounts.google.com"),
        JwtClaimValidator<java.time.Instant>("exp") { true },
        JwtClaimValidator<List<String>>("aud") { values -> values == listOf(properties.jobAudience) },
        JwtClaimValidator<String>("email") { value -> value == properties.jobServiceAccount },
        JwtClaimValidator<Boolean>("email_verified") { value -> value },
    )
