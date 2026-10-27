package ch.lostkeyfinder

import ch.lostkeyfinder.translations.Translations
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TranslationsTest {
    private val translations = Translations()

    @Test fun `packaged catalogs render localized mails and substitute parameters literally`() {
        val url = "https://example.test/chats/id?value=\$1\\x{chatUrl}"
        for ((language, expected) in listOf("de" to "Du hast neue Nachrichten.", "en" to "You have new messages.")) {
            val message = translations.text(language, "emails.unreadMessages.body", mapOf("chatUrl" to url))
            assertEquals(expected, message.lineSequence().first())
            assertEquals(1, message.windowed(url.length).count { it == url })
        }
        assertEquals("Lost Key Finder: Neue Fundmeldung", translations.text("de", "emails.found.subject"))
        assertEquals("Lost Key Finder: Item found", translations.text("en", "emails.found.subject"))
        assertEquals(translations.text("de", "emails.found.subject"), translations.text("fr", "emails.found.subject"))
    }

    @Test fun `missing keys parameters and non-text values fail instead of sending incomplete mail`() {
        assertFailsWith<IllegalArgumentException> { translations.text("de", "emails.missing") }
        assertFailsWith<IllegalArgumentException> { translations.text("de", "emails.found") }
        assertFailsWith<NoSuchElementException> { translations.text("en", "emails.found.body") }
    }
}
