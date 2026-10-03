package ch.lostkeyfinder.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfiguration(
    private val properties: ApiProperties,
) : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry
            .addMapping("/api/v1/**")
            .allowedOrigins(*properties.allowedOrigins.map(String::trim).toTypedArray())
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("Content-Type", "Authorization", "Idempotency-Key", "X-Correlation-ID")
            .exposedHeaders("X-Correlation-ID")
            .allowCredentials(false)
    }
}
