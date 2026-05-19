package com.openai.codexmobile.ui.screen

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.MimeTypeMap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private val transcriptImageHttpClient = OkHttpClient()
private val transcriptImageCache = ConcurrentHashMap<String, LoadedTranscriptImage>()

internal data class LoadedTranscriptImage(
    val bitmap: ImageBitmap,
    val bytes: ByteArray,
    val mimeType: String,
)

internal sealed interface TranscriptImageLoadState {
    data object Loading : TranscriptImageLoadState

    data class Success(
        val image: LoadedTranscriptImage,
    ) : TranscriptImageLoadState

    data class Error(
        val message: String,
    ) : TranscriptImageLoadState
}

@Composable
internal fun rememberTranscriptImageState(
    source: String,
    bridgeEndpoint: String,
    bridgeAuthToken: String,
): TranscriptImageLoadState {
    return produceState<TranscriptImageLoadState>(
        initialValue = TranscriptImageLoadState.Loading,
        key1 = source,
        key2 = bridgeEndpoint,
        key3 = bridgeAuthToken,
    ) {
        value = try {
            TranscriptImageLoadState.Success(
                loadTranscriptImage(
                    source = source,
                    bridgeEndpoint = bridgeEndpoint,
                    bridgeAuthToken = bridgeAuthToken,
                ),
            )
        } catch (error: Throwable) {
            TranscriptImageLoadState.Error(error.message ?: "图片加载失败。")
        }
    }.value
}

internal suspend fun saveTranscriptImage(
    context: Context,
    image: LoadedTranscriptImage,
    displayName: String,
): String = withContext(Dispatchers.IO) {
    val normalizedName = ensureImageFileName(displayName, image.mimeType)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, normalizedName)
            put(MediaStore.MediaColumns.MIME_TYPE, image.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/CodexMobile")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("无法创建图片保存位置。")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                output.write(image.bytes)
            } ?: throw IllegalStateException("无法写入图片文件。")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
        return@withContext "图片已保存：$normalizedName"
    }

    val picturesDir = File(
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
        "saved",
    ).apply { mkdirs() }
    val targetFile = uniqueFile(picturesDir, normalizedName)
    targetFile.writeBytes(image.bytes)
    return@withContext "图片已保存：${targetFile.absolutePath}"
}

private suspend fun loadTranscriptImage(
    source: String,
    bridgeEndpoint: String,
    bridgeAuthToken: String,
): LoadedTranscriptImage = withContext(Dispatchers.IO) {
    val cacheKey = buildString {
        append(bridgeEndpoint)
        append("|")
        append(source)
    }
    transcriptImageCache[cacheKey]?.let { return@withContext it }

    val loaded = when {
        source.startsWith("data:", ignoreCase = true) -> loadFromDataUrl(source)
        source.startsWith("bridge-attachment://", ignoreCase = true) -> {
            val attachmentId = source.removePrefix("bridge-attachment://").trim()
            require(attachmentId.isNotBlank()) { "缺少图片附件 ID。" }
            loadFromHttp(
                url = "${bridgeEndpoint.trimEnd('/')}/api/attachment/image/$attachmentId/content",
                bridgeAuthToken = bridgeAuthToken,
            )
        }

        source.startsWith("bridge-file://", ignoreCase = true) -> {
            val encodedPath = source.removePrefix("bridge-file://").trim()
            require(encodedPath.isNotBlank()) { "缺少图片路径。" }
            val decodedPath = URLDecoder.decode(encodedPath, StandardCharsets.UTF_8.name())
            val queryPath = URLEncoder.encode(decodedPath, StandardCharsets.UTF_8.name())
            loadFromHttp(
                url = "${bridgeEndpoint.trimEnd('/')}/api/image/file?path=$queryPath",
                bridgeAuthToken = bridgeAuthToken,
            )
        }

        source.startsWith("http://", ignoreCase = true) || source.startsWith("https://", ignoreCase = true) -> {
            loadFromHttp(
                url = source,
                bridgeAuthToken = bridgeAuthToken.takeIf { bridgeEndpoint.isNotBlank() && source.startsWith(bridgeEndpoint) }.orEmpty(),
            )
        }

        else -> throw IllegalArgumentException("暂不支持的图片来源。")
    }

    transcriptImageCache[cacheKey] = loaded
    loaded
}

private fun loadFromDataUrl(source: String): LoadedTranscriptImage {
    val separatorIndex = source.indexOf(',')
    require(separatorIndex > 5) { "无效的图片数据。"}
    val metadata = source.substring(5, separatorIndex)
    val payload = source.substring(separatorIndex + 1)
    val isBase64Payload = metadata.contains(";base64", ignoreCase = true)
    val mimeType = metadata.substringBefore(';').ifBlank { "image/png" }
    val bytes = if (isBase64Payload) {
        Base64.decode(payload, Base64.DEFAULT)
    } else {
        URLDecoder.decode(payload, StandardCharsets.UTF_8.name()).toByteArray(StandardCharsets.UTF_8)
    }
    return bytes.toLoadedTranscriptImage(mimeType)
}

private fun loadFromHttp(
    url: String,
    bridgeAuthToken: String,
): LoadedTranscriptImage {
    val requestBuilder = Request.Builder().url(url)
    if (bridgeAuthToken.isNotBlank()) {
        requestBuilder.header("Authorization", "Bearer $bridgeAuthToken")
    }
    transcriptImageHttpClient.newCall(requestBuilder.build()).execute().use { response ->
        require(response.isSuccessful) { "图片请求失败：HTTP ${response.code}" }
        val bytes = response.body?.bytes() ?: throw IllegalStateException("图片内容为空。")
        val mimeType = response.body?.contentType()?.toString()?.substringBefore(';') ?: "image/png"
        return bytes.toLoadedTranscriptImage(mimeType)
    }
}

private fun ByteArray.toLoadedTranscriptImage(mimeType: String): LoadedTranscriptImage {
    val bitmap = BitmapFactory.decodeByteArray(this, 0, size)
        ?: throw IllegalArgumentException("图片解码失败。")
    return LoadedTranscriptImage(
        bitmap = bitmap.asImageBitmap(),
        bytes = this,
        mimeType = mimeType,
    )
}

private fun ensureImageFileName(
    displayName: String,
    mimeType: String,
): String {
    val trimmed = displayName.trim().ifBlank { "codex-image" }
    val currentExtension = trimmed.substringAfterLast('.', "")
    if (currentExtension.isNotBlank()) {
        return trimmed
    }
    val extension = MimeTypeMap.getSingleton()
        .getExtensionFromMimeType(mimeType.lowercase(Locale.US))
        ?.takeIf { it.isNotBlank() }
        ?: "png"
    return "$trimmed.$extension"
}

private fun uniqueFile(
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
