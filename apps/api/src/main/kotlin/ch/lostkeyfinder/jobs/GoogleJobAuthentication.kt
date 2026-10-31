package ch.lostkeyfinder.jobs

import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Component
class GoogleJobAuthentication {
    private val decoder by lazy { NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build() }

    fun requireServiceAccount(
        authorization: String?,
        audience: String,
        serviceAccount: String,
    ) {
        if (audience.isBlank() || serviceAccount.isBlank()) throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)
        try {
            require(authorization?.startsWith("Bearer ") == true)
            val jwt = decoder.decode(authorization.substring(7))
            require(!googleJobValidator(audience, serviceAccount).validate(jwt).hasErrors())
        } catch (ex: Exception) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        }
    }
}

internal fun googleJobValidator(
    audience: String,
    serviceAccount: String,
) = DelegatingOAuth2TokenValidator(
    JwtValidators.createDefaultWithIssuer("https://accounts.google.com"),
    JwtClaimValidator<Instant>("exp") { true },
    JwtClaimValidator<List<String>>("aud") { it == listOf(audience) },
    JwtClaimValidator<String>("email") { it == serviceAccount },
    JwtClaimValidator<Boolean>("email_verified") { it },
)
