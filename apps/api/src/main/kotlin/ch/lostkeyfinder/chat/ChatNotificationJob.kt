package ch.lostkeyfinder.chat

import ch.lostkeyfinder.jobs.GoogleJobAuthentication
import io.swagger.v3.oas.annotations.Operation
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@ConfigurationProperties("chat-notification-job")
data class ChatNotificationJobProperties(
    val audience: String = "",
    val serviceAccount: String = "",
)

@Configuration
@EnableConfigurationProperties(ChatNotificationJobProperties::class)
class ChatNotificationJobConfiguration

@RestController
class ChatNotificationJob(
    private val notices: ChatNotifications,
    private val properties: ChatNotificationJobProperties,
    private val authentication: GoogleJobAuthentication,
) {
    @PostMapping("/api/v1/internal/chat-notifications/dispatch")
    @Operation(hidden = true)
    fun dispatch(
        @RequestHeader("Authorization", required = false) authorization: String?,
    ): Map<String, Int> {
        authentication.requireServiceAccount(authorization, properties.audience, properties.serviceAccount)
        return mapOf("processed" to notices.dispatch())
    }
}
