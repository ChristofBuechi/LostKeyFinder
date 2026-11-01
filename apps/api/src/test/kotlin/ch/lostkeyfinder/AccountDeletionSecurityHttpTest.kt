package ch.lostkeyfinder

import ch.lostkeyfinder.mail.MailSender
import ch.lostkeyfinder.users.AccountDeletion
import ch.lostkeyfinder.users.AccountDeletionWorker
import ch.lostkeyfinder.users.User
import ch.lostkeyfinder.users.UserStatus
import ch.lostkeyfinder.users.deletionKey
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("ci")
@Import(InMemoryMongoConfiguration::class, TestIdentityConfiguration::class)
class AccountDeletionSecurityHttpTest {
    @Autowired lateinit var mvc: MockMvc

    @Autowired lateinit var template: MongoTemplate

    @Autowired lateinit var identity: TestIdentityProvider

    @Autowired lateinit var worker: AccountDeletionWorker

    @MockitoBean(name = "mailDelivery")
    lateinit var mail: MailSender
    private val outbox = mutableListOf<Pair<String, String>>()

    private data class Session(
        val token: String,
        val user: User,
    )

    @BeforeEach fun setup() {
        listOf("users", "accountDeletions", "tags", "rateLimits", "auditLog").forEach { template.remove(Query(), it) }
        identity.reset()
        outbox.clear()
        Mockito.`when`(mail.configured).thenReturn(true)
        Mockito
            .doAnswer { call ->
                outbox.add(call.getArgument<String>(0) to call.getArgument<String>(2))
                null
            }.`when`(mail)
            .send(Mockito.anyString(), Mockito.anyString(), Mockito.anyString())
    }

    private fun session(email: String = "${UUID.randomUUID()}@example.com"): Session {
        val subject = UUID.randomUUID().toString()
        identity.responseEmail = email
        val token = identity.token(subject = subject, email = email)
        mvc.get("/api/v1/me") { header("Authorization", "Bearer $token") }.andExpect { status { isOk() } }
        return Session(token, template.findOne(query(where("firebaseUserId").`is`(subject)), User::class.java)!!)
    }

