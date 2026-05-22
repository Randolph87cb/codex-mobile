package com.openai.codexmobile.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.openai.codexmobile.data.UploadImageAttachmentRequest

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
    return UploadImageAttachmentRequest(
        displayName = displayName,
        mimeType = mimeType.lowercase(),
        contentBytes = rawBytes,
        sourceByteLength = rawBytes.size,
        imageWidth = options.outWidth,
        imageHeight = options.outHeight,
        preparationMode = "original",
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
        return "image"
    }
    return trimmed
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
