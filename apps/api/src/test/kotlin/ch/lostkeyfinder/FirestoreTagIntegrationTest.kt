package ch.lostkeyfinder

import ch.lostkeyfinder.tags.Tag
import ch.lostkeyfinder.tags.TagCreationLimit
import ch.lostkeyfinder.tags.TagOrigin
import ch.lostkeyfinder.tags.TagService
import ch.lostkeyfinder.tags.TagStatus
import ch.lostkeyfinder.users.User
import ch.lostkeyfinder.users.UserStatus
import com.mongodb.ConnectionString
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
        withFirestore { template ->
            val indexes = template.indexOps(Tag::class.java)
            indexes.createIndex(Index().on("publicId", Sort.Direction.ASC).unique())
            indexes.createIndex(Index().on("creationKey", Sort.Direction.ASC).unique().sparse())
            indexes.createIndex(Index().on("createdAt", Sort.Direction.DESC).on("_id", Sort.Direction.DESC))
            indexes.createIndex(
                Index().on("primaryOwnerUserId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC).on("_id", Sort.Direction.DESC),
            )
            indexes.createIndex(
                Index()
                    .on(
                        "origin",
                        Sort.Direction.ASC,
                    ).on("status", Sort.Direction.ASC)
                    .on("createdAt", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC),
            )
            template.indexOps("tagCreationLimits").createIndex(Index().on("expiresAt", Sort.Direction.ASC).expire(Duration.ZERO))
        }
}

class FirestoreTagIntegrationTest {
    @Test fun `real Firestore supports concurrent creation filtering cursor and owner status checks`() =
        withFirestore { template ->
            val now = Instant.now()
            val owner = User(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "probe@example.com", UserStatus.ACTIVE, now, now)
            val tags = TagService(template, TagCreationLimit(template))
            val key = UUID.randomUUID().toString()
            val limitId = "${owner.id}:${LocalDate.now(ZoneOffset.UTC)}"
            val selection = query(where("primaryOwnerUserId").`is`(owner.id))
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
                repeat(26) { tags.create(owner, UUID.randomUUID().toString(), "") }
                val first = tags.list(ownerId = owner.id)
                assertEquals(25, first.items.size)
                val second = tags.list(ownerId = owner.id, cursor = requireNotNull(first.nextCursor))
                assertEquals(2, second.items.size)
                assertEquals(27, (first.items + second.items).map { it.id }.toSet().size)
                val tag = tags.create(owner, key, "Firestore probe")
                assertEquals(tag, tags.owned(owner, tag.id))
                assertEquals(TagStatus.INACTIVE, tags.changeStatus(owner, tag.id, TagStatus.INACTIVE).status)
                val filtered =
                    tags.list(
                        ownerId = owner.id,
                        origin = TagOrigin.SELF_CREATED,
                        status = TagStatus.INACTIVE,
                        assigned = true,
                        publicId = tag.publicId,
                    )
                assertEquals(listOf(tag.id), filtered.items.map { it.id })
                assertEquals(emptyList(), tags.list(ownerId = owner.id, origin = TagOrigin.PREPRODUCED, assigned = false).items)
                assertEquals(
                    404,
                    assertFailsWith<ResponseStatusException> {
                        tags.owned(owner.copy(id = UUID.randomUUID().toString()), tag.id)
                    }.statusCode.value(),
                )
            } finally {
                template.remove(selection, Tag::class.java)
                template.remove(query(where("_id").`is`(limitId)), "tagCreationLimits")
            }
        }
}

private fun withFirestore(action: (MongoTemplate) -> Unit) {
    val uri = requireNotNull(System.getenv("FIRESTORE_MONGODB_URI"))
    MongoClients.create(uri).use { client -> action(MongoTemplate(client, requireNotNull(ConnectionString(uri).database))) }
}
