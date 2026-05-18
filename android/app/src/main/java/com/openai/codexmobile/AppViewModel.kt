package com.openai.codexmobile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
    val endpointInput: String = "http://192.168.31.66:8787",
    val authTokenInput: String = "",
    val connectionState: BridgeConnectionState = BridgeConnectionState.Disconnected,
    val sessions: List<SessionSummary> = emptyList(),
    val selectedSession: SessionDetail? = null,
    val draftMessage: String = "",
    val isLoading: Boolean = false,
    val message: String? = null,
    val settingsItems: List<Pair<String, String>> = defaultSettingsItems(),
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private var sessionStreamJob: Job? = null
    private var activeStreamSessionId: String? = null
    private var activeAssistantTurnId: String? = null

    init {
        refreshConnection()
    }

    fun updateEndpointInput(value: String) {
        _uiState.update { it.copy(endpointInput = value) }
    }

    fun updateAuthTokenInput(value: String) {
        bridgeApi.updateAuthToken(value)
        _uiState.update {
            it.copy(
                authTokenInput = value,
                settingsItems = defaultSettingsItems(it.connectionState, value),
            )
        }
    }

    fun updateDraftMessage(value: String) {
        _uiState.update { it.copy(draftMessage = value) }
    }

    fun connect() {
        viewModelScope.launch {
            stopSessionStream()
            _uiState.update { it.copy(isLoading = true, message = null, selectedSession = null) }
            try {
                val connectionState = bridgeApi.connect(uiState.value.endpointInput)
                val sessions = sessionRepository.listSessions()
                val selectedSession = sessions.firstOrNull()?.let { sessionRepository.getSessionDetail(it.id) }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        connectionState = connectionState,
                        sessions = sessions,
                        selectedSession = selectedSession,
                        message = connectedMessage(connectionState),
                        settingsItems = defaultSettingsItems(connectionState, it.authTokenInput),
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        connectionState = BridgeConnectionState.Disconnected,
                        sessions = emptyList(),
                        selectedSession = null,
                        message = error.message ?: "连接桥接服务失败。",
                        settingsItems = defaultSettingsItems(authTokenInput = it.authTokenInput),
                    )
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
                    settingsItems = defaultSettingsItems(authTokenInput = it.authTokenInput),
                )
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

            try {
                val detail = sessionRepository.getSessionDetail(sessionId)
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        selectedSession = detail ?: state.selectedSession,
                        message = if (detail == null) "未找到会话：$sessionId" else state.message,
                        sessionRealtimeState = state.sessionRealtimeState.copy(
                            statusText = detail?.status?.let(::localizedSessionStatus)
                                ?: state.sessionRealtimeState.statusText,
                            fallbackNotice = if (detail == null) "当前只显示上次可用快照。" else null,
                        ),
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = error.message ?: "加载会话失败。",
                    )
                }
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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            try {
                val created = bridgeApi.createSession(
                    CreateSessionRequest(
                        cwd = "D:\\workspace\\codex-mobile",
                        model = "gpt-5.5",
                        approvalMode = "manual",
                    ),
                )
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
                it.copy(
                    connectionState = connectionState,
                    settingsItems = defaultSettingsItems(connectionState, it.authTokenInput),
                )
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
                            isActive = true,
                            isConnected = true,
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
                            pendingApproval = null,
                        ),
                        message = event.errorMessage?.let { message -> "运行出错：$message" } ?: it.message,
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
                            pendingApproval = if (event.status == "awaiting_approval") {
                                it.sessionRealtimeState.pendingApproval
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
                        selectedSession = it.selectedSession?.copy(
                            status = "idle",
                            lastUpdated = event.timestamp ?: it.selectedSession.lastUpdated,
                        ),
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
                        selectedSession = it.selectedSession?.copy(status = "awaiting_approval"),
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
                        selectedSession = it.selectedSession?.copy(
                            status = event.status ?: it.selectedSession.status,
                            lastUpdated = event.timestamp ?: it.selectedSession.lastUpdated,
                        ),
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
                        selectedSession = it.selectedSession?.copy(status = "error"),
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

    override fun onCleared() {
        stopSessionStream()
        super.onCleared()
    }

    private fun applyApprovalResult(result: ApprovalActionResult) {
        _uiState.update {
            it.copy(
                isLoading = false,
                selectedSession = it.selectedSession?.copy(
                    status = result.status,
                    lastUpdated = nowIsoString(),
                ),
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
    return when (connectedState.provider) {
        "fake-fallback" -> "真实桥接不可用，已切换到本地模拟数据。"
        else -> "已通过 ${connectedState.transport} 连接桥接服务。"
    }
}

private fun defaultSettingsItems(
    connectionState: BridgeConnectionState = BridgeConnectionState.Disconnected,
    authTokenInput: String = "",
): List<Pair<String, String>> {
    val connectedState = connectionState as? BridgeConnectionState.Connected
    return listOf(
        "桥接模式" to when (connectedState?.provider) {
            "fake-fallback" -> "本地模拟"
            else -> "真实桥接"
        },
        "桥接地址" to (connectedState?.endpoint ?: "http://192.168.31.66:8787"),
        "鉴权令牌" to if (authTokenInput.isBlank()) "未配置" else "已配置",
        "运行器" to (connectedState?.runnerMode ?: "未知"),
        "遥测" to "已关闭",
    )
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
    return "已提交${result.decision.label}：$method"
}

private fun nowIsoString(): String = Instant.now().toString()

class AppViewModelFactory(
    private val bridgeApi: BridgeApi,
    private val sessionRepository: SessionRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppViewModel(bridgeApi, sessionRepository) as T
        }
        error("Unknown ViewModel class: ${modelClass.name}")
    }
}
