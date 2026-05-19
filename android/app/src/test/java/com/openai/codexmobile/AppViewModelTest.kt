package com.openai.codexmobile

import com.openai.codexmobile.data.AppSettings
import com.openai.codexmobile.data.AppSettingsStore
import com.openai.codexmobile.data.ApprovalActionResult
import com.openai.codexmobile.data.ApprovalDecision
import com.openai.codexmobile.data.BridgeApi
import com.openai.codexmobile.data.BridgeRequestId
import com.openai.codexmobile.data.CreateSessionRequest
import com.openai.codexmobile.data.SessionConfigUpdate
import com.openai.codexmobile.data.SessionRepository
import com.openai.codexmobile.data.SessionStreamEvent
import com.openai.codexmobile.diagnostics.AppLogger
import com.openai.codexmobile.model.BridgeConnectionState
import com.openai.codexmobile.model.SessionDetail
import com.openai.codexmobile.model.SessionSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun draftSessionCreatesRealSessionOnlyWhenFirstMessageIsSent() = runTest(dispatcher.scheduler) {
        val createdDetail = sampleDetail(
            id = "sess_draft",
            cwd = "D:\\workspace\\project-a",
            model = "gpt-5.5",
            status = "idle",
        )
        val bridgeApi = FakeBridgeApi(createdDetail = createdDetail)
        val repository = FakeSessionRepository(sessionSummaries = emptyList(), detailsById = emptyMap())
        val viewModel = AppViewModel(bridgeApi, repository, FakeAppSettingsStore(), FakeAppLogger())

        viewModel.startDraftSession("D:\\workspace\\project-a")
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.selectedDraftSession)
        assertNull(bridgeApi.lastCreateSessionRequest)

        viewModel.updateDraftMessage("第一条消息")
        viewModel.sendInput()
        advanceUntilIdle()

        assertEquals(
            CreateSessionRequest(
                cwd = "D:\\workspace\\project-a",
                model = "gpt-5.5",
                approvalMode = "manual",
                reasoningEffort = "medium",
                serviceTier = "fast",
            ),
            bridgeApi.lastCreateSessionRequest,
        )
        assertEquals(listOf("第一条消息"), bridgeApi.sentInputs)
        assertNull(viewModel.uiState.value.selectedDraftSession)
        assertEquals("sess_draft", viewModel.uiState.value.selectedSession?.id)
        assertTrue(
            viewModel.uiState.value.selectedSession?.transcriptPreview?.contains("你：第一条消息") == true,
        )
    }

    @Test
    fun sessionStartedEventUpdatesConfigFields() = runTest(dispatcher.scheduler) {
        val detail = sampleDetail(id = "sess_stream", status = "idle")
        val bridgeApi = FakeBridgeApi(createdDetail = detail)
        val repository = FakeSessionRepository(
            sessionSummaries = listOf(detail.toSummary()),
            detailsById = mapOf(detail.id to detail),
        )
        val viewModel = AppViewModel(bridgeApi, repository, FakeAppSettingsStore(), FakeAppLogger())

        viewModel.openSessionDetail("sess_stream")
        advanceUntilIdle()

        bridgeApi.emit(
            SessionStreamEvent.SessionStarted(
                sessionId = "sess_stream",
                status = "running",
                cwd = "D:\\workspace\\other",
                model = "gpt-5.5-coder",
                approvalMode = "auto",
                reasoningEffort = "high",
                serviceTier = "flex",
                threadId = "thread-1",
                timestamp = "2026-05-19T10:00:01Z",
            ),
        )
        advanceUntilIdle()

        val selected = viewModel.uiState.value.selectedSession
        assertEquals("D:\\workspace\\other", selected?.cwd)
        assertEquals("gpt-5.5-coder", selected?.model)
        assertEquals("auto", selected?.approvalMode)
        assertEquals("high", selected?.reasoningEffort)
        assertEquals("flex", selected?.serviceTier)
        assertEquals("进行中", viewModel.uiState.value.sessionRealtimeState.statusText)
    }

    @Test
    fun inputDuringApprovalIsQueuedAndSentAfterIdle() = runTest(dispatcher.scheduler) {
        val detail = sampleDetail(id = "sess_queue", status = "idle")
        val bridgeApi = FakeBridgeApi(createdDetail = detail)
        val repository = FakeSessionRepository(
            sessionSummaries = listOf(detail.toSummary()),
            detailsById = mapOf(detail.id to detail),
        )
        val viewModel = AppViewModel(bridgeApi, repository, FakeAppSettingsStore(), FakeAppLogger())

        viewModel.openSessionDetail("sess_queue")
        advanceUntilIdle()

        bridgeApi.emit(
            SessionStreamEvent.ToolRequest(
                sessionId = "sess_queue",
                requestId = BridgeRequestId.Text("req-queue"),
                method = "item/commandExecution/requestApproval",
                paramsSummary = "等待审批：长操作",
                timestamp = "2026-05-19T16:00:01.000Z",
            ),
        )
        advanceUntilIdle()

        viewModel.updateDraftMessage("这条消息应该排队")
        viewModel.sendInput()
        advanceUntilIdle()

        assertTrue(bridgeApi.sentInputs.isEmpty())
        assertEquals(listOf("这条消息应该排队"), viewModel.uiState.value.queuedInputs)

        bridgeApi.emit(
            SessionStreamEvent.RunStatus(
                sessionId = "sess_queue",
                status = "idle",
                timestamp = "2026-05-19T16:00:03.000Z",
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf("这条消息应该排队"), bridgeApi.sentInputs)
        assertTrue(viewModel.uiState.value.queuedInputs.isEmpty())
    }

    @Test
    fun updatingSessionConfigPersistsSettingsAndCallsBridge() = runTest(dispatcher.scheduler) {
        val detail = sampleDetail(id = "sess_config", status = "idle")
        val bridgeApi = FakeBridgeApi(createdDetail = detail)
        val repository = FakeSessionRepository(
            sessionSummaries = listOf(detail.toSummary()),
            detailsById = mapOf(detail.id to detail),
        )
        val settingsStore = FakeAppSettingsStore()
        val viewModel = AppViewModel(bridgeApi, repository, settingsStore, FakeAppLogger())

        viewModel.openSessionDetail("sess_config")
        advanceUntilIdle()

        viewModel.updateSelectedSessionModel("gpt-5.5-coder")
        viewModel.updateSelectedSessionReasoningEffort("high")
        viewModel.updateSelectedSessionServiceTier("flex")
        advanceUntilIdle()

        assertEquals(3, bridgeApi.sessionConfigUpdates.size)
        assertEquals("gpt-5.5-coder", settingsStore.saved.model)
        assertEquals("high", settingsStore.saved.reasoningEffort)
        assertEquals("flex", settingsStore.saved.serviceTier)
        assertEquals("gpt-5.5-coder", viewModel.uiState.value.selectedSession?.model)
        assertEquals("high", viewModel.uiState.value.selectedSession?.reasoningEffort)
        assertEquals("flex", viewModel.uiState.value.selectedSession?.serviceTier)
    }

    @Test
    fun snapshotWithUnknownFieldsDoesNotOverwriteKnownSessionConfig() = runTest(dispatcher.scheduler) {
        val detail = sampleDetail(
            id = "sess_known",
            model = "gpt-5.4",
            reasoningEffort = "high",
            serviceTier = "flex",
            status = "idle",
        )
        val bridgeApi = FakeBridgeApi(createdDetail = detail)
        val repository = FakeSessionRepository(
            sessionSummaries = listOf(detail.toSummary()),
            detailsById = mapOf(
                detail.id to detail,
                "${detail.id}#refresh" to detail.copy(
                    model = "未知模型",
                    approvalMode = "未知审批模式",
                    reasoningEffort = "unknown",
                    serviceTier = "unknown",
                ),
            ),
        )
        val viewModel = AppViewModel(bridgeApi, repository, FakeAppSettingsStore(), FakeAppLogger())

        viewModel.openSessionDetail("sess_known")
        advanceUntilIdle()

        bridgeApi.emit(
            SessionStreamEvent.RunInterrupted(
                sessionId = "sess_known",
                status = "idle",
                timestamp = "2026-05-19T18:00:00.000Z",
            ),
        )
        advanceUntilIdle()

        assertEquals("gpt-5.4", viewModel.uiState.value.selectedSession?.model)
        assertEquals("manual", viewModel.uiState.value.selectedSession?.approvalMode)
        assertEquals("high", viewModel.uiState.value.selectedSession?.reasoningEffort)
        assertEquals("flex", viewModel.uiState.value.selectedSession?.serviceTier)
    }

    @Test
    fun connectUsesConfiguredTokenAndUpdatesSettings() = runTest(dispatcher.scheduler) {
        val detail = sampleDetail(id = "sess_auth", status = "idle")
        val bridgeApi = FakeBridgeApi(createdDetail = detail)
        val repository = FakeSessionRepository(
            sessionSummaries = listOf(detail.toSummary()),
            detailsById = mapOf(detail.id to detail),
        )
        val settingsStore = FakeAppSettingsStore()
        val viewModel = AppViewModel(bridgeApi, repository, settingsStore, FakeAppLogger())

        viewModel.updateAuthTokenInput("bridge-secret")
        viewModel.connect()
        advanceUntilIdle()

        assertEquals("bridge-secret", bridgeApi.updatedAuthToken)
        assertEquals("bridge-secret", bridgeApi.authTokenAtConnect)
        assertTrue(
            viewModel.uiState.value.settingsItems.any { (label, value) ->
                label == "鉴权令牌" && value == "已配置"
            },
        )
        assertEquals("bridge-secret", settingsStore.saved.authToken)
    }

    @Test
    fun refreshAndClearDiagnosticsLogUpdatesUiState() = runTest(dispatcher.scheduler) {
        val detail = sampleDetail(id = "sess_logs", status = "idle")
        val appLogger = FakeAppLogger(initialLog = "初始日志")
        val viewModel = AppViewModel(
            bridgeApi = FakeBridgeApi(createdDetail = detail),
            sessionRepository = FakeSessionRepository(emptyList(), emptyMap()),
            settingsStore = FakeAppSettingsStore(),
            appLogger = appLogger,
        )

        viewModel.refreshDiagnosticsLog()

        assertTrue(viewModel.uiState.value.diagnosticsLog.contains("初始日志"))

        viewModel.clearDiagnosticsLog()

        assertEquals("暂无日志。", viewModel.uiState.value.diagnosticsLog)
    }
}

