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
import com.openai.codexmobile.data.SendInputRequest
import com.openai.codexmobile.data.SessionConfigUpdate
import com.openai.codexmobile.data.SessionInputAttachmentRef
import com.openai.codexmobile.data.SessionRepository
import com.openai.codexmobile.data.SessionStreamEvent
import com.openai.codexmobile.data.UploadImageAttachmentRequest
import com.openai.codexmobile.diagnostics.AppLogger
import com.openai.codexmobile.model.BridgeConnectionState
import com.openai.codexmobile.model.SessionDetail
import com.openai.codexmobile.model.SessionSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.ArrayDeque

private const val DraftSessionId = "__draft__"
private const val SessionStreamReconnectBaseDelayMs = 1_500L
private const val SessionStreamReconnectMaxDelayMs = 15_000L
private const val SessionStreamReconnectMaxAttempts = 5

data class AppUiState(
    val endpointInput: String,
    val authTokenInput: String,
    val cwdInput: String,
    val modelInput: String,
    val approvalModeInput: String,
    val reasoningEffortInput: String,
    val serviceTierInput: String,
    val sandboxModeInput: String,
    val connectionState: BridgeConnectionState = BridgeConnectionState.Disconnected,
    val sessions: List<SessionSummary> = emptyList(),
    val selectedSession: SessionDetail? = null,
    val selectedDraftSession: DraftSessionUiState? = null,
    val draftMessage: String = "",
    val isLoading: Boolean = false,
    val message: String? = null,
    val settingsItems: List<Pair<String, String>>,
    val sessionRealtimeState: SessionRealtimeUiState = SessionRealtimeUiState(),
    val pendingImageAttachment: PendingImageAttachmentUiState? = null,
    val queuedInputs: List<String> = emptyList(),
    val diagnosticsLog: String = "暂无日志。",
) {
    val isDraftSelected: Boolean
        get() = selectedDraftSession != null
}

