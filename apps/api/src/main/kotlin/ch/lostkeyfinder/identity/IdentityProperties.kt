package ch.lostkeyfinder.identity

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.validation.annotation.Validated
import java.net.URI

@Validated
@ConfigurationProperties("identity")
data class IdentityProperties(
    val supabaseUrl: URI = URI("http://localhost:54321"),
    val publishableKey: String = "",
    @field:NotBlank val audience: String = "authenticated",
    val algorithm: SignatureAlgorithm = SignatureAlgorithm.ES256,
) {
    val issuer: String get() = "${supabaseUrl.toString().trimEnd('/')}/auth/v1"

    @get:AssertTrue(message = "supabase-url must be an HTTP(S) origin without credentials, query or fragment")
    val isUrlValid: Boolean
        get() =
            supabaseUrl.scheme in setOf("http", "https") && supabaseUrl.host != null &&
                supabaseUrl.userInfo == null && supabaseUrl.query == null && supabaseUrl.fragment == null &&
                supabaseUrl.path in setOf("", "/") && (supabaseUrl.port == -1 || supabaseUrl.port in 1..65535)
}
