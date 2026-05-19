package com.openai.codexmobile.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.openai.codexmobile.data.UploadImageAttachmentRequest
import java.io.ByteArrayOutputStream

private const val MaxImageDimension = 1600
private const val JpegQuality = 88

fun prepareImageAttachmentForBridge(
    context: Context,
    uri: Uri,
): UploadImageAttachmentRequest {
    val contentResolver = context.contentResolver
    val displayName = resolveDisplayName(context, uri)
    val rawBytes = contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
        ?: throw IllegalArgumentException("无法读取选中的图片。")
    val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
        ?: throw IllegalArgumentException("选中的文件不是可用图片。")
    val scaled = scaleBitmapIfNeeded(bitmap)
    val output = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, JpegQuality, output)
    if (scaled !== bitmap) {
        scaled.recycle()
    }
    bitmap.recycle()
    return UploadImageAttachmentRequest(
        displayName = normalizeDisplayName(displayName),
        mimeType = "image/jpeg",
        contentBase64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP),
    )
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
        return "image.jpg"
    }
    return if (trimmed.substringAfterLast('.', "").isBlank()) {
        "$trimmed.jpg"
    } else {
        trimmed.substringBeforeLast('.') + ".jpg"
    }
}

private fun scaleBitmapIfNeeded(bitmap: Bitmap): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val longestEdge = maxOf(width, height)
    if (longestEdge <= MaxImageDimension) {
        return bitmap
    }
    val scale = MaxImageDimension.toFloat() / longestEdge.toFloat()
    val targetWidth = maxOf(1, (width * scale).toInt())
    val targetHeight = maxOf(1, (height * scale).toInt())
    return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
}