data class DraftSessionUiState(
    val localId: String = DraftSessionId,
    val cwd: String,
    val model: String,
    val approvalMode: String,
    val reasoningEffort: String,
    val serviceTier: String,
    val sandboxMode: String,
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

data class PendingImageAttachmentUiState(
    val displayName: String,
    val mimeType: String,
    val contentBase64: String,
)

class AppViewModel(
    private val bridgeApi: BridgeApi,
    private val sessionRepository: SessionRepository,
    private val settingsStore: AppSettingsStore,
    private val appLogger: AppLogger,
) : ViewModel() {

    private val initialSettings = settingsStore.load().sanitize()
    private val _uiState = MutableStateFlow(createInitialUiState(initialSettings))
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private var sessionStreamJob: Job? = null
    private var activeStreamSessionId: String? = null
    private var activeAssistantTurnId: String? = null
    private var sessionStreamReconnectJob: Job? = null
    private var pendingReconnectSessionId: String? = null
    private var sessionStreamReconnectAttempt: Int = 0
    private var isAppInForeground: Boolean = true
    private val queuedInputsBySession = mutableMapOf<String, ArrayDeque<String>>()
    private val flushingQueuedSessions = mutableSetOf<String>()

    init {
        bridgeApi.updateAuthToken(initialSettings.authToken)
        appLogger.info("AppViewModel", "ViewModel 初始化完成。")
        refreshDiagnosticsLog()
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

    fun updateReasoningEffortInput(value: String) {
        updateSettingsState {
            it.copy(
                reasoningEffortInput = normalizeReasoningEffort(value),
            )
        }
    }

    fun updateServiceTierInput(value: String) {
        updateSettingsState {
            it.copy(
                serviceTierInput = normalizeServiceTier(value),
            )
        }
    }

    fun updateSandboxModeInput(value: String) {
        updateSettingsState {
            it.copy(
                sandboxModeInput = normalizeSandboxMode(value),
            )
        }
    }

    fun refreshDiagnosticsLog() {
        _uiState.update {
            it.copy(diagnosticsLog = appLogger.readRecentLogs())
        }
    }

    fun clearDiagnosticsLog() {
        appLogger.clear()
        refreshDiagnosticsLog()
        _uiState.update {
            it.copy(message = "已清空应用日志。")
        }
    }

    fun updateDraftMessage(value: String) {
        _uiState.update { it.copy(draftMessage = value) }
    }

    fun attachPreparedImage(
        displayName: String,
        mimeType: String,
        contentBase64: String,
    ) {
        if (contentBase64.isBlank()) {
            _uiState.update { it.copy(message = "图片内容为空，无法附加。") }
            return
        }

        _uiState.update {
            it.copy(
                pendingImageAttachment = PendingImageAttachmentUiState(
                    displayName = displayName.ifBlank { "image.jpg" },
                    mimeType = mimeType.ifBlank { "image/jpeg" },
                    contentBase64 = contentBase64,
                ),
                message = "已附加图片：${displayName.ifBlank { "image.jpg" }}",
            )
        }
    }

    fun clearPendingImageAttachment() {
        _uiState.update {
            it.copy(
                pendingImageAttachment = null,
                message = "已移除图片附件。",
            )
        }
    }

    fun connect() {
        val endpoint = uiState.value.endpointInput.trim()
        if (endpoint.isBlank()) {
            appLogger.warn("AppViewModel", "用户尝试连接，但桥接地址为空。")
            _uiState.update { it.copy(message = "请先填写桥接地址。") }
            return
        }

        viewModelScope.launch {
            stopSessionStream()
            _uiState.update { it.copy(isLoading = true, message = null, selectedSession = null, selectedDraftSession = null) }
            try {
                appLogger.info("AppViewModel", "开始连接桥接服务：$endpoint")
                val connectionState = bridgeApi.connect(endpoint)
                val sessions = sessionRepository.listSessions()
                val selectedSession = sessions.firstOrNull()?.let { sessionRepository.getSessionDetail(it.id) }
                appLogger.info(
                    "AppViewModel",
                    "桥接连接成功，会话数=${sessions.size}${selectedSession?.id?.let { ", 默认会话=$it" } ?: ""}",
                )
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        connectionState = connectionState,
                        sessions = sessions,
                        selectedSession = selectedSession,
                        selectedDraftSession = null,
                        message = connectedMessage(connectionState),
                    ).withSettingsItems()
                }
                refreshDiagnosticsLog()
            } catch (error: Exception) {
                appLogger.error("AppViewModel", "连接桥接服务失败：$endpoint", error)
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        connectionState = BridgeConnectionState.Disconnected,
                        sessions = emptyList(),
                        selectedSession = null,
                        selectedDraftSession = null,
                        message = error.message ?: "连接桥接服务失败。",
                    ).withSettingsItems()
                }
                refreshDiagnosticsLog()
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            appLogger.info("AppViewModel", "用户主动断开桥接连接。")
            stopSessionStream()
            bridgeApi.disconnect()
            queuedInputsBySession.clear()
            flushingQueuedSessions.clear()
            _uiState.update {
                it.copy(
                    connectionState = BridgeConnectionState.Disconnected,
                    sessions = emptyList(),
                    selectedSession = null,
                    selectedDraftSession = null,
                    pendingImageAttachment = null,
                    message = "已断开连接。",
                    queuedInputs = emptyList(),
                ).withSettingsItems()
            }
            refreshDiagnosticsLog()
        }
    }

    fun onAppForegrounded() {
        isAppInForeground = true
        val selectedSessionId = uiState.value.selectedSession?.id ?: return
        if (!uiState.value.sessionRealtimeState.isActive) {
            return
        }
        if (
            activeStreamSessionId == selectedSessionId &&
            sessionStreamJob?.isActive == true &&
            uiState.value.sessionRealtimeState.isConnected
        ) {
            return
        }

        appLogger.info("AppViewModel", "应用回到前台，准备检查实时流：sessionId=$selectedSessionId")
        sessionStreamReconnectAttempt = 0
        scheduleSessionStreamReconnect(
            sessionId = selectedSessionId,
            immediate = true,
            reason = "应用回到前台，重新建立实时流。",
        )
    }

    fun onAppBackgrounded() {
        isAppInForeground = false
        cancelPendingSessionStreamReconnect()
    }

    fun startDraftSession(cwd: String? = null) {
        stopSessionStream()
        val draft = buildDraftSession(
            cwd = cwd?.trim().takeUnless { it.isNullOrBlank() } ?: uiState.value.cwdInput.trim(),
            model = uiState.value.modelInput.trim(),
            approvalMode = uiState.value.approvalModeInput,
            reasoningEffort = uiState.value.reasoningEffortInput,
            serviceTier = uiState.value.serviceTierInput,
            sandboxMode = uiState.value.sandboxModeInput,
        )
        appLogger.info(
            "AppViewModel",
            "创建本地草稿线程：cwd=${draft.cwd.ifBlank { "<empty>" }}, model=${draft.model}",
        )
        _uiState.update {
            it.copy(
                selectedSession = null,
                selectedDraftSession = draft,
                draftMessage = "",
                queuedInputs = emptyList(),
                message = "先输入第一句话，发送时才会真正创建会话。",
                sessionRealtimeState = SessionRealtimeUiState(
                    isActive = false,
                    isConnected = false,
                    connectionText = "尚未创建远端会话",
                    statusText = "草稿",
                    lastEventText = "当前只是本地草稿，还没有占用 bridge 会话。",
                ),
            )
        }
    }

    fun beginDraftSession(cwd: String? = null) = startDraftSession(cwd)

    fun discardDraftSession() {
        stopSessionStream()
        _uiState.update {
            it.copy(
                selectedDraftSession = null,
                draftMessage = "",
                pendingImageAttachment = null,
                queuedInputs = emptyList(),
                sessionRealtimeState = SessionRealtimeUiState(),
            )
        }
    }

    fun openSessionDetail(sessionId: String) {
        if (sessionId.isBlank() || sessionId == DraftSessionId) {
            return
        }
        if (activeStreamSessionId == sessionId && sessionStreamJob?.isActive == true) {
            return
        }

        viewModelScope.launch {
            appLogger.info("AppViewModel", "打开会话详情：sessionId=$sessionId")
            stopSessionStream(resetRealtimeState = false)
            _uiState.update {
                it.copy(
                    isLoading = true,
                    message = null,
                    selectedSession = it.selectedSession?.takeIf { detail -> detail.id == sessionId },
                    selectedDraftSession = null,
                    queuedInputs = queuedInputsFor(sessionId),
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
                appLogger.error("AppViewModel", "加载会话详情失败：sessionId=$sessionId", error)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        selectedSession = null,
                        selectedDraftSession = null,
                        message = error.message ?: "加载会话失败。",
                        queuedInputs = emptyList(),
                        sessionRealtimeState = it.sessionRealtimeState.copy(
                            isConnected = false,
                            connectionText = "会话加载失败",
                            statusText = "无法打开会话",
                            fallbackNotice = "请检查 bridge 连接和会话状态。",
                        ),
                    )
                }
                refreshDiagnosticsLog()
                return@launch
            }

            if (detail == null) {
                appLogger.warn("AppViewModel", "找不到会话详情：sessionId=$sessionId")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        selectedSession = null,
                        selectedDraftSession = null,
                        message = "未找到会话：$sessionId",
                        queuedInputs = emptyList(),
                        sessionRealtimeState = it.sessionRealtimeState.copy(
                            isConnected = false,
                            connectionText = "找不到目标会话",
                            statusText = "会话不存在",
                            fallbackNotice = "这条会话可能已经结束或不再可访问。",
                        ),
                    )
                }
                refreshDiagnosticsLog()
                return@launch
            }

            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    selectedSession = mergeSessionDetail(state.selectedSession, detail),
                    queuedInputs = queuedInputsFor(sessionId),
                    sessionRealtimeState = state.sessionRealtimeState.copy(
                        statusText = localizedSessionStatus(detail.status),
                        fallbackNotice = null,
                    ),
                )
            }

            startSessionStream(sessionId)
            flushNextQueuedInputIfIdle(sessionId)
            refreshDiagnosticsLog()
        }
    }

    fun closeSessionDetail(sessionId: String? = null) {
        if (sessionId == null || sessionId == activeStreamSessionId) {
            stopSessionStream()
        }
        if (sessionId == null || uiState.value.selectedSession?.id == sessionId || sessionId == DraftSessionId) {
            _uiState.update { it.copy(queuedInputs = emptyList()) }
        }
    }

    fun sendInput() {
        val detail = uiState.value.selectedSession
        val draftSession = uiState.value.selectedDraftSession
        val text = uiState.value.draftMessage.trim()
        val pendingImageAttachment = uiState.value.pendingImageAttachment
        if (text.isEmpty() && pendingImageAttachment == null) {
            return
        }

        if (detail == null && draftSession == null) {
            startDraftSession()
            return
        }

        if (draftSession != null) {
            if (draftSession.cwd.trim().isBlank()) {
                appLogger.warn("AppViewModel", "草稿线程发送失败，工作目录为空。")
                _uiState.update { it.copy(message = "请先为草稿线程填写工作目录。") }
                return
            }
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, message = null) }
                try {
                    appLogger.info(
                        "AppViewModel",
                        "草稿线程转正式会话：cwd=${draftSession.cwd}, firstMessageLength=${text.length}",
                    )
                    val created = bridgeApi.createSession(buildCreateSessionRequest(draftSession))
                    _uiState.update {
                        it.copy(
                            selectedDraftSession = null,
                            selectedSession = created,
                            message = "已创建会话，正在发送首条消息。",
                        )
                    }
                    refreshSessions()
                    submitInputNow(
                        detail = created,
                        text = text,
                        pendingImageAttachment = pendingImageAttachment,
                        fromQueue = false,
                    )
                } catch (error: Exception) {
                    appLogger.error("AppViewModel", "创建草稿对应远端会话失败。", error)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = error.message ?: "创建会话失败。",
                        )
                    }
                    refreshDiagnosticsLog()
                }
            }
            return
        }

        if (detail != null && shouldQueueInput(detail, uiState.value.sessionRealtimeState)) {
            if (pendingImageAttachment != null) {
                appLogger.warn("AppViewModel", "当前轮未结束，暂不支持带图片的排队发送：sessionId=${detail.id}")
                _uiState.update {
                    it.copy(message = "当前轮尚未结束，暂不支持把图片加入排队。")
                }
                return
            }
            appLogger.info(
                "AppViewModel",
                "当前轮未结束，消息进入排队：sessionId=${detail.id}, textLength=${text.length}",
            )
            enqueueQueuedInput(detail.id, text)
            _uiState.update {
                it.copy(
                    draftMessage = "",
                    message = "当前轮尚未结束，消息已加入排队。",
                    queuedInputs = queuedInputsFor(detail.id),
                )
            }
            return
        }

        val activeDetail = detail ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            try {
                appLogger.info(
                    "AppViewModel",
                    "发送消息：sessionId=${activeDetail.id}, textLength=${text.length}, hasImage=${pendingImageAttachment != null}",
                )
                submitInputNow(
                    detail = activeDetail,
                    text = text,
                    pendingImageAttachment = pendingImageAttachment,
                    fromQueue = false,
                )
            } catch (error: Exception) {
                appLogger.error("AppViewModel", "发送消息失败：sessionId=${activeDetail.id}", error)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = error.message ?: "发送消息失败。",
                    )
                }
                refreshDiagnosticsLog()
            }
        }
    }

    fun submitApproval(decision: ApprovalDecision) {
        val detail = uiState.value.selectedSession ?: return
        val approval = uiState.value.sessionRealtimeState.pendingApproval ?: return

        viewModelScope.launch {
            appLogger.info(
                "AppViewModel",
                "提交审批决定：sessionId=${detail.id}, decision=${decision.wireValue}, requestId=${approval.requestId ?: "none"}",
            )
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
                appLogger.error("AppViewModel", "提交审批失败：sessionId=${detail.id}", error)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = error.message ?: "提交审批操作失败。",
                        sessionRealtimeState = it.sessionRealtimeState.copy(
                            pendingApproval = approval.copy(isSubmitting = false),
                        ),
                    )
                }
                refreshDiagnosticsLog()
            }
        }
    }

    fun updateSelectedSessionModel(value: String) {
        applySessionConfigUpdate(
            model = value.trim().ifBlank { "gpt-5.5" },
            approvalMode = null,
            reasoningEffort = null,
            serviceTier = null,
            sandboxMode = null,
            cwd = null,
        )
    }

    fun updateSelectedSessionReasoningEffort(value: String) {
        applySessionConfigUpdate(
            model = null,
            approvalMode = null,
            reasoningEffort = normalizeReasoningEffort(value),
            serviceTier = null,
            sandboxMode = null,
            cwd = null,
        )
    }

    fun updateSelectedSessionServiceTier(value: String) {
        applySessionConfigUpdate(
            model = null,
            approvalMode = null,
            reasoningEffort = null,
            serviceTier = normalizeServiceTier(value),
            sandboxMode = null,
            cwd = null,
        )
    }

    fun updateSelectedSessionSandboxMode(value: String) {
        applySessionConfigUpdate(
            model = null,
            approvalMode = null,
            reasoningEffort = null,
            serviceTier = null,
            sandboxMode = normalizeSandboxMode(value),
            cwd = null,
        )
    }

    fun updateSelectedSessionCwd(value: String) {
        val normalized = value.trim()
        if (normalized.isBlank()) {
            _uiState.update { it.copy(message = "工作目录不能为空。") }
            return
        }
        applySessionConfigUpdate(
            model = null,
            approvalMode = null,
            reasoningEffort = null,
            serviceTier = null,
            sandboxMode = null,
            cwd = normalized,
        )
    }

    fun refreshSelectedSession() {
        val sessionId = uiState.value.selectedSession?.id ?: return
        viewModelScope.launch {
            refreshSessionSnapshot(sessionId)
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

    private fun applySessionConfigUpdate(
        model: String?,
        approvalMode: String?,
        reasoningEffort: String?,
        serviceTier: String?,
        sandboxMode: String?,
        cwd: String?,
    ) {
        val draft = uiState.value.selectedDraftSession
        if (draft != null) {
            val nextDraft = draft.copy(
                cwd = cwd ?: draft.cwd,
                model = model ?: draft.model,
                approvalMode = approvalMode ?: draft.approvalMode,
                reasoningEffort = reasoningEffort ?: draft.reasoningEffort,
                serviceTier = serviceTier ?: draft.serviceTier,
                sandboxMode = sandboxMode ?: draft.sandboxMode,
            )
            _uiState.update {
                it.copy(
                    selectedDraftSession = nextDraft,
                    cwdInput = nextDraft.cwd,
                    modelInput = nextDraft.model,
                    approvalModeInput = nextDraft.approvalMode,
                    reasoningEffortInput = nextDraft.reasoningEffort,
                    serviceTierInput = nextDraft.serviceTier,
                    sandboxModeInput = nextDraft.sandboxMode,
                ).withSettingsItems()
            }
            persistSettings(_uiState.value)
            return
        }

        val detail = uiState.value.selectedSession ?: return
        val nextDetail = detail.copy(
            cwd = cwd ?: detail.cwd,
            model = model ?: detail.model,
            approvalMode = approvalMode ?: detail.approvalMode,
            reasoningEffort = reasoningEffort ?: detail.reasoningEffort,
            serviceTier = serviceTier ?: detail.serviceTier,
            sandboxMode = sandboxMode ?: detail.sandboxMode,
            subtitle = buildSessionSubtitle(
                model = model ?: detail.model,
                approvalMode = approvalMode ?: detail.approvalMode,
                status = detail.status,
            ),
        )
        _uiState.update {
            it.copy(
                selectedSession = nextDetail,
                cwdInput = nextDetail.cwd,
                modelInput = nextDetail.model,
                approvalModeInput = nextDetail.approvalMode,
                reasoningEffortInput = nextDetail.reasoningEffort,
                serviceTierInput = nextDetail.serviceTier,
                sandboxModeInput = nextDetail.sandboxMode,
            ).withSettingsItems()
        }
        persistSettings(_uiState.value)

        viewModelScope.launch {
            try {
                appLogger.info(
                    "AppViewModel",
                    "更新会话配置：sessionId=${detail.id}, model=${model ?: detail.model}, cwd=${cwd ?: detail.cwd}",
                )
                val updated = bridgeApi.updateSessionConfig(
                    detail.id,
                    SessionConfigUpdate(
                        cwd = cwd,
                        model = model,
                        approvalMode = approvalMode,
                        reasoningEffort = reasoningEffort,
                        serviceTier = serviceTier,
                        sandboxMode = sandboxMode,
                    ),
                )
                _uiState.update {
                    if (it.selectedSession?.id == detail.id) {
                        it.copy(
                            selectedSession = mergeSessionDetail(it.selectedSession, updated),
                            message = "已更新会话配置。",
                        )
                    } else {
                        it
                    }
                }
                refreshSessions()
                refreshDiagnosticsLog()
            } catch (error: Exception) {
                appLogger.error("AppViewModel", "更新会话配置失败：sessionId=${detail.id}", error)
                _uiState.update {
                    it.copy(message = error.message ?: "更新会话配置失败。")
                }
                refreshSessionSnapshot(detail.id)
                refreshDiagnosticsLog()
            }
        }
    }

    private fun startSessionStream(sessionId: String) {
        appLogger.info("AppViewModel", "开始监听实时流：sessionId=$sessionId")
        cancelPendingSessionStreamReconnect()
        stopSessionStream(
            resetRealtimeState = false,
            resetReconnectState = false,
        )
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
                    appLogger.error("AppViewModel", "实时流监听失败：sessionId=$sessionId", error)
                    val reconnectScheduled = scheduleSessionStreamReconnect(
                        sessionId = sessionId,
                        reason = error.message ?: "实时流连接失败。",
                    )
                    _uiState.update {
                        it.copy(
                            sessionRealtimeState = it.sessionRealtimeState.copy(
                                isActive = true,
                                isConnected = false,
                                connectionText = if (reconnectScheduled) {
                                    "实时流连接失败，准备重连"
                                } else {
                                    "实时流连接失败"
                                },
                                lastEventText = if (reconnectScheduled) {
                                    "连接已中断，正在尝试重新建立实时流。"
                                } else {
                                    "无法继续接收实时事件。"
                                },
                                fallbackNotice = buildStreamReconnectNotice(
                                    reconnectScheduled = reconnectScheduled,
                                    reason = error.message ?: "当前回退到 HTTP 快照。",
                                ),
                            ),
                            message = if (reconnectScheduled) null else error.message ?: "实时流连接失败。",
                        )
                    }
                    refreshSessionSnapshot(sessionId)
                    refreshDiagnosticsLog()
                }
            }
        }
    }

    private fun stopSessionStream(
        resetRealtimeState: Boolean = true,
        resetReconnectState: Boolean = true,
    ) {
        activeStreamSessionId?.let { appLogger.info("AppViewModel", "停止实时流监听：sessionId=$it") }
        sessionStreamJob?.cancel()
        sessionStreamJob = null
        activeStreamSessionId = null
        activeAssistantTurnId = null
        cancelPendingSessionStreamReconnect(resetAttempt = resetReconnectState)
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
                appLogger.info("AppViewModel", "实时流已连接：sessionId=$sessionId")
                resetSessionStreamReconnectState()
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
                appLogger.warn(
                    "AppViewModel",
                    "实时流关闭：sessionId=$sessionId, reason=${event.reason ?: "none"}",
                )
                val reconnectScheduled = scheduleSessionStreamReconnect(
                    sessionId = sessionId,
                    reason = event.reason ?: "实时流连接已关闭。",
                )
                _uiState.update {
                    it.copy(
                        sessionRealtimeState = it.sessionRealtimeState.copy(
                            isActive = true,
                            isConnected = false,
                            connectionText = if (reconnectScheduled) {
                                "实时流已断开，准备重连"
                            } else {
                                "实时流已断开"
                            },
                            lastEventText = event.reason ?: "实时流连接已关闭。",
                            fallbackNotice = buildStreamReconnectNotice(
                                reconnectScheduled = reconnectScheduled,
                                reason = "当前停留在最后一次收到的内容快照。",
                            ),
                            pendingApproval = null,
                        ),
                    )
                }
            }

            is SessionStreamEvent.SessionStarted -> {
                appLogger.info(
                    "AppViewModel",
                    "会话实时流就绪：sessionId=$sessionId, status=${event.status}, threadId=${event.threadId ?: "none"}",
                )
                resetSessionStreamReconnectState()
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
                appLogger.info(
                    "AppViewModel",
                    "助手回复结束：sessionId=$sessionId, turnStatus=${event.turnStatus ?: "done"}",
                )
                activeAssistantTurnId = null
                val nextStatus = if (event.turnStatus == "failed") "error" else "idle"
                _uiState.update {
                    it.copy(
                        selectedSession = it.selectedSession?.copy(
                            status = nextStatus,
                            subtitle = buildSessionSubtitle(
                                model = it.selectedSession.model,
                                approvalMode = it.selectedSession.approvalMode,
                                status = nextStatus,
                            ),
                            lastUpdated = event.timestamp ?: it.selectedSession.lastUpdated,
                        ),
                        queuedInputs = queuedInputsFor(sessionId),
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
                appLogger.debug("AppViewModel", "运行状态变化：sessionId=$sessionId, status=${event.status}")
                _uiState.update {
                    it.copy(
                        selectedSession = it.selectedSession?.copy(
                            status = event.status,
                            subtitle = buildSessionSubtitle(
                                model = it.selectedSession.model,
                                approvalMode = it.selectedSession.approvalMode,
                                status = event.status,
                            ),
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
                if (event.status == "idle") {
                    flushNextQueuedInputIfIdle(sessionId)
                }
            }

            is SessionStreamEvent.RunInterrupted -> {
                appLogger.warn("AppViewModel", "任务被中断：sessionId=$sessionId")
                activeAssistantTurnId = null
                _uiState.update {
                    it.copy(
                        selectedSession = it.selectedSession
                            ?.let { detail -> appendSystemMessage(detail, "当前任务已中断。", event.timestamp) }
                            ?.copy(
                                status = "idle",
                                subtitle = buildSessionSubtitle(
                                    model = it.selectedSession.model,
                                    approvalMode = it.selectedSession.approvalMode,
                                    status = "idle",
                                ),
                            ),
                        queuedInputs = queuedInputsFor(sessionId),
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
                flushNextQueuedInputIfIdle(sessionId)
            }

            is SessionStreamEvent.ToolRequest -> {
                appLogger.info(
                    "AppViewModel",
                    "收到审批请求：sessionId=$sessionId, method=${event.method ?: "unknown"}",
                )
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
                            ?.copy(
                                status = "awaiting_approval",
                                subtitle = buildSessionSubtitle(
                                    model = it.selectedSession.model,
                                    approvalMode = it.selectedSession.approvalMode,
                                    status = "awaiting_approval",
                                ),
                            ),
                        queuedInputs = queuedInputsFor(sessionId),
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
                appLogger.info(
                    "AppViewModel",
                    "收到审批结果：sessionId=$sessionId, method=${event.method ?: "unknown"}, status=${event.status ?: "unknown"}",
                )
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
                            ?.copy(
                                status = event.status ?: it.selectedSession.status,
                                subtitle = buildSessionSubtitle(
                                    model = it.selectedSession.model,
                                    approvalMode = it.selectedSession.approvalMode,
                                    status = event.status ?: it.selectedSession.status,
                                ),
                            ),
                        queuedInputs = queuedInputsFor(sessionId),
                        sessionRealtimeState = it.sessionRealtimeState.copy(
                            lastEventText = event.summary ?: "收到工具结果事件。",
                            fallbackNotice = null,
                            pendingApproval = null,
                            statusText = event.status?.let(::localizedSessionStatus)
                                ?: it.sessionRealtimeState.statusText,
                        ),
                    )
                }
                if (event.status == "idle") {
                    flushNextQueuedInputIfIdle(sessionId)
                }
            }

            is SessionStreamEvent.Error -> {
                appLogger.error("AppViewModel", "实时流错误事件：sessionId=$sessionId, message=${event.message}")
                activeAssistantTurnId = null
                _uiState.update {
                    it.copy(
                        selectedSession = it.selectedSession
                            ?.let { detail -> appendSystemMessage(detail, "系统：${event.message}", event.timestamp) }
                            ?.copy(
                                status = "error",
                                subtitle = buildSessionSubtitle(
                                    model = it.selectedSession.model,
                                    approvalMode = it.selectedSession.approvalMode,
                                    status = "error",
                                ),
                            ),
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
        } catch (error: Exception) {
            appLogger.error("AppViewModel", "刷新会话快照失败：sessionId=$sessionId", error)
            null
        }

        if (detail != null && uiState.value.selectedSession?.id == sessionId) {
            _uiState.update {
                it.copy(
                    selectedSession = mergeSessionDetail(it.selectedSession, detail),
                    queuedInputs = queuedInputsFor(sessionId),
                )
            }
            if (detail.status == "idle") {
                flushNextQueuedInputIfIdle(sessionId)
            }
        }

        refreshSessions()
    }

    private suspend fun refreshSessions() {
        try {
            val sessions = sessionRepository.listSessions()
            _uiState.update { it.copy(sessions = sessions) }
        } catch (error: Exception) {
            appLogger.error("AppViewModel", "刷新会话列表失败。", error)
            // Keep the latest visible list if list refresh fails.
        }
    }

    private fun scheduleSessionStreamReconnect(
        sessionId: String,
        reason: String,
        immediate: Boolean = false,
    ): Boolean {
        if (!canReconnectSessionStream(sessionId)) {
            return false
        }
        if (pendingReconnectSessionId == sessionId && sessionStreamReconnectJob?.isActive == true) {
            return true
        }

        val nextAttempt = if (immediate) {
            1
        } else {
            sessionStreamReconnectAttempt + 1
        }
        if (nextAttempt > SessionStreamReconnectMaxAttempts) {
            appLogger.warn(
                "AppViewModel",
                "实时流重连次数已达上限：sessionId=$sessionId, reason=$reason",
            )
            return false
        }

        val delayMs = if (immediate) {
            0L
        } else {
            minOf(
                SessionStreamReconnectBaseDelayMs * (1L shl (nextAttempt - 1)),
                SessionStreamReconnectMaxDelayMs,
            )
        }

        sessionStreamReconnectAttempt = nextAttempt
        pendingReconnectSessionId = sessionId
        sessionStreamReconnectJob = viewModelScope.launch {
            if (delayMs > 0) {
                delay(delayMs)
            }
            if (!canReconnectSessionStream(sessionId)) {
                pendingReconnectSessionId = null
                sessionStreamReconnectJob = null
                return@launch
            }

            appLogger.info(
                "AppViewModel",
                "开始重连实时流：sessionId=$sessionId, attempt=$nextAttempt, reason=$reason",
            )
            pendingReconnectSessionId = null
            sessionStreamReconnectJob = null
            startSessionStream(sessionId)
        }
        return true
    }

    private fun canReconnectSessionStream(sessionId: String): Boolean {
        return isAppInForeground &&
            uiState.value.sessionRealtimeState.isActive &&
            uiState.value.selectedSession?.id == sessionId &&
            uiState.value.connectionState is BridgeConnectionState.Connected
    }

    private fun cancelPendingSessionStreamReconnect(resetAttempt: Boolean = false) {
        sessionStreamReconnectJob?.cancel()
        sessionStreamReconnectJob = null
        pendingReconnectSessionId = null
        if (resetAttempt) {
            sessionStreamReconnectAttempt = 0
        }
    }

    private fun resetSessionStreamReconnectState() {
        cancelPendingSessionStreamReconnect(resetAttempt = true)
    }

    private fun buildStreamReconnectNotice(
        reconnectScheduled: Boolean,
        reason: String,
    ): String {
        return if (reconnectScheduled) {
            "将在前台自动重连实时流（第 $sessionStreamReconnectAttempt 次尝试）。$reason"
        } else {
            reason
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
                reasoningEffort = state.reasoningEffortInput,
                serviceTier = state.serviceTierInput,
                sandboxMode = state.sandboxModeInput,
            ),
        )
    }

    private fun buildCreateSessionRequest(detail: SessionDetail): CreateSessionRequest {
        return CreateSessionRequest(
            cwd = detail.cwd.trim(),
            model = detail.model.trim(),
            approvalMode = detail.approvalMode,
            reasoningEffort = detail.reasoningEffort,
            serviceTier = detail.serviceTier,
            sandboxMode = detail.sandboxMode,
        )
    }

    private fun buildCreateSessionRequest(draft: DraftSessionUiState): CreateSessionRequest {
        return CreateSessionRequest(
            cwd = draft.cwd.trim(),
            model = draft.model.trim(),
            approvalMode = draft.approvalMode,
            reasoningEffort = draft.reasoningEffort,
            serviceTier = draft.serviceTier,
            sandboxMode = draft.sandboxMode,
        )
    }

    override fun onCleared() {
        appLogger.info("AppViewModel", "ViewModel 即将销毁。")
        stopSessionStream()
        super.onCleared()
    }

    private fun applyApprovalResult(result: ApprovalActionResult) {
        appLogger.info(
            "AppViewModel",
            "审批已提交：decision=${result.decision.wireValue}, status=${result.status}, requestId=${result.requestId}",
        )
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
                    ?.copy(
                        status = result.status,
                        subtitle = buildSessionSubtitle(
                            model = it.selectedSession.model,
                            approvalMode = it.selectedSession.approvalMode,
                            status = result.status,
                        ),
                    ),
                message = "已提交审批操作：${result.decision.label}",
                queuedInputs = queuedInputsFor(it.selectedSession?.id),
                sessionRealtimeState = it.sessionRealtimeState.copy(
                    statusText = localizedSessionStatus(result.status),
                    lastEventText = buildApprovalResultText(result),
                    fallbackNotice = null,
                    pendingApproval = null,
                ),
            )
        }
        refreshDiagnosticsLog()
    }

    private suspend fun submitInputNow(
        detail: SessionDetail,
        text: String,
        pendingImageAttachment: PendingImageAttachmentUiState?,
        fromQueue: Boolean,
    ) {
        appLogger.info(
            "AppViewModel",
            "提交输入到 bridge：sessionId=${detail.id}, textLength=${text.length}, hasImage=${pendingImageAttachment != null}, fromQueue=$fromQueue",
        )
        val attachmentRefs = if (pendingImageAttachment != null) {
            val uploaded = bridgeApi.uploadImageAttachment(
                UploadImageAttachmentRequest(
                    displayName = pendingImageAttachment.displayName,
                    mimeType = pendingImageAttachment.mimeType,
                    contentBase64 = pendingImageAttachment.contentBase64,
                ),
            )
            listOf(SessionInputAttachmentRef(id = uploaded.id))
        } else {
            emptyList()
        }
        bridgeApi.sendInput(
            detail.id,
            SendInputRequest(
                text = text.takeIf { it.isNotBlank() },
                attachments = attachmentRefs,
            ),
        )
        activeAssistantTurnId = null
        val updatedDetail = appendUserMessage(
            detail = detail,
            message = text,
            imageDisplayName = pendingImageAttachment?.displayName,
        )
        _uiState.update {
            it.copy(
                isLoading = false,
                selectedSession = updatedDetail,
                draftMessage = "",
                pendingImageAttachment = null,
                message = if (fromQueue) {
                    "已发送排队消息。"
                } else if (pendingImageAttachment != null) {
                    "图片已发送，等待 Codex 回复。"
                } else {
                    "消息已发送，等待实时输出。"
                },
                queuedInputs = queuedInputsFor(detail.id),
                sessionRealtimeState = it.sessionRealtimeState.copy(
                    statusText = localizedSessionStatus("running"),
                    lastEventText = if (fromQueue) {
                        "已发送一条排队消息，等待 Codex 回复。"
                    } else if (pendingImageAttachment != null) {
                        "已发送图片附件，等待 Codex 回复。"
                    } else {
                        "已发送消息，等待 Codex 回复。"
                    },
                ),
            )
        }
        refreshSessions()
        if (activeStreamSessionId != detail.id || sessionStreamJob?.isActive != true) {
            startSessionStream(detail.id)
        }
        refreshDiagnosticsLog()
    }

    private fun shouldQueueInput(
        detail: SessionDetail,
        realtimeState: SessionRealtimeUiState,
    ): Boolean {
        if (detail.status == "draft") {
            return false
        }
        return realtimeState.pendingApproval != null ||
            detail.status == "awaiting_approval" ||
            detail.status == "running"
    }

    private fun enqueueQueuedInput(sessionId: String, text: String) {
        val queue = queuedInputsBySession.getOrPut(sessionId) { ArrayDeque() }
        queue.addLast(text)
    }

    private fun queuedInputsFor(sessionId: String?): List<String> {
        if (sessionId == null) {
            return emptyList()
        }
        return queuedInputsBySession[sessionId]?.toList().orEmpty()
    }

    private suspend fun flushNextQueuedInputIfIdle(sessionId: String) {
        if (sessionId in flushingQueuedSessions) {
            return
        }

        val queue = queuedInputsBySession[sessionId]
        if (queue.isNullOrEmpty()) {
            return
        }

        val detail = uiState.value.selectedSession?.takeIf { it.id == sessionId } ?: return
        if (detail.status != "idle") {
            return
        }

        val nextMessage = queue.removeFirst()
        if (queue.isEmpty()) {
            queuedInputsBySession.remove(sessionId)
        }
        flushingQueuedSessions += sessionId

        try {
            appLogger.info("AppViewModel", "开始发送排队消息：sessionId=$sessionId")
            submitInputNow(
                detail = detail,
                text = nextMessage,
                pendingImageAttachment = null,
                fromQueue = true,
            )
        } catch (error: Exception) {
            appLogger.error("AppViewModel", "排队消息发送失败：sessionId=$sessionId", error)
            val restoredQueue = queuedInputsBySession.getOrPut(sessionId) { ArrayDeque() }
            restoredQueue.addFirst(nextMessage)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    message = error.message ?: "排队消息发送失败。",
                    queuedInputs = queuedInputsFor(sessionId),
                )
            }
            refreshDiagnosticsLog()
        } finally {
            flushingQueuedSessions -= sessionId
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
        reasoningEffortInput = settings.reasoningEffort,
        serviceTierInput = settings.serviceTier,
        sandboxModeInput = settings.sandboxMode,
        settingsItems = defaultSettingsItems(
            endpointInput = settings.endpoint,
            authTokenInput = settings.authToken,
            cwdInput = settings.cwd,
            modelInput = settings.model,
            approvalModeInput = settings.approvalMode,
            reasoningEffortInput = settings.reasoningEffort,
            serviceTierInput = settings.serviceTier,
            sandboxModeInput = settings.sandboxMode,
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
            reasoningEffortInput = reasoningEffortInput,
            serviceTierInput = serviceTierInput,
            sandboxModeInput = sandboxModeInput,
        ),
    )
}

private fun AppSettings.sanitize(): AppSettings {
    return copy(
        endpoint = endpoint.trim(),
        cwd = cwd.trim(),
        model = model.trim().ifEmpty { "gpt-5.5" },
        approvalMode = approvalMode.takeIf { it == "manual" || it == "auto" } ?: "manual",
        reasoningEffort = normalizeReasoningEffort(reasoningEffort),
        serviceTier = normalizeServiceTier(serviceTier),
        sandboxMode = normalizeSandboxMode(sandboxMode),
    )
}

private fun buildOrUpdateSessionFromStart(
    current: SessionDetail?,
    event: SessionStreamEvent.SessionStarted,
): SessionDetail {
    val model = event.model ?: current?.model ?: "gpt-5.5"
    val approvalMode = event.approvalMode ?: current?.approvalMode ?: "manual"
    val reasoningEffort = event.reasoningEffort ?: current?.reasoningEffort ?: "medium"
    val serviceTier = event.serviceTier ?: current?.serviceTier ?: "default"
    val sandboxMode = event.sandboxMode ?: current?.sandboxMode ?: "workspace-write"
    val cwd = event.cwd ?: current?.cwd ?: ""

    if (current != null && current.id == event.sessionId) {
        return current.copy(
            cwd = cwd,
            model = model,
            approvalMode = approvalMode,
            reasoningEffort = reasoningEffort,
            serviceTier = serviceTier,
            sandboxMode = sandboxMode,
            status = event.status,
            subtitle = buildSessionSubtitle(
                model = model,
                approvalMode = approvalMode,
                status = event.status,
            ),
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
        subtitle = buildSessionSubtitle(
            model = model,
            approvalMode = approvalMode,
            status = event.status,
        ),
        lastUpdated = event.timestamp ?: nowIsoString(),
        transcriptPreview = transcript,
        cwd = cwd,
        model = model,
        approvalMode = approvalMode,
        reasoningEffort = reasoningEffort,
        serviceTier = serviceTier,
        sandboxMode = sandboxMode,
        status = event.status,
    )
}

private fun buildDraftSession(
    cwd: String,
    model: String,
    approvalMode: String,
    reasoningEffort: String,
    serviceTier: String,
    sandboxMode: String,
): DraftSessionUiState {
    return DraftSessionUiState(
        cwd = cwd,
        model = model.ifBlank { "gpt-5.5" },
        approvalMode = approvalMode,
        reasoningEffort = reasoningEffort,
        serviceTier = serviceTier,
        sandboxMode = sandboxMode,
    )
}

private fun buildSessionSubtitle(
    model: String,
    approvalMode: String,
    status: String,
): String {
    return "$model • ${localizedApprovalMode(approvalMode)} • ${localizedSessionStatus(status)}"
}

private fun mergeSessionDetail(
    current: SessionDetail?,
    incoming: SessionDetail,
): SessionDetail {
    val model = incoming.model.takeUnless { it.isBlank() || it == "未知模型" }
        ?: current?.model
        ?: "gpt-5.5"
    val approvalMode = incoming.approvalMode.takeUnless { it.isBlank() || it.startsWith("未知") }
        ?: current?.approvalMode
        ?: "manual"
    val reasoningEffort = incoming.reasoningEffort.takeUnless { it.isBlank() || it == "unknown" }
        ?: current?.reasoningEffort
        ?: "medium"
    val serviceTier = incoming.serviceTier.takeUnless { it.isBlank() || it == "unknown" }
        ?: current?.serviceTier
        ?: "default"
    val sandboxMode = incoming.sandboxMode.takeUnless { it.isBlank() || it == "unknown" }
        ?: current?.sandboxMode
        ?: "workspace-write"
    val cwd = incoming.cwd.takeUnless { it.isBlank() || it == "未提供工作目录" }
        ?: current?.cwd
        ?: ""
    val status = incoming.status.takeUnless { it.isBlank() || it == "unknown" }
        ?: current?.status
        ?: "idle"

    return incoming.copy(
        cwd = cwd,
        model = model,
        approvalMode = approvalMode,
        reasoningEffort = reasoningEffort,
        serviceTier = serviceTier,
        sandboxMode = sandboxMode,
        status = status,
        subtitle = buildSessionSubtitle(
            model = model,
            approvalMode = approvalMode,
            status = status,
        ),
    )
}

private fun appendUserMessage(
    detail: SessionDetail,
    message: String,
    imageDisplayName: String?,
): SessionDetail {
    val current = detail.transcriptPreview.trimEnd()
    val nextTranscript = buildString {
        if (current.isNotBlank()) {
            append(current)
            append("\n\n")
        }
        append("你：")
        if (message.isNotBlank()) {
            append(message)
        }
        if (!imageDisplayName.isNullOrBlank()) {
            if (message.isNotBlank()) {
                append("\n")
            }
            append("[图片] ")
            append(imageDisplayName)
        }
    }

    return detail.copy(
        transcriptPreview = nextTranscript,
        status = "running",
        subtitle = buildSessionSubtitle(
            model = detail.model,
            approvalMode = detail.approvalMode,
            status = "running",
        ),
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
        subtitle = buildSessionSubtitle(
            model = "gpt-5.5",
            approvalMode = "manual",
            status = "running",
        ),
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
            subtitle = buildSessionSubtitle(
                model = base.model,
                approvalMode = base.approvalMode,
                status = "running",
            ),
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
    reasoningEffortInput: String = "medium",
    serviceTierInput: String = "default",
    sandboxModeInput: String = "workspace-write",
): List<Pair<String, String>> {
    val connectedState = connectionState as? BridgeConnectionState.Connected
    return listOf(
        "桥接模式" to "真实桥接",
        "桥接地址" to (connectedState?.endpoint ?: endpointInput.ifBlank { "未配置" }),
        "鉴权令牌" to if (authTokenInput.isBlank()) "未配置" else "已配置",
        "默认工作目录" to cwdInput.ifBlank { "未配置" },
        "默认模型" to modelInput.ifBlank { "未配置" },
        "推理强度" to localizedReasoningEffort(reasoningEffortInput),
        "速度档位" to localizedServiceTier(serviceTierInput),
        "文件权限" to localizedSandboxMode(sandboxModeInput),
        "审批模式" to localizedApprovalMode(approvalModeInput),
        "运行器" to (connectedState?.runnerMode ?: "未连接"),
        "遥测" to "已关闭",
        "应用日志" to "已开启（本地文件）",
    )
}

private fun localizedApprovalMode(mode: String): String {
    return when (mode) {
        "auto" -> "自动"
        else -> "手动"
    }
}

private fun localizedReasoningEffort(value: String): String {
    return when (value) {
        "minimal" -> "极简"
        "low" -> "低"
        "high" -> "高"
        "xhigh" -> "极高"
        else -> "中"
    }
}

private fun localizedServiceTier(value: String): String {
    return when (value) {
        "default" -> "普通"
        "fast" -> "快速"
        else -> "普通"
    }
}

private fun localizedSandboxMode(value: String): String {
    return when (value) {
        "read-only" -> "只读"
        "danger-full-access" -> "完全访问"
        else -> "工作区可写"
    }
}

private fun localizedSessionStatus(status: String): String {
    return when (status) {
        "draft" -> "草稿"
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

private fun normalizeReasoningEffort(value: String): String {
    return value.takeIf { it in setOf("minimal", "low", "medium", "high", "xhigh") } ?: "medium"
}

private fun normalizeServiceTier(value: String): String {
    return value.takeIf { it == "default" || it == "fast" } ?: "default"
}

private fun normalizeSandboxMode(value: String): String {
    return value.takeIf { it == "read-only" || it == "workspace-write" || it == "danger-full-access" }
        ?: "workspace-write"
}

private fun nowIsoString(): String = Instant.now().toString()

class AppViewModelFactory(
    private val bridgeApi: BridgeApi,
    private val sessionRepository: SessionRepository,
    private val settingsStore: AppSettingsStore,
    private val appLogger: AppLogger,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppViewModel(bridgeApi, sessionRepository, settingsStore, appLogger) as T
        }
        error("Unknown ViewModel class: ${modelClass.name}")
    }
}
