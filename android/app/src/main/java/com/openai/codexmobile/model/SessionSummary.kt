package com.openai.codexmobile.model

data class SessionSummary(
    val id: String,
    val title: String,
    val subtitle: String,
    val lastUpdated: String,
    val status: String = "unknown",
)
