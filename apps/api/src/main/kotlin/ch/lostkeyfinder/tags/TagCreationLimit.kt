package ch.lostkeyfinder.tags

import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Service
class TagCreationLimit(
    private val template: MongoTemplate,
) {
    fun reserve(ownerId: String) {
        val day = LocalDate.now(ZoneOffset.UTC)
        val key = "$ownerId:$day"
        // Fixed UTC windows; expiry only cleans storage, the conditional write enforces the limit.
        try {
            template.upsert(
                query(where("_id").`is`(key)),
                Update().setOnInsert("count", 0).setOnInsert("expiresAt", Instant.now().plusSeconds(172800)),
                "tagCreationLimits",
            )
        } catch (ex: DuplicateKeyException) {
            // Another request initialized this same fixed window; use its counter below.
        }
        val increment =
            template.updateFirst(
                query(where("_id").`is`(key).and("count").lt(100)),
                Update().inc("count", 1),
                "tagCreationLimits",
            )
        if (increment.modifiedCount != 1L) throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Daily tag creation limit reached")
    }
}
