package com.openai.codexmobile.model

data class SessionActivityEntry(
    val stableId: String,
    val itemType: String?,
    val itemId: String?,
    val title: String,
    val body: String?,
    val summary: String,
    val transcriptBlock: String,
    val updatedAt: String?,
)
