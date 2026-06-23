package com.openai.codexmobile.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

fun prepareUserAvatarImage(
    context: Context,
    uri: Uri,
): String {
    val contentResolver = context.contentResolver
    val rawBytes = contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
        ?: throw IllegalArgumentException("无法读取选中的头像图片。")
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) {
        throw IllegalArgumentException("选中的文件不是可用图片。")
    }

    val extension = resolveAvatarExtension(contentResolver.getType(uri))
    val avatarDir = File(context.filesDir, "avatars").apply { mkdirs() }
    avatarDir.listFiles { file -> file.name.startsWith(USER_AVATAR_FILE_PREFIX) }
        ?.forEach { file -> file.delete() }
    val avatarFile = File(avatarDir, "$USER_AVATAR_FILE_PREFIX.$extension")
    avatarFile.outputStream().use { output -> output.write(rawBytes) }
    return avatarFile.absolutePath
}

fun loadUserAvatarBitmap(path: String): ImageBitmap? {
    if (path.isBlank()) {
        return null
    }
    val file = File(path)
    if (!file.isFile || file.length() <= 0L) {
        return null
    }
    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        return null
    }
    val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, TARGET_AVATAR_BITMAP_SIZE)
    val bitmap = BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        },
    ) ?: return null
    return bitmap.asImageBitmap()
}

private fun resolveAvatarExtension(mimeType: String?): String {
    val normalized = mimeType?.lowercase().orEmpty()
    return MimeTypeMap.getSingleton()
        .getExtensionFromMimeType(normalized)
        ?.takeIf { it.isNotBlank() }
        ?: when (normalized) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "png"
        }
}

private fun calculateInSampleSize(width: Int, height: Int, targetSize: Int): Int {
    var sampleSize = 1
    var sampledWidth = width
    var sampledHeight = height
    while (sampledWidth / 2 >= targetSize && sampledHeight / 2 >= targetSize) {
        sampleSize *= 2
        sampledWidth /= 2
        sampledHeight /= 2
    }
    return sampleSize
}

private const val USER_AVATAR_FILE_PREFIX = "user-avatar"
private const val TARGET_AVATAR_BITMAP_SIZE = 256
