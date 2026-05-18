package com.openai.codexmobile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.openai.codexmobile.data.AppSettings
import com.openai.codexmobile.data.AppSettingsStore
import com.openai.codexmobile.data.ApprovalActionResult
import com.openai.codexmobile.data.ApprovalDecision
import com.openai.codexmobile.data.BridgeApi
import com.openai.codexmobile.data.BridgeRequestId
import com.openai.codexmobile.data.CreateSessionRequest
import com.openai.codexmobile.data.SessionRepository
import com.openai.codexmobile.data.SessionStreamEvent
import com.openai.codexmobile.model.BridgeConnectionState
import com.openai.codexmobile.model.SessionDetail
import com.openai.codexmobile.model.SessionSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

data class AppUiState(
    val endpointInput: String,
    val authTokenInput: String,
    val cwdInput: String,
    val modelInput: String,
    val approvalModeInput: String,
    val connectionState: BridgeConnectionState = BridgeConnectionState.Disconnected,
    val sessions: List<SessionSummary> = emptyList(),
    val selectedSession: SessionDetail? = null,
    val draftMessage: String = "",
    val isLoading: Boolean = false,
    val message: String? = null,
    val settingsItems: List<Pair<String, String>>,
    val sessionRealtimeState: SessionRealtimeUiState = SessionRealtimeUiState(),
)

data class SessionRealtimeUiState(
    val isActive: Boolean = false,
    val isConnected: Boolean = false,
    val connectionText: String = "未连接实时流",
    val statusText: String = "等待会话详情",
    val lastEventText: String? = null,
    val fallbackNotice: String? = null,
    val pendingApproval: PendingApprovalUiState? = null,
)

data class PendingApprovalUiState(
    val requestId: BridgeRequestId?,
    val method: String?,
    val paramsSummary: String?,
    val isSubmitting: Boolean = false,
)

