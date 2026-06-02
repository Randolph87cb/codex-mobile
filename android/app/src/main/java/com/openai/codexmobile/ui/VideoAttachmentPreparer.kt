package com.openai.codexmobile.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.openai.codexmobile.data.UploadVideoAttachmentRequest
import java.io.File
import java.util.UUID

fun prepareVideoAttachmentForBridge(
    context: Context,
    uri: Uri,
): UploadVideoAttachmentRequest {
    val contentResolver = context.contentResolver
    val displayName = normalizeVideoDisplayName(resolveVideoDisplayName(context, uri))
    val mimeType = contentResolver.getType(uri)
        ?.takeIf { it.startsWith("video/") }
        ?: inferVideoMimeType(displayName)
    require(mimeType.startsWith("video/")) { "选中的文件不是可用视频。" }

    val cacheDir = File(context.cacheDir, "video-uploads").apply { mkdirs() }
    val cacheFile = File(cacheDir, buildCachedVideoFileName(displayName))
    val sourceByteLength = contentResolver.openInputStream(uri)?.use { input ->
        cacheFile.outputStream().use { output ->
            input.copyTo(output)
        }
        cacheFile.length()
    } ?: throw IllegalArgumentException("无法读取选中的视频。")

    if (sourceByteLength <= 0L) {
        cacheFile.delete()
        throw IllegalArgumentException("视频内容为空，无法附加。")
    }

    return UploadVideoAttachmentRequest(
        displayName = displayName,
        mimeType = mimeType.lowercase(),
        contentFilePath = cacheFile.absolutePath,
        sourceByteLength = sourceByteLength,
    )
}

private fun resolveVideoDisplayName(
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

private fun normalizeVideoDisplayName(displayName: String): String {
    val trimmed = displayName.trim()
    if (trimmed.isBlank()) {
        return "video.mp4"
    }
    return trimmed
}

private fun inferVideoMimeType(displayName: String): String {
    val extension = displayName.substringAfterLast('.', "").lowercase()
    if (extension.isBlank()) {
        return "video/mp4"
    }
    return MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(extension)
        ?.takeIf { it.startsWith("video/") }
        ?: "video/mp4"
}

private fun buildCachedVideoFileName(displayName: String): String {
    val extension = displayName.substringAfterLast('.', "").takeIf { it.isNotBlank() } ?: "mp4"
    return "${UUID.randomUUID()}.$extension"
}
