package ch.lostkeyfinder

import ch.lostkeyfinder.tags.MongoTagTransactions
import ch.lostkeyfinder.tags.Tag
import ch.lostkeyfinder.tags.TagAudit
import ch.lostkeyfinder.tags.TagBatch
import ch.lostkeyfinder.tags.TagCreationLimit
import ch.lostkeyfinder.tags.TagFilter
import ch.lostkeyfinder.tags.TagOrigin
import ch.lostkeyfinder.tags.TagRegistrationDraft
import ch.lostkeyfinder.tags.TagService
import ch.lostkeyfinder.tags.TagStatus
import ch.lostkeyfinder.users.User
import ch.lostkeyfinder.users.UserStatus
import com.mongodb.ConnectionString
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// This explicit operator task uses index-admin credentials, never the runtime service account.
class FirestoreTagIndexProvisioningTest {
    @Test fun `provision tag indexes with operator credentials`() =
        withFirestore { template, _ ->
            provisionChatIndexes(template)
            provisionAccountDeletionIndexes(template)
            val indexes = template.indexOps(Tag::class.java)
            // Operator-only: the old sparse unique index rejects multiple tags without position.
            indexes.indexInfo
                .filter { it.indexFields.map { field -> field.key } == listOf("batchId", "position") }
                .forEach { indexes.dropIndex(it.name) }
            val operations: List<() -> Unit> =
                listOf(
                    {
                        // ponytail: one provisioning command also supports a completely empty database.
                        template.indexOps(User::class.java).createIndex(Index().on("firebaseUserId", Sort.Direction.ASC).unique())
                        Unit
                    },
                    {
                        indexes.createIndex(Index().on("publicId", Sort.Direction.ASC).unique())
                        Unit
                    },
                    {
                        indexes.createIndex(Index().on("creationKey", Sort.Direction.ASC).unique().sparse())
                        Unit
                    },
                    {
                        indexes.createIndex(Index().on("createdAt", Sort.Direction.DESC).on("_id", Sort.Direction.DESC))
                        Unit
                    },
                    {
                        indexes.createIndex(
                            Index()
                                .on(
                                    "primaryOwnerUserId",
                                    Sort.Direction.ASC,
                                ).on("createdAt", Sort.Direction.DESC)
                                .on("_id", Sort.Direction.DESC),
                        )
                        Unit
                    },
                    {
                        indexes.createIndex(
                            Index()
                                .on(
                                    "origin",
                                    Sort.Direction.ASC,
                                ).on("status", Sort.Direction.ASC)
                                .on("createdAt", Sort.Direction.DESC)
                                .on("_id", Sort.Direction.DESC),
                        )
                        Unit
                    },
                    {
                        indexes.createIndex(
                            Index()
                                .on("batchId", Sort.Direction.ASC)
                                .on("_id", Sort.Direction.ASC),
                        )
                        Unit
                    },
                    {
                        template.indexOps(TagAudit::class.java).createIndex(
                            Index()
                                .on("resourceId", Sort.Direction.ASC)
                                .on("createdAt", Sort.Direction.DESC)
                                .on("_id", Sort.Direction.DESC),
                        )
                        Unit
                    },
                    {
                        template.indexOps(TagBatch::class.java).createIndex(Index().on("creationKey", Sort.Direction.ASC).unique())
                        Unit
                    },
                    {
                        template
                            .indexOps(
                                TagBatch::class.java,
                            ).createIndex(Index().on("createdAt", Sort.Direction.DESC).on("_id", Sort.Direction.DESC))
                        Unit
                    },
                    {
                        template
                            .indexOps(
                                TagRegistrationDraft::class.java,
                            ).createIndex(Index().on("expiresAt", Sort.Direction.ASC).expire(Duration.ZERO))
                        Unit
                    },
                    {
                        template
                            .indexOps(
                                "rateLimits",
                            ).createIndex(Index().on("expiresAt", Sort.Direction.ASC).expire(Duration.ZERO))
                        Unit
                    },
                )
            Executors.newFixedThreadPool(4).use { pool ->
                operations.map { operation -> pool.submit<Unit> { operation() } }.forEach { it.get(240, TimeUnit.SECONDS) }
            }
        }
}

