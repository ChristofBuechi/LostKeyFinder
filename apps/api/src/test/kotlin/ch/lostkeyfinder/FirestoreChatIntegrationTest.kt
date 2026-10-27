package ch.lostkeyfinder

import ch.lostkeyfinder.chat.Chat
import ch.lostkeyfinder.chat.ChatImage
import ch.lostkeyfinder.chat.ChatMessage
import ch.lostkeyfinder.chat.ChatNotice
import ch.lostkeyfinder.chat.ChatNotifications
import ch.lostkeyfinder.chat.ChatService
import ch.lostkeyfinder.chat.FinderDraft
import ch.lostkeyfinder.chat.LossCase
import ch.lostkeyfinder.mail.MailProperties
import ch.lostkeyfinder.mail.MailSender
import ch.lostkeyfinder.tags.MongoTagTransactions
import ch.lostkeyfinder.tags.PublicRequestLimits
import ch.lostkeyfinder.tags.Tag
import ch.lostkeyfinder.tags.TagAudit
import ch.lostkeyfinder.tags.TagCreationLimit
import ch.lostkeyfinder.tags.TagProperties
import ch.lostkeyfinder.tags.TagService
import ch.lostkeyfinder.tags.TagStatus
import ch.lostkeyfinder.tags.TagTransactions
import ch.lostkeyfinder.users.User
import ch.lostkeyfinder.users.UserStatus
import com.mongodb.ConnectionString
import com.mongodb.client.MongoClients
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoOperations
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirestoreChatIntegrationTest {
    @Test fun `real Firestore serializes reports messages rollback and tag retirement without losing participant history`() {
        val uri = requireNotNull(System.getenv("FIRESTORE_MONGODB_URI"))
        MongoClients.create(uri).use { client ->
            val template = MongoTemplate(client, requireNotNull(ConnectionString(uri).database))
            provisionChatIndexes(template)
            val transactions = MongoTagTransactions(client, template)
            val limits = PublicRequestLimits(template, requireNotNull(System.getenv("PUBLIC_RATE_LIMIT_HMAC_KEY")))
            val notices = ChatNotifications(template, MailSender(MailProperties()), TagProperties("https://example.test"))
            val chats = ChatService(template, transactions, limits, notices)

            fun user(): User {
                val id = UUID.randomUUID().toString()
                val now = Instant.now()
                return User(id, id, "$id@example.com", UserStatus.ACTIVE, now, now)
            }
            val owner = user()
            val finder = user()
            val second = user()
            val tags = TagService(template, TagCreationLimit(template), transactions)
            template.insert(owner)
            template.insert(finder)
            template.insert(second)
            var createdTagId: String? = null
            try {
                val tag = tags.create(owner, UUID.randomUUID().toString(), "Chat integration")
                createdTagId = tag.id
                val drafts =
                    listOf(finder, second).map {
                        chats.start(
                            tag.publicId,
                            it.email,
                            "Found keys",
                            byteArrayOf(1, 2, 3),
                            UUID.randomUUID().toString(),
                        )
                    }
                val results =
                    Executors.newFixedThreadPool(2).use { pool ->
                        listOf(finder, second)
                            .mapIndexed {
                                index,
                                user,
                                ->
                                pool.submit<String> { chats.complete(drafts[index].flowId, user, user.email).id }
                            }.map { it.get(90, TimeUnit.SECONDS) }
                    }
                assertEquals(1, template.count(query(where("tagId").`is`(tag.id)), LossCase::class.java))
                assertEquals(2, results.distinct().size)
                val first = results.first()
                val initial = chats.messages(first, finder, null).items.single()
                assertTrue(chats.image(initial.imageId!!, owner).contentEquals(byteArrayOf(1, 2, 3)))
                assertEquals(null, template.findById(initial.imageId, ChatImage::class.java)!!.expiresAt)
                assertEquals(first, chats.complete(drafts.first().flowId, finder, finder.email).id)
                val aborting =
                    object : TagTransactions {
                        override fun <T : Any> run(action: (MongoOperations) -> T): T =
                            transactions.run { ops ->
                                action(ops)
                                error("Abort")
                            }
                    }
                val failing = ChatService(template, aborting, limits, notices)
                val key = UUID.randomUUID().toString()
                assertFailsWith<IllegalStateException> { failing.send(first, owner, "Rollback", byteArrayOf(4, 5), key) }
                assertEquals(null, template.findById(key, ChatMessage::class.java))
                assertEquals(
                    2,
                    template.count(
                        query(
                            where(
                                "messageId",
                            ).`in`(
                                template.find(query(where("chatId").`in`(results)), ChatMessage::class.java).map {
                                    it.id
                                },
                            ),
                        ),
                        ChatImage::class.java,
                    ),
                )
                assertEquals(1, chats.detail(first, owner).sequence)
                Executors.newFixedThreadPool(3).use { pool ->
                    (1..3)
                        .map {
                            pool.submit<String> {
                                chats.send(first, owner, "One retry-safe message", null, key).id
                            }
                        }.forEach { assertEquals(key, it.get(90, TimeUnit.SECONDS)) }
                }
                assertEquals(2, chats.detail(first, owner).sequence)
                val nextReport =
                    chats.start(
                        tag.publicId,
                        finder.email,
                        "Extra detail",
                        null,
                        UUID.randomUUID().toString(),
                    )
                assertEquals(first, chats.complete(nextReport.flowId, finder, finder.email).id)
                assertEquals(first, chats.complete(nextReport.flowId, finder, finder.email).id)
                assertEquals(3, chats.detail(first, owner).sequence)
                chats.read(first, finder, 3)
                assertEquals(0, chats.detail(first, finder).unread)
                assertTrue(chats.list(owner, 1, tag.id).items.size == 2)
                assertTrue(tags.history(tag.id, owner = owner).items.any { it.chatId == first })
                chats.close(first, owner)
                results.forEach { assertFalse(chats.detail(it, owner).writable) }
                val previousCaseIds = mutableSetOf(chats.detail(first, owner).caseId)
                repeat(2) { round ->
                    val report =
                        chats.start(
                            tag.publicId,
                            finder.email,
                            "Found again $round",
                            null,
                            UUID.randomUUID().toString(),
                        )
                    val conversation = chats.complete(report.flowId, finder, finder.email)
                    assertTrue(previousCaseIds.add(conversation.caseId))
                    assertTrue(conversation.id !in results)
                    assertTrue(conversation.writable)
                    chats.send(conversation.id, owner, "Thanks, where can we meet?", null, UUID.randomUUID().toString())
                    chats.send(conversation.id, finder, "At the station", null, UUID.randomUUID().toString())
                    val messages = chats.messages(conversation.id, owner, null).items
                    assertEquals(listOf(finder.id, owner.id, finder.id), messages.map { it.senderUserId })
                    assertEquals(listOf("Found again $round", "Thanks, where can we meet?", "At the station"), messages.map { it.text })
                    assertEquals(messages, chats.messages(conversation.id, finder, null).items)
                    assertFailsWith<ResponseStatusException> { chats.close(conversation.id, finder) }
                    chats.close(conversation.id, owner)
                    chats.close(conversation.id, owner)
                    assertFalse(chats.detail(conversation.id, owner).writable)
                    assertFalse(chats.detail(conversation.id, finder).writable)
                    assertFailsWith<ResponseStatusException> {
                        chats.send(conversation.id, finder, "Too late", null, UUID.randomUUID().toString())
                    }
                    assertEquals(0, template.count(query(where("activeKey").`is`(tag.id)), LossCase::class.java))
                    assertEquals(TagStatus.ACTIVE, tags.owned(owner, tag.id).status)
                    assertEquals(messages, chats.messages(conversation.id, finder, null).items)
                    assertEquals(
                        1,
                        template.count(
                            query(where("chatId").`is`(conversation.id).and("action").`is`("CHAT_CLOSED")),
                            TagAudit::class.java,
                        ),
                    )
                }
                assertEquals(3, template.count(query(where("tagId").`is`(tag.id)), LossCase::class.java))
                tags.delete(owner, tag.id)
                results.forEach { assertFalse(chats.detail(it, owner).writable) }
                assertTrue(chats.messages(first, finder, null).items.isNotEmpty())
            } finally {
                val tagId = createdTagId
                if (tagId != null) {
                    val chatIds = template.find(query(where("tagId").`is`(tagId)), Chat::class.java).map { it.id }
                    val messageIds = template.find(query(where("chatId").`in`(chatIds)), ChatMessage::class.java).map { it.id }
                    template.remove(query(where("messageId").`in`(messageIds)), ChatImage::class.java)
                    template.remove(query(where("chatId").`in`(chatIds)), ChatNotice::class.java)
                    template.remove(query(where("chatId").`in`(chatIds)), ChatMessage::class.java)
                    template.remove(query(where("tagId").`is`(tagId)), Chat::class.java)
                    template.remove(query(where("tagId").`is`(tagId)), LossCase::class.java)
                    template.remove(query(where("tagId").`is`(tagId)), FinderDraft::class.java)
                    template.remove(query(where("resourceId").`is`(tagId)), TagAudit::class.java)
                    template.remove(query(where("_id").`is`(tagId)), Tag::class.java)
                }
                template.remove(query(where("_id").`in`(listOf(owner.id, finder.id, second.id))), User::class.java)
            }
        }
    }
}
