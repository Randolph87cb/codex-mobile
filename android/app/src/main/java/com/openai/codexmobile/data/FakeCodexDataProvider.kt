package com.openai.codexmobile.data

import com.openai.codexmobile.model.BridgeConnectionState
import com.openai.codexmobile.model.SessionDetail
import com.openai.codexmobile.model.SessionSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class FakeCodexDataProvider : CodexDataProvider {
    private var connectionState: BridgeConnectionState = BridgeConnectionState.Disconnected

    override fun updateAuthToken(token: String) = Unit

    private val sessions = listOf(
        SessionSummary(
            id = "session-001",
            title = "Compose 骨架初始化",
            subtitle = "占位会话列表项",
            lastUpdated = "刚刚更新",
            cwd = "D:\\workspace\\codex-mobile",
            model = "gpt-5.5",
            status = "idle",
        ),
        SessionSummary(
            id = "session-002",
            title = "桥接协议探索",
            subtitle = "预留给后续原生桥接集成",
            lastUpdated = "12 分钟前更新",
            cwd = "D:\\workspace\\playground",
            model = "gpt-5.5",
            status = "idle",
        ),
    )

    override suspend fun connect(endpoint: String): BridgeConnectionState {
        delay(300)
        connectionState = BridgeConnectionState.Connected(endpoint)
        return connectionState
    }

    override suspend fun disconnect() {
        delay(100)
        connectionState = BridgeConnectionState.Disconnected
    }

    override suspend fun currentConnection(): BridgeConnectionState = connectionState

    override suspend fun createSession(request: CreateSessionRequest): SessionDetail {
        delay(150)
        return SessionDetail(
            id = "session-created-fake",
            title = "模拟创建的会话",
            subtitle = "${request.model} • ${request.approvalMode}",
            lastUpdated = "刚刚更新",
            transcriptPreview = "工作目录：${request.cwd}",
            cwd = request.cwd,
            model = request.model,
            approvalMode = request.approvalMode,
            reasoningEffort = request.reasoningEffort,
            serviceTier = request.serviceTier,
            status = "idle",
        )
    }

    override suspend fun updateSessionConfig(
        sessionId: String,
        update: SessionConfigUpdate,
    ): SessionDetail {
        delay(100)
        val detail = getSessionDetail(sessionId) ?: error("session not found")
        return detail.copy(
            cwd = update.cwd ?: detail.cwd,
            model = update.model ?: detail.model,
            approvalMode = update.approvalMode ?: detail.approvalMode,
            reasoningEffort = update.reasoningEffort ?: detail.reasoningEffort,
            serviceTier = update.serviceTier ?: detail.serviceTier,
        )
    }

    override suspend fun uploadImageAttachment(request: UploadImageAttachmentRequest): UploadedImageAttachment {
        delay(80)
        return UploadedImageAttachment(
            id = "fake-image-attachment",
            displayName = request.displayName,
            mimeType = request.mimeType,
        )
    }

    override suspend fun sendInput(sessionId: String, request: SendInputRequest) {
        delay(100)
    }

    override suspend fun approveSession(
        sessionId: String,
        requestId: BridgeRequestId?,
        decision: ApprovalDecision,
    ): ApprovalActionResult {
        delay(100)
        return ApprovalActionResult(
            requestId = requestId ?: BridgeRequestId.Text("fake-request"),
            decision = decision,
            status = "running",
            method = "item/commandExecution/requestApproval",
        )
    }

    override fun observeSessionEvents(sessionId: String): Flow<SessionStreamEvent> = emptyFlow()

    override suspend fun listSessions(): List<SessionSummary> {
        delay(150)
        return sessions
    }

    override suspend fun getSessionDetail(sessionId: String): SessionDetail? {
        delay(150)
        val session = sessions.firstOrNull { it.id == sessionId } ?: return null
        return SessionDetail(
            id = session.id,
            title = session.title,
            subtitle = session.subtitle,
            lastUpdated = session.lastUpdated,
            transcriptPreview = "这里是占位详情页，后续会替换成真实会话内容和工具输出。",
            cwd = session.cwd,
            model = session.model,
            approvalMode = session.approvalMode,
            reasoningEffort = session.reasoningEffort,
            serviceTier = session.serviceTier,
            status = session.status,
        )
    }
}
