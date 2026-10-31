package ch.lostkeyfinder.users

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@Tag(name = "users")
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@SecurityRequirement(name = "bearerAuth")
class UserController(
    private val users: UserService,
    private val deletions: AccountDeletionService,
) {
    @GetMapping("/api/v1/me", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(operationId = "UserController_me", summary = "Return the authenticated local user")
    fun me(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
    ): CurrentUserResponse {
        val user = users.profile(jwt)
        return response(user)
    }

    @DeleteMapping("/api/v1/me", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(operationId = "UserController_delete", summary = "Delete the authenticated account, resumable in bounded steps")
    fun delete(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
    ): AccountDeletionResponse = AccountDeletionResponse(deletions.delete(jwt))

    @PatchMapping("/api/v1/me/language", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(operationId = "UserController_changeLanguage", summary = "Set the authenticated user's preferred language")
    fun changeLanguage(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @RequestBody request: ChangeLanguageRequest,
    ): CurrentUserResponse = response(users.changeLanguage(jwt, request.language))

    private fun response(user: User) =
        CurrentUserResponse(user.id, user.email, user.status, user.createdAt, user.updatedAt, user.isAdmin, user.language)
}

data class ChangeLanguageRequest(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = ["de", "en"]) val language: String,
)

data class CurrentUserResponse(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "uuid") val id: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "email") val email: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val status: UserStatus,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val createdAt: Instant,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val updatedAt: Instant,
    @get:com.fasterxml.jackson.annotation.JsonProperty("isAdmin")
    @get:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val isAdmin: Boolean,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = ["de", "en"]) val language: String,
)

@Schema(requiredProperties = ["completed"])
data class AccountDeletionResponse(
    val completed: Boolean,
)
