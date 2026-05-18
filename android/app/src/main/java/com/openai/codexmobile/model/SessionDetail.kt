package com.openai.codexmobile.model

data class SessionDetail(
    val id: String,
    val title: String,
    val subtitle: String,
    val lastUpdated: String,
    val transcriptPreview: String,
    val status: String = "unknown",
)