class AppViewModel(
    private val bridgeApi: BridgeApi,
    private val sessionRepository: SessionRepository,
    private val settingsStore: AppSettingsStore,
) : ViewModel() {

    private val initialSettings = settingsStore.load().sanitize()
    private val _uiState = MutableStateFlow(createInitialUiState(initialSettings))
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private var sessionStreamJob: Job? = null
    private var activeStreamSessionId: String? = null
    private var activeAssistantTurnId: String? = null

    init {
        bridgeApi.updateAuthToken(initialSettings.authToken)
        refreshConnection()
    }

    fun updateEndpointInput(value: String) {
        updateSettingsState { it.copy(endpointInput = value) }
    }

    fun updateAuthTokenInput(value: String) {
        bridgeApi.updateAuthToken(value)
        updateSettingsState { it.copy(authTokenInput = value) }
    }

    fun updateCwdInput(value: String) {
        updateSettingsState { it.copy(cwdInput = value) }
    }

    fun updateModelInput(value: String) {
        updateSettingsState { it.copy(modelInput = value) }
    }

    fun updateApprovalModeInput(value: String) {
        updateSettingsState {
            it.copy(
                approvalModeInput = value.takeIf { mode -> mode == "manual" || mode == "auto" } ?: "manual",
            )
        }
    }

    fun updateDraftMessage(value: String) {
        _uiState.update { it.copy(draftMessage = value) }
    }

    fun connect() {
        val endpoint = uiState.value.endpointInput.trim()
        if (endpoint.isBlank()) {
            _uiState.update { it.copy(message = "请先填写桥接地址。") }
            return
        }

        viewModelScope.launch {
            stopSessionStream()
            _uiState.update { it.copy(isLoading = true, message = null, selectedSession = null) }
            try {
                val connectionState = bridgeApi.connect(endpoint)
                val sessions = sessionRepository.listSessions()
                val selectedSession = sessions.firstOrNull()?.let { sessionRepository.getSessionDetail(it.id) }
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        connectionState = connectionState,
                        sessions = sessions,
                        selectedSession = selectedSession,
                        message = connectedMessage(connectionState),
                    ).withSettingsItems()
                }
            } catch (error: Exception) {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        connectionState = BridgeConnectionState.Disconnected,
                        sessions = emptyList(),
                        selectedSession = null,
                        message = error.message ?: "连接桥接服务失败。",
                    ).withSettingsItems()
                }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            stopSessionStream()
            bridgeApi.disconnect()
            _uiState.update {
                it.copy(
                    connectionState = BridgeConnectionState.Disconnected,
                    sessions = emptyList(),
                    selectedSession = null,
                    message = "已断开连接。",
                ).withSettingsItems()
            }
        }
    }

    fun openSessionDetail(sessionId: String) {
        if (sessionId.isBlank()) {
            return
        }
        if (activeStreamSessionId == sessionId && sessionStreamJob?.isActive == true) {
            return
        }

        viewModelScope.launch {
            stopSessionStream(resetRealtimeState = false)
            _uiState.update {
                it.copy(
                    isLoading = true,
                    message = null,
                    selectedSession = it.selectedSession?.takeIf { detail -> detail.id == sessionId },
                    sessionRealtimeState = SessionRealtimeUiState(
                        isActive = true,
                        connectionText = "正在连接实时流",
                        statusText = it.selectedSession
                            ?.takeIf { detail -> detail.id == sessionId }
                            ?.status
                            ?.let(::localizedSessionStatus)
                            ?: "正在加载会话",
                    ),
                )
            }

            val detail = try {
                sessionRepository.getSessionDetail(sessionId)
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        selectedSession = null,
                        message = error.message ?: "加载会话失败。",
                        sessionRealtimeState = it.sessionRealtimeState.copy(
                            isConnected = false,
                            connectionText = "会话加载失败",
                            statusText = "无法打开会话",
                            fallbackNotice = "请检查 bridge 连接和会话状态。",
                        ),
                    )
                }
                return@launch
            }

            if (detail == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        selectedSession = null,
                        message = "未找到会话：$sessionId",
                        sessionRealtimeState = it.sessionRealtimeState.copy(
                            isConnected = false,
                            connectionText = "找不到目标会话",
                            statusText = "会话不存在",
                            fallbackNotice = "这条会话可能已经结束或不再可访问。",
                        ),
                    )
                }
                return@launch
            }

            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    selectedSession = detail,
                    sessionRealtimeState = state.sessionRealtimeState.copy(
                        statusText = localizedSessionStatus(detail.status),
                        fallbackNotice = null,
                    ),
                )
            }

            startSessionStream(sessionId)
        }
    }

    fun closeSessionDetail(sessionId: String? = null) {
        if (sessionId == null || sessionId == activeStreamSessionId) {
            stopSessionStream()
        }
    }

    fun createSession() {
        val request = buildCreateSessionRequest(uiState.value) ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            try {
                val created = bridgeApi.createSession(request)
                val sessions = sessionRepository.listSessions()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        sessions = sessions,
                        selectedSession = created,
                        message = "已创建会话：${created.title}",
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = error.message ?: "创建会话失败。",
                    )
                }
            }
        }
    }

    fun sendInput() {
        val detail = uiState.value.selectedSession ?: return
        val text = uiState.value.draftMessage.trim()
        if (text.isEmpty()) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            try {
                bridgeApi.sendInput(detail.id, text)
                activeAssistantTurnId = null
                val updatedDetail = appendUserMessage(detail, text)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        selectedSession = updatedDetail,
                        draftMessage = "",
                        message = "消息已发送，等待实时输出。",
                        sessionRealtimeState = it.sessionRealtimeState.copy(
                            statusText = localizedSessionStatus("running"),
                            lastEventText = "已发送消息，等待 Codex 回复。",
                        ),
                    )
                }
                refreshSessions()
                if (activeStreamSessionId != detail.id || sessionStreamJob?.isActive != true) {
                    startSessionStream(detail.id)
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = error.message ?: "发送消息失败。",
                    )
                }
            }
        }
    }

    fun submitApproval(decision: ApprovalDecision) {
        val detail = uiState.value.selectedSession ?: return
        val approval = uiState.value.sessionRealtimeState.pendingApproval ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    message = null,
                    sessionRealtimeState = it.sessionRealtimeState.copy(
                        pendingApproval = approval.copy(isSubmitting = true),
                    ),
                )
            }

            try {
                val result = bridgeApi.approveSession(detail.id, approval.requestId, decision)
                applyApprovalResult(result)
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = error.message ?: "提交审批操作失败。",
                        sessionRealtimeState = it.sessionRealtimeState.copy(
                            pendingApproval = approval.copy(isSubmitting = false),
                        ),
                    )
                }
            }
        }
    }

    private fun refreshConnection() {
        viewModelScope.launch {
            val connectionState = bridgeApi.currentConnection()
            _uiState.update {
                it.copy(connectionState = connectionState).withSettingsItems()
            }
        }
    }

    private fun startSessionStream(sessionId: String) {
        stopSessionStream(resetRealtimeState = false)
        activeStreamSessionId = sessionId
        sessionStreamJob = viewModelScope.launch {
            try {
                bridgeApi.observeSessionEvents(sessionId).collect { event ->
                    handleSessionStreamEvent(sessionId, event)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (activeStreamSessionId == sessionId) {
                    _uiState.update {
                        it.copy(
                            sessionRealtimeState = it.sessionRealtimeState.copy(
                                isActive = true,
                                isConnected = false,
                                connectionText = "实时流连接失败",
                                lastEventText = "无法继续接收实时事件。",
                                fallbackNotice = error.message ?: "当前回退到 HTTP 快照。",
                            ),
                            message = error.message ?: "实时流连接失败。",
                        )
                    }
                    refreshSessionSnapshot(sessionId)
                }
            }
        }
    }

    private fun stopSessionStream(resetRealtimeState: Boolean = true) {
        sessionStreamJob?.cancel()
        sessionStreamJob = null
        activeStreamSessionId = null
        activeAssistantTurnId = null
        if (resetRealtimeState) {
            _uiState.update {
                it.copy(sessionRealtimeState = SessionRealtimeUiState())
            }
        }
    }

    private suspend fun handleSessionStreamEvent(
        sessionId: String,
        event: SessionStreamEvent,
    ) {
        if (activeStreamSessionId != sessionId) {
            return
        }

        when (event) {
            is SessionStreamEvent.StreamOpened -> {
                _uiState.update {
                    it.copy(
                        sessionRealtimeState = it.sessionRealtimeState.copy(
                            isActive = true,
                            isConnected = true,
                            connectionText = "已连接实时流",
                            lastEventText = "已接入会话实时流。",
                            fallbackNotice = null,
                        ),
                    )
                }
            }

            is SessionStreamEvent.StreamClosed -> {
                _uiState.update {
                    it.copy(
                        sessionRealtimeState = it.sessionRealtimeState.copy(
                            isActive = true,
                            isConnected = false,
                            connectionText = "实时流已断开",
                            lastEventText = event.reason ?: "实时流连接已关闭。",
                            fallbackNotice = "当前停留在最后一次收到的内容快照。",
                            pendingApproval = null,
                        ),
                    )
                }
            }

            is SessionStreamEvent.SessionStarted -> {
                _uiState.update {
                    it.copy(
                        selectedSession = buildOrUpdateSessionFromStart(
                            current = it.selectedSession,
                            event = event,
                        ),
                        sessionRealtimeState = it.sessionRealtimeState.copy(
                            isActive = true,
                            isConnected = true,
                            connectionText = "已连接实时流",
                            statusText = localizedSessionStatus(event.status),
                            lastEventText = "会话实时流已就绪。",
                            fallbackNotice = null,
                            pendingApproval = null,
                        ),
                    )
                }
            }

            is SessionStreamEvent.AssistantDelta -> {
                if (event.text.isBlank()) {
                    return
                }

                val rendered = appendAssistantDelta(
                    detail = uiState.value.selectedSession,
                    event = event,
                    currentAssistantTurnId = activeAssistantTurnId,
                )
                activeAssistantTurnId = rendered.activeTurnId
                _uiState.update {
                    it.copy(
                        selectedSession = rendered.detail,
                        sessionRealtimeState = it.sessionRealtimeState.copy(
                            statusText = localizedSessionStatus("running"),
                            lastEventText = "Codex 正在实时输出回复。",
                        ),
                    )
                }
            }

            is SessionStreamEvent.AssistantDone -> {
                activeAssistantTurnId = null
                val nextStatus = if (event.turnStatus == "failed") "error" else "idle"
                _uiState.update {
                    it.copy(
                        selectedSession = it.selectedSession?.copy(
                            status = nextStatus,
                            lastUpdated = event.timestamp ?: it.selectedSession.lastUpdated,
                        ),
                        sessionRealtimeState = it.sessionRealtimeState.copy(
                            statusText = localizedSessionStatus(nextStatus),
                            lastEventText = when (event.turnStatus) {
                                "failed" -> "本轮回复以错误结束。"
                                else -> "本轮回复已结束。"
                            },
                            fallbackNotice = event.errorMessage,
                        ),
                        message = event.errorMessage,
                    )
                }
                refreshSessionSnapshot(sessionId)
            }

            is SessionStreamEvent.RunStatus -> {
                _uiState.update {
                    it.copy(
                        selectedSession = it.selectedSession?.copy(
                            status = event.status,
                            lastUpdated = event.timestamp ?: it.selectedSession.lastUpdated,
                        ),
                        sessionRealtimeState = it.sessionRealtimeState.copy(
                            statusText = localizedSessionStatus(event.status),
                            lastEventText = statusEventText(event.status),
                            fallbackNotice = if (event.status == "awaiting_approval") {
                                it.sessionRealtimeState.fallbackNotice
                            } else {
                                null
                            },
                        ),
                    )
                }
            }

            is SessionStreamEvent.RunInterrupted -> {
                activeAssistantTurnId = null
                _uiState.update {
                    it.copy(
                        selectedSession = it.selectedSession
                            ?.let { detail -> appendSystemMessage(detail, "当前任务已中断。", event.timestamp) }
                            ?.copy(status = "idle"),
                        sessionRealtimeState = it.sessionRealtimeState.copy(
                            statusText = localizedSessionStatus("idle"),
                            lastEventText = "当前任务已中断。",
                            fallbackNotice = null,
                            pendingApproval = null,
                        ),
                        message = "当前任务已中断。",
                    )
                }
                refreshSessionSnapshot(sessionId)
            }

            is SessionStreamEvent.ToolRequest -> {
                _uiState.update {
                    it.copy(
                        selectedSession = it.selectedSession
                            ?.let { detail ->
                                appendSystemMessage(
                                    detail = detail,
                                    message = event.paramsSummary
                                        ?: "等待审批：${event.method ?: "未知方法"}",
                                    timestamp = event.timestamp,
                                )
                            }
                            ?.copy(status = "awaiting_approval"),
                        sessionRealtimeState = it.sessionRealtimeState.copy(
                            statusText = localizedSessionStatus("awaiting_approval"),
                            lastEventText = "收到工具请求：${event.method ?: "未知方法"}",
                            fallbackNotice = "请在手机端确认这次工具请求。",
                            pendingApproval = PendingApprovalUiState(
                                requestId = event.requestId,
                                method = event.method,
                                paramsSummary = event.paramsSummary,
                            ),
                        ),
                    )
                }
            }

            is SessionStreamEvent.ToolResult -> {
                _uiState.update {
                    it.copy(
                        selectedSession = it.selectedSession
                            ?.let { detail ->
                                appendSystemMessage(
                                    detail = detail,
                                    message = event.summary ?: "审批结果：${event.method ?: "未知方法"}",
                                    timestamp = event.timestamp,
                                )
                            }
                            ?.copy(status = event.status ?: it.selectedSession.status),
                        sessionRealtimeState = it.sessionRealtimeState.copy(
                            lastEventText = event.summary ?: "收到工具结果事件。",
                            fallbackNotice = null,
                            pendingApproval = null,
                            statusText = event.status?.let(::localizedSessionStatus)
                                ?: it.sessionRealtimeState.statusText,
                        ),
                    )
                }
            }

            is SessionStreamEvent.Error -> {
                activeAssistantTurnId = null
                _uiState.update {
                    it.copy(
                        selectedSession = it.selectedSession
                            ?.let { detail -> appendSystemMessage(detail, "系统：${event.message}", event.timestamp) }
                            ?.copy(status = "error"),
                        sessionRealtimeState = it.sessionRealtimeState.copy(
                            statusText = localizedSessionStatus("error"),
                            lastEventText = "实时流返回错误事件。",
                            fallbackNotice = event.message,
                            pendingApproval = null,
                        ),
                        message = "实时流错误：${event.message}",
                    )
                }
                refreshSessionSnapshot(sessionId)
            }
        }
    }

    private suspend fun refreshSessionSnapshot(sessionId: String) {
        val detail = try {
            sessionRepository.getSessionDetail(sessionId)
        } catch (_: Exception) {
            null
        }

        if (detail != null && uiState.value.selectedSession?.id == sessionId) {
            _uiState.update { it.copy(selectedSession = detail) }
        }

        refreshSessions()
    }

    private suspend fun refreshSessions() {
        try {
            val sessions = sessionRepository.listSessions()
            _uiState.update { it.copy(sessions = sessions) }
        } catch (_: Exception) {
            // Keep the latest visible list if list refresh fails.
        }
    }

    private fun updateSettingsState(transform: (AppUiState) -> AppUiState) {
        _uiState.update { current ->
            val next = transform(current).withSettingsItems()
            persistSettings(next)
            next
        }
    }

    private fun persistSettings(state: AppUiState) {
        settingsStore.save(
            AppSettings(
                endpoint = state.endpointInput.trim(),
                authToken = state.authTokenInput,
                cwd = state.cwdInput.trim(),
                model = state.modelInput.trim(),
                approvalMode = state.approvalModeInput,
            ),
        )
    }

    private fun buildCreateSessionRequest(state: AppUiState): CreateSessionRequest? {
        val cwd = state.cwdInput.trim()
        if (cwd.isBlank()) {
            _uiState.update { it.copy(message = "请先在设置里填写默认工作目录。") }
            return null
        }

        val model = state.modelInput.trim()
        if (model.isBlank()) {
            _uiState.update { it.copy(message = "请先在设置里填写默认模型。") }
            return null
        }

        val approvalMode = state.approvalModeInput.takeIf { it == "manual" || it == "auto" } ?: "manual"
        return CreateSessionRequest(
            cwd = cwd,
            model = model,
            approvalMode = approvalMode,
        )
    }

    override fun onCleared() {
        stopSessionStream()
        super.onCleared()
    }

    private fun applyApprovalResult(result: ApprovalActionResult) {
        _uiState.update {
            it.copy(
                isLoading = false,
                selectedSession = it.selectedSession
                    ?.let { detail ->
                        appendSystemMessage(
                            detail = detail,
                            message = buildApprovalResultText(result),
                            timestamp = nowIsoString(),
                        )
                    }
                    ?.copy(status = result.status),
                message = "已提交审批操作：${result.decision.label}",
                sessionRealtimeState = it.sessionRealtimeState.copy(
                    statusText = localizedSessionStatus(result.status),
                    lastEventText = buildApprovalResultText(result),
                    fallbackNotice = null,
                    pendingApproval = null,
                ),
            )
        }
    }
}

