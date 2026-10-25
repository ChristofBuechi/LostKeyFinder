package ch.lostkeyfinder.tags

import ch.lostkeyfinder.users.User
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class TagBatchService(
    private val template: MongoTemplate,
    private val transactions: TagTransactions,
    private val properties: TagProperties,
) {
    fun create(
        admin: User,
        key: String,
        count: Int,
        label: String,
    ): TagBatchResponse =
        try {
            createOnce(admin, key, count, label)
        } catch (ex: DuplicateKeyException) {
            val batch =
                template.findOne(query(where("creationKey").`is`("${admin.id}:${key.lowercase()}")), TagBatch::class.java)
                    ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Retry the same request")
            if (batch.count != count ||
                batch.label != label.trim()
            ) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key already used")
            }
            TagBatchResponse.from(batch)
        }

    private fun createOnce(
        admin: User,
        key: String,
        count: Int,
        label: String,
    ): TagBatchResponse {
        TagService.uuid(key)
        if (count !in 1..100 || label.length > 80) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid batch")
        val creationKey = "${admin.id}:${key.lowercase()}"
        return transactions.run { ops ->
            val existing = ops.findOne(query(where("creationKey").`is`(creationKey)), TagBatch::class.java)
            if (existing != null) {
                if (existing.count != count ||
                    existing.label != label.trim()
                ) {
                    throw ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key already used")
                }
                TagBatchResponse.from(existing)
            } else {
                val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
                val batch = TagBatch(UUID.randomUUID().toString(), creationKey, count, label.trim(), admin.id, now)
                ops.insert(batch)
                ops.insert(
                    (1..count).map {
                        Tag(
                            UUID.randomUUID().toString(),
                            UUID.randomUUID().toString(),
                            TagOrigin.PREPRODUCED,
                            null,
                            TagStatus.ACTIVE,
                            "",
                            now,
                            now,
                            batchId = batch.id,
                            inventoryStatus = InventoryStatus.PREPARED,
                        )
                    },
                    Tag::class.java,
                )
                ops.insert(
                    TagAudit(
                        UUID.randomUUID().toString(),
                        admin.id,
                        "BATCH_CREATED",
                        batch.id,
                        now,
                        listOf(TagChange("count", null, count.toString()), TagChange("batchLabel", null, label.trim())),
                    ),
                )
                TagBatchResponse.from(batch)
            }
        }
    }

    fun list(cursor: String?): TagBatchPage {
        val batches =
            template.find(
                query(tagCursor(cursor)).with(Sort.by(Sort.Direction.DESC, "createdAt", "_id")).limit(26),
                TagBatch::class.java,
            )
        val items = batches.take(25)
        val next = if (batches.size > 25) items.last().let { "${it.createdAt.toEpochMilli()}_${it.id}" } else null
        return TagBatchPage(items.map(TagBatchResponse::from), next)
    }

    fun detail(id: String): TagBatchResponse = TagBatchResponse.from(batch(id))

    private fun batch(id: String): TagBatch {
        TagService.uuid(id)
        return template.findById(id.lowercase(), TagBatch::class.java)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Batch unavailable")
    }

    fun release(
        admin: User,
        id: String,
    ): TagBatchResponse {
        TagService.uuid(id)
        return transactions.run { ops ->
            val batch =
                ops.findById(id.lowercase(), TagBatch::class.java)
                    ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Batch unavailable")
            if (batch.releasedAt != null) {
                TagBatchResponse.from(batch)
            } else {
                val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
                val changed =
                    ops.updateMulti(
                        query(
                            where(
                                "batchId",
                            ).`is`(batch.id)
                                .and("inventoryStatus")
                                .`is`(InventoryStatus.PREPARED)
                                .and("primaryOwnerUserId")
                                .`is`(null)
                                .and("status")
                                .`is`(TagStatus.ACTIVE),
                        ),
                        Update.update("inventoryStatus", InventoryStatus.RELEASED).set("updatedAt", now),
                        Tag::class.java,
                    )
                if (changed.modifiedCount !=
                    batch.count.toLong()
                ) {
                    throw ResponseStatusException(HttpStatus.CONFLICT, "Batch is not ready for release")
                }
                ops.updateFirst(query(where("_id").`is`(batch.id)), Update.update("releasedAt", now), TagBatch::class.java)
                ops.insert(
                    TagAudit(
                        UUID.randomUUID().toString(),
                        admin.id,
                        "BATCH_RELEASED",
                        batch.id,
                        now,
                        listOf(
                            TagChange("inventoryStatus", InventoryStatus.PREPARED.name, InventoryStatus.RELEASED.name),
                            TagChange("batchReleasedAt", null, now.toString()),
                        ),
                    ),
                )
                TagBatchResponse.from(batch.copy(releasedAt = now))
            }
        }
    }

    fun export(
        admin: User,
        id: String,
    ): String {
        if (properties.publicBaseUrl.isBlank()) {
            throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Public tag URL not configured",
            )
        }
        val batch = batch(id)
        val tags = template.find(query(where("batchId").`is`(batch.id)).with(Sort.by("_id")), Tag::class.java)
        if (tags.size != batch.count ||
            tags.map { it.publicId }.toSet().size != batch.count
        ) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Batch incomplete")
        }
        template.insert(TagAudit(UUID.randomUUID().toString(), admin.id, "BATCH_EXPORTED", batch.id, Instant.now()))
        return "batchId,position,publicId,url\r\n" +
            tags
                .filter { it.deletedAt == null }
                .mapIndexed { index, tag ->
                    // ponytail: CSV row numbers are presentation, not persistent tag state.
                    listOf(batch.id, (index + 1).toString(), tag.publicId, "${properties.publicBaseUrl.trimEnd('/')}/f/${tag.publicId}")
                        .joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" } + "\r\n"
                }.joinToString("")
    }
}
