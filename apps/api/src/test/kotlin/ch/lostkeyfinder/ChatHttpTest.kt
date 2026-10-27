package ch.lostkeyfinder

import ch.lostkeyfinder.chat.Chat
import ch.lostkeyfinder.chat.ChatImage
import ch.lostkeyfinder.chat.ChatImages
import ch.lostkeyfinder.chat.ChatMessage
import ch.lostkeyfinder.chat.ChatNotice
import ch.lostkeyfinder.chat.ChatNotificationJobProperties
import ch.lostkeyfinder.chat.ChatNotifications
import ch.lostkeyfinder.chat.ChatService
import ch.lostkeyfinder.chat.FinderDraft
import ch.lostkeyfinder.chat.LossCase
import ch.lostkeyfinder.mail.MailProperties
import ch.lostkeyfinder.mail.MailSender
import ch.lostkeyfinder.tags.Tag
import ch.lostkeyfinder.tags.TagAudit
import ch.lostkeyfinder.tags.TagProperties
import ch.lostkeyfinder.tags.TagService
import ch.lostkeyfinder.tags.TagStatus
import ch.lostkeyfinder.users.User
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.json.JsonMapper
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("ci")
@Import(InMemoryMongoConfiguration::class, TestIdentityConfiguration::class)
class ChatHttpTest {
    @Autowired lateinit var mvc: MockMvc

    @Autowired lateinit var template: MongoTemplate

    @Autowired lateinit var identity: TestIdentityProvider

    @Autowired lateinit var chats: ChatService

    @Autowired lateinit var tags: TagService

    @Autowired lateinit var images: ChatImages

    @Autowired lateinit var registrations: ch.lostkeyfinder.tags.TagRegistrationService

    @Autowired lateinit var notifications: ChatNotifications
    private val mapper = JsonMapper.builder().build()

    private fun id() = UUID.randomUUID().toString()

    private lateinit var owner: Pair<String, User>
    private lateinit var finder: Pair<String, User>
    private lateinit var stranger: Pair<String, User>
    private lateinit var tag: Tag

    @BeforeEach fun setup() {
        listOf(
            "users",
            "tags",
            "auditLog",
            "rateLimits",
            "finderReportDrafts",
            "lossCases",
            "chats",
            "messages",
            "messageImages",
            "chatNotifications",
        ).forEach { template.remove(Query(), it) }
        identity.reset()
        owner = login()
        finder = login()
        stranger = login()
        val result = tags.create(owner.second, id(), "Private keys")
        tag = template.findById(result.id, Tag::class.java)!!
    }

    private fun login(admin: Boolean = false): Pair<String, User> {
        val email = "${id()}@example.com"
        identity.responseEmail = email
        val token = identity.token(email = email)
        val result = mvc.get("/api/v1/me") { header("Authorization", "Bearer $token") }.andExpect { status { isOk() } }.andReturn()
        val user = template.findById(mapper.readTree(result.response.contentAsString)["id"].asString(), User::class.java)!!
        if (admin) template.save(user.copy(isAdmin = true))
        return token to user.copy(isAdmin = admin)
    }

    private fun report(
        who: User = finder.second,
        image: ByteArray? = null,
    ): String {
        val draft = chats.start(tag.publicId, who.email, "Found your keys", image, id())
        return chats.complete(draft.flowId, who, who.email).id
    }

