package ch.lostkeyfinder.tags

import org.springframework.boot.context.properties.ConfigurationProperties
import java.util.Base64

@ConfigurationProperties("rate-limits")
class RateLimitProperties(
    val hmacKey: String = "",
) {
    init {
        if (hmacKey.isNotBlank()) {
            require(
                Base64.getDecoder().decode(hmacKey).size >= 32,
            ) { "Rate limit key must contain at least 32 random bytes" }
        }
    }

    fun decodedKey(): ByteArray? = if (hmacKey.isBlank()) null else Base64.getDecoder().decode(hmacKey)

    override fun toString(): String = "RateLimitProperties(hmacKey=<redacted>)"
}
