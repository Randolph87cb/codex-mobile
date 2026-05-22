package com.openai.codexmobile.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

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

    @Test
    fun parseBridgeLifecycleEvent() {
        val event = parseSessionStreamEvent(
            sessionId = "sess-restart",
            payload = """{"type":"bridge.lifecycle","sessionId":"sess-restart","timestamp":"2026-05-20T12:00:00Z","data":{"phase":"restarting","reason":"bridge restart requested","graceMs":2000,"bridgeVersion":"0.1.0","bridgeStartedAt":"2026-05-20T11:59:00Z"}}""",
        ) as? SessionStreamEvent.BridgeLifecycle

        assertNotNull(event)
        assertTrue(event?.phase == "restarting")
        assertTrue(event?.reason == "bridge restart requested")
        assertTrue(event?.graceMs == 2000)
        assertTrue(event?.bridgeVersion == "0.1.0")
        assertTrue(event?.bridgeStartedAt == "2026-05-20T11:59:00Z")
    }

    @Test
    fun parseSessionSummaryReadsArchivedFlag() {
        val summary = JSONObject(
            """{"id":"thread-archived","title":"已归档会话","model":"gpt-5.5","status":"idle","cwd":"D:\\workspace\\archived","updatedAt":"2026-05-21T10:00:00Z","archived":true}""",
        ).toSessionSummary()

        assertTrue(summary.archived)
        assertTrue(summary.id == "thread-archived")
    }

    @Test
    fun parseUploadedImageAttachmentResponsePrefersSavedPath() {
        val uploaded = parseUploadedImageAttachmentResponse(
            payload = """{"id":"att-1","displayName":"screen.png","mimeType":"image/png","stagedPath":"D:\\bridge\\staged\\screen.png","savedPath":"D:\\bridge\\saved\\screen.png"}""",
            fallbackDisplayName = "fallback.png",
            fallbackMimeType = "image/jpeg",
        )

        assertTrue(uploaded.stagedPath == "D:\\bridge\\staged\\screen.png")
        assertTrue(uploaded.savedPath == "D:\\bridge\\saved\\screen.png")
        assertTrue(uploaded.attachmentPath == "D:\\bridge\\saved\\screen.png")
    }

    @Test
    fun parseUploadedImageAttachmentResponseFallsBackToLegacyPathField() {
        val uploaded = parseUploadedImageAttachmentResponse(
            payload = """{"id":"att-legacy","path":"D:\\bridge\\staged\\legacy.png"}""",
            fallbackDisplayName = "legacy.png",
            fallbackMimeType = "image/png",
        )

        assertTrue(uploaded.displayName == "legacy.png")
        assertTrue(uploaded.mimeType == "image/png")
        assertTrue(uploaded.stagedPath == "D:\\bridge\\staged\\legacy.png")
        assertTrue(uploaded.savedPath == null)
        assertTrue(uploaded.attachmentPath == "D:\\bridge\\staged\\legacy.png")
    }
}
