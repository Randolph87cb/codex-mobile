package com.openai.codexmobile.data

import com.openai.codexmobile.model.BridgeConnectionState
import com.openai.codexmobile.model.SessionDetail
import kotlinx.coroutines.flow.Flow

data class CreateSessionRequest(
    val cwd: String = ".",
    val model: String = "gpt-5.5",
    val approvalMode: String = "manual",
    val reasoningEffort: String = "medium",
    val serviceTier: String = "default",
)

data class SessionConfigUpdate(
    val cwd: String? = null,
    val model: String? = null,
    val approvalMode: String? = null,
    val reasoningEffort: String? = null,
    val serviceTier: String? = null,
)

interface BridgeApi {
    fun updateAuthToken(token: String)
    suspend fun connect(endpoint: String): BridgeConnectionState
    suspend fun disconnect()
    suspend fun currentConnection(): BridgeConnectionState
    suspend fun createSession(request: CreateSessionRequest = CreateSessionRequest()): SessionDetail
    suspend fun updateSessionConfig(sessionId: String, update: SessionConfigUpdate): SessionDetail
    suspend fun sendInput(sessionId: String, text: String)
    suspend fun approveSession(
        sessionId: String,
        requestId: BridgeRequestId?,
        decision: ApprovalDecision,
    ): ApprovalActionResult
    fun observeSessionEvents(sessionId: String): Flow<SessionStreamEvent>
}
