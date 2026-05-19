package com.openai.codexmobile.model

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
    val status: String = "unknown",
)
