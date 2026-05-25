package com.openai.codexmobile.data

import com.openai.codexmobile.model.BridgeConnectionState
import com.openai.codexmobile.model.SessionDetail
import com.openai.codexmobile.model.SessionGoalSnapshot
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
) {
    val path: String
        get() = stagedPath
}

data class SendInputRequest(
    val text: String? = null,
    val attachments: List<SessionInputAttachmentRef> = emptyList(),
)

data class UploadImageAttachmentRequest(
    val displayName: String,
    val mimeType: String,
    val contentBytes: ByteArray,
    val sessionId: String? = null,
    val sourceByteLength: Int? = null,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
    val preparationMode: String = "original",
)

internal fun UploadImageAttachmentRequest.toDiagnosticsSummary(): String {
    val sourceBytes = sourceByteLength ?: contentBytes.size
    val dimensions = if (imageWidth != null && imageHeight != null) {
        "${imageWidth}x${imageHeight}"
    } else {
        "unknown"
    }
    return buildString {
        append("displayName=")
        append(displayName)
        append(", mimeType=")
        append(mimeType)
        append(", sourceBytes=")
        append(sourceBytes)
        append(", uploadBytes=")
        append(contentBytes.size)
        append(", dimensions=")
        append(dimensions)
        append(", preparationMode=")
        append(preparationMode)
        append(", sessionId=")
        append(sessionId ?: "none")
    }
}

data class UploadedImageAttachment(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val stagedPath: String,
    val savedPath: String? = null,
) {
    val attachmentPath: String
        get() = savedPath ?: stagedPath
}

data class SessionGoalUpdateRequest(
    val objective: String? = null,
    val status: String? = null,
    val tokenBudget: Long? = null,
)

data class SessionGoalResponse(
    val capability: String,
    val goal: SessionGoalSnapshot?,
)

data class SessionGoalClearResult(
    val capability: String,
    val cleared: Boolean,
)

interface BridgeApi {
    fun updateAuthToken(token: String)
    suspend fun connect(endpoint: String): BridgeConnectionState
    suspend fun disconnect()
    suspend fun currentConnection(): BridgeConnectionState
    suspend fun createSession(request: CreateSessionRequest = CreateSessionRequest()): SessionDetail
    suspend fun updateSessionConfig(sessionId: String, update: SessionConfigUpdate): SessionDetail
    suspend fun getSessionGoal(sessionId: String): SessionGoalResponse
    suspend fun updateSessionGoal(sessionId: String, request: SessionGoalUpdateRequest): SessionGoalResponse
    suspend fun clearSessionGoal(sessionId: String): SessionGoalClearResult
    suspend fun uploadImageAttachment(request: UploadImageAttachmentRequest): UploadedImageAttachment
    suspend fun sendInput(sessionId: String, request: SendInputRequest)
    suspend fun interruptSession(sessionId: String)
    suspend fun approveSession(
        sessionId: String,
        requestId: BridgeRequestId?,
        decision: ApprovalDecision,
    ): ApprovalActionResult
    fun observeSessionEvents(sessionId: String): Flow<SessionStreamEvent>
}
