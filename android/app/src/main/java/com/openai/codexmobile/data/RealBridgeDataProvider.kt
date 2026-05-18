package com.openai.codexmobile.data

import com.openai.codexmobile.model.BridgeConnectionState
import com.openai.codexmobile.model.SessionDetail
import com.openai.codexmobile.model.SessionSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class RealBridgeDataProvider : CodexDataProvider {
    private var baseUrl: String? = null
    private var connectionState: BridgeConnectionState = BridgeConnectionState.Disconnected

    override suspend fun connect(endpoint: String): BridgeConnectionState = withContext(Dispatchers.IO) {
        val normalizedEndpoint = normalizeEndpoint(endpoint)
        val response = request(
            method = "GET",
            url = "$normalizedEndpoint/health",
        )

        if (response.statusCode !in 200..299) {
            throw BridgeRequestException(response.statusCode, response.body)
        }

        val body = JSONObject(response.body)
        val nextState = BridgeConnectionState.Connected(
            endpoint = normalizedEndpoint,
            service = body.optString("service").takeIf { it.isNotBlank() },
            runnerMode = body.optString("runnerMode").takeIf { it.isNotBlank() },
        )
        baseUrl = normalizedEndpoint
        connectionState = nextState
        nextState
    }

    override suspend fun disconnect() {
        baseUrl = null
        connectionState = BridgeConnectionState.Disconnected
    }

    override suspend fun currentConnection(): BridgeConnectionState = connectionState

    override suspend fun createSession(request: CreateSessionRequest): SessionDetail = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("cwd", request.cwd)
            .put("model", request.model)
            .put("approvalMode", request.approvalMode)

        val response = request(
            method = "POST",
            url = "${requireBaseUrl()}/api/session",
            body = payload.toString(),
        )
        if (response.statusCode !in 200..299) {
            throw BridgeRequestException(response.statusCode, response.body)
        }

        response.body.toSessionDetail()
    }

    override suspend fun sendInput(sessionId: String, text: String) = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("text", text)
        val response = request(
            method = "POST",
            url = "${requireBaseUrl()}/api/session/$sessionId/input",
            body = payload.toString(),
        )
        if (response.statusCode !in 200..299) {
            throw BridgeRequestException(response.statusCode, response.body)
        }
    }

    override suspend fun listSessions(): List<SessionSummary> = withContext(Dispatchers.IO) {
        val response = request(
            method = "GET",
            url = "${requireBaseUrl()}/api/sessions",
        )
        if (response.statusCode !in 200..299) {
            throw BridgeRequestException(response.statusCode, response.body)
        }

        val items = JSONObject(response.body).optJSONArray("items") ?: JSONArray()
        List(items.length()) { index ->
            items.getJSONObject(index).toSessionSummary()
        }
    }

    override suspend fun getSessionDetail(sessionId: String): SessionDetail? = withContext(Dispatchers.IO) {
        val response = request(
            method = "GET",
            url = "${requireBaseUrl()}/api/session/$sessionId",
        )
        when (response.statusCode) {
            404 -> null
            in 200..299 -> response.body.toSessionDetail()
            else -> throw BridgeRequestException(response.statusCode, response.body)
        }
    }

    private fun requireBaseUrl(): String {
        return baseUrl ?: throw IllegalStateException("桥接地址尚未连接。")
    }

    private fun normalizeEndpoint(endpoint: String): String {
        val normalized = endpoint.trim().trimEnd('/')
        require(normalized.isNotEmpty()) { "桥接地址不能为空。" }
        return normalized
    }

    private fun request(
        method: String,
        url: String,
        body: String? = null,
    ): HttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 5_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        return try {
            if (body != null) {
                OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(body)
                }
            }

            val statusCode = connection.responseCode
            val payload = readBody(connection.errorStream ?: connection.inputStream)
            HttpResponse(statusCode = statusCode, body = payload)
        } finally {
            connection.disconnect()
        }
    }

    private fun readBody(stream: InputStream?): String {
        if (stream == null) {
            return ""
        }

        return BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
            reader.readText()
        }
    }
}

private data class HttpResponse(
    val statusCode: Int,
    val body: String,
)

private class BridgeRequestException(
    statusCode: Int,
    body: String,
) : IllegalStateException("桥接请求失败，HTTP $statusCode：${body.ifBlank { "<empty>" }}")

private fun String.toSessionSummary(): SessionSummary {
    return JSONObject(this).toSessionSummary()
}

private fun JSONObject.toSessionSummary(): SessionSummary {
    val id = getString("id")
    val title = optString("title").ifBlank { id }
    val model = optString("model").ifBlank { "未知模型" }
    val status = optString("status").ifBlank { "unknown" }
    val cwd = optString("cwd").ifBlank { "未提供工作目录" }
    val updatedAt = optString("updatedAt").ifBlank { optString("createdAt") }

    return SessionSummary(
        id = id,
        title = title,
        subtitle = "$model • ${localizedStatus(status)} • $cwd",
        lastUpdated = updatedAt.ifBlank { "未知更新时间" },
        status = status,
    )
}

private fun String.toSessionDetail(): SessionDetail {
    return JSONObject(this).toSessionDetail()
}

private fun JSONObject.toSessionDetail(): SessionDetail {
    val id = getString("id")
    val title = optString("title").ifBlank { id }
    val model = optString("model").ifBlank { "未知模型" }
    val approvalMode = optString("approvalMode").ifBlank { "未知审批模式" }
    val status = optString("status").ifBlank { "unknown" }
    val cwd = optString("cwd").ifBlank { "未提供工作目录" }
    val threadId = optString("threadId").ifBlank { "尚未分配" }
    val activeTurnId = optString("activeTurnId").ifBlank { "空闲" }
    val lastError = optString("lastError").ifBlank { "无" }
    val updatedAt = optString("updatedAt").ifBlank { optString("createdAt") }
    val transcriptPreview = optString("transcriptPreview")

    return SessionDetail(
        id = id,
        title = title,
        subtitle = "$model • ${localizedApprovalMode(approvalMode)} • ${localizedStatus(status)}",
        lastUpdated = updatedAt.ifBlank { "未知更新时间" },
        transcriptPreview = transcriptPreview.ifBlank {
            buildString {
                appendLine("工作目录：$cwd")
                appendLine("线程 ID：$threadId")
                appendLine("当前轮次：$activeTurnId")
                append("最近错误：$lastError")
            }
        },
        status = status,
    )
}

private fun localizedStatus(status: String): String {
    return when (status) {
        "running" -> "进行中"
        "awaiting_approval" -> "等待批准"
        "error" -> "出错"
        else -> "空闲"
    }
}

private fun localizedApprovalMode(mode: String): String {
    return when (mode) {
        "auto" -> "自动执行"
        "manual" -> "手动批准"
        else -> mode
    }
}
