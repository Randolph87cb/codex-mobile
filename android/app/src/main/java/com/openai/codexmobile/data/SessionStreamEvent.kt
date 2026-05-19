package com.openai.codexmobile.data

sealed interface SessionStreamEvent {
    val sessionId: String
    val timestamp: String?

    data class StreamOpened(
        override val sessionId: String,
        override val timestamp: String? = null,
    ) : SessionStreamEvent

    data class StreamClosed(
        override val sessionId: String,
        val reason: String? = null,
        override val timestamp: String? = null,
    ) : SessionStreamEvent

    data class SessionStarted(
        override val sessionId: String,
        val status: String,
        val cwd: String?,
        val model: String?,
        val approvalMode: String?,
        val reasoningEffort: String?,
        val serviceTier: String?,
        val sandboxMode: String?,
        val threadId: String?,
        override val timestamp: String?,
    ) : SessionStreamEvent

    data class AssistantDelta(
        override val sessionId: String,
        val text: String,
        val turnId: String?,
        override val timestamp: String?,
    ) : SessionStreamEvent

    data class AssistantDone(
        override val sessionId: String,
        val turnStatus: String?,
        val turnId: String?,
        val errorMessage: String?,
        override val timestamp: String?,
    ) : SessionStreamEvent

    data class Activity(
        override val sessionId: String,
        val itemType: String?,
        val itemId: String?,
        val transcriptBlock: String,
        val summary: String?,
        override val timestamp: String?,
    ) : SessionStreamEvent

    data class RunStatus(
        override val sessionId: String,
        val status: String,
        override val timestamp: String?,
    ) : SessionStreamEvent

    data class RunInterrupted(
        override val sessionId: String,
        val status: String?,
        override val timestamp: String?,
    ) : SessionStreamEvent

    data class ToolRequest(
        override val sessionId: String,
        val requestId: BridgeRequestId?,
        val method: String?,
        val paramsSummary: String?,
        override val timestamp: String?,
    ) : SessionStreamEvent

    data class ToolResult(
        override val sessionId: String,
        val requestId: BridgeRequestId?,
        val method: String?,
        val decision: ApprovalDecision?,
        val status: String?,
        val summary: String?,
        override val timestamp: String?,
    ) : SessionStreamEvent

    data class Error(
        override val sessionId: String,
        val message: String,
        override val timestamp: String?,
    ) : SessionStreamEvent
}
