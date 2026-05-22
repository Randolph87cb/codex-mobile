package com.openai.codexmobile.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ImageAttachmentPreparerTest {
    @Test
    fun oversizedOpaquePngKeepsOriginalBytesAndMetadata() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceFile = File(context.cacheDir, "oversized-photo.png")
        val rawBytes = createBitmapFile(
            file = sourceFile,
            width = 3000,
            height = 1800,
            format = Bitmap.CompressFormat.PNG,
        )

        val request = prepareImageAttachmentForBridge(context, Uri.fromFile(sourceFile))

        assertEquals("image/png", request.mimeType)
        assertEquals("oversized-photo.png", request.displayName)
        assertEquals(3000, request.imageWidth)
        assertEquals(1800, request.imageHeight)
        assertEquals(rawBytes.size, request.sourceByteLength)
        assertEquals("original", request.preparationMode)
        assertArrayEquals(rawBytes, request.contentBytes)
    }

    @Test
    fun smallImageKeepsOriginalBytes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceFile = File(context.cacheDir, "small-image.png")
        val rawBytes = createBitmapFile(
            file = sourceFile,
            width = 320,
            height = 240,
            format = Bitmap.CompressFormat.PNG,
        )

        val request = prepareImageAttachmentForBridge(context, Uri.fromFile(sourceFile))

        assertEquals("image/png", request.mimeType)
        assertEquals("small-image.png", request.displayName)
        assertEquals(320, request.imageWidth)
        assertEquals(240, request.imageHeight)
        assertEquals(rawBytes.size, request.sourceByteLength)
        assertEquals("original", request.preparationMode)
        assertArrayEquals(rawBytes, request.contentBytes)
    }

    private fun createBitmapFile(
        file: File,
        width: Int,
        height: Int,
        format: Bitmap.CompressFormat,
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val pixels = IntArray(width * height)
            var index = 0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val red = (x * 255 / maxOf(1, width - 1))
                    val green = (y * 255 / maxOf(1, height - 1))
                    val blue = ((x + y) * 255 / maxOf(1, width + height - 2))
                    pixels[index] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
                    index += 1
                }
            }
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            file.outputStream().use { output ->
                bitmap.compress(format, 100, output)
            }
            return file.readBytes()
        } finally {
            bitmap.recycle()
        }
    }
}
