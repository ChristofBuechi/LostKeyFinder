package ch.lostkeyfinder

import ch.lostkeyfinder.mail.MailSender
import ch.lostkeyfinder.tags.PublicRequestLimits
import ch.lostkeyfinder.tags.TagProperties
import ch.lostkeyfinder.translations.Translations
import ch.lostkeyfinder.users.DeletionConfirmations
import ch.lostkeyfinder.users.User
import ch.lostkeyfinder.users.UserStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.mock.env.MockEnvironment
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("ci")
@Import(InMemoryMongoConfiguration::class, TestIdentityConfiguration::class)
@ExtendWith(OutputCaptureExtension::class)
class DeletionConfirmationLogTest {
    @Autowired lateinit var template: MongoTemplate

    @Autowired lateinit var limits: PublicRequestLimits

    private fun user(): User {
        val now = Instant.now()
        return template.insert(
            User(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "owner@example.test", UserStatus.ACTIVE, now, now),
        )
    }

    private fun confirmations(
        mail: MailSender,
        vararg profiles: String,
    ) = DeletionConfirmations(
        template,
        mail,
        Translations(),
        TagProperties("https://example.test"),
        limits,
        MockEnvironment().withProperty("spring.profiles.active", profiles.joinToString(",")),
    )

    @Test fun `local profile fallback logs a usable expiring token but stores only hashes`(output: CapturedOutput) {
        val mail = Mockito.mock(MailSender::class.java)
        val user = user()
        val confirmations = confirmations(mail, "local")
        confirmations.send(user, user.email)
        val token = Regex("https://example.test/account/deletion-confirm#([A-Za-z0-9_-]{43})").find(output.all)!!.groupValues[1]
        val stored = template.findById(user.id, User::class.java)!!
        val proof = confirmations.requireCurrent(stored, token, user.email)
        assertFalse(proof.tokenHash == token)
        assertTrue(proof.expiresAt.isAfter(Instant.now()))
        assertTrue(proof.expiresAt.isBefore(Instant.now().plusSeconds(901)))
        assertEquals(UserStatus.ACTIVE, stored.status)
        assertEquals(0, template.getCollection("accountDeletions").countDocuments(org.bson.Document("userId", user.id)))
        Mockito.verify(mail, Mockito.never()).send(Mockito.anyString(), Mockito.anyString(), Mockito.anyString())
    }

    @ParameterizedTest
    @CsvSource("dev", "prod", "ci", "local+prod")
    fun `fallback cannot silently activate outside the local profile or alongside production`(
        profiles: String,
        output: CapturedOutput,
    ) {
        val user = user()
        val confirmations = confirmations(Mockito.mock(MailSender::class.java), *profiles.split('+').toTypedArray())
        assertEquals(503, assertFailsWith<ResponseStatusException> { confirmations.send(user, user.email) }.statusCode.value())
        assertEquals(null, template.findById(user.id, User::class.java)!!.deletionConfirmation)
        assertFalse(output.all.contains("account/deletion-confirm#"))
    }

    @Test fun `configured SMTP takes precedence and never logs its confirmation link`(output: CapturedOutput) {
        val mail = Mockito.mock(MailSender::class.java)
        Mockito.`when`(mail.configured).thenReturn(true)
        val user = user()
        confirmations(mail, "local").send(user, user.email)
        Mockito.verify(mail).send(Mockito.anyString(), Mockito.anyString(), Mockito.anyString())
        assertNotNull(template.findById(user.id, User::class.java)!!.deletionConfirmation)
        assertFalse(output.all.contains("account/deletion-confirm#"))
    }
}
