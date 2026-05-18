package com.openai.codexmobile

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
import java.util.ArrayDeque

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
    fun detailStreamAppendsAssistantDeltaAndRefreshesFinalSnapshot() = runTest(dispatcher.scheduler) {
        val initialDetail = SessionDetail(
            id = "sess_1",
            title = "实时会话",
            subtitle = "gpt-5.5 • 手动批准 • 空闲",
            lastUpdated = "2026-05-19T10:00:00.000Z",
            transcriptPreview = "你：你好",
            status = "idle",
        )
        val finalDetail = initialDetail.copy(
            lastUpdated = "2026-05-19T10:00:05.000Z",
            transcriptPreview = "你：你好\n\nCodex：你好，我是实时流。",
            status = "idle",
        )
        val bridgeApi = FakeBridgeApi(createdDetail = initialDetail)
        val repository = FakeSessionRepository(
            sessionSummaries = listOf(sessionSummaryFor(initialDetail)),
            details = arrayDequeOf(initialDetail, finalDetail),
        )
        val viewModel = AppViewModel(bridgeApi, repository, FakeAppSettingsStore())

        viewModel.openSessionDetail("sess_1")
        advanceUntilIdle()

        bridgeApi.emit(
            SessionStreamEvent.StreamOpened(sessionId = "sess_1"),
        )
        bridgeApi.emit(
            SessionStreamEvent.SessionStarted(
                sessionId = "sess_1",
                status = "idle",
                cwd = "D:\\workspace\\codex-mobile",
                model = "gpt-5.5",
                threadId = "thread_1",
                timestamp = "2026-05-19T10:00:01.000Z",
            ),
        )
        bridgeApi.emit(
            SessionStreamEvent.RunStatus(
                sessionId = "sess_1",
                status = "running",
                timestamp = "2026-05-19T10:00:02.000Z",
            ),
        )
        bridgeApi.emit(
            SessionStreamEvent.AssistantDelta(
                sessionId = "sess_1",
                text = "你好，",
                turnId = "turn_1",
                timestamp = "2026-05-19T10:00:03.000Z",
            ),
        )
        bridgeApi.emit(
            SessionStreamEvent.AssistantDelta(
                sessionId = "sess_1",
                text = "我是实时流。",
                turnId = "turn_1",
                timestamp = "2026-05-19T10:00:04.000Z",
            ),
        )
        bridgeApi.emit(
            SessionStreamEvent.AssistantDone(
                sessionId = "sess_1",
                turnStatus = "completed",
                turnId = "turn_1",
                errorMessage = null,
                timestamp = "2026-05-19T10:00:05.000Z",
            ),
        )
        advanceUntilIdle()

        assertEquals("已连接实时流", viewModel.uiState.value.sessionRealtimeState.connectionText)
        assertEquals("空闲", viewModel.uiState.value.sessionRealtimeState.statusText)
        assertEquals("本轮回复已结束。", viewModel.uiState.value.sessionRealtimeState.lastEventText)
        assertEquals(finalDetail.transcriptPreview, viewModel.uiState.value.selectedSession?.transcriptPreview)
        assertEquals(2, repository.getSessionDetailCallCount)
    }

    @Test
    fun sendInputUsesRealtimeFlowAndInterruptedEventUpdatesUi() = runTest(dispatcher.scheduler) {
        val detail = SessionDetail(
            id = "sess_2",
            title = "中断测试",
            subtitle = "gpt-5.5 • 手动批准 • 空闲",
            lastUpdated = "2026-05-19T11:00:00.000Z",
            transcriptPreview = "工作目录：D:\\workspace\\codex-mobile",
            status = "idle",
        )
        val interruptedDetail = detail.copy(
            lastUpdated = "2026-05-19T11:00:02.000Z",
            transcriptPreview = "工作目录：D:\\workspace\\codex-mobile\n\n你：停止当前任务",
            status = "idle",
        )
        val bridgeApi = FakeBridgeApi(createdDetail = detail)
        val repository = FakeSessionRepository(
            sessionSummaries = listOf(sessionSummaryFor(detail)),
            details = arrayDequeOf(detail, interruptedDetail),
        )
        val viewModel = AppViewModel(bridgeApi, repository, FakeAppSettingsStore())

        viewModel.openSessionDetail("sess_2")
        advanceUntilIdle()

        viewModel.updateDraftMessage("停止当前任务")
        viewModel.sendInput()
        advanceUntilIdle()

        bridgeApi.emit(
            SessionStreamEvent.RunInterrupted(
                sessionId = "sess_2",
                status = "idle",
                timestamp = "2026-05-19T11:00:02.000Z",
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf("停止当前任务"), bridgeApi.sentInputs)
        assertTrue(
            viewModel.uiState.value.selectedSession?.transcriptPreview?.contains("你：停止当前任务") == true,
        )
        assertEquals("当前任务已中断。", viewModel.uiState.value.message)
        assertEquals("空闲", viewModel.uiState.value.sessionRealtimeState.statusText)
        assertEquals(2, repository.getSessionDetailCallCount)
    }

    @Test
    fun connectUsesConfiguredTokenAndUpdatesSettings() = runTest(dispatcher.scheduler) {
        val detail = SessionDetail(
            id = "sess_3",
            title = "鉴权测试",
            subtitle = "gpt-5.5 • 手动批准 • 空闲",
            lastUpdated = "2026-05-19T12:00:00.000Z",
            transcriptPreview = "工作目录：D:\\workspace\\codex-mobile",
            status = "idle",
        )
        val bridgeApi = FakeBridgeApi(createdDetail = detail)
        val repository = FakeSessionRepository(
            sessionSummaries = listOf(sessionSummaryFor(detail)),
            details = arrayDequeOf(detail),
        )
        val settingsStore = FakeAppSettingsStore()
        val viewModel = AppViewModel(bridgeApi, repository, settingsStore)

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
    fun pendingApprovalCanBeSubmittedFromRealtimeState() = runTest(dispatcher.scheduler) {
        val detail = SessionDetail(
            id = "sess_4",
            title = "审批测试",
            subtitle = "gpt-5.5 • 手动批准 • 空闲",
            lastUpdated = "2026-05-19T13:00:00.000Z",
            transcriptPreview = "工作目录：D:\\workspace\\codex-mobile",
            status = "idle",
        )
        val bridgeApi = FakeBridgeApi(createdDetail = detail)
        val repository = FakeSessionRepository(
            sessionSummaries = listOf(sessionSummaryFor(detail)),
            details = arrayDequeOf(detail),
        )
        val viewModel = AppViewModel(bridgeApi, repository, FakeAppSettingsStore())

        viewModel.openSessionDetail("sess_4")
        advanceUntilIdle()

        bridgeApi.emit(
            SessionStreamEvent.ToolRequest(
                sessionId = "sess_4",
                requestId = BridgeRequestId.Text("req-1"),
                method = "item/commandExecution/requestApproval",
                paramsSummary = "等待审批：item/commandExecution/requestApproval",
                timestamp = "2026-05-19T13:00:01.000Z",
            ),
        )
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.sessionRealtimeState.pendingApproval)
        assertEquals("等待批准", viewModel.uiState.value.sessionRealtimeState.statusText)

        viewModel.submitApproval(ApprovalDecision.Reject)
        advanceUntilIdle()

        assertEquals(1, bridgeApi.approvalCalls.size)
        val approvalCall = bridgeApi.approvalCalls.single()
        assertEquals("sess_4", approvalCall.sessionId)
        assertEquals("req-1", approvalCall.requestId?.toString())
        assertEquals(ApprovalDecision.Reject, approvalCall.decision)
        assertEquals("已提交审批操作：拒绝", viewModel.uiState.value.message)
        assertEquals("进行中", viewModel.uiState.value.sessionRealtimeState.statusText)
        assertEquals(null, viewModel.uiState.value.sessionRealtimeState.pendingApproval)
    }

    @Test
    fun createSessionUsesEditableSavedSettings() = runTest(dispatcher.scheduler) {
        val detail = SessionDetail(
            id = "sess_5",
            title = "配置测试",
            subtitle = "gpt-5.5 • 手动批准 • 空闲",
            lastUpdated = "2026-05-19T14:00:00.000Z",
            transcriptPreview = "工作目录：D:\\workspace\\codex-mobile",
            status = "idle",
        )
        val bridgeApi = FakeBridgeApi(createdDetail = detail)
        val repository = FakeSessionRepository(
            sessionSummaries = listOf(sessionSummaryFor(detail)),
            details = arrayDequeOf(detail),
        )
        val settingsStore = FakeAppSettingsStore(
            initial = AppSettings(
                endpoint = "http://10.0.2.2:8787",
                authToken = "bridge-secret",
                cwd = "D:\\workspace\\codex-mobile",
                model = "gpt-5.5",
                approvalMode = "manual",
            ),
        )
        val viewModel = AppViewModel(bridgeApi, repository, settingsStore)

        viewModel.updateCwdInput("D:\\workspace\\other-project")
        viewModel.updateModelInput("gpt-5.4")
        viewModel.updateApprovalModeInput("auto")
        viewModel.createSession()
        advanceUntilIdle()

        assertEquals(
            CreateSessionRequest(
                cwd = "D:\\workspace\\other-project",
                model = "gpt-5.4",
                approvalMode = "auto",
            ),
            bridgeApi.lastCreateSessionRequest,
        )
        assertEquals("D:\\workspace\\other-project", settingsStore.saved.cwd)
        assertEquals("gpt-5.4", settingsStore.saved.model)
        assertEquals("auto", settingsStore.saved.approvalMode)
    }

    @Test
    fun missingSessionClearsSelectedSessionWithoutStartingRealtimeStream() = runTest(dispatcher.scheduler) {
        val staleDetail = SessionDetail(
            id = "sess_stale",
            title = "旧会话",
            subtitle = "gpt-5.5 • 手动批准 • 空闲",
            lastUpdated = "2026-05-19T15:00:00.000Z",
            transcriptPreview = "你：旧内容",
            status = "idle",
        )
        val bridgeApi = FakeBridgeApi(createdDetail = staleDetail)
        val repository = FakeSessionRepository(
            sessionSummaries = listOf(sessionSummaryFor(staleDetail)),
            details = arrayDequeOf(staleDetail, null),
        )
        val viewModel = AppViewModel(bridgeApi, repository, FakeAppSettingsStore())

        viewModel.openSessionDetail("sess_stale")
        advanceUntilIdle()

        viewModel.openSessionDetail("sess_missing")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedSession)
        assertEquals("未找到会话：sess_missing", viewModel.uiState.value.message)
        assertEquals(1, bridgeApi.observeSessionCalls)
    }
}

