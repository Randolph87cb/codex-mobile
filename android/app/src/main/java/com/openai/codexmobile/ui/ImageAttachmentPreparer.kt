package com.openai.codexmobile.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.openai.codexmobile.data.UploadImageAttachmentRequest
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

internal const val PreparedImageTargetBytes: Int = 12 * 1024 * 1024
internal const val PreparedImageMaxDimension: Int = 2560

fun prepareImageAttachmentForBridge(
    context: Context,
    uri: Uri,
): UploadImageAttachmentRequest {
    val contentResolver = context.contentResolver
    val displayName = normalizeDisplayName(resolveDisplayName(context, uri))
    val rawBytes = contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
        ?: throw IllegalArgumentException("无法读取选中的图片。")
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) {
        throw IllegalArgumentException("选中的文件不是可用图片。")
    }
    val mimeType = contentResolver.getType(uri)
        ?.takeIf { it.startsWith("image/") }
        ?: inferImageMimeType(displayName)
    val preparedImage = optimizeImageForUpload(
        rawBytes = rawBytes,
        mimeType = mimeType,
        displayName = displayName,
        width = options.outWidth,
        height = options.outHeight,
    )
    return UploadImageAttachmentRequest(
        displayName = preparedImage.displayName,
        mimeType = preparedImage.mimeType,
        contentBytes = preparedImage.contentBytes,
    )
}

private data class PreparedImage(
    val displayName: String,
    val mimeType: String,
    val contentBytes: ByteArray,
)

private fun optimizeImageForUpload(
    rawBytes: ByteArray,
    mimeType: String,
    displayName: String,
    width: Int,
    height: Int,
): PreparedImage {
    val normalizedMimeType = mimeType.lowercase()
    if (rawBytes.size <= PreparedImageTargetBytes && max(width, height) <= PreparedImageMaxDimension) {
        return PreparedImage(
            displayName = displayName,
            mimeType = normalizedMimeType,
            contentBytes = rawBytes,
        )
    }
    if (normalizedMimeType !in setOf("image/jpeg", "image/png", "image/webp", "image/bmp")) {
        return PreparedImage(
            displayName = displayName,
            mimeType = normalizedMimeType,
            contentBytes = rawBytes,
        )
    }

    val targetDimensions = buildTargetDimensions(width, height)
    var bestCandidate = PreparedImage(
        displayName = displayName,
        mimeType = normalizedMimeType,
        contentBytes = rawBytes,
    )

    targetDimensions.forEach { targetDimension ->
        val bitmap = decodeScaledBitmap(rawBytes, width, height, targetDimension) ?: return@forEach
        bitmap.use { decodedBitmap ->
            val hasAlpha = bitmapContainsTransparentPixels(decodedBitmap)
            if (hasAlpha) {
                val pngBytes = compressBitmap(decodedBitmap, Bitmap.CompressFormat.PNG, quality = 100)
                val pngCandidate = PreparedImage(
                    displayName = ensureFileExtension(displayName, ".png"),
                    mimeType = "image/png",
                    contentBytes = pngBytes,
                )
                if (pngBytes.size < bestCandidate.contentBytes.size) {
                    bestCandidate = pngCandidate
                }
                if (pngBytes.size <= PreparedImageTargetBytes) {
                    return pngCandidate
                }
            } else {
                for (quality in listOf(92, 84, 76, 68, 60)) {
                    val jpegBytes = compressBitmap(decodedBitmap, Bitmap.CompressFormat.JPEG, quality)
                    val jpegCandidate = PreparedImage(
                        displayName = ensureFileExtension(displayName, ".jpg"),
                        mimeType = "image/jpeg",
                        contentBytes = jpegBytes,
                    )
                    if (jpegBytes.size < bestCandidate.contentBytes.size) {
                        bestCandidate = jpegCandidate
                    }
                    if (jpegBytes.size <= PreparedImageTargetBytes) {
                        return jpegCandidate
                    }
                }
            }
        }
    }

    return bestCandidate
}

private fun bitmapContainsTransparentPixels(bitmap: Bitmap): Boolean {
    if (!bitmap.hasAlpha()) {
        return false
    }
    val width = bitmap.width
    val rowPixels = IntArray(width)
    for (y in 0 until bitmap.height) {
        bitmap.getPixels(rowPixels, 0, width, 0, y, width, 1)
        for (pixel in rowPixels) {
            if ((pixel ushr 24) != 0xFF) {
                return true
            }
        }
    }
    return false
}

private inline fun <T : Bitmap?, R> T.use(block: (T) -> R): R {
    return try {
        block(this)
    } finally {
        if (this != null && !isRecycled) {
            recycle()
        }
    }
}

private fun buildTargetDimensions(width: Int, height: Int): List<Int> {
    val longestSide = max(width, height)
    val baseTarget = minOf(longestSide, PreparedImageMaxDimension)
    return buildList {
        var current = baseTarget
        while (current >= 960) {
            add(current)
            current = (current * 0.8f).roundToInt()
        }
        if (isEmpty()) {
            add(baseTarget)
        }
    }.distinct()
}

private fun decodeScaledBitmap(
    rawBytes: ByteArray,
    width: Int,
    height: Int,
    targetLongestSide: Int,
): Bitmap? {
    val sampleSize = calculateSampleSize(width, height, targetLongestSide)
    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOptions)
}

private fun calculateSampleSize(
    width: Int,
    height: Int,
    targetLongestSide: Int,
): Int {
    var sampleSize = 1
    var currentWidth = width
    var currentHeight = height
    while (max(currentWidth, currentHeight) > targetLongestSide && sampleSize < 32) {
        sampleSize *= 2
        currentWidth = max(1, width / sampleSize)
        currentHeight = max(1, height / sampleSize)
    }
    return sampleSize
}

private fun compressBitmap(
    bitmap: Bitmap,
    format: Bitmap.CompressFormat,
    quality: Int,
): ByteArray {
    val output = ByteArrayOutputStream()
    bitmap.compress(format, quality, output)
    return output.toByteArray()
}

private fun resolveDisplayName(
    context: Context,
    uri: Uri,
): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (columnIndex >= 0) {
                return cursor.getString(columnIndex).orEmpty()
            }
        }
    }
    return uri.lastPathSegment.orEmpty()
}

private fun normalizeDisplayName(displayName: String): String {
    val trimmed = displayName.trim()
    if (trimmed.isBlank()) {
        return "image"
    }
    return trimmed
}

private fun ensureFileExtension(displayName: String, extension: String): String {
    val trimmed = normalizeDisplayName(displayName)
    val baseName = trimmed.substringBeforeLast('.', trimmed)
    return "$baseName$extension"
}

private fun inferImageMimeType(displayName: String): String {
    val extension = displayName.substringAfterLast('.', "").lowercase()
    if (extension.isBlank()) {
        return "image/png"
    }
    return MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(extension)
        ?.takeIf { it.startsWith("image/") }
        ?: "image/png"
}
