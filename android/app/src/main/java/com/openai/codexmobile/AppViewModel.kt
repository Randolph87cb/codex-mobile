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
import com.openai.codexmobile.data.SavedBridgeConnection
import com.openai.codexmobile.data.SessionConfigUpdate
import com.openai.codexmobile.data.SessionGoalUpdateRequest
import com.openai.codexmobile.data.SessionInputAttachmentRef
import com.openai.codexmobile.data.SessionRepository
import com.openai.codexmobile.data.SessionStreamEvent
import com.openai.codexmobile.data.UploadImageAttachmentRequest
import com.openai.codexmobile.data.UploadedImageAttachment
import com.openai.codexmobile.data.toDiagnosticsSummary
import com.openai.codexmobile.diagnostics.AppLogger
import com.openai.codexmobile.model.BridgeConnectionState
import com.openai.codexmobile.model.PendingApprovalSnapshot
import com.openai.codexmobile.model.SessionActivityEntry
import com.openai.codexmobile.model.SessionDetail
import com.openai.codexmobile.model.SessionGoalSnapshot
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.ArrayDeque
import java.util.Base64
import java.util.UUID

private const val DraftSessionId = "__draft__"
private const val SessionStreamReconnectBaseDelayMs = 1_500L
private const val SessionStreamReconnectMaxDelayMs = 15_000L
private const val SessionStreamReconnectMaxAttempts = 5
private const val ManagedApprovalMode = "auto"
private const val ManagedSandboxMode = "danger-full-access"

data class AppUiState(
    val endpointInput: String,
    val authTokenInput: String,
    val savedConnections: List<SavedBridgeConnection>,
    val selectedConnectionId: String,
    val cwdInput: String,
    val modelInput: String,
    val approvalModeInput: String,
    val reasoningEffortInput: String,
    val serviceTierInput: String,
    val sandboxModeInput: String,
    val fontSizeInput: String,
    val connectionState: BridgeConnectionState = BridgeConnectionState.Disconnected,
    val showArchivedSessions: Boolean = false,
    val sessions: List<SessionSummary> = emptyList(),
    val selectedSession: SessionDetail? = null,
    val selectedDraftSession: DraftSessionUiState? = null,
    val draftMessage: String = "",
    val isLoading: Boolean = false,
    val message: String? = null,
    val settingsItems: List<Pair<String, String>>,
    val sessionRealtimeState: SessionRealtimeUiState = SessionRealtimeUiState(),
    val pendingImageAttachments: List<PendingImageAttachmentUiState> = emptyList(),
    val queuedInputs: List<String> = emptyList(),
    val diagnosticsLog: String = "暂无日志。",
) {
    val isDraftSelected: Boolean
        get() = selectedDraftSession != null

    val selectedConnection: SavedBridgeConnection?
        get() = savedConnections.firstOrNull { it.id == selectedConnectionId }

    val fontSizeTypeScale: Float
        get() = typeScaleForFontSize(fontSizeInput)
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
    val isInterrupting: Boolean = false,
    val connectionText: String = "未连接实时流",
    val statusText: String = "等待会话详情",
    val lastEventText: String? = null,
    val fallbackNotice: String? = null,
    val pendingApproval: PendingApprovalUiState? = null,
    val liveExecutionActivities: List<SessionActivityEntry> = emptyList(),
)

data class PendingApprovalUiState(
    val requestId: BridgeRequestId?,
    val method: String?,
    val paramsSummary: String?,
    val isSubmitting: Boolean = false,
)

data class PendingImageAttachmentUiState(
    val localId: String,
    val displayName: String,
    val mimeType: String,
    val previewSource: String,
    val uploadState: PendingImageUploadState,
    val stagedPath: String? = null,
    val savedPath: String? = null,
    val uploadError: String? = null,
) {
    val attachmentPath: String?
        get() = savedPath ?: stagedPath
}

enum class PendingImageUploadState {
    Uploading,
    Uploaded,
    Failed,
}

