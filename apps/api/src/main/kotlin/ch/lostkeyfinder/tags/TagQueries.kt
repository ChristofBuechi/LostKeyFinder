package ch.lostkeyfinder.tags

import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

data class TagFilter(
    val origin: TagOrigin? = null,
    val status: TagStatus? = null,
    val assigned: Boolean? = null,
    val publicId: String? = null,
    val batchId: String? = null,
    val inventoryStatus: InventoryStatus? = null,
) {
    fun criteria(ownerId: String? = null): Criteria {
        val filters = mutableListOf<Criteria>()
        ownerId?.let { filters += where("primaryOwnerUserId").`is`(it) }
        origin?.let { filters += where("origin").`is`(it) }
        status?.let { filters += where("status").`is`(it) }
        assigned?.let { filters += if (it) where("primaryOwnerUserId").ne(null) else where("primaryOwnerUserId").`is`(null) }
        publicId?.let {
            TagService.uuid(it)
            filters += where("publicId").`is`(it.lowercase())
        }
        batchId?.let {
            TagService.uuid(it)
            filters += where("batchId").`is`(it.lowercase())
        }
        inventoryStatus?.let { filters += where("inventoryStatus").`is`(it) }
        return if (filters.isEmpty()) Criteria() else Criteria().andOperator(*filters.toTypedArray())
    }
}

fun tagCursor(cursor: String?): Criteria {
    if (cursor == null) return Criteria()
    val parts = cursor.split('_')
    if (parts.size != 2) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor")
    TagService.uuid(parts[1])
    val time = parts[0].toLongOrNull()?.takeIf { it >= 0 } ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor")
    val instant = Instant.ofEpochMilli(time)
    return Criteria().orOperator(where("createdAt").lt(instant), where("createdAt").`is`(instant).and("_id").lt(parts[1].lowercase()))
}
