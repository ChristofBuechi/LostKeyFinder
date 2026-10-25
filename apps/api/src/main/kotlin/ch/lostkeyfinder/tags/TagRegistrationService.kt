package ch.lostkeyfinder.tags

import ch.lostkeyfinder.identity.normalizeEmail
import ch.lostkeyfinder.users.User
import org.springframework.data.mongodb.core.MongoOperations
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
class TagRegistrationService(
    private val template: MongoTemplate,
    private val transactions: TagTransactions,
    private val limits: PublicRequestLimits,
) {
    fun resolve(
        publicId: String,
        peer: String,
    ): PublicTagResponse {
        limits.reserve("resolve-peer", peer, 300)
        TagService.uuid(publicId)
        limits.reserve("resolve-tag", publicId.lowercase(), 120)
        val tag = template.findOne(query(where("publicId").`is`(publicId.lowercase())), Tag::class.java)
        val action =
            when {
                tag == null || tag.deletedAt != null || tag.status != TagStatus.ACTIVE ||
                    (tag.origin == TagOrigin.PREPRODUCED && tag.inventoryStatus != InventoryStatus.RELEASED) -> PublicTagAction.UNAVAILABLE

                tag.primaryOwnerUserId != null -> PublicTagAction.REPORT_FOUND

                tag.origin == TagOrigin.PREPRODUCED && tag.inventoryStatus == InventoryStatus.RELEASED -> PublicTagAction.REGISTER

                else -> PublicTagAction.UNAVAILABLE
            }
        return PublicTagResponse(action)
    }

    fun start(
        publicId: String,
        email: String,
        peer: String,
    ): RegistrationStarted {
        limits.reserve("draft-peer", peer, 20)
        TagService.uuid(publicId)
        val normalized =
            try {
                normalizeEmail(email)
            } catch (ex: IllegalArgumentException) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email")
            }
        limits.reserve("draft-email", normalized, 10)
        limits.reserve("draft-tag", publicId.lowercase(), 20)
        val tag =
            template.findOne(query(where("publicId").`is`(publicId.lowercase())), Tag::class.java)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Tag unavailable")
        if (!registrable(tag)) throw ResponseStatusException(HttpStatus.CONFLICT, "Tag unavailable")
        val draft =
            TagRegistrationDraft(
                UUID.randomUUID().toString(),
                tag.id,
                tag.publicId,
                normalized,
                Instant.now().plusSeconds(14400).truncatedTo(ChronoUnit.MILLIS),
            )
        template.insert(draft)
        return RegistrationStarted(draft.id, draft.expiresAt)
    }

    fun view(
        flowId: String,
        user: User,
        verifiedEmail: String,
    ): RegistrationView {
        TagService.uuid(flowId)
        val draft =
            template.findById(flowId.lowercase(), TagRegistrationDraft::class.java)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Registration unavailable")
        authorize(draft, user, verifiedEmail)
        return RegistrationView(draft.id, draft.publicId, draft.completedUserId != null, draft.expiresAt)
    }

    fun complete(
        flowId: String,
        user: User,
        verifiedEmail: String,
    ): TagResponse {
        TagService.uuid(flowId)
        limits.reserve("registration-user", user.id, 30)
        return transactions.run { ops ->
            val draft =
                ops.findById(flowId.lowercase(), TagRegistrationDraft::class.java)
                    ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Registration unavailable")
            authorize(draft, user, verifiedEmail)
            val result = assign(ops, draft.publicId, user)
            if (draft.completedUserId == null) {
                ops.updateFirst(
                    query(where("_id").`is`(draft.id)),
                    Update.update("completedUserId", user.id).unset("email"),
                    TagRegistrationDraft::class.java,
                )
            }
            result
        }
    }

    fun direct(
        publicId: String,
        user: User,
    ): TagResponse {
        TagService.uuid(publicId)
        limits.reserve("registration-user", user.id, 30)
        return transactions.run { assign(it, publicId.lowercase(), user) }
    }

    private fun assign(
        ops: MongoOperations,
        publicId: String,
        user: User,
    ): TagResponse {
        val tag =
            ops.findOne(query(where("publicId").`is`(publicId)), Tag::class.java)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Tag unavailable")
        if (tag.deletedAt == null && tag.primaryOwnerUserId == user.id &&
            tag.status in listOf(TagStatus.ACTIVE, TagStatus.INACTIVE)
        ) {
            return TagResponse.from(tag)
        }
        if (!registrable(tag)) throw ResponseStatusException(HttpStatus.CONFLICT, "Tag unavailable")
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val result =
            ops.updateFirst(
                query(
                    where("_id")
                        .`is`(tag.id)
                        .and("primaryOwnerUserId")
                        .`is`(null)
                        .and("inventoryStatus")
                        .`is`(InventoryStatus.RELEASED)
                        .and("status")
                        .`is`(TagStatus.ACTIVE)
                        .and("origin")
                        .`is`(TagOrigin.PREPRODUCED),
                ),
                Update.update("primaryOwnerUserId", user.id).set("registeredAt", now).set("updatedAt", now),
                Tag::class.java,
            )
        if (result.modifiedCount != 1L) throw ResponseStatusException(HttpStatus.CONFLICT, "Tag unavailable")
        ops.insert(TagAudit(UUID.randomUUID().toString(), user.id, "TAG_REGISTERED", tag.id, now))
        return TagResponse.from(tag.copy(primaryOwnerUserId = user.id, registeredAt = now, updatedAt = now))
    }

    private fun authorize(
        draft: TagRegistrationDraft,
        user: User,
        verifiedEmail: String,
    ) {
        if (!draft.expiresAt.isAfter(Instant.now())) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Registration unavailable")
        val matches = if (draft.completedUserId != null) draft.completedUserId == user.id else draft.email == verifiedEmail
        if (!matches) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Registration unavailable")
    }

    private fun registrable(tag: Tag) =
        tag.deletedAt == null && tag.origin == TagOrigin.PREPRODUCED && tag.primaryOwnerUserId == null &&
            tag.inventoryStatus == InventoryStatus.RELEASED && tag.status == TagStatus.ACTIVE
}
