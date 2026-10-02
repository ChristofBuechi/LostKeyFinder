package ch.lostkeyfinder.health

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint
import org.springframework.boot.health.contributor.Status
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@Schema(name = "HealthResponseDto")
data class HealthResponseDto(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = ["ok"])
    val status: String = "ok",
)

@RestController
@RequestMapping("/api/v1/health", produces = ["application/json"])
@Tag(name = "health")
class HealthController(
    private val health: HealthEndpoint,
) {
    @GetMapping("/live")
    @Operation(operationId = "HealthController_live", summary = "Check whether the API process is alive")
    fun live() = HealthResponseDto()

    @GetMapping("/ready")
    @Operation(operationId = "HealthController_ready", summary = "Check whether the API is ready for traffic")
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "503", description = "A required dependency is unavailable")
    fun ready(): HealthResponseDto {
        if (health.healthForPath("readiness")?.status != Status.UP) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)
        }
        return HealthResponseDto()
    }
}
