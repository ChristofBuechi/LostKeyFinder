package ch.lostkeyfinder.identity

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
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

@Component
class FirebaseIdentity(
    private val properties: IdentityProperties,
    builder: RestClient.Builder,
) {
    private val client =
        builder
            .baseUrl(properties.accountsUrl.toString().trimEnd('/'))
            .requestFactory(
                JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build())
                    .apply { setReadTimeout(Duration.ofSeconds(5)) },
            ).build()

    fun deleteAccount(jwt: Jwt) {
        if (properties.apiKey.isBlank()) throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)
        try {
            client
                .post()
                .uri("/v1/accounts:delete?key={key}", properties.apiKey)
                .body(mapOf("idToken" to jwt.tokenValue))
                .retrieve()
                .toBodilessEntity()
        } catch (ex: RestClientResponseException) {
            val code = runCatching { ex.getResponseBodyAs(FirebaseError::class.java)?.error?.message }.getOrNull()
            if (ex.statusCode.value() == 400 && code == "USER_NOT_FOUND") return
            throw ResponseStatusException(
                if (code ==
                    "CREDENTIAL_TOO_OLD_LOGIN_AGAIN"
                ) {
                    HttpStatus.CONFLICT
                } else {
                    HttpStatus.SERVICE_UNAVAILABLE
                },
                "Account deletion incomplete",
            )
        } catch (ex: RestClientException) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Account deletion incomplete")
        }
    }

    fun verifiedEmail(jwt: Jwt): String {
        if (properties.apiKey.isBlank()) throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)
        val user =
            try {
                client
                    .post()
                    .uri("/v1/accounts:lookup?key={key}", properties.apiKey)
                    .body(mapOf("idToken" to jwt.tokenValue))
                    .retrieve()
                    .body(FirebaseUsers::class.java)
                    ?.users
                    ?.singleOrNull() ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
            } catch (ex: RestClientResponseException) {
                throw ResponseStatusException(
                    if (ex.statusCode.value() in
                        setOf(400, 401, 403)
                    ) {
                        HttpStatus.UNAUTHORIZED
                    } else {
                        HttpStatus.SERVICE_UNAVAILABLE
                    },
                )
            } catch (ex: RestClientException) {
                throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)
            }
        if (user.localId != jwt.subject) throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identity mismatch")
        if (!user.emailVerified) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Verified email required")
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
data class FirebaseUsers(
    val users: List<FirebaseUser> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FirebaseUser(
    val localId: String,
    val email: String?,
    val emailVerified: Boolean = false,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FirebaseError(
    val error: FirebaseErrorDetail? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FirebaseErrorDetail(
    val message: String? = null,
)
