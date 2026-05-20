package com.openai.codexmobile.ui.screen

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

private val transcriptFileHttpClient = OkHttpClient()
private val windowsDrivePathRegex = Regex("^[A-Za-z]:[\\\\/].+")
private val uncPathRegex = Regex("^\\\\\\\\[^\\\\]+\\\\[^\\\\].+")

internal data class TranscriptFileDownloadRequest(
    val url: String,
    val displayName: String,
)

internal enum class TranscriptFileDownloadStage {
    Preparing,
    Downloading,
    Saving,
}

internal data class TranscriptFileDownloadProgress(
    val displayName: String,
    val stage: TranscriptFileDownloadStage,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long? = null,
)

private data class DownloadedTranscriptFile(
    val bytes: ByteArray,
    val mimeType: String,
    val fileName: String,
)

internal fun resolveTranscriptFileDownloadRequest(
    source: String,
    bridgeEndpoint: String,
): TranscriptFileDownloadRequest? {
    val trimmed = source.trim()
    if (trimmed.isBlank()) {
        return null
    }

    val localPath = resolveTranscriptLocalFilePath(trimmed)
    if (localPath != null) {
        require(bridgeEndpoint.isNotBlank()) { "桥接地址为空，无法下载本地文件。" }
        return TranscriptFileDownloadRequest(
            url = buildTranscriptFileDownloadUrl(localPath, bridgeEndpoint),
            displayName = File(localPath).name.ifBlank { "codex-file" },
        )
    }

    if (trimmed.startsWith("/api/file/download", ignoreCase = false)) {
        require(bridgeEndpoint.isNotBlank()) { "桥接地址为空，无法下载本地文件。" }
        val pathValue = trimmed.substringAfter("path=", "").substringBefore('&')
        val displayName = pathValue
            .takeIf { it.isNotBlank() }
            ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
            ?.let { File(it).name }
            ?.ifBlank { "codex-file" }
            ?: "codex-file"
        return TranscriptFileDownloadRequest(
            url = resolveTranscriptImageUrl(trimmed, bridgeEndpoint),
            displayName = displayName,
        )
    }

    if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
        val marker = "/api/file/download?path="
        val markerIndex = trimmed.indexOf(marker, ignoreCase = true)
        if (markerIndex >= 0) {
            val pathValue = trimmed.substring(markerIndex + marker.length).substringBefore('&')
            val displayName = URLDecoder.decode(pathValue, StandardCharsets.UTF_8.name())
                .let { File(it).name }
                .ifBlank { "codex-file" }
            return TranscriptFileDownloadRequest(
                url = trimmed,
                displayName = displayName,
            )
        }
    }

    return null
}

internal suspend fun saveTranscriptFile(
    context: Context,
    request: TranscriptFileDownloadRequest,
    bridgeAuthToken: String,
    onProgress: (TranscriptFileDownloadProgress) -> Unit = {},
): String = withContext(Dispatchers.IO) {
    onProgress(
        TranscriptFileDownloadProgress(
            displayName = request.displayName,
            stage = TranscriptFileDownloadStage.Preparing,
        ),
    )
    val downloaded = downloadTranscriptFile(request, bridgeAuthToken, onProgress)
    onProgress(
        TranscriptFileDownloadProgress(
            displayName = downloaded.fileName,
            stage = TranscriptFileDownloadStage.Saving,
            bytesDownloaded = downloaded.bytes.size.toLong(),
            totalBytes = downloaded.bytes.size.toLong(),
        ),
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, downloaded.fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, downloaded.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/CodexMobile")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("无法创建文件保存位置。")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                output.write(downloaded.bytes)
            } ?: throw IllegalStateException("无法写入下载文件。")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
        return@withContext "文件已保存：${downloaded.fileName}"
    }

    val downloadsDir = File(
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
        "saved",
    ).apply { mkdirs() }
    val targetFile = uniqueTranscriptFile(downloadsDir, downloaded.fileName)
    targetFile.writeBytes(downloaded.bytes)
    return@withContext "文件已保存：${targetFile.absolutePath}"
}

private fun resolveTranscriptLocalFilePath(source: String): String? {
    return when {
        source.startsWith("bridge-file://", ignoreCase = true) -> {
            val encodedPath = source.substringAfter("://").trim()
            if (encodedPath.isBlank()) {
                null
            } else {
                URLDecoder.decode(encodedPath, StandardCharsets.UTF_8.name())
            }
        }

        source.startsWith("file://", ignoreCase = true) -> {
            val decoded = URLDecoder.decode(source.removePrefix("file://"), StandardCharsets.UTF_8.name())
            when {
                decoded.matches(windowsDrivePathRegex) -> decoded.replace('/', '\\')
                decoded.startsWith("/") && decoded.drop(1).matches(windowsDrivePathRegex) -> decoded.drop(1).replace('/', '\\')
                decoded.startsWith("//") -> decoded.replace('/', '\\')
                else -> decoded
            }
        }

        source.matches(windowsDrivePathRegex) || source.matches(uncPathRegex) -> source
        else -> null
    }
}

