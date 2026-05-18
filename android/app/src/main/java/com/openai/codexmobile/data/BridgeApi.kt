package com.openai.codexmobile.data

import com.openai.codexmobile.model.BridgeConnectionState
import com.openai.codexmobile.model.SessionDetail
import kotlinx.coroutines.flow.Flow

data class CreateSessionRequest(
    val cwd: String = ".",
    val model: String = "gpt-5.5",
    val approvalMode: String = "manual",
)

interface BridgeApi {
    suspend fun connect(endpoint: String): BridgeConnectionState
    suspend fun disconnect()
    suspend fun currentConnection(): BridgeConnectionState
    suspend fun createSession(request: CreateSessionRequest = CreateSessionRequest()): SessionDetail
    suspend fun sendInput(sessionId: String, text: String)
    fun observeSessionEvents(sessionId: String): Flow<SessionStreamEvent>
}
