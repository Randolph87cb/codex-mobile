package com.openai.codexmobile.data

import com.openai.codexmobile.diagnostics.AppLogger
import com.openai.codexmobile.model.BridgeConnectionState
import com.openai.codexmobile.model.PendingApprovalSnapshot
import com.openai.codexmobile.model.SessionDetail
import com.openai.codexmobile.model.SessionSummary
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
import java.net.SocketException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RealBridgeDataProvider(
    private val appLogger: AppLogger,
) : CodexDataProvider {
    private var baseUrl: String? = null
    private var authToken: String? = null
    private var connectionState: BridgeConnectionState = BridgeConnectionState.Disconnected
    private val webSocketClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    override fun updateAuthToken(token: String) {
        authToken = token.trim().takeIf { it.isNotEmpty() }
        appLogger.info(
            tag = "BridgeApi",
            message = if (authToken == null) "已清除 bridge 鉴权令牌。" else "已更新 bridge 鉴权令牌状态。",
        )
    }

    override suspend fun connect(endpoint: String): BridgeConnectionState = withContext(Dispatchers.IO) {
        val normalizedEndpoint = normalizeEndpoint(endpoint)
        appLogger.info("BridgeApi", "开始连接 bridge：$normalizedEndpoint")
        val response = request(
            method = "GET",
            url = "$normalizedEndpoint/health",
            summary = "health check",
        )

        if (response.statusCode !in 200..299) {
            appLogger.warn(
                "BridgeApi",
                "bridge 健康检查失败，HTTP ${response.statusCode}：${response.body.compactForLog()}",
            )
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
        appLogger.info(
            "BridgeApi",
            "bridge 连接成功：endpoint=$normalizedEndpoint, runnerMode=${nextState.runnerMode ?: "unknown"}",
        )
        nextState
    }

    override suspend fun disconnect() {
        baseUrl = null
        connectionState = BridgeConnectionState.Disconnected
        appLogger.info("BridgeApi", "已断开 bridge 连接。")
    }

    override suspend fun currentConnection(): BridgeConnectionState = connectionState

    override suspend fun createSession(request: CreateSessionRequest): SessionDetail = withContext(Dispatchers.IO) {
        appLogger.info(
            "BridgeApi",
            "创建会话：cwd=${request.cwd}, model=${request.model}, approval=${request.approvalMode}, sandbox=${request.sandboxMode}",
        )
        val payload = JSONObject()
            .put("cwd", request.cwd)
            .put("model", request.model)
            .put("approvalMode", request.approvalMode)
            .put("reasoningEffort", request.reasoningEffort)
            .put("sandboxMode", request.sandboxMode)
        if (request.serviceTier != "default") {
            payload.put("serviceTier", request.serviceTier)
        }

        val response = request(
            method = "POST",
            url = "${requireBaseUrl()}/api/session",
            body = payload.toString(),
            summary = "create session, cwd=${request.cwd}, model=${request.model}",
        )
        if (response.statusCode !in 200..299) {
            appLogger.warn(
                "BridgeApi",
                "创建会话失败，HTTP ${response.statusCode}：${response.body.compactForLog()}",
            )
            throw BridgeRequestException(response.statusCode, response.body)
        }

        response.body.toSessionDetail()
    }

    override suspend fun updateSessionConfig(sessionId: String, update: SessionConfigUpdate): SessionDetail = withContext(Dispatchers.IO) {
        appLogger.info("BridgeApi", "更新会话配置：sessionId=$sessionId, update=$update")
        val payload = JSONObject()
        update.cwd?.let { payload.put("cwd", it) }
        update.model?.let { payload.put("model", it) }
        update.approvalMode?.let { payload.put("approvalMode", it) }
        update.reasoningEffort?.let { payload.put("reasoningEffort", it) }
        update.serviceTier?.let { payload.put("serviceTier", it) }
        update.sandboxMode?.let { payload.put("sandboxMode", it) }

        val response = request(
            method = "PATCH",
            url = "${requireBaseUrl()}/api/session/$sessionId/config",
            body = payload.toString(),
            summary = "update session config, sessionId=$sessionId",
        )
        if (response.statusCode !in 200..299) {
            appLogger.warn(
                "BridgeApi",
                "更新会话配置失败，sessionId=$sessionId, HTTP ${response.statusCode}：${response.body.compactForLog()}",
            )
            throw BridgeRequestException(response.statusCode, response.body)
        }

        response.body.toSessionDetail()
    }

    override suspend fun uploadImageAttachment(request: UploadImageAttachmentRequest): UploadedImageAttachment = withContext(Dispatchers.IO) {
        appLogger.info(
            "BridgeApi",
            "上传图片附件：displayName=${request.displayName}, mimeType=${request.mimeType}, byteLength=${request.contentBytes.size}",
        )
        val uploadUrl = "${requireBaseUrl()}/api/attachment/image"
        val response = try {
            uploadMultipartImageAttachment(uploadUrl, request)
        } catch (error: SocketException) {
            appLogger.warn(
                "BridgeApi",
                "图片上传遇到 SocketException，准备重试一次：displayName=${request.displayName}, error=${error.message ?: "unknown"}",
            )
            uploadMultipartImageAttachment(uploadUrl, request)
        }
        if (response.statusCode !in 200..299) {
            appLogger.warn(
                "BridgeApi",
                "上传图片附件失败，HTTP ${response.statusCode}：${response.body.compactForLog()}",
            )
            throw BridgeRequestException(response.statusCode, response.body)
        }

        JSONObject(response.body).let { json ->
            UploadedImageAttachment(
                id = json.optString("id").ifBlank { error("bridge 未返回附件 ID。") },
                displayName = json.optString("displayName").ifBlank { request.displayName },
                mimeType = json.optString("mimeType").ifBlank { request.mimeType },
                stagedPath = json.optString("path").ifBlank { error("bridge 未返回暂存路径。") },
            )
        }
    }

    override suspend fun sendInput(sessionId: String, request: SendInputRequest) = withContext(Dispatchers.IO) {
        appLogger.info(
            "BridgeApi",
            "发送输入：sessionId=$sessionId, textLength=${request.text?.length ?: 0}, attachments=${request.attachments.size}",
        )
        val payload = JSONObject()
        request.text?.takeIf { it.isNotBlank() }?.let { payload.put("text", it) }
        if (request.attachments.isNotEmpty()) {
            val attachments = JSONArray()
            request.attachments.forEach { attachment ->
                attachments.put(JSONObject().put("path", attachment.stagedPath))
            }
            payload.put("attachments", attachments)
        }
        val response = request(
            method = "POST",
            url = "${requireBaseUrl()}/api/session/$sessionId/input",
            body = payload.toString(),
            summary = "send input, sessionId=$sessionId, textLength=${request.text?.length ?: 0}, attachments=${request.attachments.size}",
        )
        if (response.statusCode !in 200..299) {
            appLogger.warn(
                "BridgeApi",
                "发送输入失败，sessionId=$sessionId, HTTP ${response.statusCode}：${response.body.compactForLog()}",
            )
            throw BridgeRequestException(response.statusCode, response.body)
        }
    }

    override suspend fun approveSession(
        sessionId: String,
        requestId: BridgeRequestId?,
        decision: ApprovalDecision,
    ): ApprovalActionResult = withContext(Dispatchers.IO) {
        appLogger.info(
            "BridgeApi",
            "提交审批：sessionId=$sessionId, decision=${decision.wireValue}, requestId=${requestId ?: "none"}",
        )
        val payload = JSONObject().put("decision", decision.wireValue)
        requestId?.let { payload.put("requestId", it.toJsonValue()) }

        val response = request(
            method = "POST",
            url = "${requireBaseUrl()}/api/session/$sessionId/approve",
            body = payload.toString(),
            summary = "approve session, sessionId=$sessionId, decision=${decision.wireValue}",
        )
        if (response.statusCode !in 200..299) {
            appLogger.warn(
                "BridgeApi",
                "提交审批失败，sessionId=$sessionId, HTTP ${response.statusCode}：${response.body.compactForLog()}",
            )
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

    private fun uploadMultipartImageAttachment(
        url: String,
        request: UploadImageAttachmentRequest,
    ): HttpResponse {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("displayName", request.displayName)
            .addFormDataPart("mimeType", request.mimeType)
            .addFormDataPart(
                "file",
                request.displayName,
                request.contentBytes.toRequestBody(request.mimeType.toMediaTypeOrNull()),
            )
            .build()
        val requestBuilder = Request.Builder()
            .url(url)
            .post(body)
            .header("Accept", "application/json")
            .header("Connection", "close")
        authToken?.let { requestBuilder.header("Authorization", "Bearer $it") }
        appLogger.debug("BridgeApi", "HTTP 请求：POST $url (upload image attachment, displayName=${request.displayName})")
        webSocketClient.newCall(requestBuilder.build()).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            appLogger.debug(
                "BridgeApi",
                "HTTP 响应：POST $url -> ${response.code}${if (payload.isBlank()) "" else ", body=${payload.compactForLog()}"}",
            )
            return HttpResponse(
                statusCode = response.code,
                body = payload,
            )
        }
    }

    override fun observeSessionEvents(sessionId: String): Flow<SessionStreamEvent> = callbackFlow {
        appLogger.info("BridgeApi", "开始订阅会话实时流：sessionId=$sessionId")
        val requestBuilder = Request.Builder()
            .url(toWebSocketUrl("${requireBaseUrl()}/api/session/$sessionId/ws"))
        authToken?.let { requestBuilder.header("Authorization", "Bearer $it") }
        val request = requestBuilder.build()
        val closedByClient = AtomicBoolean(false)
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                appLogger.info("BridgeApi", "实时流已连接：sessionId=$sessionId")
                trySend(SessionStreamEvent.StreamOpened(sessionId = sessionId))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseSessionStreamEvent(
                    sessionId = sessionId,
                    payload = text,
                )?.let { event ->
                    appLogger.debug("BridgeApi", "实时流事件：${event.toLogSummary()}")
                    trySend(event)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                appLogger.info(
                    "BridgeApi",
                    "实时流关闭中：sessionId=$sessionId, code=$code, reason=${reason.ifBlank { "none" }}",
                )
                trySend(
                    SessionStreamEvent.StreamClosed(
                        sessionId = sessionId,
                        reason = reason.ifBlank { "实时流正在关闭。" },
                    ),
                )
                webSocket.close(normalizeCloseCode(code), normalizeCloseReason(reason))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                appLogger.info(
                    "BridgeApi",
                    "实时流已关闭：sessionId=$sessionId, code=$code, reason=${reason.ifBlank { "none" }}",
                )
                trySend(
                    SessionStreamEvent.StreamClosed(
                        sessionId = sessionId,
                        reason = reason.ifBlank { "实时流已关闭。" },
                    ),
                )
                this@callbackFlow.close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (shouldSuppressStreamFailure(t.message, closedByClient.get())) {
                    appLogger.info(
                        "BridgeApi",
                        "实时流已按客户端请求结束：sessionId=$sessionId, message=${t.message ?: "none"}",
                    )
                    this@callbackFlow.close()
                    return
                }
                appLogger.error(
                    "BridgeApi",
                    "实时流失败：sessionId=$sessionId, code=${response?.code ?: "n/a"}",
                    t,
                )
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
            appLogger.info("BridgeApi", "结束实时流订阅：sessionId=$sessionId")
            closedByClient.set(true)
            webSocket.cancel()
        }
    }

    override suspend fun listSessions(archived: Boolean): List<SessionSummary> = withContext(Dispatchers.IO) {
        val response = request(
            method = "GET",
            url = "${requireBaseUrl()}/api/sessions?archived=$archived",
            summary = "list sessions, archived=$archived",
        )
        if (response.statusCode !in 200..299) {
            appLogger.warn(
                "BridgeApi",
                "获取会话列表失败，HTTP ${response.statusCode}：${response.body.compactForLog()}",
            )
            throw BridgeRequestException(response.statusCode, response.body)
        }

        val items = JSONObject(response.body).optJSONArray("items") ?: JSONArray()
        List(items.length()) { index ->
            items.getJSONObject(index).toSessionSummary()
        }
    }

    override suspend fun archiveSession(sessionId: String) = withContext(Dispatchers.IO) {
        appLogger.info("BridgeApi", "归档会话：sessionId=$sessionId")
        val response = request(
            method = "POST",
            url = "${requireBaseUrl()}/api/session/$sessionId/archive",
            body = JSONObject().toString(),
            summary = "archive session, sessionId=$sessionId",
        )
        if (response.statusCode !in 200..299) {
            appLogger.warn(
                "BridgeApi",
                "归档会话失败，sessionId=$sessionId, HTTP ${response.statusCode}：${response.body.compactForLog()}",
            )
            throw BridgeRequestException(response.statusCode, response.body)
        }
    }

    override suspend fun unarchiveSession(sessionId: String) = withContext(Dispatchers.IO) {
        appLogger.info("BridgeApi", "恢复归档会话：sessionId=$sessionId")
        val response = request(
            method = "POST",
            url = "${requireBaseUrl()}/api/session/$sessionId/unarchive",
            body = JSONObject().toString(),
            summary = "unarchive session, sessionId=$sessionId",
        )
        if (response.statusCode !in 200..299) {
            appLogger.warn(
                "BridgeApi",
                "恢复归档会话失败，sessionId=$sessionId, HTTP ${response.statusCode}：${response.body.compactForLog()}",
            )
            throw BridgeRequestException(response.statusCode, response.body)
        }
    }

    override suspend fun getSessionDetail(sessionId: String): SessionDetail? = withContext(Dispatchers.IO) {
        val response = request(
            method = "GET",
            url = "${requireBaseUrl()}/api/session/$sessionId",
            summary = "get session detail, sessionId=$sessionId",
        )
        when (response.statusCode) {
            404 -> null
            in 200..299 -> response.body.toSessionDetail()
            else -> {
                appLogger.warn(
                    "BridgeApi",
                    "获取会话详情失败，sessionId=$sessionId, HTTP ${response.statusCode}：${response.body.compactForLog()}",
                )
                throw BridgeRequestException(response.statusCode, response.body)
            }
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
        summary: String? = null,
        forceClose: Boolean = false,
    ): HttpResponse {
        appLogger.debug("BridgeApi", "HTTP 请求：$method $url${summary?.let { " ($it)" } ?: ""}")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 5_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            authToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (forceClose) {
                setRequestProperty("Connection", "close")
            }
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
            appLogger.debug(
                "BridgeApi",
                "HTTP 响应：$method $url -> $statusCode${if (payload.isBlank()) "" else ", body=${payload.compactForLog()}"}",
            )
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

internal fun parseSessionStreamEvent(
    sessionId: String,
    payload: String,
): SessionStreamEvent? {
    val json = JSONObject(payload)
    val eventType = json.optString("type").takeIf { it.isNotBlank() }
    if (eventType == null) {
        val message = extractEventError(json.opt("error")) ?: "收到无法识别的实时流消息。"
        return SessionStreamEvent.Error(
            sessionId = sessionId,
            message = message,
            timestamp = null,
        )
    }

    val eventSessionId = json.optString("sessionId").ifBlank { sessionId }
    val timestamp = json.optString("timestamp").takeIf { it.isNotBlank() }
    val data = json.optJSONObject("data") ?: JSONObject()

    return when (eventType) {
        "session.started" -> SessionStreamEvent.SessionStarted(
            sessionId = eventSessionId,
            status = data.optString("status").ifBlank { "idle" },
            cwd = data.optString("cwd").takeIf { it.isNotBlank() },
            model = data.optString("model").takeIf { it.isNotBlank() },
            approvalMode = data.optString("approvalMode").takeIf { it.isNotBlank() },
            reasoningEffort = data.optString("reasoningEffort").takeIf { it.isNotBlank() },
            serviceTier = data.optString("serviceTier").takeIf { it.isNotBlank() },
            sandboxMode = data.optString("sandboxMode").takeIf { it.isNotBlank() },
            threadId = data.optString("threadId").takeIf { it.isNotBlank() },
            pendingApproval = parsePendingApprovalSnapshot(data.opt("pendingApproval")),
            timestamp = timestamp,
        )

        "bridge.lifecycle" -> SessionStreamEvent.BridgeLifecycle(
            sessionId = eventSessionId,
            phase = data.optString("phase").ifBlank { "running" },
            reason = data.optString("reason").takeIf { it.isNotBlank() },
            graceMs = data.takeIf { it.has("graceMs") && !it.isNull("graceMs") }?.optInt("graceMs"),
            bridgeVersion = data.optString("bridgeVersion").takeIf { it.isNotBlank() },
            bridgeStartedAt = data.optString("bridgeStartedAt").takeIf { it.isNotBlank() },
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

        "activity" -> {
            val transcriptBlock = data.optString("transcriptBlock").trim()
            if (transcriptBlock.isBlank()) {
                null
            } else {
                SessionStreamEvent.Activity(
                    sessionId = eventSessionId,
                    itemType = data.optString("itemType").takeIf { it.isNotBlank() },
                    itemId = data.optString("itemId").takeIf { it.isNotBlank() },
                    title = data.optString("title").takeIf { it.isNotBlank() },
                    body = data.optString("body").takeIf { it.isNotBlank() },
                    transcriptBlock = transcriptBlock,
                    summary = data.optString("summary").takeIf { it.isNotBlank() },
                    timestamp = timestamp,
                )
            }
        }

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

        "error" -> {
            val parsedError = parseBridgeStreamError(data.opt("error"))
            SessionStreamEvent.Error(
                sessionId = eventSessionId,
                message = parsedError?.message ?: "bridge 返回错误事件。",
                isRetryable = parsedError?.willRetry == true,
                timestamp = timestamp,
            )
        }

        else -> null
    }
}

private data class ParsedBridgeStreamError(
    val message: String?,
    val willRetry: Boolean,
)

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

internal fun String.toSessionSummary(): SessionSummary {
    return JSONObject(this).toSessionSummary()
}

internal fun JSONObject.toSessionSummary(): SessionSummary {
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
        archived = optBoolean("archived", false),
        cwd = cwd,
        model = model,
        approvalMode = optString("approvalMode").ifBlank { "manual" },
        reasoningEffort = optString("reasoningEffort").ifBlank { "medium" },
        serviceTier = optString("serviceTier").ifBlank { "default" },
        sandboxMode = optString("sandboxMode").ifBlank { "workspace-write" },
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
    val reasoningEffort = optString("reasoningEffort").ifBlank { "medium" }
    val serviceTier = optString("serviceTier").ifBlank { "default" }
    val sandboxMode = optString("sandboxMode").ifBlank { "workspace-write" }
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
        cwd = cwd,
        model = model,
        approvalMode = approvalMode,
        reasoningEffort = reasoningEffort,
        serviceTier = serviceTier,
        sandboxMode = sandboxMode,
        status = status,
        pendingApproval = parsePendingApprovalSnapshot(opt("pendingApproval")),
    )
}

private fun parsePendingApprovalSnapshot(value: Any?): PendingApprovalSnapshot? {
    val json = value as? JSONObject ?: return null
    return PendingApprovalSnapshot(
        requestId = parseRequestId(json.opt("requestId")),
        method = json.optString("method").takeIf { it.isNotBlank() },
        paramsSummary = json.optString("paramsSummary").takeIf { it.isNotBlank() },
    ).takeIf {
        it.requestId != null || it.method != null || it.paramsSummary != null
    }
}

private fun parseBridgeStreamError(value: Any?): ParsedBridgeStreamError? {
    return when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> ParsedBridgeStreamError(
            message = extractEventError(value),
            willRetry = value.optBoolean("willRetry", false),
        )
        else -> ParsedBridgeStreamError(
            message = value.toString().takeIf { it.isNotBlank() },
            willRetry = false,
        )
    }
}

private fun extractEventError(value: Any?): String? {
    return when (value) {
        null -> null
        JSONObject.NULL -> null
        is JSONObject -> {
            value.optString("message").takeIf { it.isNotBlank() }
                ?: extractEventError(value.opt("error"))
                ?: value.toString().takeIf { it.isNotBlank() }
        }
        else -> value.toString().takeIf { it.isNotBlank() }
    }
}

internal fun shouldSuppressStreamFailure(message: String?, closedByClient: Boolean): Boolean {
    if (!closedByClient) {
        return false
    }

    if (message.isNullOrBlank()) {
        return true
    }

    return message.contains("closed", ignoreCase = true) ||
        message.contains("canceled", ignoreCase = true)
}

private fun SessionStreamEvent.toLogSummary(): String {
    return when (this) {
        is SessionStreamEvent.StreamOpened -> "stream.opened sessionId=$sessionId"
        is SessionStreamEvent.StreamClosed -> "stream.closed sessionId=$sessionId reason=${reason ?: "none"}"
        is SessionStreamEvent.SessionStarted -> "session.started sessionId=$sessionId status=$status"
        is SessionStreamEvent.BridgeLifecycle -> "bridge.lifecycle sessionId=$sessionId phase=$phase"
        is SessionStreamEvent.AssistantDelta -> "assistant.delta sessionId=$sessionId chars=${text.length}"
        is SessionStreamEvent.AssistantDone -> "assistant.done sessionId=$sessionId status=${turnStatus ?: "unknown"}"
        is SessionStreamEvent.Activity -> "activity sessionId=$sessionId itemType=${itemType ?: "unknown"}"
        is SessionStreamEvent.RunStatus -> "run.status sessionId=$sessionId status=$status"
        is SessionStreamEvent.RunInterrupted -> "run.interrupted sessionId=$sessionId status=${status ?: "unknown"}"
        is SessionStreamEvent.ToolRequest -> "tool.request sessionId=$sessionId method=${method ?: "unknown"}"
        is SessionStreamEvent.ToolResult -> "tool.result sessionId=$sessionId method=${method ?: "unknown"} status=${status ?: "unknown"}"
        is SessionStreamEvent.Error -> "error sessionId=$sessionId message=$message"
    }
}

private fun String.compactForLog(maxLength: Int = 400): String {
    val compact = replace("\r", "\\r").replace("\n", "\\n")
    return if (compact.length <= maxLength) compact else "${compact.take(maxLength)}..."
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
