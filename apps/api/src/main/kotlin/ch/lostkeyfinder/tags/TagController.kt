package ch.lostkeyfinder.tags

import ch.lostkeyfinder.users.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class CreateTagRequest(
    @field:Size(max = 80) val label: String = "",
)

data class ChangeTagStatusRequest(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val status: TagStatus,
)

@RestController
@RequestMapping("/api/v1/tags", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "tags")
@SecurityRequirement(name = "bearerAuth")
class TagController(
    private val users: UserService,
    private val tags: TagService,
) {
    @PostMapping
    @Operation(operationId = "TagController_create")
    fun create(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @RequestHeader("Idempotency-Key") key: String,
        @Valid @RequestBody request: CreateTagRequest,
    ) = tags.create(users.currentUser(jwt), key, request.label)

    @GetMapping
    @Operation(operationId = "TagController_list")
    fun list(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(required = false) cursor: String?,
    ) = tags.list(ownerId = users.currentUser(jwt).id, cursor = cursor)

    @GetMapping("/{tagId}")
    @Operation(operationId = "TagController_detail")
    fun detail(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @PathVariable tagId: String,
    ) = tags.owned(users.currentUser(jwt), tagId)

    @GetMapping("/by-public/{publicId}")
    @Operation(operationId = "TagController_ownedPublic")
    fun ownedPublic(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @PathVariable publicId: String,
    ) = tags.ownedPublic(users.currentUser(jwt), publicId)

    @PatchMapping("/{tagId}/status")
    @Operation(operationId = "TagController_changeStatus")
    fun changeStatus(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @PathVariable tagId: String,
        @Valid @RequestBody request: ChangeTagStatusRequest,
    ) = tags.changeStatus(users.currentUser(jwt), tagId, request.status)
}

@RestController
@RequestMapping("/api/v1/admin/tags", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "admin-tags")
@SecurityRequirement(name = "bearerAuth")
class AdminTagController(
    private val users: UserService,
    private val tags: TagService,
) {
    @GetMapping
    @Operation(operationId = "AdminTagController_list")
    fun list(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(required = false) cursor: String?,
        @ParameterObject @ModelAttribute filter: TagFilter,
    ): TagPage {
        users.requireAdmin(jwt)
        return tags.list(cursor = cursor, filter = filter)
    }

    @GetMapping("/summary")
    @Operation(operationId = "AdminTagController_summary")
    fun summary(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @ParameterObject @ModelAttribute filter: TagFilter,
    ): TagSummary {
        users.requireAdmin(jwt)
        return tags.summary(filter)
    }

    @GetMapping("/{tagId}")
    @Operation(operationId = "AdminTagController_detail")
    fun detail(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @PathVariable tagId: String,
    ): TagResponse {
        users.requireAdmin(jwt)
        return tags.adminDetail(tagId)
    }
}
