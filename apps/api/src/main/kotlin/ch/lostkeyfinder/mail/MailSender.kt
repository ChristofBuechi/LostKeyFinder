package ch.lostkeyfinder.mail

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.stereotype.Service

@ConfigurationProperties("mail")
data class MailProperties(
    val host: String = "",
    val port: Int = 587,
    val username: String = "",
    val password: String = "",
    val from: String = "",
    val startTls: Boolean = true,
)

@Configuration
@EnableConfigurationProperties(MailProperties::class)
class MailConfiguration

@Service
class MailSender(
    private val properties: MailProperties,
) {
    private val sender =
        JavaMailSenderImpl().apply {
            host = properties.host
            port = properties.port
            username = properties.username
            password = properties.password
            javaMailProperties["mail.smtp.auth"] = properties.username.isNotBlank().toString()
            javaMailProperties["mail.smtp.starttls.enable"] = properties.startTls.toString()
            javaMailProperties["mail.smtp.starttls.required"] = properties.startTls.toString()
            javaMailProperties["mail.smtp.connectiontimeout"] = "5000"
            javaMailProperties["mail.smtp.timeout"] = "5000"
            javaMailProperties["mail.smtp.writetimeout"] = "5000"
        }

    fun send(
        to: String,
        subject: String,
        text: String,
    ) {
        check(properties.host.isNotBlank() && properties.from.isNotBlank()) { "Mail transport is not configured" }
        sender.send(
            SimpleMailMessage().apply {
                setFrom(properties.from)
                setTo(to)
                this.subject = subject
                this.text = text
            },
        )
    }
}
