package com.openai.codexmobile.data

import com.openai.codexmobile.model.BridgeConnectionState
import com.openai.codexmobile.model.SessionDetail
import kotlinx.coroutines.flow.Flow

data class CreateSessionRequest(
    val cwd: String = ".",
    val model: String = "gpt-5.5",
    val approvalMode: String = "auto",
    val reasoningEffort: String = "medium",
    val serviceTier: String = "default",
    val sandboxMode: String = "danger-full-access",
)

data class SessionConfigUpdate(
    val cwd: String? = null,
    val model: String? = null,
    val approvalMode: String? = null,
    val reasoningEffort: String? = null,
    val serviceTier: String? = null,
    val sandboxMode: String? = null,
)

data class SessionInputAttachmentRef(
    val stagedPath: String,
)

data class SendInputRequest(
    val text: String? = null,
    val attachments: List<SessionInputAttachmentRef> = emptyList(),
)

data class UploadImageAttachmentRequest(
    val displayName: String,
    val mimeType: String,
    val contentBytes: ByteArray,
)

data class UploadedImageAttachment(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val stagedPath: String,
)

interface BridgeApi {
    fun updateAuthToken(token: String)
    suspend fun connect(endpoint: String): BridgeConnectionState
    suspend fun disconnect()
    suspend fun currentConnection(): BridgeConnectionState
    suspend fun createSession(request: CreateSessionRequest = CreateSessionRequest()): SessionDetail
    suspend fun updateSessionConfig(sessionId: String, update: SessionConfigUpdate): SessionDetail
    suspend fun uploadImageAttachment(request: UploadImageAttachmentRequest): UploadedImageAttachment
    suspend fun sendInput(sessionId: String, request: SendInputRequest)
    suspend fun approveSession(
        sessionId: String,
        requestId: BridgeRequestId?,
        decision: ApprovalDecision,
    ): ApprovalActionResult
    fun observeSessionEvents(sessionId: String): Flow<SessionStreamEvent>
}