private class FakeBridgeApi(
    private val createdDetail: SessionDetail,
) : BridgeApi {
    private val events = MutableSharedFlow<SessionStreamEvent>(extraBufferCapacity = 16)
    private var currentDetail: SessionDetail = createdDetail
    val sentInputs = mutableListOf<String>()
    val approvalCalls = mutableListOf<ApprovalCall>()
    val sessionConfigUpdates = mutableListOf<SessionConfigUpdate>()
    var updatedAuthToken: String = ""
    var authTokenAtConnect: String = ""
    var lastCreateSessionRequest: CreateSessionRequest? = null

    override fun updateAuthToken(token: String) {
        updatedAuthToken = token
    }

    override suspend fun connect(endpoint: String): BridgeConnectionState {
        authTokenAtConnect = updatedAuthToken
        return BridgeConnectionState.Connected(endpoint = endpoint)
    }

    override suspend fun disconnect() = Unit

    override suspend fun currentConnection(): BridgeConnectionState = BridgeConnectionState.Disconnected

    override suspend fun createSession(request: CreateSessionRequest): SessionDetail {
        lastCreateSessionRequest = request
        currentDetail = createdDetail.copy(
            cwd = request.cwd,
            model = request.model,
            approvalMode = request.approvalMode,
            reasoningEffort = request.reasoningEffort,
            serviceTier = request.serviceTier,
        )
        return currentDetail
    }

    override suspend fun updateSessionConfig(
        sessionId: String,
        update: SessionConfigUpdate,
    ): SessionDetail {
        sessionConfigUpdates += update
        currentDetail = currentDetail.copy(
            cwd = update.cwd ?: currentDetail.cwd,
            model = update.model ?: currentDetail.model,
            approvalMode = update.approvalMode ?: currentDetail.approvalMode,
            reasoningEffort = update.reasoningEffort ?: currentDetail.reasoningEffort,
            serviceTier = update.serviceTier ?: currentDetail.serviceTier,
        )
        return currentDetail
    }

    override suspend fun sendInput(sessionId: String, text: String) {
        sentInputs += text
    }

    override suspend fun approveSession(
        sessionId: String,
        requestId: BridgeRequestId?,
        decision: ApprovalDecision,
    ): ApprovalActionResult {
        approvalCalls += ApprovalCall(sessionId, requestId, decision)
        return ApprovalActionResult(
            requestId = requestId ?: BridgeRequestId.Text("req-fake"),
            decision = decision,
            status = "running",
            method = "item/commandExecution/requestApproval",
        )
    }

    override fun observeSessionEvents(sessionId: String): Flow<SessionStreamEvent> = events

    suspend fun emit(event: SessionStreamEvent) {
        events.emit(event)
    }
}

