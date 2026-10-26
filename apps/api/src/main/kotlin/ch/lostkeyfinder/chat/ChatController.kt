package ch.lostkeyfinder.chat

import ch.lostkeyfinder.identity.FirebaseIdentity
import ch.lostkeyfinder.users.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.CacheControl
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
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@io.swagger.v3.oas.annotations.media.Schema(requiredProperties = ["through"])
data class ChatReadRequest(
    val through: Long,
)

@RestController
@RequestMapping("/api/v1", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "chat")
class ChatController(
    private val chats: ChatService,
    private val images: ChatImages,
    private val users: UserService,
    private val identity: FirebaseIdentity,
    private val limits: ch.lostkeyfinder.tags.PublicRequestLimits,
) {
    @PostMapping("/public/tags/{publicId}/finder-drafts", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(operationId = "ChatController_start", security = [])
    fun start(
        @PathVariable publicId: String,
        @RequestParam email: String,
        @RequestParam text: String,
        @RequestPart(required = false) image: MultipartFile?,
        @RequestHeader("Idempotency-Key") key: String,
        request: HttpServletRequest,
    ): FinderStarted {
        limits.reserve("finder-upload-peer", request.remoteAddr, 20)
        return chats.start(publicId, email, text, images.sanitize(image), key, request.remoteAddr)
    }

    @PostMapping("/finder-reports/{flowId}/complete")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(operationId = "ChatController_complete")
    fun complete(
        @PathVariable flowId: String,
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
    ) = chats.complete(flowId, users.currentUser(jwt), identity.verifiedEmail(jwt))

    @GetMapping("/chats")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(operationId = "ChatController_list")
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(required = false) tagId: String?,
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
    ) = chats.list(users.currentUser(jwt), page, tagId?.lowercase())

    @GetMapping("/chats/{chatId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(operationId = "ChatController_detail")
    fun detail(
        @PathVariable chatId: String,
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
    ) = chats.detail(chatId.lowercase(), users.currentUser(jwt))

    @GetMapping("/chats/{chatId}/messages")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(operationId = "ChatController_messages")
    fun messages(
        @PathVariable chatId: String,
        @RequestParam(required = false) before: Long?,
        @RequestParam(required = false) messageId: String?,
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
    ) = chats.messages(chatId.lowercase(), users.currentUser(jwt), before, messageId)

    @PostMapping("/chats/{chatId}/messages", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @SecurityRequirement(name = "bearerAuth")
    @Operation(operationId = "ChatController_send")
    fun send(
        @PathVariable chatId: String,
        @RequestParam(defaultValue = "") text: String,
        @RequestPart(required = false) image: MultipartFile?,
        @RequestHeader("Idempotency-Key") key: String,
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
    ): ChatMessage {
        val user = users.currentUser(jwt)
        chats.detail(chatId.lowercase(), user)
        return chats.send(chatId.lowercase(), user, text, images.sanitize(image), key)
    }

    @PostMapping("/chats/{chatId}/read")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(operationId = "ChatController_read")
    fun read(
        @PathVariable chatId: String,
        @RequestBody body: ChatReadRequest,
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
    ) = chats.read(chatId.lowercase(), users.currentUser(jwt), body.through)

    @PostMapping("/chats/{chatId}/close")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(operationId = "ChatController_close")
    fun close(
        @PathVariable chatId: String,
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
    ) = chats.close(chatId.lowercase(), users.currentUser(jwt))

    @GetMapping("/chat-images/{imageId}", produces = [MediaType.IMAGE_JPEG_VALUE])
    @SecurityRequirement(name = "bearerAuth")
    @Operation(operationId = "ChatController_image")
    fun image(
        @PathVariable imageId: String,
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ByteArray> =
        ResponseEntity
            .ok()
            .contentType(MediaType.IMAGE_JPEG)
            .cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options", "nosniff")
            .body(chats.image(imageId.lowercase(), users.currentUser(jwt)))
}