    private fun email(session: Session): String {
        identity.responseEmail = session.user.email
        val result =
            mvc
                .post("/api/v1/me/deletion-confirmations") {
                    header("Authorization", "Bearer ${session.token}")
                    header("Host", "attacker.example")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"email":"attacker@example.com","userId":"attacker"}"""
                }.andExpect {
                    status { isAccepted() }
                    jsonPath("$.confirmationRequired") { value(true) }
                }.andReturn()
        val text = outbox.last().second
        val token = text.substringAfter("/account/deletion-confirm#").lineSequence().first()
        assertEquals(session.user.email, outbox.last().first)
        assertTrue(text.contains("https://example.test/account/deletion-confirm#"))
        assertFalse(result.response.contentAsString.contains(token))
        return token
    }

    private fun confirm(
        session: Session,
        token: String,
    ) = mvc.delete("/api/v1/me") {
        header("Authorization", "Bearer ${session.token}")
        contentType = MediaType.APPLICATION_JSON
        content = """{"token":"$token"}"""
    }

    @Test fun `email request leaves the account active and the queue empty until explicit confirmation`() {
        val owner = session()
        val token = email(owner)
        val user = template.findById(owner.user.id, User::class.java)!!
        assertEquals(UserStatus.ACTIVE, user.status)
        assertNotNull(user.deletionConfirmation)
        assertFalse(user.deletionConfirmation!!.tokenHash == token)
        assertTrue(user.deletionConfirmation!!.expiresAt.isAfter(Instant.now()))
        assertEquals(0, template.count(Query(), AccountDeletion::class.java))
        assertEquals(0, worker.dispatch())
        mvc.get("/api/v1/me") { header("Authorization", "Bearer ${owner.token}") }.andExpect {
            jsonPath("$.deletionConfirmation") { doesNotExist() }
        }
        mvc.get("/api/v1/me/deletion-confirmations").andExpect { status { isUnauthorized() } }
        mvc.delete("/api/v1/me") { header("Authorization", "Bearer ${owner.token}") }.andExpect { status { isBadRequest() } }
        confirm(owner, "short").andExpect { status { isNotFound() } }
        confirm(owner, "A".repeat(43)).andExpect { status { isNotFound() } }
        confirm(owner, user.deletionConfirmation!!.tokenHash).andExpect { status { isNotFound() } }
        identity.responseEmail = owner.user.email
        confirm(owner, token).andExpect { status { isAccepted() } }
        confirm(owner, token).andExpect { status { isAccepted() } }
        assertEquals(1, template.count(Query(), AccountDeletion::class.java))
        assertNotNull(template.findById(deletionKey(owner.user.firebaseUserId), AccountDeletion::class.java)!!.confirmedAt)
        assertEquals(UserStatus.DELETING, template.findById(owner.user.id, User::class.java)!!.status)
    }

    @Test fun `another identity even an administrator with the same email cannot consume the owner token`() {
        val owner = session()
        val ownerToken = email(owner)
        val other = session(owner.user.email)
        template.updateFirst(query(where("_id").`is`(other.user.id)), Update.update("isAdmin", true), User::class.java)
        email(other)
        confirm(other, ownerToken).andExpect { status { isNotFound() } }
        mvc
            .delete("/api/v1/me") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"token":"$ownerToken"}"""
            }.andExpect { status { isUnauthorized() } }
        assertEquals(0, template.count(Query(), AccountDeletion::class.java))
        assertEquals(UserStatus.ACTIVE, template.findById(owner.user.id, User::class.java)!!.status)
        assertEquals(UserStatus.ACTIVE, template.findById(other.user.id, User::class.java)!!.status)
        identity.responseEmail = owner.user.email
        mvc
            .delete("/api/v1/me?userId=${other.user.id}&firebaseUserId=${other.user.firebaseUserId}") {
                header("Authorization", "Bearer ${owner.token}")
                contentType = MediaType.APPLICATION_JSON
                content = """{"token":"$ownerToken"}"""
            }.andExpect { status { isAccepted() } }
        assertEquals(owner.user.id, template.findAll(AccountDeletion::class.java).single().userId)
        assertEquals(UserStatus.ACTIVE, template.findById(other.user.id, User::class.java)!!.status)
    }

    @Test fun `resends invalidate previous links and expiry does not lock the account`() {
        val owner = session()
        val old = email(owner)
        val latest = email(owner)
        confirm(owner, old).andExpect { status { isNotFound() } }
        template.updateFirst(
            query(where("_id").`is`(owner.user.id)),
            Update.update("deletionConfirmation.expiresAt", Instant.now().minusSeconds(1)),
            User::class.java,
        )
        confirm(owner, latest).andExpect { status { isNotFound() } }
        assertEquals(UserStatus.ACTIVE, template.findById(owner.user.id, User::class.java)!!.status)
        assertEquals(0, template.count(Query(), AccountDeletion::class.java))
    }

    @Test fun `a changed verified email cannot use the link issued for the previous email`() {
        val owner = session()
        val token = email(owner)
        val newEmail = "changed@example.com"
        identity.responseEmail = newEmail
        val newToken = identity.token(subject = owner.user.firebaseUserId, email = newEmail)
        confirm(Session(newToken, owner.user), token).andExpect { status { isNotFound() } }
        assertEquals(0, template.count(Query(), AccountDeletion::class.java))
    }

    @Test fun `unconfigured or failed delivery cannot queue deletion or leave a usable token`() {
        val owner = session()
        Mockito.`when`(mail.configured).thenReturn(false)
        mvc
            .post("/api/v1/me/deletion-confirmations") {
                header("Authorization", "Bearer ${owner.token}")
            }.andExpect { status { isServiceUnavailable() } }
        assertNull(template.findById(owner.user.id, User::class.java)!!.deletionConfirmation)
        Mockito.`when`(mail.configured).thenReturn(true)
        Mockito
            .doThrow(
                org.springframework.mail.MailSendException("offline"),
            ).`when`(mail)
            .send(Mockito.anyString(), Mockito.anyString(), Mockito.anyString())
        mvc
            .post("/api/v1/me/deletion-confirmations") {
                header("Authorization", "Bearer ${owner.token}")
            }.andExpect { status { isServiceUnavailable() } }
        assertNull(template.findById(owner.user.id, User::class.java)!!.deletionConfirmation)
        assertEquals(UserStatus.ACTIVE, template.findById(owner.user.id, User::class.java)!!.status)
        assertEquals(0, template.count(Query(), AccountDeletion::class.java))
    }

    @Test fun `email requests are limited and unconfirmed legacy jobs are paused until proof is consumed`() {
        val owner = session()
        template.updateFirst(query(where("_id").`is`(owner.user.id)), Update.update("status", UserStatus.DELETING), User::class.java)
        template.insert(AccountDeletion(deletionKey(owner.user.firebaseUserId), owner.user.id, Instant.now()))
        assertEquals(0, worker.dispatch())
        repeat(3) { email(owner) }
        mvc
            .post("/api/v1/me/deletion-confirmations") {
                header("Authorization", "Bearer ${owner.token}")
            }.andExpect { status { isTooManyRequests() } }
        assertEquals(0, identity.deleteRequests.get())
        val token =
            outbox
                .last()
                .second
                .substringAfter("/account/deletion-confirm#")
                .lineSequence()
                .first()
        confirm(owner, token).andExpect { status { isAccepted() } }
        assertNotNull(template.findById(deletionKey(owner.user.firebaseUserId), AccountDeletion::class.java)!!.confirmedAt)
    }
}
