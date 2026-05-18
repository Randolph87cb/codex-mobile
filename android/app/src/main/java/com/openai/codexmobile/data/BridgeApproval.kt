package com.openai.codexmobile.data

sealed interface BridgeRequestId {
    fun toJsonValue(): Any

    data class Text(
        val value: String,
    ) : BridgeRequestId {
        override fun toJsonValue(): Any = value
        override fun toString(): String = value
    }

    data class Number(
        val value: Long,
    ) : BridgeRequestId {
        override fun toJsonValue(): Any = value
        override fun toString(): String = value.toString()
    }
}

enum class ApprovalDecision(
    val wireValue: String,
    val label: String,
) {
    Approve("approve", "批准"),
    ApproveForSession("approve_for_session", "本会话都批准"),
    Reject("reject", "拒绝"),
    RejectAndInterrupt("reject_and_interrupt", "拒绝并中断");

    companion object {
        fun fromWireValue(value: String?): ApprovalDecision? {
            return entries.firstOrNull { it.wireValue == value }
        }
    }
}

data class ApprovalActionResult(
    val requestId: BridgeRequestId,
    val decision: ApprovalDecision,
    val status: String,
    val method: String?,
)
