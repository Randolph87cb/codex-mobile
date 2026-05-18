package com.openai.codexmobile.data

import com.openai.codexmobile.model.BridgeConnectionState
import com.openai.codexmobile.model.SessionDetail
import com.openai.codexmobile.model.SessionSummary
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
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
    private var authToken: String? = null
    private var connectionState: BridgeConnectionState = BridgeConnectionState.Disconnected
    private val webSocketClient = OkHttpClient()

    override fun updateAuthToken(token: String) {
        authToken = token.trim().takeIf { it.isNotEmpty() }
    }

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

    override suspend fun approveSession(
        sessionId: String,
        requestId: BridgeRequestId?,
        decision: ApprovalDecision,
    ): ApprovalActionResult = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("decision", decision.wireValue)
        requestId?.let { payload.put("requestId", it.toJsonValue()) }

        val response = request(
            method = "POST",
            url = "${requireBaseUrl()}/api/session/$sessionId/approve",
            body = payload.toString(),
        )
        if (response.statusCode !in 200..299) {
            throw BridgeRequestException(response.statusCode, response.body)
        }

        val json = JSONObject(response.body)
        ApprovalActionResult(
            requestId = parseRequestId(json.opt("requestId"))
                ?: requestId
                ?: BridgeRequestId.Text("unknown-request"),
            decision = ApprovalDecision.fromWireValue(json.optString("decision"))
                ?: decision,
            status = json.optString("status").ifBlank { "unknown" },
            method = json.optString("method").takeIf { it.isNotBlank() },
        )
    }

    override fun observeSessionEvents(sessionId: String): Flow<SessionStreamEvent> = callbackFlow {
        val requestBuilder = Request.Builder()
            .url(toWebSocketUrl("${requireBaseUrl()}/api/session/$sessionId/ws"))
        authToken?.let { requestBuilder.header("Authorization", "Bearer $it") }
        val request = requestBuilder.build()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                trySend(SessionStreamEvent.StreamOpened(sessionId = sessionId))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseSessionStreamEvent(
                    sessionId = sessionId,
                    payload = text,
                )?.let { trySend(it) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                trySend(
                    SessionStreamEvent.StreamClosed(
                        sessionId = sessionId,
                        reason = reason.ifBlank { "实时流正在关闭。" },
                    ),
                )
                webSocket.close(normalizeCloseCode(code), normalizeCloseReason(reason))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                trySend(
                    SessionStreamEvent.StreamClosed(
                        sessionId = sessionId,
                        reason = reason.ifBlank { "实时流已关闭。" },
                    ),
                )
                this@callbackFlow.close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                trySend(
                    SessionStreamEvent.Error(
                        sessionId = sessionId,
                        message = t.message ?: "实时流连接失败。",
                        timestamp = null,
                    ),
                )
                this@callbackFlow.close(t)
            }
        }

        val webSocket = webSocketClient.newWebSocket(request, listener)
        awaitClose {
            webSocket.cancel()
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

    private fun toWebSocketUrl(httpUrl: String): String {
        return when {
            httpUrl.startsWith("https://") -> "wss://${httpUrl.removePrefix("https://")}"
            httpUrl.startsWith("http://") -> "ws://${httpUrl.removePrefix("http://")}"
            else -> throw IllegalArgumentException("桥接地址必须以 http:// 或 https:// 开头。")
        }
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
            authToken?.let { setRequestProperty("Authorization", "Bearer $it") }
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

private fun parseSessionStreamEvent(
    sessionId: String,
    payload: String,
): SessionStreamEvent? {
    val json = JSONObject(payload)
    if (!json.has("type")) {
        val message = json.optString("error").ifBlank { "收到无法识别的实时流消息。" }
        return SessionStreamEvent.Error(
            sessionId = sessionId,
            message = message,
            timestamp = null,
        )
    }

    val eventSessionId = json.optString("sessionId").ifBlank { sessionId }
    val timestamp = json.optString("timestamp").takeIf { it.isNotBlank() }
    val data = json.optJSONObject("data") ?: JSONObject()

    return when (json.getString("type")) {
        "session.started" -> SessionStreamEvent.SessionStarted(
            sessionId = eventSessionId,
            status = data.optString("status").ifBlank { "idle" },
            cwd = data.optString("cwd").takeIf { it.isNotBlank() },
            model = data.optString("model").takeIf { it.isNotBlank() },
            threadId = data.optString("threadId").takeIf { it.isNotBlank() },
            timestamp = timestamp,
        )

        "assistant.delta" -> SessionStreamEvent.AssistantDelta(
            sessionId = eventSessionId,
            text = data.optString("text"),
            turnId = data.optString("turnId").takeIf { it.isNotBlank() },
            timestamp = timestamp,
        )

        "assistant.done" -> SessionStreamEvent.AssistantDone(
            sessionId = eventSessionId,
            turnStatus = data.optString("status").takeIf { it.isNotBlank() },
            turnId = data.optString("turnId").takeIf { it.isNotBlank() },
            errorMessage = extractEventError(data.opt("error")),
            timestamp = timestamp,
        )

        "run.status" -> SessionStreamEvent.RunStatus(
            sessionId = eventSessionId,
            status = data.optString("status").ifBlank { "unknown" },
            timestamp = timestamp,
        )

        "run.interrupted" -> SessionStreamEvent.RunInterrupted(
            sessionId = eventSessionId,
            status = data.optString("status").takeIf { it.isNotBlank() },
            timestamp = timestamp,
        )

        "tool.request" -> SessionStreamEvent.ToolRequest(
            sessionId = eventSessionId,
            requestId = parseRequestId(data.opt("requestId")),
            method = data.optString("method").takeIf { it.isNotBlank() },
            paramsSummary = summarizeToolRequest(
                method = data.optString("method").takeIf { it.isNotBlank() },
                params = data.opt("params"),
            ),
            timestamp = timestamp,
        )

        "tool.result" -> SessionStreamEvent.ToolResult(
            sessionId = eventSessionId,
            requestId = parseRequestId(data.opt("requestId")),
            method = data.optString("method").takeIf { it.isNotBlank() },
            decision = ApprovalDecision.fromWireValue(data.optString("decision")),
            status = data.optString("status").takeIf { it.isNotBlank() },
            summary = summarizeToolResult(data),
            timestamp = timestamp,
        )

        "error" -> SessionStreamEvent.Error(
            sessionId = eventSessionId,
            message = extractEventError(data.opt("error")) ?: "bridge 返回错误事件。",
            timestamp = timestamp,
        )

        else -> null
    }
}

private fun parseRequestId(value: Any?): BridgeRequestId? {
    return when (value) {
        null -> null
        JSONObject.NULL -> null
        is Int -> BridgeRequestId.Number(value.toLong())
        is Long -> BridgeRequestId.Number(value)
        is Number -> BridgeRequestId.Number(value.toLong())
        is String -> value.takeIf { it.isNotBlank() }?.let(BridgeRequestId::Text)
        else -> value.toString().takeIf { it.isNotBlank() }?.let(BridgeRequestId::Text)
    }
}

private fun summarizeToolRequest(
    method: String?,
    params: Any?,
): String {
    val methodText = method ?: "未知方法"
    val paramsText = when (params) {
        null, JSONObject.NULL -> "无附加参数"
        is JSONObject -> params.toString(2)
        else -> params.toString()
    }
    return buildString {
        append("等待审批：")
        append(methodText)
        append("\n")
        append(paramsText)
    }
}

private fun summarizeToolResult(data: JSONObject): String {
    val method = data.optString("method").ifBlank { "未知方法" }
    val decision = ApprovalDecision.fromWireValue(data.optString("decision"))?.label ?: "已处理"
    val requestId = parseRequestId(data.opt("requestId"))?.toString()
    return buildString {
        append("审批结果：")
        append(decision)
        append("（")
        append(method)
        append("）")
        if (!requestId.isNullOrBlank()) {
            append("\n请求 ID：")
            append(requestId)
        }
    }
}

private fun normalizeCloseCode(code: Int): Int {
    return if (code in 1000..4999 && code !in setOf(1004, 1005, 1006, 1015)) {
        code
    } else {
        1000
    }
}

private fun normalizeCloseReason(reason: String): String? {
    return reason.trim().takeIf { it.isNotEmpty() }?.take(123)
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

private fun extractEventError(value: Any?): String? {
    return when (value) {
        null -> null
        JSONObject.NULL -> null
        is JSONObject -> {
            value.optString("message").takeIf { it.isNotBlank() } ?: value.toString()
        }
        else -> value.toString().takeIf { it.isNotBlank() }
    }
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
