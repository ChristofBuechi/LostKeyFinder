package ch.lostkeyfinder.chat

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.Semaphore
import javax.imageio.ImageIO

@Component
class ChatImages {
    // ponytail: wait in the HTTP request; no separate worker or persistent queue.
    private val slots = Semaphore(2, true)

    fun sanitize(file: MultipartFile?): ByteArray? {
        if (file == null || file.isEmpty) return null
        if (file.size > 5_000_000) throw ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Image too large")
        try {
            slots.acquire()
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Image processing interrupted")
        }
        try {
            ImageIO.createImageInputStream(ByteArrayInputStream(file.bytes)).use { input ->
                val readers = ImageIO.getImageReaders(input)
                if (!readers.hasNext()) invalid()
                val reader = readers.next()
                try {
                    if (reader.formatName.lowercase() !in listOf("jpeg", "jpg", "png")) invalid()
                    reader.input = input
                    if (reader.getWidth(0) !in 1..2048 || reader.getHeight(0) !in 1..2048) invalid()
                    val image = reader.read(0)
                    val clean = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
                    val graphics = clean.createGraphics()
                    try {
                        graphics.color = java.awt.Color.WHITE
                        graphics.fillRect(0, 0, clean.width, clean.height)
                        graphics.drawImage(image, 0, 0, null)
                    } finally {
                        graphics.dispose()
                        image.flush()
                    }
                    val output = ByteArrayOutputStream()
                    try {
                        if (!ImageIO.write(clean, "jpeg", output)) invalid()
                    } finally {
                        clean.flush()
                    }
                    return output.toByteArray().also { if (it.size > 5_000_000) invalid() }
                } finally {
                    reader.dispose()
                }
            }
        } catch (ex: ResponseStatusException) {
            throw ex
        } catch (ex: Exception) {
            invalid()
        } finally {
            slots.release()
        }
    }

    private fun invalid(): Nothing =
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid image; use JPEG or PNG up to 2048 x 2048")
}
