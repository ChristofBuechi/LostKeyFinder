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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@Tag(name = "users")
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@SecurityRequirement(name = "bearerAuth")
class UserController(
    private val users: UserService,
) {
    @GetMapping("/api/v1/me", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(operationId = "UserController_me", summary = "Return the authenticated local user")
    fun me(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
    ): CurrentUserResponse {
        val user = users.currentUser(jwt)
        return CurrentUserResponse(user.id, user.email, user.status, user.createdAt, user.updatedAt)
    }
}

data class CurrentUserResponse(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "uuid") val id: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "email") val email: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val status: UserStatus,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val createdAt: Instant,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val updatedAt: Instant,
)
