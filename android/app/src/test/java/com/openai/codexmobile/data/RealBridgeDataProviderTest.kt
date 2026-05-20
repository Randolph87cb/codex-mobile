package com.openai.codexmobile.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RealBridgeDataProviderTest {
    @Test
    fun shouldSuppressStreamFailureOnlyForClientInitiatedShutdown() {
        assertTrue(shouldSuppressStreamFailure("Socket closed", closedByClient = true))
        assertTrue(shouldSuppressStreamFailure(null, closedByClient = true))
        assertFalse(shouldSuppressStreamFailure("Socket closed", closedByClient = false))
    }

    @Test
    fun parseSessionStartedRestoresPendingApprovalSnapshot() {
        val event = parseSessionStreamEvent(
            sessionId = "sess-pending",
            payload = """{"type":"session.started","sessionId":"sess-pending","timestamp":"2026-05-20T10:00:00Z","data":{"status":"awaiting_approval","pendingApproval":{"requestId":"req-pending","method":"item/permissions/requestApproval","paramsSummary":"等待审批：item/permissions/requestApproval"}}}""",
        ) as? SessionStreamEvent.SessionStarted

        assertNotNull(event)
        assertTrue(event?.pendingApproval?.requestId == BridgeRequestId.Text("req-pending"))
        assertTrue(event?.pendingApproval?.method == "item/permissions/requestApproval")
        assertTrue(event?.pendingApproval?.paramsSummary == "等待审批：item/permissions/requestApproval")
    }
}
