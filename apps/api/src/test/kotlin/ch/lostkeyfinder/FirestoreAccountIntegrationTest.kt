package ch.lostkeyfinder

import ch.lostkeyfinder.chat.Chat
import ch.lostkeyfinder.chat.ChatMessage
import ch.lostkeyfinder.chat.ChatReport
import ch.lostkeyfinder.chat.ChatReports
import ch.lostkeyfinder.chat.ReportReason
import ch.lostkeyfinder.identity.FirebaseIdentity
import ch.lostkeyfinder.identity.IdentityProperties
import ch.lostkeyfinder.identity.SecurityConfiguration
import ch.lostkeyfinder.tags.InventoryStatus
import ch.lostkeyfinder.tags.MongoTagTransactions
import ch.lostkeyfinder.tags.PublicRequestLimits
import ch.lostkeyfinder.tags.Tag
import ch.lostkeyfinder.tags.TagAudit
import ch.lostkeyfinder.tags.TagOrigin
import ch.lostkeyfinder.tags.TagStatus
import ch.lostkeyfinder.users.AccountDeletion
import ch.lostkeyfinder.users.AccountDeletionService
import ch.lostkeyfinder.users.User
import ch.lostkeyfinder.users.UserService
import ch.lostkeyfinder.users.requireActiveUser
import com.mongodb.ConnectionString
import com.mongodb.client.MongoClients
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.mock.env.MockEnvironment
import org.springframework.web.client.RestClient
import org.springframework.web.server.ResponseStatusException
import java.net.URI
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FirestoreAccountIntegrationTest {
    @Test fun `real account deletion resumes across chunks and provider failure`() {
        val uri = requireNotNull(System.getenv("FIRESTORE_MONGODB_URI"))
        MongoClients.create(uri).use { client ->
            val template = MongoTemplate(client, requireNotNull(ConnectionString(uri).database))
            val transactions = MongoTagTransactions(client, template)
            TestIdentityProvider().use { provider ->
                val props = IdentityProperties("test-project", "test-publishable-key", URI("${provider.origin}/jwks"), URI(provider.origin))
                val identity = FirebaseIdentity(props, RestClient.builder())
                val users = UserService(template, identity)
                val decoder = SecurityConfiguration().jwtDecoder(props, MockEnvironment())
                val jwt = decoder.decode(provider.token())
                val owner = users.currentUser(jwt)
                val finder = users.currentUser(decoder.decode(provider.token()))
                val deletions = AccountDeletionService(template, transactions, identity, users)
                val ids = (1..28).map { UUID.randomUUID().toString() }
                val chatId = UUID.randomUUID().toString()
                val messageId = UUID.randomUUID().toString()
                try {
                    val now = Instant.now()
                    template.insert(
                        ids.take(26).map {
                            Tag(
                                it,
                                UUID.randomUUID().toString(),
                                TagOrigin.SELF_CREATED,
                                owner.id,
                                TagStatus.ACTIVE,
                                "Private label",
                                now,
                                now,
                            )
                        },
                        Tag::class.java,
                    )
                    template.insert(
                        Tag(
                            ids[26],
                            UUID.randomUUID().toString(),
                            TagOrigin.PREPRODUCED,
                            owner.id,
                            TagStatus.INACTIVE,
                            "Private hardware",
                            now,
                            now,
                            batchId = UUID.randomUUID().toString(),
                            inventoryStatus = InventoryStatus.RELEASED,
                        ),
                    )
                    template.insert(
                        Tag(
                            ids[27],
                            UUID.randomUUID().toString(),
                            TagOrigin.PREPRODUCED,
                            owner.id,
                            TagStatus.BLOCKED,
                            "Blocked hardware",
                            now,
                            now,
                            batchId = UUID.randomUUID().toString(),
                            inventoryStatus = InventoryStatus.RELEASED,
                        ),
                    )
                    template.insert(
                        Chat(
                            chatId,
                            UUID.randomUUID().toString(),
                            ids[0],
                            owner.id,
                            finder.id,
                            listOf(owner.id, finder.id),
                            now,
                            now,
                            sequence = 1,
                        ),
                    )
                    template.insert(ChatMessage(messageId, chatId, finder.id, 1, "Shared message", now))
                    val reports =
                        ChatReports(
                            template,
                            transactions,
                            PublicRequestLimits(template, requireNotNull(System.getenv("PUBLIC_RATE_LIMIT_HMAC_KEY"))),
                        )
                    reports.report(finder, chatId, messageId, ReportReason.SPAM)
                    reports.report(finder, chatId, messageId, ReportReason.OTHER)
                    assertEquals(1, template.count(query(where("chatId").`is`(chatId)), ChatReport::class.java))
                    assertFalse(deletions.delete(jwt))
                    assertFailsWith<ResponseStatusException> { users.currentUser(jwt) }
                    assertFailsWith<ResponseStatusException> {
                        transactions.run { ops ->
                            requireActiveUser(ops, owner.id)
                            true
                        }
                    }
                    provider.deleteStatus = 503
                    assertFailsWith<ResponseStatusException> { deletions.delete(jwt) }
                    assertTrue(template.findById(ids[26], Tag::class.java)!!.primaryOwnerUserId == null)
                    assertEquals(TagStatus.ACTIVE, template.findById(ids[26], Tag::class.java)!!.status)
                    assertEquals(TagStatus.BLOCKED, template.findById(ids[27], Tag::class.java)!!.status)
                    assertNotNull(template.findById(chatId, Chat::class.java)!!.closedAt)
                    assertEquals("Shared message", template.findById(messageId, ChatMessage::class.java)!!.text)
                    provider.deleteStatus = 200
                    assertTrue(deletions.delete(jwt))
                    assertTrue(deletions.delete(jwt))
                    assertNull(template.findById(owner.id, User::class.java))
                    assertFailsWith<ResponseStatusException> { users.currentUser(jwt) }
                } finally {
                    template.remove(query(where("_id").`in`(ids)), Tag::class.java)
                    template.remove(query(where("resourceId").`in`(ids)), TagAudit::class.java)
                    template.remove(query(where("_id").`is`(chatId)), Chat::class.java)
                    template.remove(query(where("_id").`is`(messageId)), ChatMessage::class.java)
                    template.remove(query(where("chatId").`is`(chatId)), ChatReport::class.java)
                    template.remove(query(where("userId").`is`(owner.id)), AccountDeletion::class.java)
                    template.remove(query(where("_id").`in`(listOf(owner.id, finder.id))), User::class.java)
                }
            }
        }
    }
}
