package ch.lostkeyfinder.tags

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

enum class TagOrigin { SELF_CREATED, PREPRODUCED }

enum class TagStatus { ACTIVE, INACTIVE, BLOCKED, RETIRED }

enum class InventoryStatus { PREPARED, RELEASED }

@Document("tags")
data class Tag(
    @field:Id val id: String,
    val publicId: String,
    val origin: TagOrigin,
    val primaryOwnerUserId: String?,
    val status: TagStatus,
    val label: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val creationKey: String? = null,
    val batchId: String? = null,
    val inventoryStatus: InventoryStatus? = null,
    val registeredAt: Instant? = null,
    val deletedAt: Instant? = null,
)

@Schema(
    requiredProperties = [
        "id", "publicId", "origin", "primaryOwnerUserId", "status", "label", "createdAt", "updatedAt",
        "batchId", "inventoryStatus", "registeredAt", "deletedAt",
    ],
)
data class TagResponse(
    val id: String,
    val publicId: String,
    val origin: TagOrigin,
    val primaryOwnerUserId: String?,
    val status: TagStatus,
    val label: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val batchId: String?,
    val inventoryStatus: InventoryStatus?,
    val registeredAt: Instant?,
    val deletedAt: Instant?,
) {
    companion object {
        fun from(tag: Tag) =
            TagResponse(
                tag.id,
                tag.publicId,
                tag.origin,
                tag.primaryOwnerUserId,
                tag.status,
                tag.label,
                tag.createdAt,
                tag.updatedAt,
                tag.batchId,
                tag.inventoryStatus,
                tag.registeredAt,
                tag.deletedAt,
            )
    }
}

@Schema(requiredProperties = ["items", "page", "pageSize", "totalItems", "totalPages"])
data class TagPage(
    val items: List<TagResponse>,
    val page: Int,
    val pageSize: Int,
    val totalItems: Long,
    val totalPages: Int,
)

@Schema(requiredProperties = ["id", "action", "createdAt", "actorUserId", "changes", "chatId", "messageId"])
data class TagHistoryEntry(
    val id: String,
    val action: String,
    val createdAt: Instant,
    val actorUserId: String?,
    val changes: List<TagChange>,
    val chatId: String? = null,
    val messageId: String? = null,
)

@Schema(requiredProperties = ["items", "page", "totalPages"])
data class TagHistoryPage(
    val items: List<TagHistoryEntry>,
    val page: Int,
    val totalPages: Int,
)

@Schema(requiredProperties = ["field", "before", "after"])
data class TagChange(
    val field: String,
    val before: String?,
    val after: String?,
)

// Explicit business fields keep technical idempotency keys out of the history API.
private fun Tag.historyValues(): Map<String, String?> =
    mapOf(
        "publicId" to publicId,
        "origin" to origin.name,
        "primaryOwnerUserId" to primaryOwnerUserId,
        "status" to status.name,
        "label" to label,
        "batchId" to batchId,
        "inventoryStatus" to inventoryStatus?.name,
        "createdAt" to createdAt.toString(),
        "updatedAt" to updatedAt.toString(),
        "registeredAt" to registeredAt?.toString(),
        "deletedAt" to deletedAt?.toString(),
    )

fun tagChanges(
    before: Tag?,
    after: Tag,
): List<TagChange> {
    val previous = before?.historyValues().orEmpty()
    return after.historyValues().mapNotNull { (field, value) ->
        if (previous[field] == value) null else TagChange(field, previous[field], value)
    }
}
