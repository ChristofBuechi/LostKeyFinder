package ch.lostkeyfinder

import ch.lostkeyfinder.mail.MailConfiguration
import ch.lostkeyfinder.mail.MailProperties
import ch.lostkeyfinder.mail.MailSender
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class MailConfigurationTest {
    @Test fun `missing or incomplete mail configuration skips delivery without connecting`() {
        val incomplete =
            listOf(
                MailProperties(),
                MailProperties(host = "127.0.0.1", port = 1),
                MailProperties(from = "notify@example.test"),
                MailProperties(host = "127.0.0.1", port = 1, from = "notify@example.test", username = "user"),
                MailProperties(host = "127.0.0.1", port = 1, from = "notify@example.test", password = "secret"),
            )
        incomplete.forEach { properties ->
            val mail = MailSender(properties)
            assertFalse(mail.configured)
            mail.send("recipient@example.test", "Subject", "Body")
        }
    }

    @Test fun `general mail transport binds its own configuration without a chat module`() {
        ApplicationContextRunner()
            .withUserConfiguration(MailConfiguration::class.java, MailSender::class.java)
            .withPropertyValues(
                "mail.host=smtp.example.test",
                "mail.port=2525",
                "mail.username=test-user",
                "mail.password=test-password",
                "mail.from=notify@example.test",
                "mail.start-tls=false",
            ).run { context ->
                assertEquals(
                    MailProperties("smtp.example.test", 2525, "test-user", "test-password", "notify@example.test", false),
                    context.getBean(MailProperties::class.java),
                )
                assertNotNull(context.getBean(MailSender::class.java))
                assertFalse(context.containsBean("chatNotifications"))
                assertFalse(context.containsBean("chatNotificationJob"))
            }
    }
}