private data class ApprovalCall(
    val sessionId: String,
    val requestId: BridgeRequestId?,
    val decision: ApprovalDecision,
)

private class FakeSessionRepository(
    private val sessionSummaries: List<SessionSummary>,
    private val detailsById: Map<String, SessionDetail>,
) : SessionRepository {
    private val getCounts = mutableMapOf<String, Int>()

    override suspend fun listSessions(): List<SessionSummary> = sessionSummaries

    override suspend fun getSessionDetail(sessionId: String): SessionDetail? {
        val count = getCounts.getOrDefault(sessionId, 0)
        getCounts[sessionId] = count + 1
        return detailsById["$sessionId#refresh"].takeIf { count > 0 } ?: detailsById[sessionId]
    }
}

private class FakeAppSettingsStore(
    initial: AppSettings = AppSettings(
        endpoint = "http://10.0.2.2:8787",
        authToken = "",
        cwd = "D:\\workspace\\codex-mobile",
        model = "gpt-5.5",
        approvalMode = "manual",
        reasoningEffort = "medium",
        serviceTier = "fast",
    ),
) : AppSettingsStore {
    var saved: AppSettings = initial
        private set

    override fun load(): AppSettings = saved

    override fun save(settings: AppSettings) {
        saved = settings
    }
}

