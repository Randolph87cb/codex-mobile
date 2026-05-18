package com.openai.codexmobile.model

data class SessionSummary(
    val id: String,
    val title: String,
    val subtitle: String,
    val lastUpdated: String,
    val cwd: String = "未提供工作目录",
    val model: String = "gpt-5.5",
    val approvalMode: String = "manual",
    val reasoningEffort: String = "medium",
    val serviceTier: String = "fast",
    val status: String = "unknown",
)