private fun createInitialUiState(settings: AppSettings): AppUiState {
    return AppUiState(
        endpointInput = settings.endpoint,
        authTokenInput = settings.authToken,
        cwdInput = settings.cwd,
        modelInput = settings.model,
        approvalModeInput = settings.approvalMode,
        settingsItems = defaultSettingsItems(
            endpointInput = settings.endpoint,
            authTokenInput = settings.authToken,
            cwdInput = settings.cwd,
            modelInput = settings.model,
            approvalModeInput = settings.approvalMode,
        ),
    )
}

private fun AppUiState.withSettingsItems(): AppUiState {
    return copy(
        settingsItems = defaultSettingsItems(
            connectionState = connectionState,
            endpointInput = endpointInput,
            authTokenInput = authTokenInput,
            cwdInput = cwdInput,
            modelInput = modelInput,
            approvalModeInput = approvalModeInput,
        ),
    )
}

private fun AppSettings.sanitize(): AppSettings {
    return copy(
        endpoint = endpoint.trim(),
        cwd = cwd.trim(),
        model = model.trim().ifEmpty { "gpt-5.5" },
        approvalMode = approvalMode.takeIf { it == "manual" || it == "auto" } ?: "manual",
    )
}

private fun buildOrUpdateSessionFromStart(
    current: SessionDetail?,
    event: SessionStreamEvent.SessionStarted,
): SessionDetail {
    if (current != null && current.id == event.sessionId) {
        return current.copy(
            status = event.status,
            lastUpdated = event.timestamp ?: current.lastUpdated,
        )
    }

    val transcript = buildString {
        appendLine("工作目录：${event.cwd ?: "未提供"}")
        appendLine("线程 ID：${event.threadId ?: "尚未分配"}")
        append("实时流已连接，等待新的助手输出。")
    }

    return SessionDetail(
        id = event.sessionId,
        title = event.sessionId,
        subtitle = "${event.model ?: "未知模型"} • 实时流",
        lastUpdated = event.timestamp ?: nowIsoString(),
        transcriptPreview = transcript,
        status = event.status,
    )
}