private class FakeAppLogger(
    initialLog: String = "暂无日志。",
) : AppLogger {
    private var content = initialLog

    override fun debug(tag: String, message: String) = append(tag, message)

    override fun info(tag: String, message: String) = append(tag, message)

    override fun warn(tag: String, message: String) = append(tag, message)

    override fun error(tag: String, message: String, throwable: Throwable?) {
        append(tag, buildString {
            append(message)
            throwable?.message?.let { append(": ").append(it) }
        })
    }

    override fun readRecentLogs(maxChars: Int): String {
        return if (content.isBlank()) "暂无日志。" else content.takeLast(maxChars)
    }

    override fun clear() {
        content = ""
    }

    override fun installCrashHandler() = Unit

    private fun append(tag: String, message: String) {
        val line = "[$tag] $message"
        content = if (content.isBlank() || content == "暂无日志。") {
            line
        } else {
            "$content\n$line"
        }
    }
}

private fun sampleDetail(
    id: String,
    cwd: String = "D:\\workspace\\codex-mobile",
    model: String = "gpt-5.5",
    approvalMode: String = "manual",
    reasoningEffort: String = "medium",
    serviceTier: String = "fast",
    status: String,
): SessionDetail {
    return SessionDetail(
        id = id,
        title = "测试会话",
        subtitle = "$model • 手动 • 空闲",
        lastUpdated = "2026-05-19T10:00:00.000Z",
        transcriptPreview = "工作目录：$cwd",
        cwd = cwd,
        model = model,
        approvalMode = approvalMode,
        reasoningEffort = reasoningEffort,
        serviceTier = serviceTier,
        status = status,
    )
}

private fun SessionDetail.toSummary(): SessionSummary {
    return SessionSummary(
        id = id,
        title = title,
        subtitle = "$model • $status • $cwd",
        lastUpdated = lastUpdated,
        cwd = cwd,
        model = model,
        approvalMode = approvalMode,
        reasoningEffort = reasoningEffort,
        serviceTier = serviceTier,
        status = status,
    )
}
