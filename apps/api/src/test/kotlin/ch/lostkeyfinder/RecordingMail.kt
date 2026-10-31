package ch.lostkeyfinder

import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSenderImpl
import java.util.concurrent.CopyOnWriteArrayList

class RecordingMail : JavaMailSenderImpl() {
    val messages = CopyOnWriteArrayList<SimpleMailMessage>()

    init {
        host = "test-only"
    }

    override fun send(message: SimpleMailMessage) {
        messages.add(SimpleMailMessage(message))
    }

    fun token(): String =
        messages
            .last()
            .text!!
            .substringAfter("/account/deletion-confirm#")
            .lineSequence()
            .first()
}
