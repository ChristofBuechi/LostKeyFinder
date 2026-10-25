package ch.lostkeyfinder

import ch.lostkeyfinder.tags.InventoryStatus
import ch.lostkeyfinder.tags.MongoTagTransactions
import ch.lostkeyfinder.tags.PublicRequestLimits
import ch.lostkeyfinder.tags.Tag
import ch.lostkeyfinder.tags.TagAudit
import ch.lostkeyfinder.tags.TagBatch
import ch.lostkeyfinder.tags.TagBatchService
import ch.lostkeyfinder.tags.TagCreationLimit
import ch.lostkeyfinder.tags.TagFilter
import ch.lostkeyfinder.tags.TagProperties
import ch.lostkeyfinder.tags.TagRegistrationDraft
import ch.lostkeyfinder.tags.TagRegistrationService
import ch.lostkeyfinder.tags.TagService
import ch.lostkeyfinder.tags.TagStatus
import ch.lostkeyfinder.users.User
import ch.lostkeyfinder.users.UserStatus
import com.mongodb.ConnectionString
import com.mongodb.client.MongoClients
import org.bson.Document
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class FirestoreHardwareIntegrationTest {
    @Test fun `real transactions preserve whole series and exactly one winning owner including draft consumption`() {
        val uri = requireNotNull(System.getenv("FIRESTORE_MONGODB_URI"))
        MongoClients.create(uri).use { client ->
            val template = MongoTemplate(client, requireNotNull(ConnectionString(uri).database))
            val transactions = MongoTagTransactions(client, template)
            val now = Instant.now()

            fun user() =
                User(
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    "${UUID.randomUUID()}@example.com",
                    UserStatus.ACTIVE,
                    now,
                    now,
                )
            val admin = user().copy(isAdmin = true)
            val owners = listOf(user(), user())
            val batches = TagBatchService(template, transactions, TagProperties("https://example.test"))
            val registrations = TagRegistrationService(template, transactions, PublicRequestLimits(template))
            val batchIds = mutableListOf<String>()
            val flowId = UUID.randomUUID().toString()
            val key = UUID.randomUUID().toString()
            try {
                val ids =
                    Executors.newFixedThreadPool(4).use { pool ->
                        (1..4)
                            .map {
                                pool.submit<String> {
                                    batches
                                        .create(
                                            admin,
                                            key,
                                            100,
                                            "integration",
                                        ).id
                                }
                            }.map { it.get(60, TimeUnit.SECONDS) }
                    }
                batchIds += ids.first()
                assertEquals(1, ids.toSet().size)
                val id = ids.first()
                val selection = query(where("batchId").`is`(id))
                assertEquals(100L, template.count(selection, Tag::class.java))
                val tags = template.find(selection, Tag::class.java)
                assertEquals(100, tags.map { it.position }.toSet().size)
                batches.release(admin, id)
                batches.release(admin, id)
                assertEquals(
                    101,
                    batches
                        .export(admin, id)
                        .trim()
                        .lines()
                        .size,
                )
                val tag = tags.first()
                val results =
                    Executors.newFixedThreadPool(2).use { pool ->
                        owners
                            .map { owner ->
                                pool.submit<Int> {
                                    try {
                                        registrations.direct(tag.publicId, owner)
                                        200
                                    } catch (ex: ResponseStatusException) {
                                        ex.statusCode.value()
                                    }
                                }
                            }.map { it.get(60, TimeUnit.SECONDS) }
                            .sorted()
                    }
                assertEquals(listOf(200, 409), results)
                val winner = template.findById(tag.id, Tag::class.java)!!.primaryOwnerUserId
                assertNotNull(winner)
                registrations.direct(tag.publicId, owners.first { it.id == winner })
                val draftTag = tags[1]
                template.insert(TagRegistrationDraft(flowId, draftTag.id, draftTag.publicId, owners[0].email, now.plusSeconds(14400)))
                registrations.complete(flowId, owners[0], owners[0].email)
                registrations.complete(flowId, owners[0], owners[0].email)
                assertEquals(null, template.findById(flowId, TagRegistrationDraft::class.java)!!.email)
                assertEquals(owners[0].id, template.findById(draftTag.id, Tag::class.java)!!.primaryOwnerUserId)
                val tagService = TagService(template, TagCreationLimit(template))
                val summary = tagService.summary(TagFilter(batchId = id, inventoryStatus = InventoryStatus.RELEASED))
                assertEquals(100L, summary.total)
                assertEquals(2L, summary.assigned)
                assertEquals(98L, summary.unassigned)
                assertEquals(25, tagService.list(batchId = id, inventoryStatus = InventoryStatus.RELEASED).items.size)

                val badBatch = batches.create(admin, UUID.randomUUID().toString(), 3, "rollback")
                batchIds += badBatch.id
                template.updateFirst(query(where("batchId").`is`(badBatch.id)), Update.update("status", TagStatus.BLOCKED), Tag::class.java)
                assertEquals(409, assertFailsWith<ResponseStatusException> { batches.release(admin, badBatch.id) }.statusCode.value())
                assertEquals(
                    3L,
                    template.count(
                        query(where("batchId").`is`(badBatch.id).and("inventoryStatus").`is`(InventoryStatus.PREPARED)),
                        Tag::class.java,
                    ),
                )
                assertEquals(null, batches.detail(badBatch.id).releasedAt)
            } finally {
                // Include a committed series even if a response was lost before its ID was recorded.
                batchIds += template.find(query(where("createdByUserId").`is`(admin.id)), TagBatch::class.java).map { it.id }
                template.remove(query(where("batchId").`in`(batchIds)), Tag::class.java)
                template.remove(query(where("_id").`in`(batchIds)), TagBatch::class.java)
                template.remove(query(where("actorUserId").`in`((owners + admin).map { it.id })), TagAudit::class.java)
                template.remove(query(where("_id").`is`(flowId)), TagRegistrationDraft::class.java)
                // Clean only the exact isolated users' buckets; never remove live users' limits.
                val stored = template.findById("public-v1", Document::class.java, "rateLimitKeys")
                if (stored != null) {
                    val secret = Base64.getDecoder().decode(stored.getString("key"))
                    for (owner in owners) {
                        for (window in (now.epochSecond / 3600)..(Instant.now().epochSecond / 3600)) {
                            val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret, "HmacSHA256")) }
                            val bucket =
                                Base64.getUrlEncoder().withoutPadding().encodeToString(
                                    mac.doFinal("registration-user:${owner.id}:$window".toByteArray()),
                                )
                            template.remove(query(where("_id").`is`(bucket)), "publicRequestLimits")
                        }
                    }
                }
            }
        }
    }
}
