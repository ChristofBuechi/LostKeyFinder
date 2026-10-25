package ch.lostkeyfinder.tags

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document("tagRegistrationDrafts")
data class TagRegistrationDraft(
    @field:Id val id: String,
    val tagId: String,
    val publicId: String,
    val email: String?,
    val expiresAt: Instant,
    val completedUserId: String? = null,
)

@Schema(requiredProperties = ["flowId", "expiresAt"])
data class RegistrationStarted(
    val flowId: String,
    val expiresAt: Instant,
)

@Schema(requiredProperties = ["flowId", "publicId", "completed", "expiresAt"])
data class RegistrationView(
    val flowId: String,
    val publicId: String,
    val completed: Boolean,
    val expiresAt: Instant,
)

enum class PublicTagAction { REGISTER, REPORT_FOUND, UNAVAILABLE }

@Schema(requiredProperties = ["action"])
data class PublicTagResponse(
    val action: PublicTagAction,
)
