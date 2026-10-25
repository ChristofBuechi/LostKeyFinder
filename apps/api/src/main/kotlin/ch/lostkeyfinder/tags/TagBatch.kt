package ch.lostkeyfinder.tags

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document("tagBatches")
data class TagBatch(
    @field:Id val id: String,
    val creationKey: String,
    val count: Int,
    val label: String,
    val createdByUserId: String,
    val createdAt: Instant,
    val releasedAt: Instant? = null,
)

@Schema(requiredProperties = ["id", "count", "label", "createdAt", "releasedAt"])
data class TagBatchResponse(
    val id: String,
    val count: Int,
    val label: String,
    val createdAt: Instant,
    val releasedAt: Instant?,
) {
    companion object {
        fun from(batch: TagBatch) = TagBatchResponse(batch.id, batch.count, batch.label, batch.createdAt, batch.releasedAt)
    }
}

@Schema(requiredProperties = ["items", "nextCursor"])
data class TagBatchPage(
    val items: List<TagBatchResponse>,
    val nextCursor: String?,
)

@Document("auditLog")
data class TagAudit(
    @field:Id val id: String,
    val actorUserId: String,
    val action: String,
    val resourceId: String,
    val createdAt: Instant,
)

@Schema(requiredProperties = ["total", "assigned", "unassigned", "blocked", "retired", "prepared", "released"])
data class TagSummary(
    val total: Long = 0,
    val assigned: Long = 0,
    val unassigned: Long = 0,
    val blocked: Long = 0,
    val retired: Long = 0,
    val prepared: Long = 0,
    val released: Long = 0,
)
