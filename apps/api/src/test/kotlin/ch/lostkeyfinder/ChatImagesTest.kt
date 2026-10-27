package ch.lostkeyfinder

import ch.lostkeyfinder.chat.ChatImages
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.server.ResponseStatusException
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChatImagesTest {
    private fun image() =
        ByteArrayOutputStream()
            .also {
                ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", it)
            }.toByteArray()

    @Test
    @Timeout(10)
    fun `a third image waits for a processing slot instead of being rejected`() {
        val images = ChatImages()
        val entered = CountDownLatch(2)
        val release = CountDownLatch(1)
        val bytes = image()
        val blocked =
            object : MockMultipartFile("image", bytes) {
                override fun getBytes(): ByteArray {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    return super.getBytes()
                }
            }
        Executors.newFixedThreadPool(3).use { pool ->
            try {
                val first = pool.submit<ByteArray?> { images.sanitize(blocked) }
                val second = pool.submit<ByteArray?> { images.sanitize(blocked) }
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                val queued = pool.submit<ByteArray?> { images.sanitize(MockMultipartFile("image", bytes)) }
                assertFailsWith<TimeoutException> { queued.get(100, TimeUnit.MILLISECONDS) }
                release.countDown()
                for (result in listOf(first, second, queued)) assertNotNull(result.get(5, TimeUnit.SECONDS))
            } finally {
                release.countDown()
            }
        }
    }

    @Test
    fun `interrupted waiting restores the interrupt flag without consuming a slot`() {
        val images = ChatImages()
        val file = MockMultipartFile("image", image())
        try {
            Thread.currentThread().interrupt()
            assertEquals(503, assertFailsWith<ResponseStatusException> { images.sanitize(file) }.statusCode.value())
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
        assertNotNull(images.sanitize(file))
    }
}
