package com.openai.codexmobile

import com.openai.codexmobile.data.BridgeApi
import com.openai.codexmobile.data.CreateSessionRequest
import com.openai.codexmobile.data.SessionRepository
import com.openai.codexmobile.model.BridgeConnectionState
import com.openai.codexmobile.model.SessionDetail
import com.openai.codexmobile.model.SessionSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun sendInputPollsUntilSessionSettles() = runTest(dispatcher.scheduler) {
        val createdDetail = SessionDetail(
            id = "sess_1",
            title = "新会话",
            subtitle = "gpt-5.5 • manual • 空闲",
            lastUpdated = "2026-05-19T10:00:00.000Z",
            transcriptPreview = "工作目录：D:\\workspace\\codex-mobile",
            status = "idle",
        )
        val sessionSummary = SessionSummary(
            id = "sess_1",
            title = "新会话",
            subtitle = "gpt-5.5 • 空闲 • D:\\workspace\\codex-mobile",
            lastUpdated = "2026-05-19T10:00:00.000Z",
            status = "idle",
        )
        val bridgeApi = FakeBridgeApi(createdDetail)
        val repository = FakeSessionRepository(
            sessionSummaries = listOf(sessionSummary),
            details = arrayDequeOf(
                createdDetail.copy(
                    lastUpdated = "2026-05-19T10:00:01.000Z",
                    transcriptPreview = "你：你是谁",
                    status = "running",
                ),
                createdDetail.copy(
                    lastUpdated = "2026-05-19T10:00:02.000Z",
                    transcriptPreview = "你：你是谁\n\nCodex：我是你的 Windows Codex 移动端入口。",
                    status = "idle",
                ),
            ),
        )
        val viewModel = AppViewModel(bridgeApi, repository)

        viewModel.createSession()
        advanceUntilIdle()

        viewModel.updateDraftMessage("你是谁")
        viewModel.sendInput()
        advanceUntilIdle()

        assertEquals(listOf("你是谁"), bridgeApi.sentInputs)
        assertEquals("消息已发送。", viewModel.uiState.value.message)
        assertTrue(
            viewModel.uiState.value.selectedSession?.transcriptPreview?.contains("Codex：我是你的 Windows Codex 移动端入口。")
                == true,
        )
    }
}

private class FakeBridgeApi(
    private val createdDetail: SessionDetail,
) : BridgeApi {
    val sentInputs = mutableListOf<String>()

    override suspend fun connect(endpoint: String): BridgeConnectionState {
        return BridgeConnectionState.Connected(endpoint = endpoint)
    }

    override suspend fun disconnect() = Unit

    override suspend fun currentConnection(): BridgeConnectionState = BridgeConnectionState.Disconnected

    override suspend fun createSession(request: CreateSessionRequest): SessionDetail = createdDetail

    override suspend fun sendInput(sessionId: String, text: String) {
        sentInputs += text
    }
}

private class FakeSessionRepository(
    private val sessionSummaries: List<SessionSummary>,
    details: ArrayDeque<SessionDetail>,
) : SessionRepository {
    private val detailsQueue = details
    private var lastDetail: SessionDetail? = details.lastOrNull()

    override suspend fun listSessions(): List<SessionSummary> = sessionSummaries

    override suspend fun getSessionDetail(sessionId: String): SessionDetail? {
        val next = if (detailsQueue.isEmpty()) lastDetail else detailsQueue.removeFirst()
        if (next != null) {
            lastDetail = next
        }
        return next
    }
}

private fun arrayDequeOf(vararg details: SessionDetail): ArrayDeque<SessionDetail> {
    return ArrayDeque(details.toList())
}