private data class RequestedSessionConfigChange(
    val cwd: String? = null,
    val model: String? = null,
    val approvalMode: String? = null,
    val reasoningEffort: String? = null,
    val serviceTier: String? = null,
    val sandboxMode: String? = null,
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
    private var bridgeRestartExpectedSessionId: String? = null
    private var isAppInForeground: Boolean = true
    private val queuedInputsBySession = mutableMapOf<String, ArrayDeque<String>>()
    private val flushingQueuedSessions = mutableSetOf<String>()
    private val pendingImageUploadRequests = mutableMapOf<String, UploadImageAttachmentRequest>()
    private val pendingImageUploadJobs = mutableMapOf<String, Job>()
    private val imageUploadMutex = Mutex()

    init {
        bridgeApi.updateAuthToken(initialSettings.authToken)
        persistSettings(_uiState.value)
        appLogger.info("AppViewModel", "ViewModel 初始化完成。")
        refreshDiagnosticsLog()
        refreshConnection()
    }

    fun updateEndpointInput(value: String) {
        updateSettingsState { state ->
            state.copy(
                endpointInput = value,
                savedConnections = state.savedConnections.replaceConnection(state.selectedConnectionId) {
                    it.copy(endpoint = value)
                },
            )
        }
    }

    fun updateAuthTokenInput(value: String) {
        bridgeApi.updateAuthToken(value)
        updateSettingsState { state ->
            state.copy(
                authTokenInput = value,
                savedConnections = state.savedConnections.replaceConnection(state.selectedConnectionId) {
                    it.copy(authToken = value)
                },
            )
        }
    }

    fun updateSelectedConnectionName(value: String) {
        updateSettingsState { state ->
            state.copy(
                savedConnections = state.savedConnections.replaceConnection(state.selectedConnectionId) {
                    it.copy(name = value)
                },
            )
        }
    }

    fun addSavedConnection() {
        updateSettingsState { state ->
            val newConnection = SavedBridgeConnection(
                id = UUID.randomUUID().toString(),
                name = buildNextConnectionName(state.savedConnections),
                endpoint = "",
                authToken = "",
            )
            bridgeApi.updateAuthToken("")
            state.copy(
                savedConnections = state.savedConnections + newConnection,
                selectedConnectionId = newConnection.id,
                endpointInput = "",
                authTokenInput = "",
                message = "已新增连接，请填写桥接地址和令牌。",
            )
        }
    }

    fun selectSavedConnection(connectionId: String) {
        val selected = uiState.value.savedConnections.firstOrNull { it.id == connectionId } ?: return
        bridgeApi.updateAuthToken(selected.authToken)
        updateSettingsState { state ->
            state.copy(
                selectedConnectionId = selected.id,
                endpointInput = selected.endpoint,
                authTokenInput = selected.authToken,
                message = "已切换当前连接：${selected.name.ifBlank { "未命名连接" }}",
            )
        }
    }

    fun deleteSavedConnection(connectionId: String) {
        val currentState = uiState.value
        if (currentState.savedConnections.size <= 1) {
            _uiState.update { it.copy(message = "至少保留一个连接配置。") }
            return
        }
        updateSettingsState { state ->
            val remaining = state.savedConnections.filterNot { it.id == connectionId }
            val nextSelected = if (state.selectedConnectionId == connectionId) {
                remaining.first()
            } else {
                remaining.firstOrNull { it.id == state.selectedConnectionId } ?: remaining.first()
            }
            bridgeApi.updateAuthToken(nextSelected.authToken)
            state.copy(
                savedConnections = remaining,
                selectedConnectionId = nextSelected.id,
                endpointInput = nextSelected.endpoint,
                authTokenInput = nextSelected.authToken,
                message = "已删除连接配置：${currentState.savedConnections.firstOrNull { it.id == connectionId }?.name ?: "未命名连接"}",
            )
        }
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
                approvalModeInput = ManagedApprovalMode,
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
                sandboxModeInput = ManagedSandboxMode,
            )
        }
    }

    fun updateFontSizeInput(value: String) {
        updateSettingsState {
            it.copy(
                fontSizeInput = normalizeFontSize(value),
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

    fun attachPreparedImages(
        attachments: List<UploadImageAttachmentRequest>,
    ) {
        val validAttachments = attachments.filter { it.contentBytes.isNotEmpty() }
        if (validAttachments.isEmpty()) {
            _uiState.update { it.copy(message = "图片内容为空，无法附加。") }
            return
        }

        val activeSessionId = uiState.value.selectedSession?.id?.takeIf { it.isNotBlank() }
        val appended = validAttachments.map { attachment ->
            val localId = UUID.randomUUID().toString()
            pendingImageUploadRequests[localId] = attachment.copy(sessionId = activeSessionId)
            PendingImageAttachmentUiState(
                localId = localId,
                displayName = attachment.displayName.ifBlank { "image" },
                mimeType = attachment.mimeType.ifBlank { "image/jpeg" },
                previewSource = buildInlineImageSource(
                    mimeType = attachment.mimeType.ifBlank { "image/jpeg" },
                    contentBytes = attachment.contentBytes,
                ),
                uploadState = PendingImageUploadState.Uploading,
            )
        }

        _uiState.update {
            it.copy(
                pendingImageAttachments = it.pendingImageAttachments + appended,
                message = if (appended.size == 1) "已附加图片，开始上传。" else "已附加 ${appended.size} 张图片，开始上传。",
            )
        }

        appended.forEach { entry ->
            val request = pendingImageUploadRequests[entry.localId] ?: return@forEach
            startPendingImageUpload(entry.localId, request)
        }
    }

    fun removePendingImageAttachment(localId: String) {
        pendingImageUploadJobs.remove(localId)?.cancel()
        pendingImageUploadRequests.remove(localId)
        _uiState.update {
            val remaining = it.pendingImageAttachments.filterNot { attachment -> attachment.localId == localId }
            it.copy(
                pendingImageAttachments = remaining,
                message = "已移除图片附件。",
            )
        }
    }

    fun retryPendingImageAttachment(localId: String) {
        val request = pendingImageUploadRequests[localId]
        if (request == null) {
            _uiState.update { it.copy(message = "这张图片没有可重试的上传任务。") }
            return
        }

        _uiState.update { state ->
            state.copy(
                pendingImageAttachments = state.pendingImageAttachments.map { attachment ->
                    if (attachment.localId != localId) {
                        attachment
                    } else {
                        attachment.copy(
                            uploadState = PendingImageUploadState.Uploading,
                            uploadError = null,
                        )
                    }
                },
                message = "正在重新上传图片：${request.displayName}",
            )
        }
        startPendingImageUpload(localId, request)
    }

    private fun startPendingImageUpload(
        localId: String,
        request: UploadImageAttachmentRequest,
    ) {
        pendingImageUploadJobs.remove(localId)?.cancel()
        pendingImageUploadJobs[localId] = viewModelScope.launch {
            imageUploadMutex.withLock {
                val stillPending = uiState.value.pendingImageAttachments.any { it.localId == localId }
                if (!stillPending) {
                    return@withLock
                }

                appLogger.info(
                    "AppViewModel",
                    "开始预上传图片：localId=$localId, ${request.toDiagnosticsSummary()}",
                )
                runCatching {
                    bridgeApi.uploadImageAttachment(request)
                }.onSuccess { uploaded ->
                    _uiState.update { state ->
                        state.copy(
                            pendingImageAttachments = state.pendingImageAttachments.map { attachment ->
                                if (attachment.localId != localId) {
                                    attachment
                                } else {
                                    attachment.copy(
                                        previewSource = buildBridgeImageSource(uploaded.attachmentPath),
                                        uploadState = PendingImageUploadState.Uploaded,
                                        stagedPath = uploaded.stagedPath,
                                        savedPath = uploaded.savedPath,
                                        uploadError = null,
                                    )
                                }
                            },
                            message = "图片已上传：${uploaded.displayName}",
                        )
                    }
                    appLogger.info(
                        "AppViewModel",
                        "图片预上传完成：localId=$localId, ${request.toDiagnosticsSummary()}, stagedPath=${uploaded.stagedPath}, savedPath=${uploaded.savedPath ?: "none"}",
                    )
                }.onFailure { error ->
                    if (error is CancellationException) {
                        throw error
                    }
                    _uiState.update { state ->
                        state.copy(
                            pendingImageAttachments = state.pendingImageAttachments.map { attachment ->
                                if (attachment.localId != localId) {
                                    attachment
                                } else {
                                    attachment.copy(
                                        uploadState = PendingImageUploadState.Failed,
                                        uploadError = error.message ?: "上传失败",
                                    )
                                }
                            },
                            message = error.message ?: "图片上传失败。",
                        )
                    }
                    appLogger.error(
                        "AppViewModel",
                        "图片预上传失败：localId=$localId, ${request.toDiagnosticsSummary()}",
                        error,
                    )
                }
                refreshDiagnosticsLog()
            }
        }.also { job ->
            job.invokeOnCompletion {
                pendingImageUploadJobs.remove(localId)
            }
        }
    }

    private fun clearPendingImageUploads() {
        pendingImageUploadJobs.values.forEach { it.cancel() }
        pendingImageUploadJobs.clear()
        pendingImageUploadRequests.clear()
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
                val syncedSessions = loadManagedSessionSummaries(uiState.value.showArchivedSessions)
                val selectedSession = syncedSessions.firstOrNull()
                    ?.let { sessionRepository.getSessionDetail(it.id) }
                    ?.let(::enforceManagedSessionDetailLocally)
                appLogger.info(
                    "AppViewModel",
                    "桥接连接成功，会话数=${syncedSessions.size}${selectedSession?.id?.let { ", 默认会话=$it" } ?: ""}",
                )
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        connectionState = connectionState,
                        sessions = syncedSessions,
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

    fun setShowArchivedSessions(showArchived: Boolean) {
        if (uiState.value.showArchivedSessions == showArchived) {
            return
        }

        viewModelScope.launch {
            appLogger.info("AppViewModel", "切换会话筛选：archived=$showArchived")
            stopSessionStream()
            clearPendingImageUploads()
            _uiState.update {
                it.copy(
                    isLoading = true,
                    message = null,
                    showArchivedSessions = showArchived,
                    selectedSession = null,
                    selectedDraftSession = null,
                    pendingImageAttachments = emptyList(),
                    queuedInputs = emptyList(),
                    sessionRealtimeState = SessionRealtimeUiState(),
                )
            }
            try {
                val sessions = loadManagedSessionSummaries(showArchived)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        sessions = sessions,
                        message = if (showArchived) "已切换到归档会话。" else "已切换到当前会话。",
                    )
                }
            } catch (error: Exception) {
                appLogger.error("AppViewModel", "切换会话筛选失败：archived=$showArchived", error)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        sessions = emptyList(),
                        message = error.message ?: "刷新会话列表失败。",
                    )
                }
            }
            refreshDiagnosticsLog()
        }
    }

    fun archiveSession(sessionId: String) {
        if (sessionId.isBlank()) {
            return
        }

        viewModelScope.launch {
            val shouldClearSelection = uiState.value.selectedSession?.id == sessionId
            appLogger.info("AppViewModel", "归档会话：sessionId=$sessionId")
            if (shouldClearSelection) {
                stopSessionStream()
                clearPendingImageUploads()
            }
            _uiState.update { it.copy(isLoading = true, message = null) }
            try {
                sessionRepository.archiveSession(sessionId)
                val sessions = loadManagedSessionSummaries(uiState.value.showArchivedSessions)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        sessions = sessions,
                        selectedSession = if (shouldClearSelection) null else it.selectedSession,
                        selectedDraftSession = if (shouldClearSelection) null else it.selectedDraftSession,
                        queuedInputs = if (shouldClearSelection) emptyList() else it.queuedInputs,
                        sessionRealtimeState = if (shouldClearSelection) SessionRealtimeUiState() else it.sessionRealtimeState,
                        message = "已归档会话。",
                    )
                }
            } catch (error: Exception) {
                appLogger.error("AppViewModel", "归档会话失败：sessionId=$sessionId", error)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = error.message ?: "归档会话失败。",
                    )
                }
            }
            refreshDiagnosticsLog()
        }
    }

    fun unarchiveSession(sessionId: String) {
        if (sessionId.isBlank()) {
            return
        }

        viewModelScope.launch {
            appLogger.info("AppViewModel", "恢复归档会话：sessionId=$sessionId")
            _uiState.update { it.copy(isLoading = true, message = null) }
            try {
                sessionRepository.unarchiveSession(sessionId)
                val sessions = loadManagedSessionSummaries(uiState.value.showArchivedSessions)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        sessions = sessions,
                        message = "已恢复会话。",
                    )
                }
            } catch (error: Exception) {
                appLogger.error("AppViewModel", "恢复归档会话失败：sessionId=$sessionId", error)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = error.message ?: "恢复归档会话失败。",
                    )
                }
            }
            refreshDiagnosticsLog()
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            appLogger.info("AppViewModel", "用户主动断开桥接连接。")
            stopSessionStream()
            clearPendingImageUploads()
            bridgeApi.disconnect()
            queuedInputsBySession.clear()
            flushingQueuedSessions.clear()
            _uiState.update {
                it.copy(
                    connectionState = BridgeConnectionState.Disconnected,
                    sessions = emptyList(),
                    selectedSession = null,
                    selectedDraftSession = null,
                    pendingImageAttachments = emptyList(),
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
        clearPendingImageUploads()
        _uiState.update {
            it.copy(
                selectedDraftSession = null,
                draftMessage = "",
                pendingImageAttachments = emptyList(),
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

            val managedDetail = ensureManagedSessionPolicy(detail)

            _uiState.update { state ->
                val merged = mergeSessionDetail(state.selectedSession, managedDetail)
                state.copy(
                    isLoading = false,
                    selectedSession = merged,
                    queuedInputs = queuedInputsFor(sessionId),
                    sessionRealtimeState = state.sessionRealtimeState.copy(
                        statusText = localizedSessionStatus(merged.status),
                        fallbackNotice = null,
                        pendingApproval = merged.pendingApproval?.toUiState(),
                    ),
                )
            }

            startSessionStream(sessionId)
            maybeAutoApprovePending(sessionId)
            flushNextQueuedInputIfIdle(sessionId)
            refreshDiagnosticsLog()
        }
    }

    fun closeSessionDetail(sessionId: String? = null) {
        if (sessionId == null || sessionId == activeStreamSessionId) {
            stopSessionStream()
        }
        if (sessionId == null || uiState.value.selectedSession?.id == sessionId || sessionId == DraftSessionId) {
            if (sessionId == null || sessionId == DraftSessionId) {
                clearPendingImageUploads()
            }
            _uiState.update { it.copy(queuedInputs = emptyList()) }
        }
    }

    fun sendInput() {
        val detail = uiState.value.selectedSession
        val draftSession = uiState.value.selectedDraftSession
        val text = uiState.value.draftMessage.trim()
        val pendingImageAttachments = uiState.value.pendingImageAttachments
        if (text.isEmpty() && pendingImageAttachments.isEmpty()) {
            return
        }
        if (pendingImageAttachments.any { it.uploadState == PendingImageUploadState.Uploading }) {
            appLogger.warn("AppViewModel", "用户尝试发送，但仍有图片上传中。")
            _uiState.update { it.copy(message = "图片仍在上传，请稍后再发送。") }
            return
        }
        if (pendingImageAttachments.any { it.uploadState == PendingImageUploadState.Failed }) {
            appLogger.warn("AppViewModel", "用户尝试发送，但存在上传失败的图片。")
            _uiState.update { it.copy(message = "有图片上传失败，请重试或移除后再发送。") }
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
                    val resolvedPendingImageAttachments = resolvePendingImageAttachmentsForSession(
                        sessionId = created.id,
                        pendingImageAttachments = pendingImageAttachments,
                    )
                    submitInputNow(
                        detail = created,
                        text = text,
                        pendingImageAttachments = resolvedPendingImageAttachments,
                        fromQueue = false,
                    )
                    _uiState.update {
                        it.copy(
                            selectedDraftSession = null,
                            selectedSession = it.selectedSession ?: created,
                            message = "已创建会话并发送首条消息。",
                        )
                    }
                    refreshSessions()
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

        val activeDetail = detail ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            try {
                val resolvedDetail = resolveSessionDetailBeforeSending(
                    detail = activeDetail,
                    realtimeState = uiState.value.sessionRealtimeState,
                )
                if (shouldQueueInput(resolvedDetail, uiState.value.sessionRealtimeState)) {
                    if (pendingImageAttachments.isNotEmpty()) {
                        appLogger.warn("AppViewModel", "当前轮未结束，暂不支持带图片的排队发送：sessionId=${resolvedDetail.id}")
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                message = "当前轮尚未结束，暂不支持把图片加入排队。",
                            )
                        }
                        return@launch
                    }
                    appLogger.info(
                        "AppViewModel",
                        "当前轮未结束，消息进入排队：sessionId=${resolvedDetail.id}, textLength=${text.length}",
                    )
                    enqueueQueuedInput(resolvedDetail.id, text)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            draftMessage = "",
                            message = "当前轮尚未结束，消息已加入排队。",
                            queuedInputs = queuedInputsFor(resolvedDetail.id),
                        )
                    }
                    return@launch
                }
                    appLogger.info(
                        "AppViewModel",
                    "发送消息：sessionId=${resolvedDetail.id}, textLength=${text.length}, imageCount=${pendingImageAttachments.size}",
                    )
                    submitInputNow(
                        detail = resolvedDetail,
                        text = text,
                        pendingImageAttachments = pendingImageAttachments,
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

    fun interruptSelectedSession() {
        val detail = uiState.value.selectedSession
        if (!canInterruptSession(detail, uiState.value.sessionRealtimeState)) {
            _uiState.update { it.copy(message = "当前没有可中断的任务。") }
            return
        }

        val sessionId = detail?.id ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    sessionRealtimeState = it.sessionRealtimeState.copy(
                        isInterrupting = true,
                        lastEventText = "正在请求中断当前任务。",
                    ),
                    message = null,
                )
            }
            try {
                appLogger.info("AppViewModel", "请求中断当前任务：sessionId=$sessionId")
                bridgeApi.interruptSession(sessionId)
                _uiState.update {
                    it.copy(
                        sessionRealtimeState = it.sessionRealtimeState.copy(
                            isInterrupting = false,
                            lastEventText = "已发送中断请求，等待会话停止。",
                        ),
                        message = "已发送中断请求。",
                    )
                }
            } catch (error: Exception) {
                appLogger.error("AppViewModel", "请求中断当前任务失败：sessionId=$sessionId", error)
                _uiState.update {
                    it.copy(
                        sessionRealtimeState = it.sessionRealtimeState.copy(
                            isInterrupting = false,
                        ),
                        message = error.message ?: "中断当前任务失败。",
                    )
                }
            }
            refreshDiagnosticsLog()
        }
    }

    fun submitApproval(decision: ApprovalDecision) {
        val detail = uiState.value.selectedSession ?: return
        val approval = uiState.value.sessionRealtimeState.pendingApproval ?: return

        submitApprovalForPending(detail, approval, decision, autoTriggered = false)
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
            sandboxMode = ManagedSandboxMode,
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

    fun updateSelectedSessionGoal(
        objective: String,
        tokenBudget: Long?,
    ) {
        val detail = uiState.value.selectedSession ?: return
        val normalizedObjective = objective.trim()
        if (normalizedObjective.isBlank()) {
            _uiState.update { it.copy(message = "目标内容不能为空。") }
            return
        }

        viewModelScope.launch {
            val now = nowIsoString()
            _uiState.update {
                it.copy(
                    isLoading = true,
                    message = null,
                    selectedSession = it.selectedSession?.copy(
                        goal = SessionGoalSnapshot(
                            objective = normalizedObjective,
                            status = it.selectedSession.goal?.status ?: "active",
                            tokenBudget = tokenBudget ?: it.selectedSession.goal?.tokenBudget,
                            tokensUsed = it.selectedSession.goal?.tokensUsed ?: 0L,
                            timeUsedSeconds = it.selectedSession.goal?.timeUsedSeconds ?: 0L,
                            createdAt = it.selectedSession.goal?.createdAt ?: now,
                            updatedAt = now,
                        ),
                        goalCapability = "supported",
                    ),
                )
            }

            try {
                val result = bridgeApi.updateSessionGoal(
                    detail.id,
                    SessionGoalUpdateRequest(
                        objective = normalizedObjective,
                        tokenBudget = tokenBudget,
                    ),
                )
                applySessionGoalResponse(
                    sessionId = detail.id,
                    capability = result.capability,
                    goal = result.goal,
                    userVisibleMessage = "已更新目标。",
                    lastEventText = "目标已更新。",
                )
            } catch (error: Exception) {
                appLogger.error("AppViewModel", "更新会话目标失败：sessionId=${detail.id}", error)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = error.message ?: "更新目标失败。",
                    )
                }
                refreshSessionSnapshot(detail.id)
                refreshDiagnosticsLog()
            }
        }
    }

    fun pauseSelectedSessionGoal() {
        updateSelectedSessionGoalStatus("paused", "已暂停目标。", "目标已暂停。")
    }

    fun resumeSelectedSessionGoal() {
        updateSelectedSessionGoalStatus("active", "已恢复目标。", "目标已恢复。")
    }

    fun clearSelectedSessionGoal() {
        val detail = uiState.value.selectedSession ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            try {
                val result = bridgeApi.clearSessionGoal(detail.id)
                _uiState.update {
                    if (it.selectedSession?.id != detail.id) {
                        it.copy(isLoading = false)
                    } else {
                        it.copy(
                            isLoading = false,
                            message = "已清除目标。",
                            selectedSession = it.selectedSession.copy(
                                goal = null,
                                goalCapability = result.capability,
                            ),
                            sessionRealtimeState = it.sessionRealtimeState.copy(
                                lastEventText = "目标已清除。",
                            ),
                        )
                    }
                }
                refreshDiagnosticsLog()
            } catch (error: Exception) {
                appLogger.error("AppViewModel", "清除会话目标失败：sessionId=${detail.id}", error)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = error.message ?: "清除目标失败。",
                    )
                }
                refreshSessionSnapshot(detail.id)
                refreshDiagnosticsLog()
            }
        }
    }

    private fun updateSelectedSessionGoalStatus(
        status: String,
        userVisibleMessage: String,
        lastEventText: String,
    ) {
        val detail = uiState.value.selectedSession ?: return
        val currentGoal = detail.goal ?: run {
            _uiState.update { it.copy(message = "当前还没有可更新的目标。") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    message = null,
                    selectedSession = it.selectedSession?.copy(
                        goal = currentGoal.copy(
                            status = status,
                            updatedAt = nowIsoString(),
                        ),
                        goalCapability = "supported",
                    ),
                )
            }
            try {
                val result = bridgeApi.updateSessionGoal(
                    detail.id,
                    SessionGoalUpdateRequest(status = status),
                )
                applySessionGoalResponse(
                    sessionId = detail.id,
                    capability = result.capability,
                    goal = result.goal,
                    userVisibleMessage = userVisibleMessage,
                    lastEventText = lastEventText,
                )
            } catch (error: Exception) {
                appLogger.error("AppViewModel", "更新目标状态失败：sessionId=${detail.id}, status=$status", error)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = error.message ?: "更新目标状态失败。",
                    )
                }
                refreshSessionSnapshot(detail.id)
                refreshDiagnosticsLog()
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
                it.copy(selectedDraftSession = nextDraft)
            }
            return
        }

        val detail = uiState.value.selectedSession ?: return
        val requestedChange = RequestedSessionConfigChange(
            cwd = cwd,
            model = model,
            approvalMode = approvalMode,
            reasoningEffort = reasoningEffort,
            serviceTier = serviceTier,
            sandboxMode = sandboxMode,
        )
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
            it.copy(selectedSession = nextDetail)
        }

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
                            selectedSession = mergeSessionConfigUpdateResult(
                                current = it.selectedSession,
                                incoming = updated,
                                requestedChange = requestedChange,
                            ),
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
            clearBridgeRestartState = false,
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
                    val bridgeRestartExpected = bridgeRestartExpectedSessionId == sessionId
                    val reconnectScheduled = scheduleSessionStreamReconnect(
                        sessionId = sessionId,
                        reason = error.message ?: if (bridgeRestartExpected) "bridge 正在重启。" else "实时流连接失败。",
                        immediate = bridgeRestartExpected,
                    )
                    _uiState.update {
                        it.copy(
                            sessionRealtimeState = it.sessionRealtimeState.copy(
                                isActive = true,
                                isConnected = false,
                                connectionText = if (reconnectScheduled) {
                                    if (bridgeRestartExpected) {
                                        "bridge 重启中，准备恢复"
                                    } else {
                                        "实时流连接失败，准备重连"
                                    }
                                } else {
                                    if (bridgeRestartExpected) {
                                        "bridge 重启中"
                                    } else {
                                        "实时流连接失败"
                                    }
                                },
                                lastEventText = if (reconnectScheduled) {
                                    if (bridgeRestartExpected) {
                                        "bridge 连接已切换，正在恢复实时流。"
                                    } else {
                                        "连接已中断，正在尝试重新建立实时流。"
                                    }
                                } else {
                                    "无法继续接收实时事件。"
                                },
                                fallbackNotice = if (bridgeRestartExpected) {
                                    buildBridgeRestartNotice(
                                        reconnectScheduled = reconnectScheduled,
                                        reason = error.message,
                                    )
                                } else {
                                    buildStreamReconnectNotice(
                                        reconnectScheduled = reconnectScheduled,
                                        reason = error.message ?: "当前回退到 HTTP 快照。",
                                    )
                                },
                            ),
                            message = if (reconnectScheduled || bridgeRestartExpected) {
                                null
                            } else {
                                error.message ?: "实时流连接失败。"
                            },
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
        clearBridgeRestartState: Boolean = true,
    ) {
        activeStreamSessionId?.let { appLogger.info("AppViewModel", "停止实时流监听：sessionId=$it") }
        sessionStreamJob?.cancel()
        sessionStreamJob = null
        activeStreamSessionId = null
        activeAssistantTurnId = null
        if (clearBridgeRestartState) {
            bridgeRestartExpectedSessionId = null
        }
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
                bridgeRestartExpectedSessionId = null
                resetSessionStreamReconnectState()
                _uiState.update {
                    it.copy(
                        sessionRealtimeState = it.sessionRealtimeState.copy(
                            isActive = true,
                            isConnected = true,
                            isInterrupting = false,
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
                val bridgeRestartExpected = bridgeRestartExpectedSessionId == sessionId
                val reconnectScheduled = scheduleSessionStreamReconnect(
                    sessionId = sessionId,
                    reason = event.reason ?: if (bridgeRestartExpected) "bridge 正在重启。" else "实时流连接已关闭。",
                    immediate = bridgeRestartExpected,
                )
                _uiState.update {
                    it.copy(
                        sessionRealtimeState = it.sessionRealtimeState.copy(
                            isActive = true,
                            isConnected = false,
                            connectionText = if (reconnectScheduled) {
                                if (bridgeRestartExpected) {
                                    "bridge 重启中，准备恢复"
                                } else {
                                    "实时流已断开，准备重连"
                                }
                            } else {
                                if (bridgeRestartExpected) {
                                    "bridge 重启中"
                                } else {
                                    "实时流已断开"
                                }
                            },
                            lastEventText = if (bridgeRestartExpected) {
                                "bridge 正在重启，等待自动恢复当前会话。"
                            } else {
                                event.reason ?: "实时流连接已关闭。"
                            },
                            fallbackNotice = if (bridgeRestartExpected) {
                                buildBridgeRestartNotice(
                                    reconnectScheduled = reconnectScheduled,
                                    reason = event.reason,
                                )
                            } else {
                                buildStreamReconnectNotice(
                                    reconnectScheduled = reconnectScheduled,
                                    reason = "当前停留在最后一次收到的内容快照。",
                                )
                            },
                            pendingApproval = it.selectedSession?.pendingApproval?.toUiState()
                                ?: it.sessionRealtimeState.pendingApproval,
                        ),
                    )
                }
                if (shouldRefreshBusyState(uiState.value.selectedSession, uiState.value.sessionRealtimeState)) {
                    refreshSessionSnapshot(sessionId)
                }
            }

            is SessionStreamEvent.SessionStarted -> {
                appLogger.info(
                    "AppViewModel",
                    "会话实时流就绪：sessionId=$sessionId, status=${event.status}, threadId=${event.threadId ?: "none"}",
                )
                bridgeRestartExpectedSessionId = null
                resetSessionStreamReconnectState()
                val nextDetail = buildOrUpdateSessionFromStart(
                    current = uiState.value.selectedSession,
                    event = event,
                )
                _uiState.update {
                    it.copy(
                        selectedSession = nextDetail,
                        sessionRealtimeState = it.sessionRealtimeState.copy(
                            isActive = true,
                            isConnected = true,
                            isInterrupting = false,
                            connectionText = "已连接实时流",
                            statusText = localizedSessionStatus(nextDetail.status),
                            lastEventText = "会话实时流已就绪。",
                            fallbackNotice = null,
                            pendingApproval = event.pendingApproval?.toUiState()
                                ?: nextDetail.pendingApproval?.toUiState()
                                ?: it.sessionRealtimeState.pendingApproval,
                            liveExecutionActivities = if (event.status == "running" || event.status == "awaiting_approval") {
                                it.sessionRealtimeState.liveExecutionActivities
                            } else {
                                emptyList()
                            },
                        ),
                    )
                }
                maybeAutoApprovePending(sessionId)
            }

            is SessionStreamEvent.GoalUpdated -> {
                _uiState.update {
                    it.copy(
                        selectedSession = it.selectedSession?.takeIf { detail -> detail.id == sessionId }?.copy(
                            goal = event.goal,
                            goalCapability = event.goalCapability ?: "supported",
                            lastUpdated = event.timestamp ?: it.selectedSession.lastUpdated,
                        ),
                        sessionRealtimeState = it.sessionRealtimeState.copy(
                            lastEventText = "目标已更新。",
                        ),
                    )
                }
            }

            is SessionStreamEvent.GoalCleared -> {
                _uiState.update {
                    it.copy(
                        selectedSession = it.selectedSession?.takeIf { detail -> detail.id == sessionId }?.copy(
                            goal = null,
                            goalCapability = event.goalCapability ?: "supported",
                            lastUpdated = event.timestamp ?: it.selectedSession.lastUpdated,
                        ),
                        sessionRealtimeState = it.sessionRealtimeState.copy(
                            lastEventText = "目标已清除。",
                        ),
                    )
                }
            }

            is SessionStreamEvent.BridgeLifecycle -> {
                appLogger.info(
                    "AppViewModel",
                    "bridge 生命周期事件：sessionId=$sessionId, phase=${event.phase}, reason=${event.reason ?: "none"}",
                )
                if (event.phase == "restarting") {
                    bridgeRestartExpectedSessionId = sessionId
                }
                _uiState.update {
                    it.copy(
                        sessionRealtimeState = it.sessionRealtimeState.copy(
                            isActive = true,
                            isConnected = true,
                            connectionText = if (event.phase == "restarting") {
                                "bridge 即将重启"
                            } else {
                                it.sessionRealtimeState.connectionText
                            },
                            lastEventText = if (event.phase == "restarting") {
                                "bridge 正在进入平滑重启窗口。"
                            } else {
                                it.sessionRealtimeState.lastEventText
                            },
                            fallbackNotice = if (event.phase == "restarting") {
                                buildBridgeLifecycleNotice(event)
                            } else {
                                it.sessionRealtimeState.fallbackNotice
                            },
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
                            isInterrupting = false,
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
                            isInterrupting = false,
                            fallbackNotice = event.errorMessage,
                        ),
                        message = event.errorMessage,
                    )
                }
                refreshSessionSnapshot(sessionId)
            }

            is SessionStreamEvent.Activity -> {
                if (event.transcriptBlock.isBlank()) {
                    return
                }

                val activityEntry = event.toActivityEntry() ?: return
                _uiState.update {
                    it.copy(
                        sessionRealtimeState = it.sessionRealtimeState.copy(
                            lastEventText = summarizeActivityEvent(event),
                            liveExecutionActivities = upsertLiveExecutionActivity(
                                current = it.sessionRealtimeState.liveExecutionActivities,
                                incoming = activityEntry,
                            ),
                        ),
                    )
                }
            }

            is SessionStreamEvent.RunStatus -> {
                appLogger.debug("AppViewModel", "运行状态变化：sessionId=$sessionId, status=${event.status}")
                _uiState.update {
                    val normalizedStatus = normalizeSessionStatus(
                        status = event.status,
                        incomingPendingApproval = null,
                        currentPendingApproval = it.selectedSession?.pendingApproval,
                        fallbackStatus = it.selectedSession?.status,
                    )
                    it.copy(
                        selectedSession = it.selectedSession?.copy(
                            status = normalizedStatus,
                            subtitle = buildSessionSubtitle(
                                model = it.selectedSession.model,
                                approvalMode = it.selectedSession.approvalMode,
                                status = normalizedStatus,
                            ),
                            lastUpdated = event.timestamp ?: it.selectedSession.lastUpdated,
                        ),
                        sessionRealtimeState = it.sessionRealtimeState.copy(
                            statusText = localizedSessionStatus(normalizedStatus),
                            lastEventText = statusEventText(normalizedStatus),
                            isInterrupting = if (normalizedStatus == "running" || normalizedStatus == "awaiting_approval") {
                                it.sessionRealtimeState.isInterrupting
                            } else {
                                false
                            },
                            fallbackNotice = if (normalizedStatus == "awaiting_approval") {
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
                            isInterrupting = false,
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
                val pendingApproval = PendingApprovalUiState(
                    requestId = event.requestId,
                    method = event.method,
                    paramsSummary = event.paramsSummary,
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
                                pendingApproval = pendingApproval.toSnapshot(),
                            ),
                        queuedInputs = queuedInputsFor(sessionId),
                        sessionRealtimeState = it.sessionRealtimeState.copy(
                            statusText = localizedSessionStatus("awaiting_approval"),
                            lastEventText = "收到工具请求：${event.method ?: "未知方法"}",
                            fallbackNotice = "手机端正在自动放行这次工具请求。",
                            pendingApproval = pendingApproval,
                        ),
                    )
                }
                maybeAutoApprovePending(sessionId)
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
                                pendingApproval = null,
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
                if (event.isRetryable) {
                    appLogger.warn(
                        "AppViewModel",
                        "实时流收到可重试错误：sessionId=$sessionId, message=${event.message}",
                    )
                } else {
                    appLogger.error("AppViewModel", "实时流错误事件：sessionId=$sessionId, message=${event.message}")
                    activeAssistantTurnId = null
                }
                _uiState.update {
                    it.copy(
                        selectedSession = if (event.isRetryable) {
                            it.selectedSession
                        } else {
                            it.selectedSession
                                ?.let { detail -> appendSystemMessage(detail, "系统：${event.message}", event.timestamp) }
                                ?.copy(
                                    status = "error",
                                    subtitle = buildSessionSubtitle(
                                        model = it.selectedSession.model,
                                        approvalMode = it.selectedSession.approvalMode,
                                        status = "error",
                                    ),
                                )
                        },
                        sessionRealtimeState = it.sessionRealtimeState.copy(
                            statusText = if (event.isRetryable) {
                                localizedSessionStatus(it.selectedSession?.status ?: "running")
                            } else {
                                localizedSessionStatus("error")
                            },
                            lastEventText = if (event.isRetryable) {
                                "上游响应流暂时中断，bridge 正在重试。"
                            } else {
                                "实时流返回错误事件。"
                            },
                            fallbackNotice = if (event.isRetryable) {
                                buildRetryableStreamNotice(event.message)
                            } else {
                                event.message
                            },
                            pendingApproval = it.selectedSession?.pendingApproval?.toUiState()
                                ?: it.sessionRealtimeState.pendingApproval,
                        ),
                        message = if (event.isRetryable) {
                            null
                        } else {
                            "实时流错误：${event.message}"
                        },
                    )
                }
                refreshSessionSnapshot(sessionId)
            }
        }
    }

    private suspend fun refreshSessionSnapshot(sessionId: String): SessionDetail? {
        val detail = try {
            sessionRepository.getSessionDetail(sessionId)
        } catch (error: Exception) {
            appLogger.error("AppViewModel", "刷新会话快照失败：sessionId=$sessionId", error)
            null
        }

        var mergedDetail: SessionDetail? = null
        if (detail != null && uiState.value.selectedSession?.id == sessionId) {
            _uiState.update {
                val merged = mergeSessionDetail(it.selectedSession, detail)
                mergedDetail = merged
                it.copy(
                    selectedSession = merged,
                    queuedInputs = queuedInputsFor(sessionId),
                    sessionRealtimeState = it.sessionRealtimeState.copy(
                        statusText = localizedSessionStatus(merged.status),
                        fallbackNotice = when {
                            merged.status == "awaiting_approval" || merged.status == "error" ->
                                it.sessionRealtimeState.fallbackNotice
                            merged.status == "running" && isRetryableStreamNotice(it.sessionRealtimeState.fallbackNotice) ->
                                it.sessionRealtimeState.fallbackNotice
                            else -> null
                        },
                        pendingApproval = if (merged.status == "awaiting_approval") {
                            merged.pendingApproval?.toUiState() ?: it.sessionRealtimeState.pendingApproval
                        } else {
                            null
                        },
                        liveExecutionActivities = if (merged.status == "running" || merged.status == "awaiting_approval") {
                            it.sessionRealtimeState.liveExecutionActivities
                        } else {
                            emptyList()
                        },
                    ),
                )
            }
            if (detail.status == "idle") {
                flushNextQueuedInputIfIdle(sessionId)
            }
            if (mergedDetail?.status == "awaiting_approval") {
                maybeAutoApprovePending(sessionId)
            }
        }

        refreshSessions()
        return mergedDetail ?: detail
    }

    private suspend fun refreshSessions() {
        try {
            val sessions = loadManagedSessionSummaries(uiState.value.showArchivedSessions)
            val visibleIds = sessions.map { it.id }.toSet()
            _uiState.update {
                it.copy(
                    sessions = sessions,
                    selectedSession = if (visibleIds.isEmpty()) {
                        it.selectedSession
                    } else {
                        it.selectedSession?.takeIf { detail -> visibleIds.contains(detail.id) }
                    },
                )
            }
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

    private fun buildRetryableStreamNotice(message: String): String {
        return "上游响应流暂时中断，bridge 正在重试：$message"
    }

    private fun buildBridgeLifecycleNotice(event: SessionStreamEvent.BridgeLifecycle): String {
        val base = "bridge 正在平滑重启，旧连接关闭后会自动恢复当前会话。"
        val delayNotice = event.graceMs?.takeIf { it > 0 }?.let { "预计 ${it} ms 后进入切换。" }.orEmpty()
        val reasonNotice = event.reason?.takeIf { it.isNotBlank() }?.let { "原因：$it" }.orEmpty()
        return listOf(base, delayNotice, reasonNotice)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    private fun buildBridgeRestartNotice(
        reconnectScheduled: Boolean,
        reason: String?,
    ): String {
        val base = if (reconnectScheduled) {
            "bridge 正在重启，客户端会自动重连并刷新会话快照。"
        } else {
            "bridge 重启后未能继续自动重连，请稍后手动同步。"
        }
        val reasonNotice = reason?.takeIf { it.isNotBlank() }?.let { "原因：$it" }.orEmpty()
        return listOf(base, reasonNotice)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    private fun isRetryableStreamNotice(message: String?): Boolean {
        return message?.startsWith("上游响应流暂时中断，bridge 正在重试：") == true
    }

    private fun updateSettingsState(transform: (AppUiState) -> AppUiState) {
        _uiState.update { current ->
            val next = transform(current).withSettingsItems()
            persistSettings(next)
            next
        }
    }

    private fun persistSettings(state: AppUiState) {
        val normalizedConnections = state.savedConnections
            .replaceConnection(state.selectedConnectionId) {
                it.copy(
                    endpoint = state.endpointInput,
                    authToken = state.authTokenInput,
                )
            }
            .mapIndexed { index, connection ->
                connection.copy(
                    name = connection.name.trim().ifBlank { defaultConnectionName(index) },
                    endpoint = connection.endpoint.trim(),
                )
            }
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
                fontSize = state.fontSizeInput,
                savedConnections = normalizedConnections,
                selectedConnectionId = state.selectedConnectionId,
            ),
        )
    }

    private suspend fun synchronizeManagedSessionPolicies(sessions: List<SessionSummary>): List<SessionSummary> {
        var changed = false
        for (session in sessions) {
            if (session.approvalMode == ManagedApprovalMode && session.sandboxMode == ManagedSandboxMode) {
                continue
            }

            changed = true
            try {
                appLogger.info(
                    "AppViewModel",
                    "同步会话托管策略：sessionId=${session.id}, approvalMode=${session.approvalMode}, sandboxMode=${session.sandboxMode}",
                )
                bridgeApi.updateSessionConfig(
                    session.id,
                    SessionConfigUpdate(
                        approvalMode = ManagedApprovalMode,
                        sandboxMode = ManagedSandboxMode,
                    ),
                )
            } catch (error: Exception) {
                appLogger.error("AppViewModel", "同步会话托管策略失败：sessionId=${session.id}", error)
            }
        }

        val latest = if (changed) {
            sessionRepository.listSessions()
        } else {
            sessions
        }
        return latest.map(::enforceManagedSessionSummaryLocally)
    }

    private suspend fun loadManagedSessionSummaries(archived: Boolean): List<SessionSummary> {
        return synchronizeManagedSessionPolicies(sessionRepository.listSessions(archived = archived))
    }

    private suspend fun ensureManagedSessionPolicy(detail: SessionDetail): SessionDetail {
        if (detail.approvalMode == ManagedApprovalMode && detail.sandboxMode == ManagedSandboxMode) {
            return enforceManagedSessionDetailLocally(detail)
        }

        return try {
            appLogger.info(
                "AppViewModel",
                "修正会话托管策略：sessionId=${detail.id}, approvalMode=${detail.approvalMode}, sandboxMode=${detail.sandboxMode}",
            )
            bridgeApi.updateSessionConfig(
                detail.id,
                SessionConfigUpdate(
                    approvalMode = ManagedApprovalMode,
                    sandboxMode = ManagedSandboxMode,
                ),
            ).let(::enforceManagedSessionDetailLocally)
        } catch (error: Exception) {
            appLogger.error("AppViewModel", "修正会话托管策略失败：sessionId=${detail.id}", error)
            enforceManagedSessionDetailLocally(detail)
        }
    }

    private fun buildCreateSessionRequest(detail: SessionDetail): CreateSessionRequest {
        return CreateSessionRequest(
            cwd = detail.cwd.trim(),
            model = detail.model.trim(),
            approvalMode = ManagedApprovalMode,
            reasoningEffort = detail.reasoningEffort,
            serviceTier = detail.serviceTier,
            sandboxMode = ManagedSandboxMode,
        )
    }

    private fun buildCreateSessionRequest(draft: DraftSessionUiState): CreateSessionRequest {
        return CreateSessionRequest(
            cwd = draft.cwd.trim(),
            model = draft.model.trim(),
            approvalMode = ManagedApprovalMode,
            reasoningEffort = draft.reasoningEffort,
            serviceTier = draft.serviceTier,
            sandboxMode = ManagedSandboxMode,
        )
    }

    override fun onCleared() {
        appLogger.info("AppViewModel", "ViewModel 即将销毁。")
        stopSessionStream()
        super.onCleared()
    }

    private fun submitApprovalForPending(
        detail: SessionDetail,
        approval: PendingApprovalUiState,
        decision: ApprovalDecision,
        autoTriggered: Boolean,
    ) {
        if (approval.isSubmitting) {
            return
        }

        viewModelScope.launch {
            appLogger.info(
                "AppViewModel",
                "提交审批决定：sessionId=${detail.id}, decision=${decision.wireValue}, requestId=${approval.requestId ?: "none"}, auto=$autoTriggered",
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
                applyApprovalResult(
                    result = result,
                    userVisibleMessage = if (autoTriggered) null else "已提交审批操作：${result.decision.label}",
                    lastEventText = if (autoTriggered) {
                        "手机端已自动授予当前会话权限。"
                    } else {
                        buildApprovalResultText(result)
                    },
                )
            } catch (error: Exception) {
                appLogger.error("AppViewModel", "提交审批失败：sessionId=${detail.id}", error)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = error.message ?: "提交审批操作失败。",
                        sessionRealtimeState = it.sessionRealtimeState.copy(
                            pendingApproval = approval.copy(isSubmitting = false),
                            fallbackNotice = if (autoTriggered) {
                                "自动放行失败，请手动重试这次审批。"
                            } else {
                                it.sessionRealtimeState.fallbackNotice
                            },
                        ),
                    )
                }
                refreshDiagnosticsLog()
            }
        }
    }

    private fun maybeAutoApprovePending(sessionId: String) {
        val detail = uiState.value.selectedSession?.takeIf { it.id == sessionId } ?: return
        val approval = uiState.value.sessionRealtimeState.pendingApproval ?: return
        if (detail.status != "awaiting_approval") {
            return
        }
        if (detail.approvalMode != ManagedApprovalMode) {
            return
        }

        submitApprovalForPending(
            detail = detail,
            approval = approval,
            decision = ApprovalDecision.ApproveForSession,
            autoTriggered = true,
        )
    }

    private fun applyApprovalResult(
        result: ApprovalActionResult,
        userVisibleMessage: String?,
        lastEventText: String,
    ) {
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
                        pendingApproval = null,
                        subtitle = buildSessionSubtitle(
                            model = it.selectedSession.model,
                            approvalMode = it.selectedSession.approvalMode,
                            status = result.status,
                        ),
                    ),
                message = userVisibleMessage,
                queuedInputs = queuedInputsFor(it.selectedSession?.id),
                sessionRealtimeState = it.sessionRealtimeState.copy(
                    statusText = localizedSessionStatus(result.status),
                    lastEventText = lastEventText,
                    fallbackNotice = null,
                    pendingApproval = null,
                ),
            )
        }
        refreshDiagnosticsLog()
    }

    private fun applySessionGoalResponse(
        sessionId: String,
        capability: String,
        goal: SessionGoalSnapshot?,
        userVisibleMessage: String,
        lastEventText: String,
    ) {
        _uiState.update {
            if (it.selectedSession?.id != sessionId) {
                it.copy(isLoading = false, message = userVisibleMessage)
            } else {
                it.copy(
                    isLoading = false,
                    message = userVisibleMessage,
                    selectedSession = it.selectedSession.copy(
                        goal = goal,
                        goalCapability = capability,
                        lastUpdated = goal?.updatedAt?.takeIf { updated -> updated.isNotBlank() }
                            ?: it.selectedSession.lastUpdated,
                    ),
                    sessionRealtimeState = it.sessionRealtimeState.copy(
                        lastEventText = lastEventText,
                    ),
                )
            }
        }
        refreshDiagnosticsLog()
    }

    private suspend fun submitInputNow(
        detail: SessionDetail,
        text: String,
        pendingImageAttachments: List<PendingImageAttachmentUiState>,
        fromQueue: Boolean,
    ) {
        appLogger.info(
            "AppViewModel",
            "提交输入到 bridge：sessionId=${detail.id}, textLength=${text.length}, imageCount=${pendingImageAttachments.size}, fromQueue=$fromQueue",
        )
        val uploadedImages = pendingImageAttachments.map { pendingImageAttachment ->
            val stagedPath = pendingImageAttachment.stagedPath
                ?: throw IllegalStateException("图片尚未完成预上传：${pendingImageAttachment.displayName}")
            UploadedImageAttachment(
                id = pendingImageAttachment.localId,
                displayName = pendingImageAttachment.displayName,
                mimeType = pendingImageAttachment.mimeType,
                stagedPath = stagedPath,
                savedPath = pendingImageAttachment.savedPath,
            )
        }
        val attachmentRefs = uploadedImages.map { uploaded ->
            SessionInputAttachmentRef(stagedPath = uploaded.attachmentPath)
        }
        bridgeApi.sendInput(
            detail.id,
            SendInputRequest(
                text = text.takeIf { it.isNotBlank() },
                attachments = attachmentRefs,
            ),
        )
        activeAssistantTurnId = null
        clearPendingImageUploads()
        val updatedDetail = appendUserMessage(
            detail = detail,
            message = text,
            uploadedImages = uploadedImages,
        )
        _uiState.update {
            it.copy(
                isLoading = false,
                selectedSession = updatedDetail,
                draftMessage = "",
                pendingImageAttachments = emptyList(),
                message = if (fromQueue) {
                    "已发送排队消息。"
                } else if (pendingImageAttachments.isNotEmpty()) {
                    "图片已发送，等待 Codex 回复。"
                } else {
                    "消息已发送，等待实时输出。"
                },
                queuedInputs = queuedInputsFor(detail.id),
                sessionRealtimeState = it.sessionRealtimeState.copy(
                    statusText = localizedSessionStatus("running"),
                    isInterrupting = false,
                    lastEventText = if (fromQueue) {
                        "已发送一条排队消息，等待 Codex 回复。"
                    } else if (pendingImageAttachments.isNotEmpty()) {
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

    private suspend fun resolvePendingImageAttachmentsForSession(
        sessionId: String,
        pendingImageAttachments: List<PendingImageAttachmentUiState>,
    ): List<PendingImageAttachmentUiState> {
        if (pendingImageAttachments.isEmpty()) {
            return pendingImageAttachments
        }

        return pendingImageAttachments.map { attachment ->
            if (attachment.savedPath != null) {
                return@map attachment
            }

            val originalRequest = pendingImageUploadRequests[attachment.localId]
                ?: return@map attachment
            if (!originalRequest.sessionId.isNullOrBlank()) {
                return@map attachment
            }

            val saveRequest = originalRequest.copy(sessionId = sessionId)
            appLogger.info(
                "AppViewModel",
                "草稿会话图片转正式保存：localId=${attachment.localId}, sessionId=$sessionId",
            )
            val savedUpload = bridgeApi.uploadImageAttachment(saveRequest)
            pendingImageUploadRequests[attachment.localId] = saveRequest
            attachment.copy(
                previewSource = buildBridgeImageSource(savedUpload.attachmentPath),
                stagedPath = savedUpload.stagedPath,
                savedPath = savedUpload.savedPath,
                uploadError = null,
            )
        }
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

    private fun canInterruptSession(
        detail: SessionDetail?,
        realtimeState: SessionRealtimeUiState,
    ): Boolean {
        if (detail == null || detail.status == "draft") {
            return false
        }
        return realtimeState.pendingApproval != null ||
            detail.status == "awaiting_approval" ||
            detail.status == "running"
    }

    private suspend fun resolveSessionDetailBeforeSending(
        detail: SessionDetail,
        realtimeState: SessionRealtimeUiState,
    ): SessionDetail {
        if (!shouldRefreshBusyState(detail, realtimeState)) {
            return detail
        }

        appLogger.info(
            "AppViewModel",
            "发送前先同步会话状态：sessionId=${detail.id}, localStatus=${detail.status}",
        )
        return refreshSessionSnapshot(detail.id)
            ?: uiState.value.selectedSession?.takeIf { it.id == detail.id }
            ?: detail
    }

    private fun shouldRefreshBusyState(
        detail: SessionDetail?,
        realtimeState: SessionRealtimeUiState,
    ): Boolean {
        if (detail == null) {
            return false
        }
        if (realtimeState.pendingApproval != null) {
            return false
        }
        if (realtimeState.isConnected) {
            return false
        }
        return detail.status == "running" || detail.status == "awaiting_approval"
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
                pendingImageAttachments = emptyList(),
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
        savedConnections = settings.savedConnections,
        selectedConnectionId = settings.selectedConnectionId ?: settings.savedConnections.first().id,
        cwdInput = settings.cwd,
        modelInput = settings.model,
        approvalModeInput = settings.approvalMode,
        reasoningEffortInput = settings.reasoningEffort,
        serviceTierInput = settings.serviceTier,
        sandboxModeInput = settings.sandboxMode,
        fontSizeInput = settings.fontSize,
        settingsItems = defaultSettingsItems(
            savedConnections = settings.savedConnections,
            selectedConnectionId = settings.selectedConnectionId ?: settings.savedConnections.first().id,
            endpointInput = settings.endpoint,
            authTokenInput = settings.authToken,
            cwdInput = settings.cwd,
            modelInput = settings.model,
            approvalModeInput = settings.approvalMode,
            reasoningEffortInput = settings.reasoningEffort,
            serviceTierInput = settings.serviceTier,
            sandboxModeInput = settings.sandboxMode,
            fontSizeInput = settings.fontSize,
        ),
    )
}

private fun AppUiState.withSettingsItems(): AppUiState {
    return copy(
        settingsItems = defaultSettingsItems(
            connectionState = connectionState,
            savedConnections = savedConnections,
            selectedConnectionId = selectedConnectionId,
            endpointInput = endpointInput,
            authTokenInput = authTokenInput,
            cwdInput = cwdInput,
            modelInput = modelInput,
            approvalModeInput = approvalModeInput,
            reasoningEffortInput = reasoningEffortInput,
            serviceTierInput = serviceTierInput,
            sandboxModeInput = sandboxModeInput,
            fontSizeInput = fontSizeInput,
        ),
    )
}

private fun AppSettings.sanitize(): AppSettings {
    val fallbackConnection = SavedBridgeConnection(
        id = selectedConnectionId ?: "default-connection",
        name = defaultConnectionName(0),
        endpoint = endpoint,
        authToken = authToken,
    )
    val normalizedConnections = savedConnections
        .ifEmpty { listOf(fallbackConnection) }
        .mapIndexed { index, connection ->
            val connectionId = connection.id.ifBlank {
                if (index == 0) {
                    fallbackConnection.id
                } else {
                    "connection-${index + 1}"
                }
            }
            connection.copy(
                id = connectionId,
                name = connection.name.trim().ifBlank { defaultConnectionName(index) },
                endpoint = connection.endpoint.trim(),
            )
        }
    val resolvedSelectedConnection = normalizedConnections.firstOrNull { it.id == selectedConnectionId }
        ?: normalizedConnections.first()
    return copy(
        endpoint = resolvedSelectedConnection.endpoint.trim(),
        authToken = resolvedSelectedConnection.authToken,
        cwd = cwd.trim(),
        model = model.trim().ifEmpty { "gpt-5.5" },
        approvalMode = ManagedApprovalMode,
        reasoningEffort = normalizeReasoningEffort(reasoningEffort),
        serviceTier = normalizeServiceTier(serviceTier),
        sandboxMode = ManagedSandboxMode,
        fontSize = normalizeFontSize(fontSize),
        savedConnections = normalizedConnections,
        selectedConnectionId = resolvedSelectedConnection.id,
    )
}

private fun enforceManagedSessionSummaryLocally(session: SessionSummary): SessionSummary {
    return session.copy(
        approvalMode = ManagedApprovalMode,
        sandboxMode = ManagedSandboxMode,
        subtitle = "${session.model} • ${localizedSessionStatus(session.status)} • ${session.cwd}",
    )
}

private fun enforceManagedSessionDetailLocally(detail: SessionDetail): SessionDetail {
    return detail.copy(
        approvalMode = ManagedApprovalMode,
        sandboxMode = ManagedSandboxMode,
        subtitle = buildSessionSubtitle(
            model = detail.model,
            approvalMode = ManagedApprovalMode,
            status = detail.status,
        ),
    )
}

private fun PendingApprovalUiState.toSnapshot() = PendingApprovalSnapshot(
    requestId = requestId,
    method = method,
    paramsSummary = paramsSummary,
)

private fun PendingApprovalSnapshot.toUiState() = PendingApprovalUiState(
    requestId = requestId,
    method = method,
    paramsSummary = paramsSummary,
)

private fun buildOrUpdateSessionFromStart(
    current: SessionDetail?,
    event: SessionStreamEvent.SessionStarted,
): SessionDetail {
    val model = event.model ?: current?.model ?: "gpt-5.5"
    val approvalMode = ManagedApprovalMode
    val reasoningEffort = event.reasoningEffort ?: current?.reasoningEffort ?: "medium"
    val serviceTier = event.serviceTier ?: current?.serviceTier ?: "default"
    val sandboxMode = ManagedSandboxMode
    val cwd = event.cwd ?: current?.cwd ?: ""
    val normalizedStatus = normalizeSessionStatus(
        status = event.status,
        incomingPendingApproval = event.pendingApproval,
        currentPendingApproval = current?.pendingApproval,
        fallbackStatus = current?.status,
    )

    if (current != null && current.id == event.sessionId) {
        return current.copy(
            cwd = cwd,
            model = model,
            approvalMode = approvalMode,
            reasoningEffort = reasoningEffort,
            serviceTier = serviceTier,
            sandboxMode = sandboxMode,
            status = normalizedStatus,
            goal = event.goal ?: current.goal,
            goalCapability = event.goalCapability ?: current.goalCapability,
            pendingApproval = if (normalizedStatus == "awaiting_approval") {
                event.pendingApproval ?: current.pendingApproval
            } else {
                null
            },
            subtitle = buildSessionSubtitle(
                model = model,
                approvalMode = approvalMode,
                status = normalizedStatus,
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
            status = normalizedStatus,
        ),
        lastUpdated = event.timestamp ?: nowIsoString(),
        transcriptPreview = transcript,
        cwd = cwd,
        model = model,
        approvalMode = approvalMode,
        reasoningEffort = reasoningEffort,
        serviceTier = serviceTier,
        sandboxMode = sandboxMode,
        status = normalizedStatus,
        goal = event.goal,
        goalCapability = event.goalCapability ?: "unknown",
        pendingApproval = event.pendingApproval.takeIf { normalizedStatus == "awaiting_approval" },
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
        approvalMode = ManagedApprovalMode,
        reasoningEffort = reasoningEffort,
        serviceTier = serviceTier,
        sandboxMode = ManagedSandboxMode,
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
    val approvalMode = ManagedApprovalMode
    val reasoningEffort = incoming.reasoningEffort.takeUnless { it.isBlank() || it == "unknown" }
        ?: current?.reasoningEffort
        ?: "medium"
    val serviceTier = incoming.serviceTier.takeUnless { it.isBlank() || it == "unknown" }
        ?: current?.serviceTier
        ?: "default"
    val sandboxMode = ManagedSandboxMode
    val cwd = incoming.cwd.takeUnless { it.isBlank() || it == "未提供工作目录" }
        ?: current?.cwd
        ?: ""
    val rawStatus = incoming.status.takeUnless { it.isBlank() || it == "unknown" }
        ?: current?.status
        ?: "idle"
    val status = normalizeSessionStatus(
        status = rawStatus,
        incomingPendingApproval = incoming.pendingApproval,
        currentPendingApproval = current?.pendingApproval,
        fallbackStatus = current?.status,
    )
    val transcriptPreview = chooseMoreCompleteTranscript(
        current = current?.transcriptPreview,
        incoming = incoming.transcriptPreview,
    )

    return incoming.copy(
        transcriptPreview = transcriptPreview,
        cwd = cwd,
        model = model,
        approvalMode = approvalMode,
        reasoningEffort = reasoningEffort,
        serviceTier = serviceTier,
        sandboxMode = sandboxMode,
        goal = incoming.goal ?: current?.goal,
        goalCapability = incoming.goalCapability.takeUnless { it == "unknown" } ?: current?.goalCapability ?: "unknown",
        pendingApproval = if (status == "awaiting_approval") {
            incoming.pendingApproval ?: current?.pendingApproval
        } else {
            null
        },
        status = status,
        subtitle = buildSessionSubtitle(
            model = model,
            approvalMode = approvalMode,
            status = status,
        ),
    )
}

private fun mergeSessionConfigUpdateResult(
    current: SessionDetail?,
    incoming: SessionDetail,
    requestedChange: RequestedSessionConfigChange,
): SessionDetail {
    val existing = current ?: return incoming
    val cwd = if (requestedChange.cwd != null) {
        incoming.cwd.takeUnless { it.isBlank() || it == "未提供工作目录" }
            ?: requestedChange.cwd
    } else {
        existing.cwd
    }
    val model = if (requestedChange.model != null) {
        incoming.model.takeUnless { it.isBlank() || it == "未知模型" }
            ?: requestedChange.model
    } else {
        existing.model
    }
    val approvalMode = ManagedApprovalMode
    val reasoningEffort = if (requestedChange.reasoningEffort != null) {
        incoming.reasoningEffort.takeUnless { it.isBlank() || it == "unknown" }
            ?: requestedChange.reasoningEffort
    } else {
        existing.reasoningEffort
    }
    val serviceTier = if (requestedChange.serviceTier != null) {
        incoming.serviceTier.takeUnless { it.isBlank() || it == "unknown" }
            ?: requestedChange.serviceTier
    } else {
        existing.serviceTier
    }
    val sandboxMode = ManagedSandboxMode
    val transcriptPreview = chooseMoreCompleteTranscript(
        current = existing.transcriptPreview,
        incoming = incoming.transcriptPreview,
    )

    return existing.copy(
        title = incoming.title.takeUnless { it.isBlank() } ?: existing.title,
        lastUpdated = incoming.lastUpdated.takeUnless { it.isBlank() } ?: existing.lastUpdated,
        transcriptPreview = transcriptPreview,
        cwd = cwd,
        model = model,
        approvalMode = approvalMode,
        reasoningEffort = reasoningEffort,
        serviceTier = serviceTier,
        sandboxMode = sandboxMode,
        goal = incoming.goal ?: existing.goal,
        goalCapability = incoming.goalCapability.takeUnless { it == "unknown" } ?: existing.goalCapability,
        pendingApproval = if (existing.status == "awaiting_approval") {
            incoming.pendingApproval ?: existing.pendingApproval
        } else {
            null
        },
        subtitle = buildSessionSubtitle(
            model = model,
            approvalMode = approvalMode,
            status = existing.status,
        ),
    )
}

private fun normalizeSessionStatus(
    status: String,
    incomingPendingApproval: PendingApprovalSnapshot?,
    currentPendingApproval: PendingApprovalSnapshot?,
    fallbackStatus: String?,
): String {
    if (status != "awaiting_approval") {
        return status
    }

    if (incomingPendingApproval != null || currentPendingApproval != null) {
        return status
    }

    return when (fallbackStatus) {
        "running", "idle", "error" -> fallbackStatus
        else -> "running"
    }
}

private fun chooseMoreCompleteTranscript(
    current: String?,
    incoming: String,
): String {
    val currentText = current?.trim().orEmpty()
    val incomingText = incoming.trim()

    if (incomingText.isBlank()) {
        return currentText
    }
    if (currentText.isBlank()) {
        return incomingText
    }
    if (incomingText == currentText) {
        return incomingText
    }
    if (incomingText.contains(currentText)) {
        return incomingText
    }
    if (currentText.contains(incomingText)) {
        return currentText
    }

    val currentScore = transcriptCompletenessScore(currentText)
    val incomingScore = transcriptCompletenessScore(incomingText)
    return when {
        incomingScore > currentScore -> incomingText
        currentScore > incomingScore -> currentText
        incomingText.length >= currentText.length -> incomingText
        else -> currentText
    }
}

private fun transcriptCompletenessScore(transcript: String): Int {
    val normalized = transcript.trim()
    if (normalized.isBlank()) {
        return 0
    }

    val conversationBlocks = listOf(
        "你：",
        "Codex：",
        "系统：",
        "等待审批：",
        "审批结果：",
    ).sumOf { marker ->
        Regex(Regex.escape(marker)).findAll(normalized).count()
    }
    val metadataLines = listOf(
        "工作目录：",
        "线程 ID：",
        "当前轮次：",
        "最近错误：",
        "预览：",
    ).count { marker ->
        normalized.lines().any { line -> line.startsWith(marker) }
    }

    return (conversationBlocks * 10) + normalized.length - (metadataLines * 3)
}

private fun appendUserMessage(
    detail: SessionDetail,
    message: String,
    uploadedImages: List<UploadedImageAttachment>,
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
        uploadedImages.forEachIndexed { index, image ->
            if (message.isNotBlank() || index > 0) {
                append("\n")
            }
            append("![")
            append(image.displayName)
            append("](")
            append(buildBridgeImageSource(image.attachmentPath))
            append(")")
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

private fun buildInlineImageSource(
    mimeType: String,
    contentBytes: ByteArray,
): String {
    val encoded = Base64.getEncoder().encodeToString(contentBytes)
    return "data:$mimeType;base64,$encoded"
}

private fun buildBridgeImageSource(stagedPath: String): String {
    val encodedPath = URLEncoder.encode(stagedPath, StandardCharsets.UTF_8.name())
    return "bridge-file://$encodedPath"
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

private fun SessionStreamEvent.Activity.toActivityEntry(): SessionActivityEntry? {
    val normalizedTitle = title?.trim().takeUnless { it.isNullOrEmpty() }
        ?: transcriptBlock
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?.removePrefix("系统：")
            ?.trim()
            ?.takeUnless { it.isEmpty() }
        ?: return null
    val normalizedBody = body?.trim().takeUnless { it.isNullOrEmpty() }
    val normalizedSummary = summary?.trim().takeUnless { it.isNullOrEmpty() }
        ?: normalizedBody
            ?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.isNotEmpty() }
            ?.take(120)
        ?: normalizedTitle
    val stableId = itemId?.trim().takeUnless { it.isNullOrEmpty() }
        ?: buildString {
            append(itemType?.trim().takeUnless { it.isNullOrEmpty() } ?: "system")
            append(":")
            append(normalizedTitle)
            append(":")
            append(timestamp ?: "live")
        }
    return SessionActivityEntry(
        stableId = stableId,
        itemType = itemType,
        itemId = itemId,
        title = normalizedTitle,
        body = normalizedBody,
        summary = normalizedSummary,
        transcriptBlock = transcriptBlock,
        updatedAt = timestamp,
    )
}

private fun upsertLiveExecutionActivity(
    current: List<SessionActivityEntry>,
    incoming: SessionActivityEntry,
): List<SessionActivityEntry> {
    val existingIndex = current.indexOfFirst { entry -> entry.stableId == incoming.stableId }
    if (existingIndex < 0) {
        return current + incoming
    }
    return current.mapIndexed { index, entry ->
        if (index != existingIndex) {
            entry
        } else {
            incoming
        }
    }
}

private fun summarizeActivityEvent(event: SessionStreamEvent.Activity): String {
    val summary = event.summary?.trim()
    if (!summary.isNullOrEmpty()) {
        return summary
    }

    val firstLine = event.transcriptBlock
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        ?: return "收到新的操作事件。"

    return firstLine.removePrefix("系统：").ifBlank { "收到新的操作事件。" }
}

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
    savedConnections: List<SavedBridgeConnection> = emptyList(),
    selectedConnectionId: String = "",
    endpointInput: String = "",
    authTokenInput: String = "",
    cwdInput: String = "",
    modelInput: String = "gpt-5.5",
    approvalModeInput: String = "manual",
    reasoningEffortInput: String = "medium",
    serviceTierInput: String = "default",
    sandboxModeInput: String = "workspace-write",
    fontSizeInput: String = "standard",
): List<Pair<String, String>> {
    val connectedState = connectionState as? BridgeConnectionState.Connected
    val selectedConnection = savedConnections.firstOrNull { it.id == selectedConnectionId }
    return listOf(
        "桥接模式" to "真实桥接",
        "当前连接" to (selectedConnection?.name?.ifBlank { "未命名连接" } ?: "未配置"),
        "已保存连接数" to savedConnections.size.toString(),
        "桥接地址" to (connectedState?.endpoint ?: endpointInput.ifBlank { "未配置" }),
        "鉴权令牌" to if (authTokenInput.isBlank()) "未配置" else "已配置",
        "默认工作目录" to cwdInput.ifBlank { "未配置" },
        "默认模型" to modelInput.ifBlank { "未配置" },
        "推理强度" to localizedReasoningEffort(reasoningEffortInput),
        "速度档位" to localizedServiceTier(serviceTierInput),
        "字体大小" to localizedFontSize(fontSizeInput),
        "文件权限" to localizedSandboxMode(sandboxModeInput),
        "审批模式" to localizedApprovalMode(approvalModeInput),
        "运行器" to (connectedState?.runnerMode ?: "未连接"),
        "遥测" to "已关闭",
        "应用日志" to "已开启（本地文件）",
    )
}

internal fun typeScaleForFontSize(value: String): Float {
    return when (normalizeFontSize(value)) {
        "small" -> 0.92f
        "large" -> 1.12f
        else -> 1.0f
    }
}

private fun normalizeFontSize(value: String): String {
    return when (value) {
        "small", "standard", "large" -> value
        else -> "standard"
    }
}

private fun List<SavedBridgeConnection>.replaceConnection(
    connectionId: String,
    transform: (SavedBridgeConnection) -> SavedBridgeConnection,
): List<SavedBridgeConnection> {
    return map { connection ->
        if (connection.id == connectionId) {
            transform(connection)
        } else {
            connection
        }
    }
}

private fun buildNextConnectionName(connections: List<SavedBridgeConnection>): String {
    return "连接 ${connections.size + 1}"
}

private fun defaultConnectionName(index: Int): String {
    return if (index == 0) {
        "默认连接"
    } else {
        "连接 ${index + 1}"
    }
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

private fun localizedFontSize(value: String): String {
    return when (normalizeFontSize(value)) {
        "small" -> "小"
        "large" -> "大"
        else -> "标准"
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