private class FakeBridgeApi(
    private val createdDetail: SessionDetail,
) : BridgeApi {
    private val events = MutableSharedFlow<SessionStreamEvent>(extraBufferCapacity = 16)
    val sentInputs = mutableListOf<String>()
    val approvalCalls = mutableListOf<ApprovalCall>()
    var updatedAuthToken: String = ""
    var authTokenAtConnect: String = ""
    var lastCreateSessionRequest: CreateSessionRequest? = null
    var observeSessionCalls: Int = 0

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
        return createdDetail
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

    override fun observeSessionEvents(sessionId: String): Flow<SessionStreamEvent> {
        observeSessionCalls += 1
        return events
    }

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
    details: List<SessionDetail?>,
) : SessionRepository {
    private val detailsQueue = details.toMutableList()
    private var lastDetail: SessionDetail? = details.lastOrNull()
    var getSessionDetailCallCount: Int = 0
        private set

    override suspend fun listSessions(): List<SessionSummary> = sessionSummaries

    override suspend fun getSessionDetail(sessionId: String): SessionDetail? {
        getSessionDetailCallCount += 1
        val next = if (detailsQueue.isEmpty()) lastDetail else detailsQueue.removeAt(0)
        if (next != null) {
            lastDetail = next
        }
        return next
    }
}

private class FakeAppSettingsStore(
    initial: AppSettings = AppSettings(
        endpoint = "http://10.0.2.2:8787",
        authToken = "",
        cwd = "D:\\workspace\\codex-mobile",
        model = "gpt-5.5",
        approvalMode = "manual",
    ),
) : AppSettingsStore {
    var saved: AppSettings = initial
        private set

    override fun load(): AppSettings = saved

    override fun save(settings: AppSettings) {
        saved = settings
    }
}

private fun sessionSummaryFor(detail: SessionDetail): SessionSummary {
    return SessionSummary(
        id = detail.id,
        title = detail.title,
        subtitle = "gpt-5.5 • 空闲 • D:\\workspace\\codex-mobile",
        lastUpdated = detail.lastUpdated,
        status = detail.status,
    )
}

private fun arrayDequeOf(vararg details: SessionDetail?): List<SessionDetail?> {
    return details.toList()
}
