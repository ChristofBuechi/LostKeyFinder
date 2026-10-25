package ch.lostkeyfinder.tags

import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.ZoneOffset

@Service
class TagCreationLimit(
    private val template: MongoTemplate,
) {
    fun reserve(ownerId: String) {
        val day = LocalDate.now(ZoneOffset.UTC)
        // Reset a previous day's window atomically; concurrent resets cannot erase today's increments.
        template.updateFirst(
            query(
                where("_id").`is`(ownerId).orOperator(
                    where("tagCreationLimit.day").exists(false),
                    where("tagCreationLimit.day").lt(day.toString()),
                ),
            ),
            Update.update("tagCreationLimit", mapOf("day" to day.toString(), "count" to 0)),
            "users",
        )
        val increment =
            template.updateFirst(
                query(
                    where("_id")
                        .`is`(ownerId)
                        .and("tagCreationLimit.day")
                        .`is`(day.toString())
                        .and("tagCreationLimit.count")
                        .lt(100),
                ),
                Update().inc("tagCreationLimit.count", 1),
                "users",
            )
        if (increment.modifiedCount != 1L) throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Daily tag creation limit reached")
    }
}
