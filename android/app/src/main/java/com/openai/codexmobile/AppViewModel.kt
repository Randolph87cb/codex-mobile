package com.openai.codexmobile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.openai.codexmobile.data.BridgeApi
import com.openai.codexmobile.data.CreateSessionRequest
import com.openai.codexmobile.data.SessionRepository
import com.openai.codexmobile.model.BridgeConnectionState
import com.openai.codexmobile.model.SessionDetail
import com.openai.codexmobile.model.SessionSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppUiState(
    val endpointInput: String = "http://192.168.31.66:8787",
    val connectionState: BridgeConnectionState = BridgeConnectionState.Disconnected,
    val sessions: List<SessionSummary> = emptyList(),
    val selectedSession: SessionDetail? = null,
    val draftMessage: String = "",
    val isLoading: Boolean = false,
    val message: String? = null,
    val settingsItems: List<Pair<String, String>> = defaultSettingsItems(),
)

class AppViewModel(
    private val bridgeApi: BridgeApi,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        refreshConnection()
    }

    fun updateEndpointInput(value: String) {
        _uiState.update { it.copy(endpointInput = value) }
    }

    fun updateDraftMessage(value: String) {
        _uiState.update { it.copy(draftMessage = value) }
    }

    fun connect() {
        viewModelScope.launch {
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
                        settingsItems = defaultSettingsItems(connectionState),
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
                        settingsItems = defaultSettingsItems(),
                    )
                }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            bridgeApi.disconnect()
            _uiState.update {
                it.copy(
                    connectionState = BridgeConnectionState.Disconnected,
                    sessions = emptyList(),
                    selectedSession = null,
                    message = "已断开连接。",
                    settingsItems = defaultSettingsItems(),
                )
            }
        }
    }

    fun loadSession(sessionId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val detail = sessionRepository.getSessionDetail(sessionId)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        selectedSession = detail,
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
        val sessionId = uiState.value.selectedSession?.id ?: return
        val text = uiState.value.draftMessage.trim()
        if (text.isEmpty()) {
            return
        }

        viewModelScope.launch {
            val previousDetail = uiState.value.selectedSession
            _uiState.update { it.copy(isLoading = true, message = null) }
            try {
                bridgeApi.sendInput(sessionId, text)
                val detail = awaitSessionRefresh(sessionId, previousDetail)
                val sessions = sessionRepository.listSessions()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        selectedSession = detail ?: it.selectedSession,
                        sessions = sessions,
                        draftMessage = "",
                        message = "消息已发送。",
                    )
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

    private fun refreshConnection() {
        viewModelScope.launch {
            val connectionState = bridgeApi.currentConnection()
            _uiState.update {
                it.copy(
                    connectionState = connectionState,
                    settingsItems = defaultSettingsItems(connectionState),
                )
            }
        }
    }

    private suspend fun awaitSessionRefresh(
        sessionId: String,
        previousDetail: SessionDetail?,
    ): SessionDetail? {
        var latestDetail = previousDetail
        repeat(30) { attempt ->
            val detail = sessionRepository.getSessionDetail(sessionId)
            if (detail != null) {
                latestDetail = detail
            }

            val hasChanged = detail != null && (
                detail.transcriptPreview != previousDetail?.transcriptPreview ||
                    detail.lastUpdated != previousDetail?.lastUpdated
                )
            val isSettled = detail != null && detail.status != "running"
            val hasVisibleReply = detail != null && detail.transcriptPreview.contains("Codex：")
            if (detail != null && hasChanged && (isSettled || hasVisibleReply)) {
                return detail
            }

            if (attempt < 29) {
                delay(1000)
            }
        }

        return latestDetail
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
): List<Pair<String, String>> {
    val connectedState = connectionState as? BridgeConnectionState.Connected
    return listOf(
        "桥接模式" to when (connectedState?.provider) {
            "fake-fallback" -> "本地模拟"
            else -> "真实桥接"
        },
        "桥接地址" to (connectedState?.endpoint ?: "http://192.168.31.66:8787"),
        "运行器" to (connectedState?.runnerMode ?: "未知"),
        "遥测" to "已关闭",
    )
}

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
