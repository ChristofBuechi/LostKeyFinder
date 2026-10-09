package ch.lostkeyfinder.identity

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.server.ResponseStatusException
import java.net.http.HttpClient
import java.time.Duration
import java.time.Instant

@Component
class SupabaseIdentity(
    private val properties: IdentityProperties,
    builder: RestClient.Builder,
) {
    private val client =
        builder
            .baseUrl(properties.supabaseUrl.toString().trimEnd('/'))
            .requestFactory(
                JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build())
                    .apply { setReadTimeout(Duration.ofSeconds(5)) },
            ).build()

    fun verifiedEmail(jwt: Jwt): String {
        if (properties.publishableKey.isBlank()) throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)
        val user =
            try {
                client
                    .get()
                    .uri("/auth/v1/user")
                    .header("apikey", properties.publishableKey)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${jwt.tokenValue}")
                    .retrieve()
                    .body(SupabaseUser::class.java) ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)
            } catch (ex: RestClientResponseException) {
                throw ResponseStatusException(
                    if (ex.statusCode.value() in
                        setOf(401, 403)
                    ) {
                        HttpStatus.UNAUTHORIZED
                    } else {
                        HttpStatus.SERVICE_UNAVAILABLE
                    },
                )
            } catch (ex: RestClientException) {
                throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)
            }
        if (user.id != jwt.subject) throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identity mismatch")
        if (user.emailConfirmedAt == null) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Verified email required")
        val email =
            try {
                normalizeEmail(requireNotNull(user.email)).also {
                    require(it == normalizeEmail(requireNotNull(jwt.getClaimAsString("email"))))
                }
            } catch (ex: IllegalArgumentException) {
                throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email identity")
            }
        return email
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class SupabaseUser(
    val id: String,
    val email: String?,
    @param:JsonProperty("email_confirmed_at") val emailConfirmedAt: Instant?,
)
