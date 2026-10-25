package ch.lostkeyfinder.tags

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.net.URI

@ConfigurationProperties("tags")
data class TagProperties(
    val publicBaseUrl: String = "",
) {
    init {
        if (publicBaseUrl.isNotBlank()) {
            val uri = URI(publicBaseUrl)
            require(
                uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
                    uri.path in listOf("", "/"),
            ) {
                "Public tag base URL must be an HTTPS origin"
            }
        }
    }
}

@Configuration
@EnableConfigurationProperties(TagProperties::class, RateLimitProperties::class)
class TagConfiguration
