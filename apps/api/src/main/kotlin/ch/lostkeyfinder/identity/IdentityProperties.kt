package ch.lostkeyfinder.identity

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.net.URI

@Validated
@ConfigurationProperties("identity")
data class IdentityProperties(
    @field:NotBlank val projectId: String = "lost-key-finder-dev",
    val apiKey: String = "",
    val jwkSetUri: URI = URI("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"),
    val accountsUrl: URI = URI("https://identitytoolkit.googleapis.com"),
) {
    val issuer: String get() = "https://securetoken.google.com/$projectId"
}
