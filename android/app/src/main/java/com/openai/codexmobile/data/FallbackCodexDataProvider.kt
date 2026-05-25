package com.openai.codexmobile.data

import com.openai.codexmobile.model.BridgeConnectionState
import com.openai.codexmobile.model.SessionDetail
import com.openai.codexmobile.model.SessionSummary
import kotlinx.coroutines.flow.Flow

class FallbackCodexDataProvider(
    private val primary: CodexDataProvider,
    private val fallback: CodexDataProvider,
) : CodexDataProvider {
    private var activeProvider: CodexDataProvider? = null
    private var connectionState: BridgeConnectionState = BridgeConnectionState.Disconnected

    override fun updateAuthToken(token: String) {
        primary.updateAuthToken(token)
        fallback.updateAuthToken(token)
    }

    override suspend fun connect(endpoint: String): BridgeConnectionState {
        return try {
            val primaryState = primary.connect(endpoint)
            activeProvider = primary
            connectionState = primaryState
            primaryState
        } catch (_: Exception) {
            fallback.connect(endpoint)
            activeProvider = fallback
            connectionState = BridgeConnectionState.Connected(
                endpoint = endpoint.trimEnd('/'),
                transport = "模拟",
                provider = "fake-fallback",
            )
            connectionState
        }
    }

    override suspend fun disconnect() {
        primary.disconnect()
        fallback.disconnect()
        activeProvider = null
        connectionState = BridgeConnectionState.Disconnected
    }

    override suspend fun currentConnection(): BridgeConnectionState = connectionState

    override suspend fun createSession(request: CreateSessionRequest): SessionDetail {
        return requireActiveProvider().createSession(request)
    }

    override suspend fun updateSessionConfig(
        sessionId: String,
        update: SessionConfigUpdate,
    ): SessionDetail {
        return requireActiveProvider().updateSessionConfig(sessionId, update)
    }

    override suspend fun getSessionGoal(sessionId: String): SessionGoalResponse {
        return requireActiveProvider().getSessionGoal(sessionId)
    }

    override suspend fun updateSessionGoal(
        sessionId: String,
        request: SessionGoalUpdateRequest,
    ): SessionGoalResponse {
        return requireActiveProvider().updateSessionGoal(sessionId, request)
    }

    override suspend fun clearSessionGoal(sessionId: String): SessionGoalClearResult {
        return requireActiveProvider().clearSessionGoal(sessionId)
    }

    override suspend fun uploadImageAttachment(request: UploadImageAttachmentRequest): UploadedImageAttachment {
        return requireActiveProvider().uploadImageAttachment(request)
    }

    override suspend fun sendInput(sessionId: String, request: SendInputRequest) {
        requireActiveProvider().sendInput(sessionId, request)
    }

    override suspend fun interruptSession(sessionId: String) {
        requireActiveProvider().interruptSession(sessionId)
    }

    override suspend fun approveSession(
        sessionId: String,
        requestId: BridgeRequestId?,
        decision: ApprovalDecision,
    ): ApprovalActionResult {
        return requireActiveProvider().approveSession(sessionId, requestId, decision)
    }

    override fun observeSessionEvents(sessionId: String): Flow<SessionStreamEvent> {
        return requireActiveProvider().observeSessionEvents(sessionId)
    }

    override suspend fun listSessions(archived: Boolean): List<SessionSummary> {
        return requireActiveProvider().listSessions(archived)
    }

    override suspend fun getSessionDetail(sessionId: String): SessionDetail? {
        return requireActiveProvider().getSessionDetail(sessionId)
    }

    override suspend fun archiveSession(sessionId: String) {
        requireActiveProvider().archiveSession(sessionId)
    }

    override suspend fun unarchiveSession(sessionId: String) {
        requireActiveProvider().unarchiveSession(sessionId)
    }

    private fun requireActiveProvider(): CodexDataProvider {
        return activeProvider ?: primary
    }
}
