package com.openai.codexmobile.model

import com.openai.codexmobile.data.BridgeRequestId

data class PendingApprovalSnapshot(
    val requestId: BridgeRequestId?,
    val method: String?,
    val paramsSummary: String?,
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
    val pendingApproval: PendingApprovalSnapshot? = null,
)
