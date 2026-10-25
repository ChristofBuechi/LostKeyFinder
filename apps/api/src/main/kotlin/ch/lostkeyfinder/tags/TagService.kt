package ch.lostkeyfinder.tags

import ch.lostkeyfinder.users.User
import org.bson.Document
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
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
class TagService(
    private val template: MongoTemplate,
    private val creationLimit: TagCreationLimit,
) {
    fun create(
        owner: User,
        key: String,
        label: String,
    ): TagResponse {
        uuid(key)
        val creationKey = "${owner.id}:${key.lowercase()}"
        val selection = query(where("creationKey").`is`(creationKey))
        val existing = template.findOne(selection, Tag::class.java)
        if (existing != null) return createdResponse(existing, label)
        creationLimit.reserve(owner.id)
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val update =
            Update()
                .setOnInsert("_id", UUID.randomUUID().toString())
                .setOnInsert("publicId", UUID.randomUUID().toString())
                .setOnInsert("origin", TagOrigin.SELF_CREATED)
                .setOnInsert("primaryOwnerUserId", owner.id)
                .setOnInsert("status", TagStatus.ACTIVE)
                .setOnInsert("label", label.trim())
                .setOnInsert("createdAt", now)
                .setOnInsert("updatedAt", now)
                .setOnInsert("registeredAt", now)
        val tag =
            try {
                template.findAndModify(selection, update, FindAndModifyOptions.options().upsert(true).returnNew(true), Tag::class.java)
            } catch (ex: DuplicateKeyException) {
                template.findOne(selection, Tag::class.java)
            } ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)
        return createdResponse(tag, label)
    }

    private fun createdResponse(
        tag: Tag,
        label: String,
    ): TagResponse {
        if (tag.label != label.trim()) throw ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key already used")
        return TagResponse.from(tag)
    }

    fun owned(
        owner: User,
        id: String,
    ): TagResponse {
        uuid(id)
        return template
            .findOne(query(where("_id").`is`(id).and("primaryOwnerUserId").`is`(owner.id)), Tag::class.java)
            ?.let(TagResponse::from) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Tag unavailable")
    }

    fun changeStatus(
        owner: User,
        id: String,
        status: TagStatus,
    ): TagResponse {
        uuid(id)
        if (status !in listOf(TagStatus.ACTIVE, TagStatus.INACTIVE)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported owner action")
        }
        val selection =
            query(
                where("_id")
                    .`is`(id)
                    .and("primaryOwnerUserId")
                    .`is`(owner.id)
                    .and("status")
                    .`in`(TagStatus.ACTIVE, TagStatus.INACTIVE),
            )
        val tag =
            template.findAndModify(
                selection,
                Update.update("status", status).set("updatedAt", Instant.now().truncatedTo(ChronoUnit.MILLIS)),
                FindAndModifyOptions.options().returnNew(true),
                Tag::class.java,
            ) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Tag unavailable")
        return TagResponse.from(tag)
    }

    fun list(
        ownerId: String? = null,
        cursor: String? = null,
        filter: TagFilter = TagFilter(),
    ): TagPage {
        val filters = filter.criteria(ownerId)
        val criteria = if (cursor == null) filters else Criteria().andOperator(filters, tagCursor(cursor))
        val tags =
            template.find(
                query(criteria).with(Sort.by(Sort.Direction.DESC, "createdAt", "_id")).limit(PAGE_SIZE + 1),
                Tag::class.java,
            )
        val page = tags.take(PAGE_SIZE)
        val next = if (tags.size > PAGE_SIZE) page.last().let { "${it.createdAt.toEpochMilli()}_${it.id}" } else null
        return TagPage(page.map(TagResponse::from), next)
    }

    fun adminDetail(id: String): TagResponse {
        uuid(id)
        return template.findById(id.lowercase(), Tag::class.java)?.let(TagResponse::from)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Tag unavailable")
    }

    fun ownedPublic(
        owner: User,
        publicId: String,
    ): TagResponse {
        uuid(publicId)
        return template
            .findOne(
                query(where("publicId").`is`(publicId.lowercase()).and("primaryOwnerUserId").`is`(owner.id)),
                Tag::class.java,
            )?.let(TagResponse::from) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Tag unavailable")
    }

    fun summary(filter: TagFilter): TagSummary {
        fun sum(condition: Document) = Document("\$sum", Document("\$cond", listOf(condition, 1, 0)))

        fun equals(
            field: String,
            value: Any?,
        ) = Document("\$eq", listOf("\$$field", value))
        val owner = Document("\$ifNull", listOf("\$primaryOwnerUserId", null))
        val group =
            Document("_id", null)
                .append("total", Document("\$sum", 1))
                .append("assigned", sum(Document("\$ne", listOf(owner, null))))
                .append("unassigned", sum(Document("\$eq", listOf(owner, null))))
                .append("blocked", sum(equals("status", "BLOCKED")))
                .append("retired", sum(equals("status", "RETIRED")))
                .append("prepared", sum(equals("inventoryStatus", "PREPARED")))
                .append("released", sum(equals("inventoryStatus", "RELEASED")))
        val pipeline =
            Aggregation.newAggregation({ Document("\$match", filter.criteria().criteriaObject) }, { Document("\$group", group) })
        return template.aggregate(pipeline, "tags", TagSummary::class.java).uniqueMappedResult ?: TagSummary()
    }

    companion object {
        private const val PAGE_SIZE = 25
        private val uuidV4 = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")

        fun uuid(value: String) {
            if (!uuidV4.matches(value)) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid identifier")
        }
    }
}
