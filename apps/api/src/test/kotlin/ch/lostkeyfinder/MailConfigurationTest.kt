package ch.lostkeyfinder

import ch.lostkeyfinder.mail.MailSender
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.mail.javamail.JavaMailSenderImpl
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MailConfigurationTest {
    private val runner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration::class.java))
            .withUserConfiguration(MailSender::class.java)

    @Test fun `missing or incomplete mail configuration skips delivery without connecting`() {
        for (properties in listOf(
            emptyList(),
            listOf("spring.mail.host=127.0.0.1", "spring.mail.port=1"),
            listOf("mail.from=notify@example.test"),
            listOf("spring.mail.host=", "mail.from=notify@example.test"),
            listOf("spring.mail.host=127.0.0.1", "spring.mail.port=1", "mail.from=notify@example.test", "spring.mail.username=user"),
            listOf("spring.mail.host=127.0.0.1", "spring.mail.port=1", "mail.from=notify@example.test", "spring.mail.password=secret"),
        )) {
            runner.withPropertyValues(*properties.toTypedArray()).run { context ->
                assertNull(context.startupFailure)
                val mail = context.getBean(MailSender::class.java)
                assertFalse(mail.configured)
                mail.send("recipient@example.test", "Subject", "Body")
            }
        }
    }

    @Test fun `SMTP environment variables bind the Boot transport without a chat module`() {
        runner
            .withInitializer(ConfigDataApplicationContextInitializer())
            .withPropertyValues(
                "SMTP_HOST=smtp.example.test",
                "SMTP_PORT=2525",
                "SMTP_USERNAME=test-user",
                "SMTP_PASSWORD=test-password",
                "MAIL_FROM=notify@example.test",
                "SMTP_START_TLS=false",
            ).run { context ->
                val sender = context.getBean(JavaMailSenderImpl::class.java)
                assertEquals("smtp.example.test", sender.host)
                assertEquals(2525, sender.port)
                assertEquals("test-user", sender.username)
                assertEquals("test-password", sender.password)
                assertTrue(context.getBean(MailSender::class.java).configured)
                assertEquals("true", sender.javaMailProperties["mail.smtp.auth"])
                assertEquals("false", sender.javaMailProperties["mail.smtp.starttls.enable"])
                assertEquals("false", sender.javaMailProperties["mail.smtp.starttls.required"])
                assertFalse(context.containsBean("chatNotifications"))
                assertFalse(context.containsBean("chatNotificationJob"))
            }
    }

    @Test fun `SMTP defaults retain strict TLS finite timeouts and unauthenticated relay support`() {
        runner
            .withInitializer(ConfigDataApplicationContextInitializer())
            .withPropertyValues("SMTP_HOST=smtp.example.test", "MAIL_FROM=notify@example.test")
            .run { context ->
                val sender = context.getBean(JavaMailSenderImpl::class.java)
                assertEquals(587, sender.port)
                assertTrue(context.getBean(MailSender::class.java).configured)
                assertEquals("false", sender.javaMailProperties["mail.smtp.auth"])
                for (property in listOf("starttls.enable", "starttls.required")) {
                    assertEquals("true", sender.javaMailProperties["mail.smtp.$property"])
                }
                for (property in listOf("connectiontimeout", "timeout", "writetimeout")) {
                    assertEquals("5000", sender.javaMailProperties["mail.smtp.$property"])
                }
            }
    }
}
