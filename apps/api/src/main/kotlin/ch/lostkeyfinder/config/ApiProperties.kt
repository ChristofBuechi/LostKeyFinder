package ch.lostkeyfinder.config

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.net.URI

@Validated
@ConfigurationProperties("api")
data class ApiProperties(
    @field:NotBlank val version: String = "0.1.0",
    val environment: String = "development",
    val allowedOrigins: List<String> = listOf("http://localhost:4200"),
) {
    @get:AssertTrue(message = "environment must be development, test or production")
    val isEnvironmentValid: Boolean
        get() = environment in setOf("development", "test", "production")

    @get:AssertTrue(message = "allowed-origins must contain HTTP(S) origins without paths, credentials or queries")
    val isOriginsValid: Boolean
        get() =
            allowedOrigins.isNotEmpty() &&
                allowedOrigins.all { raw ->
                    runCatching {
                        val uri = URI(raw.trim())
                        uri.scheme in setOf("http", "https") && uri.host != null && uri.userInfo == null &&
                            (uri.port == -1 || uri.port in 1..65535) &&
                            uri.rawPath.isNullOrEmpty() && uri.rawQuery == null && uri.rawFragment == null
                    }.getOrDefault(false)
                }
}
