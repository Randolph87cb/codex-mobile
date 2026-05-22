package com.openai.codexmobile.model

import com.openai.codexmobile.data.BridgeRequestId

data class PendingApprovalSnapshot(
    val requestId: BridgeRequestId?,
    val method: String?,
    val paramsSummary: String?,
)

data class SessionGoalSnapshot(
    val objective: String,
    val status: String,
    val tokenBudget: Long? = null,
    val tokensUsed: Long = 0,
    val timeUsedSeconds: Long = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
)

data class SessionDetail(
    val id: String,
    val title: String,
    val subtitle: String,
    val lastUpdated: String,
    val transcriptPreview: String,
    val cwd: String = "",
    val model: String = "gpt-5.5",
    val approvalMode: String = "manual",
    val reasoningEffort: String = "medium",
    val serviceTier: String = "default",
    val sandboxMode: String = "workspace-write",
    val status: String = "unknown",
    val goal: SessionGoalSnapshot? = null,
    val goalCapability: String = "unknown",
    val pendingApproval: PendingApprovalSnapshot? = null,
)
