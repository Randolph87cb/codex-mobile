package com.openai.codexmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openai.codexmobile.data.AppSettings
import com.openai.codexmobile.data.AppSettingsStore
import com.openai.codexmobile.data.ApprovalActionResult
import com.openai.codexmobile.data.ApprovalDecision
import com.openai.codexmobile.data.BridgeRequestId
import com.openai.codexmobile.data.CodexDataProvider
import com.openai.codexmobile.data.CreateSessionRequest
import com.openai.codexmobile.data.SessionStreamEvent
import com.openai.codexmobile.model.BridgeConnectionState
import com.openai.codexmobile.model.SessionDetail
import com.openai.codexmobile.model.SessionSummary
import com.openai.codexmobile.ui.CodexMobileApp
import com.openai.codexmobile.ui.theme.CodexMobileTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ReplayHarnessActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settingsStore = InMemoryAppSettingsStore(
            AppSettings(
                endpoint = "http://10.0.2.2:8787",
                authToken = "",
                cwd = "D:\\workspace\\codex-mobile",
                model = "gpt-5.5",
                approvalMode = "manual",
            ),
        )
        val dataProvider = DeterministicReplayDataProvider()

        setContent {
            CodexMobileTheme {
                val appViewModel: AppViewModel = viewModel(
                    factory = AppViewModelFactory(
                        bridgeApi = dataProvider,
                        sessionRepository = dataProvider,
                        settingsStore = settingsStore,
                    ),
                )
                CodexMobileApp(appViewModel = appViewModel)
            }
        }
    }
}

private class InMemoryAppSettingsStore(
    private var current: AppSettings,
) : AppSettingsStore {
    override fun load(): AppSettings = current

    override fun save(settings: AppSettings) {
        current = settings
    }
}

private class DeterministicReplayDataProvider : CodexDataProvider {
    private var connectionState: BridgeConnectionState = BridgeConnectionState.Disconnected
    private val requestId = BridgeRequestId.Text("instrumentation-request-1")
    private val session = SessionSummary(
        id = "session-test-001",
        title = "测试会话",
        subtitle = "用于稳定 UI 回放",
        lastUpdated = "刚刚更新",
        status = "idle",
    )
    private val detail = SessionDetail(
        id = session.id,
        title = session.title,
        subtitle = session.subtitle,
        lastUpdated = session.lastUpdated,
        transcriptPreview = buildString {
            appendLine("你：请给我一个示例脚本")
            appendLine()
            appendLine("Codex：这里是一段 Kotlin 示例。")
            appendLine("```kotlin")
            appendLine("println(\"hello from test\")")
            appendLine("```")
            appendLine()
            appendLine("审批结果：历史工具结果")
            append("这条记录用于校验工具结果渲染。")
        },
        status = session.status,
    )
    private val longApprovalSummary = buildString {
        appendLine("等待审批：执行测试命令")
        appendLine("命令：echo instrumentation")
        repeat(18) { index ->
            appendLine("参数说明 ${index + 1}：这里是一段很长的审批内容，用于确认长文本不会把按钮顶出可见区域。")
        }
    }.trim()

    override fun updateAuthToken(token: String) = Unit

    override suspend fun connect(endpoint: String): BridgeConnectionState {
        connectionState = BridgeConnectionState.Connected(
            endpoint = endpoint,
            service = "replay-harness",
            runnerMode = "instrumentation",
        )
        return connectionState
    }

    override suspend fun disconnect() {
        connectionState = BridgeConnectionState.Disconnected
    }

    override suspend fun currentConnection(): BridgeConnectionState = connectionState

    override suspend fun createSession(request: CreateSessionRequest): SessionDetail = detail

    override suspend fun sendInput(sessionId: String, text: String) = Unit

    override suspend fun approveSession(
        sessionId: String,
        requestId: BridgeRequestId?,
        decision: ApprovalDecision,
    ): ApprovalActionResult {
        return ApprovalActionResult(
            requestId = requestId ?: this.requestId,
            decision = decision,
            status = "running",
            method = "item/commandExecution/requestApproval",
        )
    }

    override fun observeSessionEvents(sessionId: String): Flow<SessionStreamEvent> {
        return flow {
            emit(SessionStreamEvent.StreamOpened(sessionId = sessionId))
            emit(
                SessionStreamEvent.ToolRequest(
                    sessionId = sessionId,
                    requestId = requestId,
                    method = "item/commandExecution/requestApproval",
                    paramsSummary = longApprovalSummary,
                    timestamp = "2026-05-19T02:40:00Z",
                ),
            )
        }
    }

    override suspend fun listSessions(): List<SessionSummary> = listOf(session)

    override suspend fun getSessionDetail(sessionId: String): SessionDetail? {
        return detail.takeIf { it.id == sessionId }
    }
}