private fun buildTranscriptFileDownloadUrl(
    localPath: String,
    bridgeEndpoint: String,
): String {
    val queryPath = URLEncoder.encode(localPath, StandardCharsets.UTF_8.name())
    return "${bridgeEndpoint.trimEnd('/')}/api/file/download?path=$queryPath"
}

private fun downloadTranscriptFile(
    request: TranscriptFileDownloadRequest,
    bridgeAuthToken: String,
    onProgress: (TranscriptFileDownloadProgress) -> Unit,
): DownloadedTranscriptFile {
    val requestBuilder = Request.Builder().url(request.url)
    if (bridgeAuthToken.isNotBlank()) {
        requestBuilder.header("Authorization", "Bearer $bridgeAuthToken")
    }
    transcriptFileHttpClient.newCall(requestBuilder.build()).execute().use { response ->
        require(response.isSuccessful) { "文件请求失败：HTTP ${response.code}" }
        val responseBody = response.body ?: throw IllegalStateException("文件内容为空。")
        val mimeType = response.body?.contentType()?.toString()?.substringBefore(';') ?: "application/octet-stream"
        val fileName = response.header("Content-Disposition")
            ?.let(::parseDispositionFileName)
            ?.ifBlank { null }
            ?: ensureTranscriptFileName(request.displayName, mimeType)
        val totalBytes = responseBody.contentLength().takeIf { it >= 0 }
        val bytes = responseBody.byteStream().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var downloadedBytes = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) {
                    break
                }
                output.write(buffer, 0, read)
                downloadedBytes += read
                onProgress(
                    TranscriptFileDownloadProgress(
                        displayName = fileName,
                        stage = TranscriptFileDownloadStage.Downloading,
                        bytesDownloaded = downloadedBytes,
                        totalBytes = totalBytes,
                    ),
                )
            }
            output.toByteArray()
        }
        return DownloadedTranscriptFile(
            bytes = bytes,
            mimeType = mimeType,
            fileName = ensureTranscriptFileName(fileName, mimeType),
        )
    }
}

private fun parseDispositionFileName(header: String): String? {
    val utf8Marker = "filename*=UTF-8''"
    val utf8Index = header.indexOf(utf8Marker, ignoreCase = true)
    if (utf8Index >= 0) {
        return URLDecoder.decode(
            header.substring(utf8Index + utf8Marker.length).substringBefore(';').trim(),
            StandardCharsets.UTF_8.name(),
        )
    }

    val plainName = Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE)
        .find(header)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
    return plainName?.takeIf { it.isNotBlank() }
}

private fun ensureTranscriptFileName(
    displayName: String,
    mimeType: String,
): String {
    val trimmed = displayName.trim().ifBlank { "codex-file" }
    val safeName = trimmed.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    val currentExtension = safeName.substringAfterLast('.', "")
    if (currentExtension.isNotBlank()) {
        return safeName
    }
    val extension = MimeTypeMap.getSingleton()
        .getExtensionFromMimeType(mimeType.lowercase(Locale.US))
        ?.takeIf { it.isNotBlank() }
        ?: "bin"
    return "$safeName.$extension"
}

private fun uniqueTranscriptFile(
    directory: File,
    fileName: String,
): File {
    val candidate = File(directory, fileName)
    if (!candidate.exists()) {
        return candidate
    }

    val name = fileName.substringBeforeLast('.', fileName)
    val extension = fileName.substringAfterLast('.', "")
    var index = 1
    while (true) {
        val nextName = if (extension.isBlank()) {
            "$name-$index"
        } else {
            "$name-$index.$extension"
        }
        val nextFile = File(directory, nextName)
        if (!nextFile.exists()) {
            return nextFile
        }
        index += 1
    }
}

internal fun formatTranscriptFileByteCount(byteCount: Long): String {
    if (byteCount < 1024) {
        return "${byteCount} B"
    }

    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = byteCount.toDouble()
    var unitIndex = -1
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex += 1
    }

    val formatted = if (value >= 100 || value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }
    return "$formatted ${units[unitIndex.coerceAtLeast(0)]}"
}

internal fun calculateTranscriptFileDownloadFraction(progress: TranscriptFileDownloadProgress): Float? {
    val totalBytes = progress.totalBytes ?: return null
    if (totalBytes <= 0L) {
        return null
    }
    return (progress.bytesDownloaded.toDouble() / totalBytes.toDouble())
        .coerceIn(0.0, 1.0)
        .toFloat()
}
