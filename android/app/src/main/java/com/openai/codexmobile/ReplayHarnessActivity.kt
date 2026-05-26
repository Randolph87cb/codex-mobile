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
import com.openai.codexmobile.data.SendInputRequest
import com.openai.codexmobile.data.SessionGoalClearResult
import com.openai.codexmobile.data.SessionGoalResponse
import com.openai.codexmobile.data.SessionGoalUpdateRequest
import com.openai.codexmobile.data.SessionStreamEvent
import com.openai.codexmobile.data.UploadImageAttachmentRequest
import com.openai.codexmobile.data.UploadedImageAttachment
import com.openai.codexmobile.diagnostics.FileAppLogger
import com.openai.codexmobile.model.AccountQuotaCreditsSnapshot
import com.openai.codexmobile.model.AccountQuotaSnapshot
import com.openai.codexmobile.model.AccountQuotaWindowSnapshot
import com.openai.codexmobile.model.BridgeConnectionState
import com.openai.codexmobile.model.SessionDetail
import com.openai.codexmobile.model.SessionGoalSnapshot
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
                approvalMode = "auto",
                reasoningEffort = "medium",
                serviceTier = "default",
                sandboxMode = "danger-full-access",
            ),
        )
        val dataProvider = DeterministicReplayDataProvider()
        val appLogger = FileAppLogger(applicationContext).also {
            it.info("ReplayHarness", "启动 UI 回放模式。")
        }

        setContent {
            CodexMobileTheme {
                val appViewModel: AppViewModel = viewModel(
                    factory = AppViewModelFactory(
                        bridgeApi = dataProvider,
                        sessionRepository = dataProvider,
                        settingsStore = settingsStore,
                        appLogger = appLogger,
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
    private var goalSnapshot = SessionGoalSnapshot(
        objective = "检查手机端目标卡片与审批卡片的排版",
        status = "active",
        tokenBudget = 120000L,
        tokensUsed = 3400L,
        timeUsedSeconds = 180L,
        createdAt = "2026-05-22T09:00:00Z",
        updatedAt = "2026-05-22T09:03:00Z",
    )
    private var session = SessionSummary(
        id = "session-test-001",
        title = "测试会话",
        subtitle = "用于稳定 UI 回放",
        lastUpdated = "2026-05-26T09:45:00Z",
        cwd = "D:\\workspace\\codex-mobile",
        model = "gpt-5.5",
        approvalMode = "auto",
        reasoningEffort = "medium",
        serviceTier = "default",
        sandboxMode = "danger-full-access",
        status = "idle",
    )
    private var detail = SessionDetail(
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
        cwd = session.cwd,
        model = session.model,
        approvalMode = session.approvalMode,
        reasoningEffort = session.reasoningEffort,
        serviceTier = session.serviceTier,
        sandboxMode = session.sandboxMode,
        status = session.status,
        goal = goalSnapshot,
        goalCapability = "supported",
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

    override suspend fun getAccountQuota(): AccountQuotaSnapshot {
        return AccountQuotaSnapshot(
            limitId = "codex",
            planType = "prolite",
            fiveHours = AccountQuotaWindowSnapshot(
                usedPercent = 6,
                windowDurationMins = 300,
                resetsAt = "2026-05-25T11:51:54Z",
            ),
            oneWeek = AccountQuotaWindowSnapshot(
                usedPercent = 16,
                windowDurationMins = 10080,
                resetsAt = "2026-05-31T00:41:21Z",
            ),
            credits = AccountQuotaCreditsSnapshot(
                hasCredits = false,
                unlimited = false,
                balance = "0",
            ),
        )
    }

    override suspend fun createSession(request: CreateSessionRequest): SessionDetail = detail

    override suspend fun updateSessionConfig(
        sessionId: String,
        update: com.openai.codexmobile.data.SessionConfigUpdate,
    ): SessionDetail {
        detail = detail.copy(
            cwd = update.cwd ?: detail.cwd,
            model = update.model ?: detail.model,
            approvalMode = update.approvalMode ?: detail.approvalMode,
            reasoningEffort = update.reasoningEffort ?: detail.reasoningEffort,
            serviceTier = update.serviceTier ?: detail.serviceTier,
            sandboxMode = update.sandboxMode ?: detail.sandboxMode,
        )
        return detail
    }

    override suspend fun renameSessionTitle(sessionId: String, title: String): SessionDetail {
        val normalizedTitle = title.trim()
        session = session.copy(title = normalizedTitle)
        detail = detail.copy(title = normalizedTitle)
        return detail
    }

    override suspend fun getSessionGoal(sessionId: String): SessionGoalResponse {
        return SessionGoalResponse(
            capability = "supported",
            goal = goalSnapshot,
        )
    }

    override suspend fun updateSessionGoal(
        sessionId: String,
        request: SessionGoalUpdateRequest,
    ): SessionGoalResponse {
        goalSnapshot = goalSnapshot.copy(
            objective = request.objective ?: goalSnapshot.objective,
            status = request.status ?: goalSnapshot.status,
            tokenBudget = request.tokenBudget ?: goalSnapshot.tokenBudget,
            updatedAt = "2026-05-22T09:05:00Z",
        )
        detail = detail.copy(
            goal = goalSnapshot,
            goalCapability = "supported",
        )
        return SessionGoalResponse(
            capability = "supported",
            goal = goalSnapshot,
        )
    }

    override suspend fun clearSessionGoal(sessionId: String): SessionGoalClearResult {
        detail = detail.copy(
            goal = null,
            goalCapability = "supported",
        )
        return SessionGoalClearResult(
            capability = "supported",
            cleared = true,
        )
    }

    override suspend fun uploadImageAttachment(request: UploadImageAttachmentRequest): UploadedImageAttachment {
        return UploadedImageAttachment(
            id = "replay-image",
            displayName = request.displayName,
            mimeType = request.mimeType,
            stagedPath = "D:\\workspace\\codex-mobile\\.tmp\\replay-image.png",
        )
    }

    override suspend fun sendInput(sessionId: String, request: SendInputRequest) = Unit

    override suspend fun interruptSession(sessionId: String) = Unit

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

    override suspend fun listSessions(archived: Boolean): List<SessionSummary> = if (archived) emptyList() else listOf(session)

    override suspend fun getSessionDetail(sessionId: String): SessionDetail? {
        return detail.takeIf { it.id == sessionId }
    }

    override suspend fun archiveSession(sessionId: String) = Unit

    override suspend fun unarchiveSession(sessionId: String) = Unit
}
