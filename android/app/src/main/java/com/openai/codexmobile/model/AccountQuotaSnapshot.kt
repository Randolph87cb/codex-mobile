package com.openai.codexmobile.model

data class AccountQuotaWindowSnapshot(
    val usedPercent: Int,
    val windowDurationMins: Int,
    val resetsAt: String? = null,
)

data class AccountQuotaCreditsSnapshot(
    val hasCredits: Boolean,
    val unlimited: Boolean,
    val balance: String? = null,
)

data class AccountQuotaSnapshot(
    val limitId: String? = null,
    val planType: String? = null,
    val rateLimitReachedType: String? = null,
    val fiveHours: AccountQuotaWindowSnapshot? = null,
    val oneWeek: AccountQuotaWindowSnapshot? = null,
    val credits: AccountQuotaCreditsSnapshot? = null,
)
