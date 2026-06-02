package com.openai.codexmobile.data

import com.openai.codexmobile.model.BridgeConnectionState
import com.openai.codexmobile.model.AccountQuotaCreditsSnapshot
import com.openai.codexmobile.model.AccountQuotaSnapshot
import com.openai.codexmobile.model.AccountQuotaWindowSnapshot
import com.openai.codexmobile.model.SessionDetail
import com.openai.codexmobile.model.SessionGoalSnapshot
import com.openai.codexmobile.model.SessionSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class FakeCodexDataProvider : CodexDataProvider {
    private var connectionState: BridgeConnectionState = BridgeConnectionState.Disconnected
    private var sessionGoal: SessionGoalSnapshot? = null
    private val sessionDetailsById = mutableMapOf<String, SessionDetail>()

    override fun updateAuthToken(token: String) = Unit

    private val sessions = mutableListOf(
        SessionSummary(
            id = "session-001",
            title = "Compose 骨架初始化",
            subtitle = "占位会话列表项",
            lastUpdated = "2026-05-26T09:40:00Z",
            cwd = "D:\\workspace\\codex-mobile",
            model = "gpt-5.5",
            status = "idle",
        ),
        SessionSummary(
            id = "session-002",
            title = "桥接协议探索",
            subtitle = "预留给后续原生桥接集成",
            lastUpdated = "2026-05-25T09:40:00Z",
            cwd = "D:\\workspace\\playground",
            model = "gpt-5.5",
            status = "idle",
        ),
    )

    init {
        sessions.forEach { session ->
            sessionDetailsById[session.id] = SessionDetail(
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
                sandboxMode = session.sandboxMode,
                status = session.status,
                goal = sessionGoal,
                goalCapability = "supported",
            )
        }
    }

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

    override suspend fun getAccountQuota(): AccountQuotaSnapshot {
        delay(80)
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

    override suspend fun createSession(request: CreateSessionRequest): SessionDetail {
        delay(150)
        return SessionDetail(
            id = "session-created-fake",
            title = "模拟创建的会话",
            subtitle = "${request.model} • ${request.approvalMode}",
            lastUpdated = "2026-05-26T09:45:00Z",
            transcriptPreview = "工作目录：${request.cwd}",
            cwd = request.cwd,
            model = request.model,
            approvalMode = request.approvalMode,
            reasoningEffort = request.reasoningEffort,
            serviceTier = request.serviceTier,
            sandboxMode = request.sandboxMode,
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
            sandboxMode = update.sandboxMode ?: detail.sandboxMode,
        ).also { updated ->
            sessionDetailsById[sessionId] = updated
        }
    }

    override suspend fun renameSessionTitle(sessionId: String, title: String): SessionDetail {
        delay(80)
        val detail = getSessionDetail(sessionId) ?: error("session not found")
        val updated = detail.copy(title = title.trim())
        sessionDetailsById[sessionId] = updated
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index >= 0) {
            sessions[index] = sessions[index].copy(title = updated.title)
        }
        return updated
    }

    override suspend fun getSessionGoal(sessionId: String): SessionGoalResponse {
        delay(60)
        return SessionGoalResponse(
            capability = "supported",
            goal = sessionGoal,
        )
    }

    override suspend fun updateSessionGoal(
        sessionId: String,
        request: SessionGoalUpdateRequest,
    ): SessionGoalResponse {
        delay(80)
        val existing = sessionGoal
        sessionGoal = SessionGoalSnapshot(
            objective = request.objective ?: existing?.objective ?: "模拟目标",
            status = request.status ?: existing?.status ?: "active",
            tokenBudget = request.tokenBudget ?: existing?.tokenBudget,
            tokensUsed = existing?.tokensUsed ?: 0L,
            timeUsedSeconds = existing?.timeUsedSeconds ?: 0L,
            createdAt = existing?.createdAt ?: "2026-05-22T09:00:00Z",
            updatedAt = "2026-05-22T09:05:00Z",
        )
        return SessionGoalResponse(
            capability = "supported",
            goal = sessionGoal,
        )
    }

    override suspend fun clearSessionGoal(sessionId: String): SessionGoalClearResult {
        delay(60)
        sessionGoal = null
        return SessionGoalClearResult(
            capability = "supported",
            cleared = true,
        )
    }

    override suspend fun uploadImageAttachment(request: UploadImageAttachmentRequest): UploadedImageAttachment {
        delay(80)
        return UploadedImageAttachment(
            id = "fake-image-attachment",
            displayName = request.displayName,
            mimeType = request.mimeType,
            stagedPath = "D:\\workspace\\codex-mobile\\.tmp\\fake-image-attachment.png",
        )
    }

    override suspend fun uploadVideoAttachment(request: UploadVideoAttachmentRequest): UploadedVideoAttachment {
        delay(80)
        return UploadedVideoAttachment(
            id = "fake-video-attachment",
            displayName = request.displayName,
            mimeType = request.mimeType,
            stagedPath = "D:\\workspace\\codex-mobile\\.tmp\\fake-video-attachment.mp4",
        )
    }

    override suspend fun sendInput(sessionId: String, request: SendInputRequest) {
        delay(100)
    }

    override suspend fun interruptSession(sessionId: String) {
        delay(80)
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

    override suspend fun listSessions(archived: Boolean): List<SessionSummary> {
        delay(150)
        return if (archived) emptyList() else sessions
    }

    override suspend fun getSessionDetail(sessionId: String): SessionDetail? {
        delay(150)
        return sessionDetailsById[sessionId]?.copy(goal = sessionGoal, goalCapability = "supported")
    }

    override suspend fun archiveSession(sessionId: String) {
        delay(100)
    }

    override suspend fun unarchiveSession(sessionId: String) {
        delay(100)
    }
}
