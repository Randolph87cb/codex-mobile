package com.openai.codexmobile.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ImageAttachmentPreparerTest {
    @Test
    fun oversizedOpaquePngIsResizedForUpload() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceFile = File(context.cacheDir, "oversized-photo.png")
        val rawBytes = createBitmapFile(
            file = sourceFile,
            width = 3000,
            height = 1800,
            format = Bitmap.CompressFormat.PNG,
            transparentPixel = false,
        )

        val request = prepareImageAttachmentForBridge(context, Uri.fromFile(sourceFile))

        assertEquals("image/jpeg", request.mimeType)
        assertTrue(request.displayName.endsWith(".jpg"))
        assertTrue(request.contentBytes.size <= PreparedImageTargetBytes)
        assertTrue(request.contentBytes.size < rawBytes.size)
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
            transparentPixel = false,
        )

        val request = prepareImageAttachmentForBridge(context, Uri.fromFile(sourceFile))

        assertEquals("image/png", request.mimeType)
        assertEquals("small-image.png", request.displayName)
        assertArrayEquals(rawBytes, request.contentBytes)
    }

    @Test
    fun oversizedTransparentPngKeepsPngMimeType() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceFile = File(context.cacheDir, "oversized-transparent.png")
        val rawBytes = createBitmapFile(
            file = sourceFile,
            width = 2800,
            height = 1800,
            format = Bitmap.CompressFormat.PNG,
            transparentPixel = true,
        )

        val request = prepareImageAttachmentForBridge(context, Uri.fromFile(sourceFile))

        assertEquals("image/png", request.mimeType)
        assertTrue(request.displayName.endsWith(".png"))
        assertTrue(request.contentBytes.size <= rawBytes.size)
    }

    private fun createBitmapFile(
        file: File,
        width: Int,
        height: Int,
        format: Bitmap.CompressFormat,
        transparentPixel: Boolean,
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
                    val alpha = if (transparentPixel && x < width / 6 && y < height / 6) 0x00 else 0xFF
                    pixels[index] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
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
