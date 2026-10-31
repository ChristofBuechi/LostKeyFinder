package ch.lostkeyfinder.identity

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.google.auth.oauth2.GoogleCredentials
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.net.http.HttpClient
import java.time.Duration
import java.util.function.Supplier

@Configuration
class FirebaseAdminConfiguration {
    @Bean
    fun firebaseAdminCredentials(): Supplier<GoogleCredentials> =
        Supplier {
            GoogleCredentials.getApplicationDefault().createScoped("https://www.googleapis.com/auth/identitytoolkit")
        }
}

@Component
class FirebaseAdmin(
    private val properties: IdentityProperties,
    builder: RestClient.Builder,
    credentials: Supplier<GoogleCredentials>,
) {
    // Credentials are only needed by the worker; local development can start without ADC.
    private val credentials by lazy { credentials.get() }
    private val client =
        builder
            .baseUrl(properties.accountsUrl.toString().trimEnd('/'))
            .requestFactory(
                JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()).apply {
                    setReadTimeout(Duration.ofSeconds(10))
                },
            ).build()

    fun deleteUser(firebaseUserId: String) {
        require(firebaseUserId.isNotBlank() && firebaseUserId.length <= 128)
        credentials.refreshIfExpired()
        try {
            client
                .post()
                .uri("/v1/projects/{project}/accounts:delete", properties.projectId)
                .headers { it.setBearerAuth(requireNotNull(credentials.accessToken).tokenValue) }
                .body(mapOf("localId" to firebaseUserId))
                .retrieve()
                .toBodilessEntity()
        } catch (ex: RestClientResponseException) {
            val code = runCatching { ex.getResponseBodyAs(FirebaseError::class.java)?.error?.message }.getOrNull()
            if (ex.statusCode.value() == 400 && code == "USER_NOT_FOUND") return
            throw ex
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class FirebaseError(
    val error: FirebaseErrorDetail? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FirebaseErrorDetail(
    val message: String? = null,
)