private fun appendUserMessage(
    detail: SessionDetail,
    message: String,
): SessionDetail {
    val current = detail.transcriptPreview.trimEnd()
    val nextTranscript = buildString {
        if (current.isNotBlank()) {
            append(current)
            append("\n\n")
        }
        append("你：")
        append(message)
    }

    return detail.copy(
        transcriptPreview = nextTranscript,
        status = "running",
        lastUpdated = nowIsoString(),
    )
}

private fun appendSystemMessage(
    detail: SessionDetail,
    message: String,
    timestamp: String?,
): SessionDetail {
    val current = detail.transcriptPreview.trimEnd()
    val nextTranscript = buildString {
        if (current.isNotBlank()) {
            append(current)
            append("\n\n")
        }
        append(message)
    }

    return detail.copy(
        transcriptPreview = nextTranscript,
        lastUpdated = timestamp ?: nowIsoString(),
    )
}

private fun appendAssistantDelta(
    detail: SessionDetail?,
    event: SessionStreamEvent.AssistantDelta,
    currentAssistantTurnId: String?,
): AssistantDeltaRenderResult {
    val base = detail ?: SessionDetail(
        id = event.sessionId,
        title = event.sessionId,
        subtitle = "实时会话",
        lastUpdated = event.timestamp ?: nowIsoString(),
        transcriptPreview = "",
        status = "running",
    )
    val current = base.transcriptPreview.trimEnd()
    val turnId = event.turnId ?: "__unknown__"
    val startsNewAssistantBlock = currentAssistantTurnId != turnId

    val nextTranscript = buildString {
        if (current.isNotBlank()) {
            append(current)
        }
        if (startsNewAssistantBlock) {
            if (current.isNotBlank()) {
                append("\n\n")
            }
            append("Codex：")
        }
        append(event.text)
    }

    return AssistantDeltaRenderResult(
        detail = base.copy(
            transcriptPreview = nextTranscript,
            status = "running",
            lastUpdated = event.timestamp ?: nowIsoString(),
        ),
        activeTurnId = turnId,
    )
}