class FirestoreTagIntegrationTest {
    @Test fun `real Firestore supports concurrent creation filtering numbered pages and owner status checks`() =
        withFirestore { template, client ->
            val now = Instant.now()
            val owner = User(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "probe@example.com", UserStatus.ACTIVE, now, now)
            val tags = TagService(template, TagCreationLimit(template), MongoTagTransactions(client, template))
            val key = UUID.randomUUID().toString()
            val selection = query(where("primaryOwnerUserId").`is`(owner.id))
            template.insert(owner)
            try {
                val results =
                    Executors.newFixedThreadPool(4).use { pool ->
                        (1..8)
                            .map {
                                pool.submit<String> {
                                    tags
                                        .create(
                                            owner,
                                            key,
                                            "Firestore probe",
                                        ).id
                                }
                            }.map { it.get(45, TimeUnit.SECONDS) }
                    }
                assertEquals(1, results.toSet().size)
                assertEquals(
                    1L,
                    template.count(
                        query(where("resourceId").`is`(results.first()).and("action").`is`("TAG_CREATED")),
                        TagAudit::class.java,
                    ),
                )
                repeat(26) { tags.create(owner, UUID.randomUUID().toString(), "") }
                val first = tags.list(ownerId = owner.id)
                assertEquals(25, first.items.size)
                val second = tags.list(ownerId = owner.id, page = 2)
                assertEquals(2, second.items.size)
                assertEquals(27L, first.totalItems)
                assertEquals(2, first.totalPages)
                assertEquals(2, second.page)
                assertEquals(27, (first.items + second.items).map { it.id }.toSet().size)
                val tag = tags.create(owner, key, "Firestore probe")
                assertEquals(tag, tags.owned(owner, tag.id))
                assertEquals(TagStatus.INACTIVE, tags.changeStatus(owner, tag.id, TagStatus.INACTIVE).status)
                val history = tags.history(tag.id, owner = owner)
                assertEquals(listOf("TAG_DEACTIVATED", "TAG_CREATED"), history.items.map { it.action })
                kotlin.test.assertTrue(history.items.all { it.actorUserId == null })
                val filtered =
                    tags.list(
                        ownerId = owner.id,
                        filter =
                            TagFilter(
                                origin = TagOrigin.SELF_CREATED,
                                status = TagStatus.INACTIVE,
                                assigned = true,
                                publicId = tag.publicId,
                            ),
                    )
                assertEquals(listOf(tag.id), filtered.items.map { it.id })
                assertEquals(
                    emptyList(),
                    tags.list(ownerId = owner.id, filter = TagFilter(origin = TagOrigin.PREPRODUCED, assigned = false)).items,
                )
                assertEquals(
                    404,
                    assertFailsWith<ResponseStatusException> {
                        tags.owned(owner.copy(id = UUID.randomUUID().toString()), tag.id)
                    }.statusCode.value(),
                )
                tags.delete(owner, tag.id)
                val deleted = template.findById(tag.id, Tag::class.java)!!
                kotlin.test.assertNotNull(deleted.deletedAt)
                assertEquals(TagStatus.RETIRED, deleted.status)
                assertEquals(tag.publicId, deleted.publicId)
                assertEquals(tag.label, deleted.label)
                assertEquals(26L, tags.list(ownerId = owner.id).totalItems)
                assertEquals(404, assertFailsWith<ResponseStatusException> { tags.owned(owner, tag.id) }.statusCode.value())
            } finally {
                template.remove(query(where("actorUserId").`is`(owner.id)), TagAudit::class.java)
                template.remove(selection, Tag::class.java)
                template.remove(query(where("_id").`is`(owner.id)), User::class.java)
            }
        }
}

private fun withFirestore(action: (MongoTemplate, MongoClient) -> Unit) {
    val uri = requireNotNull(System.getenv("FIRESTORE_MONGODB_URI"))
    MongoClients.create(uri).use { client -> action(MongoTemplate(client, requireNotNull(ConnectionString(uri).database)), client) }
}
