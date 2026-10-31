package ch.lostkeyfinder.users

import org.springframework.data.mongodb.core.MongoOperations
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

// Call inside the mutation transaction: this write conflicts with concurrent account deletion.
fun MongoOperations.lockActiveUser(id: String) {
    val result =
        updateFirst(
            query(where("_id").`is`(id).and("status").`is`(UserStatus.ACTIVE)),
            Update().inc("activityVersion", 1),
            User::class.java,
        )
    if (result.matchedCount != 1L) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Account unavailable")
}
