package ch.lostkeyfinder.mail

import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.stereotype.Service

@Service("mailDelivery")
class MailSender(
    private val sender: JavaMailSenderImpl?,
    @param:Value("\${mail.from:}") private val from: String,
) {
    val configured =
        sender != null && !sender.host.isNullOrBlank() && from.isNotBlank() &&
            (sender.username.isNullOrBlank() == sender.password.isNullOrBlank())

    init {
        sender?.let { it.javaMailProperties["mail.smtp.auth"] = (!it.username.isNullOrBlank()).toString() }
    }

    fun send(
        to: String,
        subject: String,
        text: String,
    ) {
        if (!configured) return
        sender!!.send(
            SimpleMailMessage().apply {
                setFrom(from)
                setTo(to)
                this.subject = subject
                this.text = text
            },
        )
    }
}
