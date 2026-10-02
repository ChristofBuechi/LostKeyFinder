package ch.lostkeyfinder.version

import ch.lostkeyfinder.config.ApiProperties
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@Schema(name = "VersionResponseDto")
data class VersionResponseDto(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val version: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val environment: String,
)

@RestController
@Tag(name = "version")
class VersionController(
    private val properties: ApiProperties,
) {
    @GetMapping("/api/v1/version", produces = ["application/json"])
    @Operation(operationId = "VersionController_version", summary = "Return the running API version")
    fun version() = VersionResponseDto(properties.version, properties.environment)
}
