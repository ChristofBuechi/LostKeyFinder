package ch.lostkeyfinder.users

import ch.lostkeyfinder.identity.normalizeEmail
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.UUID

enum class UserStatus { ACTIVE, DISABLED, DELETING }

@Document("users")
data class User(
    @field:Id val id: String,
    val supabaseUserId: String,
    val email: String,
    val status: UserStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(UUID.fromString(id).version() == 4 && UUID.fromString(supabaseUserId).version() == 4)
        require(email == normalizeEmail(email))
        require(!updatedAt.isBefore(createdAt))
    }

    @get:Transient
    val canAccess: Boolean get() = status == UserStatus.ACTIVE
}
