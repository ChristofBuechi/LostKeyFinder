package ch.lostkeyfinder.users

import ch.lostkeyfinder.identity.normalizeEmail
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.UUID

enum class UserStatus { ACTIVE, DISABLED, DELETING }

data class TagCreationWindow(
    val day: String,
    val count: Int,
)

@Document("users")
data class User(
    @field:Id val id: String,
    val firebaseUserId: String,
    val email: String,
    val status: UserStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val isAdmin: Boolean = false,
    val language: String = "de",
    val tagCreationLimit: TagCreationWindow? = null,
) {
    init {
        require(UUID.fromString(id).version() == 4 && firebaseUserId.isNotBlank() && firebaseUserId.length <= 128)
        require(email == normalizeEmail(email))
        require(!updatedAt.isBefore(createdAt))
    }

    @get:Transient
    val canAccess: Boolean get() = status == UserStatus.ACTIVE
}
