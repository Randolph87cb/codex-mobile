package com.openai.codexmobile

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
import com.openai.codexmobile.data.UploadImageAttachmentRequest
import com.openai.codexmobile.data.UploadedImageAttachment
import com.openai.codexmobile.data.SessionRepository
import com.openai.codexmobile.data.SessionStreamEvent
import com.openai.codexmobile.diagnostics.AppLogger
import com.openai.codexmobile.model.BridgeConnectionState
import com.openai.codexmobile.model.PendingApprovalSnapshot
import com.openai.codexmobile.model.SessionDetail
import com.openai.codexmobile.model.SessionSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
                approvalMode = "auto",
                reasoningEffort = "medium",
                serviceTier = "default",
                sandboxMode = "danger-full-access",
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
            detailsById = mapOf(
                detail.id to detail,
                "${detail.id}#refresh" to detail.copy(status = "running"),
            ),
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
                serviceTier = "fast",
                sandboxMode = "danger-full-access",
                threadId = "thread-1",
                pendingApproval = null,
                timestamp = "2026-05-19T10:00:01Z",
            ),
        )
        advanceUntilIdle()

        val selected = viewModel.uiState.value.selectedSession
        assertEquals("D:\\workspace\\other", selected?.cwd)
        assertEquals("gpt-5.5-coder", selected?.model)
        assertEquals("auto", selected?.approvalMode)
        assertEquals("high", selected?.reasoningEffort)
        assertEquals("fast", selected?.serviceTier)
        assertEquals("danger-full-access", selected?.sandboxMode)
        assertEquals("进行中", viewModel.uiState.value.sessionRealtimeState.statusText)
    }

    @Test
    fun activityEventTracksStructuredExecutionActivityAndUpdatesRealtimeSummary() = runTest(dispatcher.scheduler) {
        val detail = sampleDetail(id = "sess_activity", status = "running")
        val bridgeApi = FakeBridgeApi(createdDetail = detail)
        val repository = FakeSessionRepository(
            sessionSummaries = listOf(detail.toSummary()),
            detailsById = mapOf(
                detail.id to detail,
                "${detail.id}#refresh" to detail.copy(status = "running"),
            ),
        )
        val viewModel = AppViewModel(bridgeApi, repository, FakeAppSettingsStore(), FakeAppLogger())

        viewModel.openSessionDetail("sess_activity")
        advanceUntilIdle()

        bridgeApi.emit(
            SessionStreamEvent.Activity(
                sessionId = "sess_activity",
                itemType = "commandExecution",
                itemId = "item-1",
                title = "命令执行",
                body = "命令：npm test",
                transcriptBlock = "系统：命令执行\n命令：npm test",
                summary = "命令执行",
                timestamp = "2026-05-19T10:05:00Z",
            ),
        )
        advanceUntilIdle()

        assertEquals("工作目录：D:\\workspace\\codex-mobile", viewModel.uiState.value.selectedSession?.transcriptPreview)
        assertEquals("命令执行", viewModel.uiState.value.sessionRealtimeState.lastEventText)
        assertEquals(1, viewModel.uiState.value.sessionRealtimeState.liveExecutionActivities.size)
        val activity = viewModel.uiState.value.sessionRealtimeState.liveExecutionActivities.single()
        assertEquals("item-1", activity.stableId)
        assertEquals("命令执行", activity.title)
        assertEquals("命令：npm test", activity.body)
    }

    @Test
    fun activityEventMergesReasoningDeltasByItemId() = runTest(dispatcher.scheduler) {
        val detail = sampleDetail(id = "sess_reasoning", status = "running")
        val bridgeApi = FakeBridgeApi(createdDetail = detail)
        val repository = FakeSessionRepository(
            sessionSummaries = listOf(detail.toSummary()),
            detailsById = mapOf(
                detail.id to detail,
                "${detail.id}#refresh" to detail.copy(status = "running"),
            ),
        )
        val viewModel = AppViewModel(bridgeApi, repository, FakeAppSettingsStore(), FakeAppLogger())

        viewModel.openSessionDetail("sess_reasoning")
        advanceUntilIdle()

        bridgeApi.emit(
            SessionStreamEvent.Activity(
                sessionId = "sess_reasoning",
                itemType = "reasoning",
                itemId = "reasoning-1",
                title = "推理摘要",
                body = "README 大体跟上了。",
                transcriptBlock = "系统：推理摘要\nREADME 大体跟上了。",
                summary = "README 大体跟上了。",
                timestamp = "2026-05-19T10:05:00Z",
            ),
        )
        bridgeApi.emit(
            SessionStreamEvent.Activity(
                sessionId = "sess_reasoning",
                itemType = "reasoning",
                itemId = "reasoning-1",
                title = "推理摘要",
                body = "README 大体跟上了。\n还要检查 docs/api.md。",
                transcriptBlock = "系统：推理摘要\nREADME 大体跟上了。\n还要检查 docs/api.md。",
                summary = "还要检查 docs/api.md。",
                timestamp = "2026-05-19T10:05:01Z",
            ),
        )
        advanceUntilIdle()

        val activities = viewModel.uiState.value.sessionRealtimeState.liveExecutionActivities
        assertEquals(1, activities.size)
        assertEquals("推理摘要", activities.single().title)
        assertEquals(
            "README 大体跟上了。\n还要检查 docs/api.md。",
            activities.single().body,
        )
        assertEquals("还要检查 docs/api.md。", viewModel.uiState.value.sessionRealtimeState.lastEventText)
    }

    @Test
    fun inputDuringApprovalIsQueuedAndSentAfterIdle() = runTest(dispatcher.scheduler) {
        val detail = sampleDetail(id = "sess_queue", status = "idle")
        val bridgeApi = FakeBridgeApi(createdDetail = detail)
        val repository = FakeSessionRepository(
            sessionSummaries = listOf(detail.toSummary()),
            detailsById = mapOf(
                detail.id to detail,
                "${detail.id}#refresh" to detail.copy(status = "running"),
            ),
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
    fun reopenAwaitingApprovalSessionRestoresAndAutoApprovesPendingRequest() = runTest(dispatcher.scheduler) {
        val detail = sampleDetail(
            id = "sess_restore_approval",
            approvalMode = "manual",
            sandboxMode = "workspace-write",
            status = "awaiting_approval",
        ).copy(
            pendingApproval = PendingApprovalSnapshot(
                requestId = BridgeRequestId.Text("req-restore"),
                method = "item/permissions/requestApproval",
                paramsSummary = "等待审批：item/permissions/requestApproval",
            ),
        )
        val bridgeApi = FakeBridgeApi(createdDetail = detail)
        val repository = FakeSessionRepository(
            sessionSummaries = listOf(detail.toSummary()),
            detailsById = mapOf(detail.id to detail),
        )
        val viewModel = AppViewModel(bridgeApi, repository, FakeAppSettingsStore(), FakeAppLogger())

        viewModel.openSessionDetail("sess_restore_approval")
        advanceUntilIdle()
        viewModel.closeSessionDetail("sess_restore_approval")
        viewModel.openSessionDetail("sess_restore_approval")
        advanceUntilIdle()

        assertTrue(
            bridgeApi.sessionConfigUpdates.any {
                it.approvalMode == "auto" && it.sandboxMode == "danger-full-access"
            },
        )
        assertTrue(
            bridgeApi.approvalCalls.any {
                it.sessionId == "sess_restore_approval" &&
                    it.requestId == BridgeRequestId.Text("req-restore") &&
                    it.decision == ApprovalDecision.ApproveForSession
            },
        )
        assertEquals("running", viewModel.uiState.value.selectedSession?.status)
        assertNull(viewModel.uiState.value.sessionRealtimeState.pendingApproval)
    }

    @Test
    fun refreshSnapshotDoesNotRevertApprovedSessionBackToAwaitingApprovalWithoutPendingRequest() = runTest(dispatcher.scheduler) {
        val detail = sampleDetail(
            id = "sess_stale_approval_status",
            status = "awaiting_approval",
        ).copy(
            pendingApproval = PendingApprovalSnapshot(
                requestId = BridgeRequestId.Text("req-stale"),
                method = "item/permissions/requestApproval",
                paramsSummary = "等待审批：item/permissions/requestApproval",
            ),
        )
        val bridgeApi = FakeBridgeApi(createdDetail = detail)
        val repository = FakeSessionRepository(
            sessionSummaries = listOf(detail.toSummary()),
            detailsById = mapOf(
                detail.id to detail,
                "${detail.id}#refresh" to detail.copy(
                    status = "awaiting_approval",
                    pendingApproval = null,
                ),
            ),
        )
        val viewModel = AppViewModel(bridgeApi, repository, FakeAppSettingsStore(), FakeAppLogger())

        viewModel.openSessionDetail("sess_stale_approval_status")
        advanceUntilIdle()

        assertEquals("running", viewModel.uiState.value.selectedSession?.status)
        assertEquals("进行中", viewModel.uiState.value.sessionRealtimeState.statusText)
        assertNull(viewModel.uiState.value.selectedSession?.pendingApproval)
        assertNull(viewModel.uiState.value.sessionRealtimeState.pendingApproval)
    }

    @Test
    fun connectSynchronizesExistingSessionsToManagedPolicy() = runTest(dispatcher.scheduler) {
        val detail = sampleDetail(
            id = "sess_managed_sync",
            approvalMode = "manual",
            sandboxMode = "workspace-write",
            status = "idle",
        )
        val bridgeApi = FakeBridgeApi(createdDetail = detail)
        val repository = FakeSessionRepository(
            sessionSummaries = listOf(detail.toSummary()),
            detailsById = mapOf(detail.id to detail),
        )
        val viewModel = AppViewModel(bridgeApi, repository, FakeAppSettingsStore(), FakeAppLogger())

        viewModel.connect()
        advanceUntilIdle()

        assertTrue(
            bridgeApi.sessionConfigUpdates.any {
                it.approvalMode == "auto" && it.sandboxMode == "danger-full-access"
            },
        )
        assertEquals("auto", viewModel.uiState.value.selectedSession?.approvalMode)
        assertEquals("danger-full-access", viewModel.uiState.value.selectedSession?.sandboxMode)
    }

    @Test
    fun streamClosedInForegroundReconnectsAutomatically() = runTest(dispatcher.scheduler) {
        val detail = sampleDetail(id = "sess_reconnect", status = "running")
        val bridgeApi = FakeBridgeApi(createdDetail = detail)
        val repository = FakeSessionRepository(
            sessionSummaries = listOf(detail.toSummary()),
            detailsById = mapOf(detail.id to detail),
        )
        val viewModel = AppViewModel(bridgeApi, repository, FakeAppSettingsStore(), FakeAppLogger())

        viewModel.connect()
        advanceUntilIdle()
        viewModel.openSessionDetail("sess_reconnect")
        advanceUntilIdle()

        assertEquals(1, bridgeApi.observeSessionEventsCallCount)

        bridgeApi.emit(
            SessionStreamEvent.StreamClosed(
                sessionId = "sess_reconnect",
                reason = "socket closed",
                timestamp = "2026-05-19T19:00:00.000Z",
            ),
        )
        runCurrent()
        assertEquals("实时流已断开，准备重连", viewModel.uiState.value.sessionRealtimeState.connectionText)

        advanceTimeBy(1_500L)
        runCurrent()

        assertEquals(2, bridgeApi.observeSessionEventsCallCount)
    }

    @Test
    fun streamClosedRefreshesBusySessionSnapshotToIdle() = runTest(dispatcher.scheduler) {
        val detail = sampleDetail(id = "sess_stream_fix", status = "running")
        val refreshed = detail.copy(status = "idle")
        val bridgeApi = FakeBridgeApi(createdDetail = detail)
        val repository = FakeSessionRepository(
            sessionSummaries = listOf(detail.toSummary()),
            detailsById = mapOf(
                detail.id to detail,
                "${detail.id}#refresh" to refreshed,
            ),
        )
        val viewModel = AppViewModel(bridgeApi, repository, FakeAppSettingsStore(), FakeAppLogger())

        viewModel.connect()
        advanceUntilIdle()
        viewModel.openSessionDetail("sess_stream_fix")
        advanceUntilIdle()

        bridgeApi.emit(
            SessionStreamEvent.StreamClosed(
                sessionId = "sess_stream_fix",
                reason = "socket closed",
                timestamp = "2026-05-19T19:20:00.000Z",
            ),
        )
        advanceUntilIdle()

        assertEquals("idle", viewModel.uiState.value.selectedSession?.status)
        assertEquals("空闲", viewModel.uiState.value.sessionRealtimeState.statusText)
    }

    @Test
    fun retryableStreamErrorKeepsRunningStateAndDoesNotShowUserError() = runTest(dispatcher.scheduler) {
        val detail = sampleDetail(id = "sess_retryable_error", status = "running")
        val bridgeApi = FakeBridgeApi(createdDetail = detail)
        val repository = FakeSessionRepository(
            sessionSummaries = listOf(detail.toSummary()),
            detailsById = mapOf(
                detail.id to detail,
                "${detail.id}#refresh" to detail.copy(status = "running"),
            ),
        )
        val viewModel = AppViewModel(bridgeApi, repository, FakeAppSettingsStore(), FakeAppLogger())

        viewModel.openSessionDetail("sess_retryable_error")
        advanceUntilIdle()

        bridgeApi.emit(
            SessionStreamEvent.Error(
                sessionId = "sess_retryable_error",
                message = "Reconnecting... 2/5",
                isRetryable = true,
                timestamp = "2026-05-20T00:52:31.218933Z",
            ),
        )
        advanceUntilIdle()

        assertEquals("running", viewModel.uiState.value.selectedSession?.status)
        assertEquals("进行中", viewModel.uiState.value.sessionRealtimeState.statusText)
        assertEquals("上游响应流暂时中断，bridge 正在重试。", viewModel.uiState.value.sessionRealtimeState.lastEventText)
        assertEquals(
            "上游响应流暂时中断，bridge 正在重试：Reconnecting... 2/5",
            viewModel.uiState.value.sessionRealtimeState.fallbackNotice,
        )
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun terminalStreamErrorStillShowsUserVisibleFailure() = runTest(dispatcher.scheduler) {
        val detail = sampleDetail(id = "sess_terminal_error", status = "running")
        val bridgeApi = FakeBridgeApi(createdDetail = detail)
        val repository = FakeSessionRepository(
            sessionSummaries = listOf(detail.toSummary()),
            detailsById = mapOf(
                detail.id to detail,
                "${detail.id}#refresh" to detail.copy(status = "error"),
            ),
        )
        val viewModel = AppViewModel(bridgeApi, repository, FakeAppSettingsStore(), FakeAppLogger())

        viewModel.openSessionDetail("sess_terminal_error")
        advanceUntilIdle()

        bridgeApi.emit(
            SessionStreamEvent.Error(
                sessionId = "sess_terminal_error",
                message = "stream failed",
                timestamp = "2026-05-20T00:52:31.218933Z",
            ),
        )
        advanceUntilIdle()

        assertEquals("error", viewModel.uiState.value.selectedSession?.status)
        assertEquals("出错", viewModel.uiState.value.sessionRealtimeState.statusText)
        assertEquals("stream failed", viewModel.uiState.value.sessionRealtimeState.fallbackNotice)
        assertEquals("实时流错误：stream failed", viewModel.uiState.value.message)
    }

    @Test
    fun streamClosedInBackgroundWaitsUntilForegroundToReconnect() = runTest(dispatcher.scheduler) {
        val detail = sampleDetail(id = "sess_resume", status = "running")
        val bridgeApi = FakeBridgeApi(createdDetail = detail)
        val repository = FakeSessionRepository(
            sessionSummaries = listOf(detail.toSummary()),
            detailsById = mapOf(detail.id to detail),
        )
        val viewModel = AppViewModel(bridgeApi, repository, FakeAppSettingsStore(), FakeAppLogger())

        viewModel.connect()
        advanceUntilIdle()
        viewModel.openSessionDetail("sess_resume")
        advanceUntilIdle()
        assertEquals(1, bridgeApi.observeSessionEventsCallCount)

        viewModel.onAppBackgrounded()
        bridgeApi.emit(
            SessionStreamEvent.StreamClosed(
                sessionId = "sess_resume",
                reason = "background socket closed",
                timestamp = "2026-05-19T19:10:00.000Z",
            ),
        )
        advanceTimeBy(5_000L)
        advanceUntilIdle()

        assertEquals(1, bridgeApi.observeSessionEventsCallCount)

        viewModel.onAppForegrounded()
        advanceUntilIdle()

        assertEquals(2, bridgeApi.observeSessionEventsCallCount)
    }

    @Test
    fun updatingSessionConfigKeepsGlobalSettingsAndCallsBridge() = runTest(dispatcher.scheduler) {
        val detail = sampleDetail(id = "sess_config", status = "idle")
        val bridgeApi = FakeBridgeApi(createdDetail = detail)
        val repository = FakeSessionRepository(
            sessionSummaries = listOf(detail.toSummary()),
            detailsById = mapOf(detail.id to detail),
        )
        val globalSettings = AppSettings(
            endpoint = "http://10.0.2.2:8787",
            authToken = "",
            cwd = "D:\\workspace\\global-default",
            model = "gpt-5.4",
            approvalMode = "auto",
            reasoningEffort = "low",
            serviceTier = "default",
            sandboxMode = "danger-full-access",
        )
        val settingsStore = FakeAppSettingsStore(globalSettings)
        val viewModel = AppViewModel(bridgeApi, repository, settingsStore, FakeAppLogger())

        viewModel.openSessionDetail("sess_config")
        advanceUntilIdle()

        viewModel.updateSelectedSessionModel("gpt-5.5-coder")
        viewModel.updateSelectedSessionReasoningEffort("high")
        viewModel.updateSelectedSessionServiceTier("fast")
        viewModel.updateSelectedSessionSandboxMode("danger-full-access")
        advanceUntilIdle()

        assertEquals(4, bridgeApi.sessionConfigUpdates.size)
        assertEquals(globalSettings.endpoint, settingsStore.saved.endpoint)
        assertEquals(globalSettings.cwd, settingsStore.saved.cwd)
        assertEquals(globalSettings.model, settingsStore.saved.model)
        assertEquals(globalSettings.approvalMode, settingsStore.saved.approvalMode)
        assertEquals(globalSettings.reasoningEffort, settingsStore.saved.reasoningEffort)
        assertEquals(globalSettings.serviceTier, settingsStore.saved.serviceTier)
        assertEquals(globalSettings.sandboxMode, settingsStore.saved.sandboxMode)
        assertEquals("D:\\workspace\\global-default", viewModel.uiState.value.cwdInput)
        assertEquals("gpt-5.4", viewModel.uiState.value.modelInput)
        assertEquals("low", viewModel.uiState.value.reasoningEffortInput)
        assertEquals("default", viewModel.uiState.value.serviceTierInput)
        assertEquals("danger-full-access", viewModel.uiState.value.sandboxModeInput)
        assertEquals("gpt-5.5-coder", viewModel.uiState.value.selectedSession?.model)
        assertEquals("high", viewModel.uiState.value.selectedSession?.reasoningEffort)
        assertEquals("fast", viewModel.uiState.value.selectedSession?.serviceTier)
        assertEquals("danger-full-access", viewModel.uiState.value.selectedSession?.sandboxMode)
    }

    @Test
    fun updatingSessionConfigWhileAwaitingApprovalKeepsUpdatedReasoningAndSandbox() = runTest(dispatcher.scheduler) {
        val detail = sampleDetail(id = "sess_pending_config", status = "idle")
        val bridgeApi = FakeBridgeApi(createdDetail = detail)
        val repository = FakeSessionRepository(
            sessionSummaries = listOf(detail.toSummary()),
            detailsById = mapOf(detail.id to detail),
        )
        val viewModel = AppViewModel(bridgeApi, repository, FakeAppSettingsStore(), FakeAppLogger())

        viewModel.openSessionDetail("sess_pending_config")
        advanceUntilIdle()

        bridgeApi.emit(
            SessionStreamEvent.ToolRequest(
                sessionId = "sess_pending_config",
                requestId = BridgeRequestId.Text("req-config"),
                method = "item/commandExecution/requestApproval",
                paramsSummary = "等待审批：配置变更前已有工具请求",
                timestamp = "2026-05-19T20:00:00.000Z",
            ),
        )
        advanceUntilIdle()

        viewModel.updateSelectedSessionReasoningEffort("high")
        viewModel.updateSelectedSessionSandboxMode("danger-full-access")
        advanceUntilIdle()

        assertEquals("running", viewModel.uiState.value.selectedSession?.status)
        assertEquals("high", viewModel.uiState.value.selectedSession?.reasoningEffort)
        assertEquals("danger-full-access", viewModel.uiState.value.selectedSession?.sandboxMode)
    }

    @Test
    fun updatingDraftConfigDoesNotOverwriteGlobalSettings() = runTest(dispatcher.scheduler) {
        val createdDetail = sampleDetail(id = "sess_draft_config", status = "idle")
        val bridgeApi = FakeBridgeApi(createdDetail = createdDetail)
        val repository = FakeSessionRepository(emptyList(), emptyMap())
        val globalSettings = AppSettings(
            endpoint = "http://10.0.2.2:8787",
            authToken = "",
            cwd = "D:\\workspace\\global-default",
            model = "gpt-5.4",
            approvalMode = "auto",
            reasoningEffort = "low",
            serviceTier = "default",
            sandboxMode = "danger-full-access",
        )
        val settingsStore = FakeAppSettingsStore(globalSettings)
        val viewModel = AppViewModel(bridgeApi, repository, settingsStore, FakeAppLogger())

        viewModel.startDraftSession()
        advanceUntilIdle()

        viewModel.updateSelectedSessionCwd("D:\\workspace\\draft-specific")
        viewModel.updateSelectedSessionModel("gpt-5.5-coder")
        viewModel.updateSelectedSessionReasoningEffort("high")
        viewModel.updateSelectedSessionServiceTier("fast")
        viewModel.updateSelectedSessionSandboxMode("danger-full-access")
        advanceUntilIdle()

        assertEquals(globalSettings.endpoint, settingsStore.saved.endpoint)
        assertEquals(globalSettings.cwd, settingsStore.saved.cwd)
        assertEquals(globalSettings.model, settingsStore.saved.model)
        assertEquals(globalSettings.approvalMode, settingsStore.saved.approvalMode)
        assertEquals(globalSettings.reasoningEffort, settingsStore.saved.reasoningEffort)
        assertEquals(globalSettings.serviceTier, settingsStore.saved.serviceTier)
        assertEquals(globalSettings.sandboxMode, settingsStore.saved.sandboxMode)
        assertEquals("D:\\workspace\\global-default", viewModel.uiState.value.cwdInput)
        assertEquals("gpt-5.4", viewModel.uiState.value.modelInput)
        assertEquals("low", viewModel.uiState.value.reasoningEffortInput)
        assertEquals("default", viewModel.uiState.value.serviceTierInput)
        assertEquals("danger-full-access", viewModel.uiState.value.sandboxModeInput)
        assertEquals("D:\\workspace\\draft-specific", viewModel.uiState.value.selectedDraftSession?.cwd)
        assertEquals("gpt-5.5-coder", viewModel.uiState.value.selectedDraftSession?.model)
        assertEquals("high", viewModel.uiState.value.selectedDraftSession?.reasoningEffort)
        assertEquals("fast", viewModel.uiState.value.selectedDraftSession?.serviceTier)
        assertEquals("danger-full-access", viewModel.uiState.value.selectedDraftSession?.sandboxMode)

        viewModel.updateDraftMessage("创建草稿会话")
        viewModel.sendInput()
        advanceUntilIdle()

        assertEquals(
            CreateSessionRequest(
                cwd = "D:\\workspace\\draft-specific",
                model = "gpt-5.5-coder",
                approvalMode = "auto",
                reasoningEffort = "high",
                serviceTier = "fast",
                sandboxMode = "danger-full-access",
            ),
            bridgeApi.lastCreateSessionRequest,
        )
        assertEquals(globalSettings.endpoint, settingsStore.saved.endpoint)
        assertEquals(globalSettings.cwd, settingsStore.saved.cwd)
        assertEquals(globalSettings.model, settingsStore.saved.model)
        assertEquals(globalSettings.approvalMode, settingsStore.saved.approvalMode)
        assertEquals(globalSettings.reasoningEffort, settingsStore.saved.reasoningEffort)
        assertEquals(globalSettings.serviceTier, settingsStore.saved.serviceTier)
        assertEquals(globalSettings.sandboxMode, settingsStore.saved.sandboxMode)
    }

    @Test
    fun sendInputRefreshesStaleRunningStatusBeforeSubmitting() = runTest(dispatcher.scheduler) {
        val detail = sampleDetail(id = "sess_stale_running", status = "running")
        val refreshed = detail.copy(status = "idle")
        val bridgeApi = FakeBridgeApi(createdDetail = detail)
        val repository = FakeSessionRepository(
            sessionSummaries = listOf(detail.toSummary()),
            detailsById = mapOf(
                detail.id to detail,
                "${detail.id}#refresh" to refreshed,
            ),
        )
        val viewModel = AppViewModel(bridgeApi, repository, FakeAppSettingsStore(), FakeAppLogger())

        viewModel.openSessionDetail("sess_stale_running")
        advanceUntilIdle()

        viewModel.updateDraftMessage("继续执行")
        viewModel.sendInput()
        advanceUntilIdle()

        assertEquals(listOf("继续执行"), bridgeApi.sentInputs)
        assertTrue(viewModel.uiState.value.queuedInputs.isEmpty())
        assertEquals("running", viewModel.uiState.value.selectedSession?.status)
    }

    @Test
    fun updatingSessionConfigDoesNotDiscardMoreCompleteTranscript() = runTest(dispatcher.scheduler) {
        val detail = sampleDetail(
            id = "sess_transcript",
            status = "idle",
        ).copy(
            transcriptPreview = "你：第一句\n\nCodex：第一段回复\n\n你：第二句",
        )
        val bridgeApi = FakeBridgeApi(createdDetail = detail.copy(transcriptPreview = "工作目录：D:\\workspace\\codex-mobile"))
        val repository = FakeSessionRepository(
            sessionSummaries = listOf(detail.toSummary()),
            detailsById = mapOf(detail.id to detail),
        )
        val viewModel = AppViewModel(bridgeApi, repository, FakeAppSettingsStore(), FakeAppLogger())

        viewModel.openSessionDetail("sess_transcript")
        advanceUntilIdle()

        viewModel.updateSelectedSessionModel("gpt-5.5-coder")
        advanceUntilIdle()

        assertEquals(
            "你：第一句\n\nCodex：第一段回复\n\n你：第二句",
            viewModel.uiState.value.selectedSession?.transcriptPreview,
        )
    }

    @Test
    fun defaultServiceTierUsesOrdinaryModeAndPersistsAsDefault() = runTest(dispatcher.scheduler) {
        val detail = sampleDetail(id = "sess_default", status = "idle")
        val bridgeApi = FakeBridgeApi(createdDetail = detail)
        val repository = FakeSessionRepository(
            sessionSummaries = listOf(detail.toSummary()),
            detailsById = mapOf(detail.id to detail),
        )
        val settingsStore = FakeAppSettingsStore()
        val viewModel = AppViewModel(bridgeApi, repository, settingsStore, FakeAppLogger())

        assertEquals("default", viewModel.uiState.value.serviceTierInput)

        viewModel.openSessionDetail("sess_default")
        advanceUntilIdle()

        viewModel.updateSelectedSessionServiceTier("default")
        advanceUntilIdle()

        assertTrue(bridgeApi.sessionConfigUpdates.isNotEmpty())
        assertEquals("default", bridgeApi.sessionConfigUpdates.last().serviceTier)
        assertEquals("default", settingsStore.saved.serviceTier)
        assertEquals("default", viewModel.uiState.value.selectedSession?.serviceTier)
    }

    @Test
    fun snapshotWithUnknownFieldsDoesNotOverwriteKnownSessionConfig() = runTest(dispatcher.scheduler) {
        val detail = sampleDetail(
            id = "sess_known",
            model = "gpt-5.4",
            reasoningEffort = "high",
            serviceTier = "fast",
            sandboxMode = "danger-full-access",
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
                    sandboxMode = "unknown",
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
        assertEquals("auto", viewModel.uiState.value.selectedSession?.approvalMode)
        assertEquals("high", viewModel.uiState.value.selectedSession?.reasoningEffort)
        assertEquals("fast", viewModel.uiState.value.selectedSession?.serviceTier)
        assertEquals("danger-full-access", viewModel.uiState.value.selectedSession?.sandboxMode)
    }

    @Test
    fun imageAttachmentsUploadBeforeSendingInput() = runTest(dispatcher.scheduler) {
        val detail = sampleDetail(id = "sess_image", status = "idle")
        val bridgeApi = FakeBridgeApi(createdDetail = detail)
        val repository = FakeSessionRepository(
            sessionSummaries = listOf(detail.toSummary()),
            detailsById = mapOf(detail.id to detail),
        )
        val viewModel = AppViewModel(bridgeApi, repository, FakeAppSettingsStore(), FakeAppLogger())

        viewModel.openSessionDetail("sess_image")
        advanceUntilIdle()
        viewModel.attachPreparedImages(
            listOf(
                UploadImageAttachmentRequest(
                    displayName = "screen.png",
                    mimeType = "image/png",
                    contentBytes = "hello".toByteArray(),
                ),
                UploadImageAttachmentRequest(
                    displayName = "diagram.webp",
                    mimeType = "image/webp",
                    contentBytes = "world".toByteArray(),
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals(2, bridgeApi.uploadedImageRequests.size)
        assertTrue(
            viewModel.uiState.value.pendingImageAttachments.all {
                it.uploadState == PendingImageUploadState.Uploaded && it.stagedPath != null
            },
        )

        viewModel.updateDraftMessage("帮我看看")
        viewModel.sendInput()
        advanceUntilIdle()

        assertEquals(listOf("screen.png", "diagram.webp"), bridgeApi.uploadedImageRequests.map { it.displayName })
        assertEquals(1, bridgeApi.sendInputRequests.size)
        assertEquals("帮我看看", bridgeApi.sendInputRequests.single().text)
        assertEquals(
            listOf("D:\\bridge\\staged\\uploaded-1.png", "D:\\bridge\\staged\\uploaded-2.png"),
            bridgeApi.sendInputRequests.single().attachments.map { it.stagedPath },
        )
        assertTrue(viewModel.uiState.value.pendingImageAttachments.isEmpty())
        assertTrue(
            viewModel.uiState.value.selectedSession?.transcriptPreview?.contains(
                "![screen.png](bridge-file://D%3A%5Cbridge%5Cstaged%5Cuploaded-1.png)",
            ) == true,
        )
        assertTrue(
            viewModel.uiState.value.selectedSession?.transcriptPreview?.contains(
                "![diagram.webp](bridge-file://D%3A%5Cbridge%5Cstaged%5Cuploaded-2.png)",
            ) == true,
        )
    }

    @Test
    fun sendInputIsBlockedWhileImagesAreStillUploading() = runTest(dispatcher.scheduler) {
        val detail = sampleDetail(id = "sess_uploading", status = "idle")
        val bridgeApi = FakeBridgeApi(
            createdDetail = detail,
            uploadDelayMs = 5_000L,
        )
        val repository = FakeSessionRepository(
            sessionSummaries = listOf(detail.toSummary()),
            detailsById = mapOf(detail.id to detail),
        )
        val viewModel = AppViewModel(bridgeApi, repository, FakeAppSettingsStore(), FakeAppLogger())

        viewModel.openSessionDetail("sess_uploading")
        advanceUntilIdle()

        viewModel.attachPreparedImages(
            listOf(
                UploadImageAttachmentRequest(
                    displayName = "screen.png",
                    mimeType = "image/png",
                    contentBytes = "hello".toByteArray(),
                ),
            ),
        )
        runCurrent()
        viewModel.updateDraftMessage("先别发")
        viewModel.sendInput()
        runCurrent()

        assertTrue(viewModel.uiState.value.pendingImageAttachments.any { it.uploadState == PendingImageUploadState.Uploading })
        assertTrue(viewModel.uiState.value.message?.contains("图片仍在上传") == true)
        assertTrue(bridgeApi.sendInputRequests.isEmpty())
    }

    @Test
    fun failedImageUploadCanBeRetriedThenSent() = runTest(dispatcher.scheduler) {
        val detail = sampleDetail(id = "sess_retry", status = "idle")
        val bridgeApi = FakeBridgeApi(
            createdDetail = detail,
            failUploadsRemaining = 1,
        )
        val repository = FakeSessionRepository(
            sessionSummaries = listOf(detail.toSummary()),
            detailsById = mapOf(detail.id to detail),
        )
        val viewModel = AppViewModel(bridgeApi, repository, FakeAppSettingsStore(), FakeAppLogger())

        viewModel.openSessionDetail("sess_retry")
        advanceUntilIdle()

        viewModel.attachPreparedImages(
            listOf(
                UploadImageAttachmentRequest(
                    displayName = "screen.png",
                    mimeType = "image/png",
                    contentBytes = "hello".toByteArray(),
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals(PendingImageUploadState.Failed, viewModel.uiState.value.pendingImageAttachments.single().uploadState)
        viewModel.updateDraftMessage("重试后发送")
        viewModel.sendInput()
        runCurrent()
        assertTrue(bridgeApi.sendInputRequests.isEmpty())

        val localId = viewModel.uiState.value.pendingImageAttachments.single().localId
        viewModel.retryPendingImageAttachment(localId)
        advanceUntilIdle()

        assertEquals(PendingImageUploadState.Uploaded, viewModel.uiState.value.pendingImageAttachments.single().uploadState)

        viewModel.sendInput()
        advanceUntilIdle()

        assertEquals(1, bridgeApi.sendInputRequests.size)
        assertEquals("重试后发送", bridgeApi.sendInputRequests.single().text)
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
    fun legacySingleConnectionSettingsAreExposedAsSavedConnection() = runTest(dispatcher.scheduler) {
        val detail = sampleDetail(id = "sess_legacy", status = "idle")
        val viewModel = AppViewModel(
            bridgeApi = FakeBridgeApi(createdDetail = detail),
            sessionRepository = FakeSessionRepository(emptyList(), emptyMap()),
            settingsStore = FakeAppSettingsStore(
                AppSettings(
                    endpoint = "https://bridge-a.example.com",
                    authToken = "token-a",
                    cwd = "D:\\workspace\\codex-mobile",
                    model = "gpt-5.5",
                    approvalMode = "manual",
                    reasoningEffort = "medium",
                    serviceTier = "default",
                    sandboxMode = "workspace-write",
                ),
            ),
            appLogger = FakeAppLogger(),
        )

        val state = viewModel.uiState.value
        assertEquals(1, state.savedConnections.size)
        assertEquals("https://bridge-a.example.com", state.savedConnections.single().endpoint)
        assertEquals("token-a", state.savedConnections.single().authToken)
        assertEquals(state.savedConnections.single().id, state.selectedConnectionId)
    }

    @Test
    fun selectingSavedConnectionSwitchesEndpointAndTokenTogether() = runTest(dispatcher.scheduler) {
        val detail = sampleDetail(id = "sess_select_connection", status = "idle")
        val settingsStore = FakeAppSettingsStore(
            AppSettings(
                endpoint = "https://bridge-a.example.com",
                authToken = "token-a",
                cwd = "D:\\workspace\\codex-mobile",
                model = "gpt-5.5",
                approvalMode = "manual",
                reasoningEffort = "medium",
                serviceTier = "default",
                sandboxMode = "workspace-write",
                savedConnections = listOf(
                    SavedBridgeConnection(
                        id = "conn-a",
                        name = "办公室",
                        endpoint = "https://bridge-a.example.com",
                        authToken = "token-a",
                    ),
                    SavedBridgeConnection(
                        id = "conn-b",
                        name = "家里",
                        endpoint = "https://bridge-b.example.com",
                        authToken = "token-b",
                    ),
                ),
                selectedConnectionId = "conn-a",
            ),
        )
        val bridgeApi = FakeBridgeApi(createdDetail = detail)
        val viewModel = AppViewModel(
            bridgeApi = bridgeApi,
            sessionRepository = FakeSessionRepository(emptyList(), emptyMap()),
            settingsStore = settingsStore,
            appLogger = FakeAppLogger(),
        )

        viewModel.selectSavedConnection("conn-b")
        advanceUntilIdle()

        assertEquals("https://bridge-b.example.com", viewModel.uiState.value.endpointInput)
        assertEquals("token-b", viewModel.uiState.value.authTokenInput)
        assertEquals("token-b", bridgeApi.updatedAuthToken)
        assertEquals("conn-b", settingsStore.saved.selectedConnectionId)
    }

    @Test
    fun editingCurrentConnectionKeepsOtherSavedConnectionsUntouched() = runTest(dispatcher.scheduler) {
        val detail = sampleDetail(id = "sess_edit_connection", status = "idle")
        val settingsStore = FakeAppSettingsStore(
            AppSettings(
                endpoint = "https://bridge-a.example.com",
                authToken = "token-a",
                cwd = "D:\\workspace\\codex-mobile",
                model = "gpt-5.5",
                approvalMode = "manual",
                reasoningEffort = "medium",
                serviceTier = "default",
                sandboxMode = "workspace-write",
                savedConnections = listOf(
                    SavedBridgeConnection(
                        id = "conn-a",
                        name = "办公室",
                        endpoint = "https://bridge-a.example.com",
                        authToken = "token-a",
                    ),
                    SavedBridgeConnection(
                        id = "conn-b",
                        name = "家里",
                        endpoint = "https://bridge-b.example.com",
                        authToken = "token-b",
                    ),
                ),
                selectedConnectionId = "conn-a",
            ),
        )
        val viewModel = AppViewModel(
            bridgeApi = FakeBridgeApi(createdDetail = detail),
            sessionRepository = FakeSessionRepository(emptyList(), emptyMap()),
            settingsStore = settingsStore,
            appLogger = FakeAppLogger(),
        )

        viewModel.updateSelectedConnectionName("办公室新桥接")
        viewModel.updateEndpointInput("https://bridge-a2.example.com")
        viewModel.updateAuthTokenInput("token-a2")
        advanceUntilIdle()

        val savedConnections = settingsStore.saved.savedConnections
        val office = savedConnections.first { it.id == "conn-a" }
        val home = savedConnections.first { it.id == "conn-b" }
        assertEquals("办公室新桥接", office.name)
        assertEquals("https://bridge-a2.example.com", office.endpoint)
        assertEquals("token-a2", office.authToken)
        assertEquals("家里", home.name)
        assertEquals("https://bridge-b.example.com", home.endpoint)
        assertEquals("token-b", home.authToken)
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
    private val uploadDelayMs: Long = 0L,
    private var failUploadsRemaining: Int = 0,
) : BridgeApi {
    private val events = MutableSharedFlow<SessionStreamEvent>(extraBufferCapacity = 16)
    private var currentDetail: SessionDetail = createdDetail
    var observeSessionEventsCallCount: Int = 0
        private set
    val sentInputs = mutableListOf<String>()
    val sendInputRequests = mutableListOf<SendInputRequest>()
    val uploadedImageRequests = mutableListOf<UploadImageAttachmentRequest>()
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
            sandboxMode = request.sandboxMode,
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
            sandboxMode = update.sandboxMode ?: currentDetail.sandboxMode,
        )
        return currentDetail
    }

    override suspend fun uploadImageAttachment(request: UploadImageAttachmentRequest): UploadedImageAttachment {
        if (uploadDelayMs > 0) {
            kotlinx.coroutines.delay(uploadDelayMs)
        }
        uploadedImageRequests += request
        if (failUploadsRemaining > 0) {
            failUploadsRemaining -= 1
            throw IllegalStateException("图片上传失败。")
        }
        return UploadedImageAttachment(
            id = "uploaded-${uploadedImageRequests.size}",
            displayName = request.displayName,
            mimeType = request.mimeType,
            stagedPath = "D:\\bridge\\staged\\uploaded-${uploadedImageRequests.size}.png",
        )
    }

    override suspend fun sendInput(sessionId: String, request: SendInputRequest) {
        sendInputRequests += request
        request.text?.let { sentInputs += it }
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
        observeSessionEventsCallCount += 1
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
        approvalMode = "auto",
        reasoningEffort = "medium",
        serviceTier = "default",
        sandboxMode = "danger-full-access",
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
    approvalMode: String = "auto",
    reasoningEffort: String = "medium",
    serviceTier: String = "default",
    sandboxMode: String = "danger-full-access",
    status: String,
): SessionDetail {
    return SessionDetail(
        id = id,
        title = "测试会话",
        subtitle = "$model • ${if (approvalMode == "auto") "自动" else "手动"} • 空闲",
        lastUpdated = "2026-05-19T10:00:00.000Z",
        transcriptPreview = "工作目录：$cwd",
        cwd = cwd,
        model = model,
        approvalMode = approvalMode,
        reasoningEffort = reasoningEffort,
        serviceTier = serviceTier,
        sandboxMode = sandboxMode,
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
        sandboxMode = sandboxMode,
        status = status,
    )
}
