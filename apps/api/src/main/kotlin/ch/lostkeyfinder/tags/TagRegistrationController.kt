package ch.lostkeyfinder.tags

import ch.lostkeyfinder.identity.FirebaseIdentity
import ch.lostkeyfinder.users.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class StartTagRegistrationRequest(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) @field:Size(min = 3, max = 254) val email: String,
)

@RestController
@RequestMapping("/api/v1", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "tag-registration")
class TagRegistrationController(
    private val registrations: TagRegistrationService,
    private val users: UserService,
    private val identity: FirebaseIdentity,
) {
    @GetMapping("/public/tags/{publicId}")
    @Operation(operationId = "TagRegistrationController_resolve")
    fun resolve(
        @PathVariable publicId: String,
        request: HttpServletRequest,
    ) = registrations.resolve(publicId, request.remoteAddr)

    @PostMapping("/public/tags/{publicId}/registration-drafts")
    @Operation(operationId = "TagRegistrationController_start")
    fun start(
        @PathVariable publicId: String,
        @Valid @RequestBody body: StartTagRegistrationRequest,
        request: HttpServletRequest,
    ) = registrations.start(publicId, body.email, request.remoteAddr)

    @GetMapping("/tag-registrations/{flowId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(operationId = "TagRegistrationController_view")
    fun view(
        @PathVariable flowId: String,
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
    ) = registrations.view(flowId, users.currentUser(jwt), identity.verifiedEmail(jwt))

    @PostMapping("/tag-registrations/{flowId}/complete")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(operationId = "TagRegistrationController_complete")
    fun complete(
        @PathVariable flowId: String,
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
    ) = registrations.complete(flowId, users.currentUser(jwt), identity.verifiedEmail(jwt))

    @PostMapping("/tags/{publicId}/register")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(operationId = "TagRegistrationController_direct")
    fun direct(
        @PathVariable publicId: String,
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
    ): TagResponse {
        val user = users.currentUser(jwt)
        identity.verifiedEmail(jwt)
        return registrations.direct(publicId, user)
    }
}