private data class AssistantDeltaRenderResult(
    val detail: SessionDetail,
    val activeTurnId: String,
)

private fun statusEventText(status: String): String {
    return when (status) {
        "running" -> "Codex 正在处理当前输入。"
        "awaiting_approval" -> "当前步骤等待 bridge 侧审批。"
        "error" -> "当前运行状态为出错。"
        else -> "当前轮次处于空闲状态。"
    }
}

private fun connectedMessage(connectionState: BridgeConnectionState): String {
    val connectedState = connectionState as? BridgeConnectionState.Connected ?: return "已连接。"
    return "已通过 ${connectedState.transport} 连接桥接服务。"
}

private fun defaultSettingsItems(
    connectionState: BridgeConnectionState = BridgeConnectionState.Disconnected,
    endpointInput: String = "",
    authTokenInput: String = "",
    cwdInput: String = "",
    modelInput: String = "gpt-5.5",
    approvalModeInput: String = "manual",
): List<Pair<String, String>> {
    val connectedState = connectionState as? BridgeConnectionState.Connected
    return listOf(
        "桥接模式" to "真实桥接",
        "桥接地址" to (connectedState?.endpoint ?: endpointInput.ifBlank { "未配置" }),
        "鉴权令牌" to if (authTokenInput.isBlank()) "未配置" else "已配置",
        "默认工作目录" to cwdInput.ifBlank { "未配置" },
        "默认模型" to modelInput.ifBlank { "未配置" },
        "审批模式" to localizedApprovalMode(approvalModeInput),
        "运行器" to (connectedState?.runnerMode ?: "未连接"),
        "遥测" to "已关闭",
    )
}

private fun localizedApprovalMode(mode: String): String {
    return when (mode) {
        "auto" -> "自动"
        else -> "手动"
    }
}

private fun localizedSessionStatus(status: String): String {
    return when (status) {
        "running" -> "进行中"
        "awaiting_approval" -> "等待批准"
        "error" -> "出错"
        else -> "空闲"
    }
}

private fun buildApprovalResultText(result: ApprovalActionResult): String {
    val method = result.method ?: "未知方法"
    return "审批结果：${result.decision.label}（$method）"
}

private fun nowIsoString(): String = Instant.now().toString()

class AppViewModelFactory(
    private val bridgeApi: BridgeApi,
    private val sessionRepository: SessionRepository,
    private val settingsStore: AppSettingsStore,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppViewModel(bridgeApi, sessionRepository, settingsStore) as T
        }
        error("Unknown ViewModel class: ${modelClass.name}")
    }
}
