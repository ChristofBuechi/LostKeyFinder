package ch.lostkeyfinder.tags

import ch.lostkeyfinder.users.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class CreateTagBatchRequest(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) @field:Min(1) @field:Max(100) val count: Int,
    @field:Size(max = 80) val label: String = "",
)

@RestController
@RequestMapping("/api/v1/admin/tag-batches", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "tag-batches")
@SecurityRequirement(name = "bearerAuth")
class TagBatchController(
    private val users: UserService,
    private val batches: TagBatchService,
) {
    @PostMapping
    @Operation(operationId = "TagBatchController_create")
    fun create(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @RequestHeader("Idempotency-Key") key: String,
        @Valid @RequestBody body: CreateTagBatchRequest,
    ) = batches.create(users.requireAdmin(jwt), key, body.count, body.label)

    @GetMapping
    @Operation(operationId = "TagBatchController_list")
    fun list(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(required = false) cursor: String?,
    ): TagBatchPage {
        users.requireAdmin(jwt)
        return batches.list(cursor)
    }

    @GetMapping("/{batchId}")
    @Operation(operationId = "TagBatchController_detail")
    fun detail(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @PathVariable batchId: String,
    ): TagBatchResponse {
        users.requireAdmin(jwt)
        return batches.detail(batchId)
    }

    @PostMapping("/{batchId}/release")
    @Operation(operationId = "TagBatchController_release")
    fun release(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @PathVariable batchId: String,
    ) = batches.release(users.requireAdmin(jwt), batchId)

    @GetMapping("/{batchId}/export", produces = ["text/csv"])
    @Operation(operationId = "TagBatchController_export")
    fun export(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @PathVariable batchId: String,
    ): ResponseEntity<String> {
        val csv = batches.export(users.requireAdmin(jwt), batchId)
        return ResponseEntity
            .ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=tags-${batchId.lowercase()}.csv")
            .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
            .body(csv)
    }
}