    private fun png(): ByteArray =
        ByteArrayOutputStream()
            .also {
                ImageIO.write(BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB), "png", it)
            }.toByteArray()

    @Test fun `public report requires verified matching identity and completes once with image and audit`() {
        val file = MockMultipartFile("image", "test.png", "image/png", png())
        val key = id()
        val result =
            mvc
                .perform(
                    multipart("/api/v1/public/tags/${tag.publicId}/finder-drafts")
                        .param("email", finder.second.email)
                        .param("text", "Found it")
                        .file(file)
                        .header("Idempotency-Key", key),
                ).andReturn()
                .response
        assertEquals(200, result.status)
        val flow = mapper.readTree(result.contentAsString)["flowId"].asString()
        mvc.post("/api/v1/finder-reports/$flow/complete").andExpect { status { isUnauthorized() } }
        mvc
            .post("/api/v1/finder-reports/$flow/complete") { header("Authorization", "Bearer ${stranger.first}") }
            .andExpect { status { isNotFound() } }
        identity.responseEmail = finder.second.email
        val chat =
            mvc
                .post("/api/v1/finder-reports/$flow/complete") { header("Authorization", "Bearer ${finder.first}") }
                .andExpect {
                    status { isOk() }
                    jsonPath("$.writable") { value(true) }
                }.andReturn()
        val chatId = mapper.readTree(chat.response.contentAsString)["id"].asString()
        assertEquals(chatId, chats.complete(flow, finder.second, finder.second.email).id)
        assertEquals(1, template.count(Query(), Chat::class.java))
        assertEquals(1, template.count(Query(), ChatMessage::class.java))
        assertEquals(1, template.count(Query(), LossCase::class.java))
        val draft = template.findById(flow, FinderDraft::class.java)!!
        assertEquals(null, draft.email)
        assertEquals("", draft.text)
        val storedImage = template.findOne(Query(), ChatImage::class.java)!!
        assertEquals(null, storedImage.expiresAt)
        assertEquals(null, storedImage.draftId)
        assertNotNull(storedImage.messageId)
        mvc
            .get("/api/v1/chat-images/${storedImage.id}") { header("Authorization", "Bearer ${owner.first}") }
            .andExpect {
                status { isOk() }
                header { string("Content-Type", "image/jpeg") }
                header { string("Cache-Control", "no-store") }
            }
        mvc
            .get("/api/v1/chat-images/${storedImage.id}") {
                header("Authorization", "Bearer ${stranger.first}")
            }.andExpect { status { isNotFound() } }
        assertEquals(2, template.count(query(where("chatId").`is`(chatId)), TagAudit::class.java))
        assertEquals("FAILED", template.findOne(Query(), ChatNotice::class.java)!!.status)
    }

    @Test fun `private chats survive owner changes without granting new owners or admin writing rights`() {
        val first = report()
        val second = report(stranger.second)
        assertEquals(chats.detail(first, owner.second).caseId, chats.detail(second, owner.second).caseId)
        assertEquals(2, chats.list(owner.second, 1, tag.id).items.size)
        assertEquals(1, chats.list(finder.second, 1, null).items.size)
        assertFailsWith<ResponseStatusException> { chats.detail(second, finder.second) }
        val admin = login(true)
        mvc.get("/api/v1/chats/$first") { header("Authorization", "Bearer ${admin.first}") }.andExpect { status { isOk() } }
        assertEquals(2, chats.list(admin.second, 1, tag.id).items.size)
        assertFailsWith<ResponseStatusException> { chats.send(first, admin.second, "No impersonation", null, id()) }
        assertFailsWith<ResponseStatusException> { chats.read(first, admin.second, 1) }
        assertFailsWith<ResponseStatusException> { chats.close(first, finder.second) }
        tags.changeStatus(owner.second, tag.id, TagStatus.INACTIVE)
        assertTrue(chats.detail(first, finder.second).writable)
        assertFailsWith<ResponseStatusException> { chats.start(tag.publicId, finder.second.email, "Again", null, id()) }
        tags.delete(owner.second, tag.id)
        assertFalse(chats.detail(first, finder.second).writable)
        assertFalse(chats.detail(second, owner.second).writable)
        assertTrue(chats.messages(first, admin.second, null).items.isNotEmpty())
        assertFailsWith<ResponseStatusException> { chats.send(first, owner.second, "Closed", null, id()) }
        template.updateFirst(query(where("_id").`is`(tag.id)), Update.update("primaryOwnerUserId", admin.second.id), Tag::class.java)
        assertTrue(chats.messages(first, owner.second, null).items.isNotEmpty())
        mvc.get("/api/v1/chats/$first") { header("Authorization", "Bearer ${stranger.first}") }.andExpect { status { isNotFound() } }
        template.updateFirst(query(where("_id").`is`(owner.second.id)), Update.update("status", "DISABLED"), User::class.java)
        mvc.get("/api/v1/chats/$first") { header("Authorization", "Bearer ${owner.first}") }.andExpect { status { isForbidden() } }
    }

    @Test fun `messages are paged idempotent and closing one case preserves history and permits a new case`() {
        val chat = report()
        val key = id()
        val first = chats.send(chat, owner.second, " Hello ", null, key)
        assertEquals(first.id, chats.send(chat, owner.second, " Hello ", null, key).id)
        assertEquals("Hello", first.text)
        assertFailsWith<ResponseStatusException> { chats.send(chat, owner.second, "Changed", null, key) }
        repeat(52) { chats.send(chat, finder.second, "Message $it", null, id()) }
        val latest = chats.messages(chat, owner.second, null)
        assertEquals(50, latest.items.size)
        mvc
            .get("/api/v1/chats/$chat/messages?messageId=${latest.items.first().id}") {
                header("Authorization", "Bearer ${owner.first}")
            }.andExpect {
                status { isOk() }
                jsonPath("$.items[49].sequence") { value(54) }
            }
        assertNotNull(latest.before)
        assertEquals(4, chats.messages(chat, owner.second, latest.before).items.size)
        assertTrue(chats.detail(chat, owner.second).unread > 0)
        mvc
            .post("/api/v1/chats/$chat/read") {
                header("Authorization", "Bearer ${owner.first}")
                contentType = MediaType.APPLICATION_JSON
                content = "{\"through\":54}"
            }.andExpect { status { isOk() } }
        assertEquals(0, chats.detail(chat, owner.second).unread)
        chats.read(chat, owner.second, 1)
        assertEquals(0, chats.detail(chat, owner.second).unread)
        mvc.post("/api/v1/chats/$chat/close") { header("Authorization", "Bearer ${owner.first}") }.andExpect { status { isOk() } }
        chats.close(chat, owner.second)
        assertFalse(chats.detail(chat, owner.second).writable)
        assertEquals(1, template.count(query(where("action").`is`("CHAT_CLOSED")), TagAudit::class.java))
        val newChat = report()
        assertTrue(newChat != chat)
        assertTrue(chats.detail(newChat, owner.second).writable)
        assertEquals(2, chats.list(owner.second, 99, null).items.size)
        mvc.get("/api/v1/chats?tagId=${tag.id}") { header("Authorization", "Bearer ${owner.first}") }.andExpect { status { isOk() } }
        mvc.get("/api/v1/chats/$chat/messages") { header("Authorization", "Bearer ${owner.first}") }.andExpect { status { isOk() } }
    }

    @Test fun `validation rejects expired mismatched self reports invalid cursors and empty messages`() {
        val key = id()
        val draft = chats.start(tag.publicId, finder.second.email, "Found", null, key)
        assertEquals(draft, chats.start(tag.publicId, finder.second.email, "Found", null, key))
        assertFailsWith<ResponseStatusException> { chats.start(tag.publicId, finder.second.email, "Changed", null, key) }
        template.updateFirst(query(where("_id").`is`(draft.flowId)), Update.update("expiresAt", Instant.EPOCH), FinderDraft::class.java)
        assertFailsWith<ResponseStatusException> { chats.complete(draft.flowId, finder.second, finder.second.email) }
        val own = chats.start(tag.publicId, owner.second.email, "Self", null, id())
        assertFailsWith<ResponseStatusException> { chats.complete(own.flowId, owner.second, owner.second.email) }
        assertFailsWith<ResponseStatusException> { chats.start(tag.publicId, "bad", "Found", null, id()) }
        assertFailsWith<ResponseStatusException> { chats.start(id(), finder.second.email, "Found", null, id()) }
        assertFailsWith<ResponseStatusException> { chats.start(tag.publicId, finder.second.email, " ", null, id()) }
        assertFailsWith<ResponseStatusException> { chats.start(tag.publicId, finder.second.email, "a".repeat(2001), null, id()) }
        val chat = report()
        assertFailsWith<ResponseStatusException> { chats.messages(chat, owner.second, 0) }
        assertFailsWith<ResponseStatusException> { chats.list(owner.second, 0, null) }
        assertFailsWith<ResponseStatusException> { chats.read(chat, owner.second, -1) }
        assertFailsWith<ResponseStatusException> { chats.read(chat, owner.second, 100) }
        assertFailsWith<ResponseStatusException> { chats.send(chat, finder.second, " ", null, id()) }
        assertFailsWith<ResponseStatusException> { chats.image(id(), finder.second) }
        assertFailsWith<ResponseStatusException> { chats.image("invalid", finder.second) }
        mvc.post("/api/v1/internal/chat-notifications/dispatch").andExpect { status { isServiceUnavailable() } }
    }

    @Test fun `image processing validates bytes and strips source representation`() {
        assertEquals(null, images.sanitize(null))
        assertEquals(null, images.sanitize(MockMultipartFile("image", byteArrayOf())))
        assertFailsWith<ResponseStatusException> { images.sanitize(MockMultipartFile("image", ByteArray(5_000_001))) }
        assertFailsWith<ResponseStatusException> { images.sanitize(MockMultipartFile("image", byteArrayOf(1, 2, 3))) }
        val giant =
            ByteArrayOutputStream()
                .also {
                    ImageIO.write(
                        BufferedImage(2049, 1, BufferedImage.TYPE_INT_RGB),
                        "png",
                        it,
                    )
                }.toByteArray()
        assertFailsWith<ResponseStatusException> { images.sanitize(MockMultipartFile("image", giant)) }
        val clean = images.sanitize(MockMultipartFile("image", png()))!!
        assertEquals(0xff.toByte(), clean[0])
        assertEquals(0xd8.toByte(), clean[1])
        val chat = report()
        val response =
            mvc
                .perform(
                    multipart("/api/v1/chats/$chat/messages")
                        .param("text", "Picture")
                        .file(MockMultipartFile("image", "test.png", "image/png", png()))
                        .header("Idempotency-Key", id())
                        .header("Authorization", "Bearer ${finder.first}"),
                ).andReturn()
                .response
        assertEquals(200, response.status)
        val image = mapper.readTree(response.contentAsString)["imageId"].asString()
        assertTrue(chats.image(image, owner.second).contentEquals(clean))
        val detached = template.insert(ChatImage(id(), id(), null, clean, Instant.now().plusSeconds(3600)))
        assertFailsWith<ResponseStatusException> { chats.image(detached.id, owner.second) }
    }

    @Test fun `notification batching skips read messages and retains failed delivery for retry`() {
        val chat = report()
        chats.send(chat, owner.second, "First", null, id())
        val before = template.findById("$chat:${finder.second.id}", ChatNotice::class.java)!!
        chats.send(chat, owner.second, "Second", null, id())
        val after = template.findById(before.id, ChatNotice::class.java)!!
        assertEquals(before.dueAt, after.dueAt)
        assertEquals(3, after.targetSequence)
        chats.read(chat, finder.second, 3)
        template.updateMulti(Query(), Update.update("dueAt", Instant.EPOCH), ChatNotice::class.java)
        assertEquals(2, notifications.dispatch())
        assertEquals("SKIPPED", template.findById(before.id, ChatNotice::class.java)!!.status)
        chats.send(chat, owner.second, "Unread", null, id())
        assertEquals("PENDING", template.findById(before.id, ChatNotice::class.java)!!.status)
        assertTrue(template.findById(before.id, ChatNotice::class.java)!!.dueAt.isAfter(Instant.now()))
        val configured =
            ChatNotifications(
                template,
                MailSender(MailProperties(host = "127.0.0.1", port = 1, from = "test@example.com")),
                TagProperties("https://example.com"),
            )
        template.updateMulti(Query(), Update.update("dueAt", Instant.EPOCH), ChatNotice::class.java)
        configured.dispatch()
        assertEquals("FAILED", template.findById(before.id, ChatNotice::class.java)!!.status)
    }

    @Test fun `SMTP success acknowledges only the delivered batch and leaves concurrent replies pending`() {
        val chat = report()
        chats.send(chat, owner.second, "Private message must not enter mail", null, id())
        val noticeId = "$chat:${finder.second.id}"
        template.updateFirst(query(where("_id").`is`(noticeId)), Update.update("dueAt", Instant.EPOCH), ChatNotice::class.java)
        java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { server ->
            val pool =
                java.util.concurrent.Executors
                    .newSingleThreadExecutor()
            val received =
                pool.submit<String> {
                    server.accept().use { socket ->
                        socket.soTimeout = 5000
                        val input = socket.getInputStream().bufferedReader()
                        val output = socket.getOutputStream().bufferedWriter()

                        fun reply(value: String) {
                            output.write(value + "\r\n")
                            output.flush()
                        }
                        reply("220 localhost")
                        val body = StringBuilder()
                        while (true) {
                            val line = input.readLine() ?: break
                            when {
                                line.startsWith("DATA") -> {
                                    reply("354 Send message")
                                    while (true) {
                                        val part = input.readLine() ?: break
                                        if (part == ".") break
                                        body.appendLine(part)
                                    }
                                    chats.send(chat, owner.second, "Arrived during SMTP", null, id())
                                    reply("250 accepted")
                                }

                                line.startsWith("QUIT") -> {
                                    reply("221 bye")
                                    break
                                }

                                else -> {
                                    reply("250 OK")
                                }
                            }
                        }
                        body.toString()
                    }
                }
            try {
                val configured =
                    ChatNotifications(
                        template,
                        MailSender(
                            MailProperties(
                                host = "127.0.0.1",
                                port = server.localPort,
                                from = "notify@example.com",
                                startTls = false,
                            ),
                        ),
                        TagProperties("https://example.test"),
                    )
                assertEquals(1, configured.dispatch())
                val mail = received.get(10, java.util.concurrent.TimeUnit.SECONDS)
                assertTrue(mail.contains("/chats/$chat"))
                assertFalse(mail.contains("Private message"))
                val notice = template.findById(noticeId, ChatNotice::class.java)!!
                assertEquals("PENDING", notice.status)
                assertEquals(2, notice.deliveredSequence)
                assertEquals(3, notice.targetSequence)
                chats.read(chat, finder.second, 3)
                template.updateFirst(query(where("_id").`is`(noticeId)), Update.update("dueAt", Instant.EPOCH), ChatNotice::class.java)
                configured.dispatch()
                assertEquals("SKIPPED", template.findById(noticeId, ChatNotice::class.java)!!.status)
            } finally {
                pool.shutdownNow()
            }
        }
    }

    @Test fun `hardware release closes old chats and a new registration never transfers old chat history`() {
        template.save(
            tag.copy(
                origin = ch.lostkeyfinder.tags.TagOrigin.PREPRODUCED,
                inventoryStatus = ch.lostkeyfinder.tags.InventoryStatus.RELEASED,
            ),
        )
        val oldChat = report()
        tags.release(owner.second, tag.id)
        assertFalse(chats.detail(oldChat, owner.second).writable)
        val next = login()
        registrations.direct(tag.publicId, next.second)
        assertFailsWith<ResponseStatusException> { chats.detail(oldChat, next.second) }
        assertTrue(tags.history(tag.id, owner = next.second).items.none { it.chatId != null })
        assertTrue(tags.history(tag.id).items.any { it.chatId == oldChat })
        assertTrue(chats.list(owner.second, 1, null).items.any { it.id == oldChat })
        val nextChat = report()
        assertTrue(chats.detail(nextChat, next.second).writable)
    }

    @Test fun `a fresh report by the same finder appends once to their existing chat`() {
        val chat = report()
        val draft = chats.start(tag.publicId, finder.second.email, "Additional detail", byteArrayOf(1, 2), id())
        assertEquals(chat, chats.complete(draft.flowId, finder.second, finder.second.email).id)
        assertEquals(chat, chats.complete(draft.flowId, finder.second, finder.second.email).id)
        assertEquals(1, template.count(Query(), Chat::class.java))
        val messages = chats.messages(chat, owner.second, null).items
        assertEquals(2, messages.size)
        assertEquals("Additional detail", messages.last().text)
        assertTrue(chats.image(messages.last().imageId!!, owner.second).contentEquals(byteArrayOf(1, 2)))
    }

    @Test fun `notification job rejects absent or malformed credentials when configured`() {
        val job =
            ch.lostkeyfinder.chat.ChatNotificationJob(
                notifications,
                ChatNotificationJobProperties(
                    audience = "https://example.test",
                    serviceAccount = "scheduler@example.iam.gserviceaccount.com",
                ),
            )
        for (header in listOf(null, "Basic invalid", "Bearer invalid")) {
            assertEquals(401, assertFailsWith<ResponseStatusException> { job.dispatch(header) }.statusCode.value())
        }
    }
}
