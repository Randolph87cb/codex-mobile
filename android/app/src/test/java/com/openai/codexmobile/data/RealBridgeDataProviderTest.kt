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
    fun buildStreamFailureNoticePrefersFriendlyTransportMessages() {
        assertTrue(buildStreamFailureNotice("Broken pipe", statusCode = null) == "实时流连接已中断。")
        assertTrue(
            buildStreamFailureNotice(
                message = "anything",
                statusCode = 503,
            ) == "bridge 正在重启，实时流暂时不可用。",
        )
        assertTrue(
            buildStreamFailureNotice(
                message = "unauthorized",
                statusCode = 401,
            ) == "实时流鉴权失败，请检查 bridge 令牌后重试。",
        )
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
    fun parseGoalUpdatedEvent() {
        val event = parseSessionStreamEvent(
            sessionId = "sess-goal",
            payload = """{"type":"goal.updated","sessionId":"sess-goal","timestamp":"2026-05-22T09:05:00Z","data":{"goalCapability":"supported","goal":{"objective":"把 goal 模式接进手机端","status":"paused","tokenBudget":120000,"tokensUsed":3400,"timeUsedSeconds":180,"createdAt":"2026-05-22T09:00:00Z","updatedAt":"2026-05-22T09:05:00Z"}}}""",
        ) as? SessionStreamEvent.GoalUpdated

        assertNotNull(event)
        assertTrue(event?.goalCapability == "supported")
        assertTrue(event?.goal?.objective == "把 goal 模式接进手机端")
        assertTrue(event?.goal?.status == "paused")
        assertTrue(event?.goal?.tokenBudget == 120000L)
    }

    @Test
    fun parseSessionDetailReadsGoalFields() {
        val detail = JSONObject(
            """{"id":"sess-goal-detail","title":"目标线程","subtitle":"gpt-5.5 • 自动 • 空闲","lastUpdated":"2026-05-22T09:05:00Z","transcriptPreview":"工作目录：D:\\workspace\\codex-mobile","cwd":"D:\\workspace\\codex-mobile","model":"gpt-5.5","approvalMode":"auto","reasoningEffort":"medium","serviceTier":"default","sandboxMode":"danger-full-access","status":"idle","goalCapability":"supported","goal":{"objective":"把 goal 模式接进手机端","status":"active","tokenBudget":150000,"tokensUsed":4500,"timeUsedSeconds":200,"createdAt":"2026-05-22T09:00:00Z","updatedAt":"2026-05-22T09:05:00Z"}}""",
        ).toSessionDetail()

        assertTrue(detail.goalCapability == "supported")
        assertTrue(detail.goal?.objective == "把 goal 模式接进手机端")
        assertTrue(detail.goal?.tokenBudget == 150000L)
        assertTrue(detail.goal?.tokensUsed == 4500L)
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
    fun parseAccountQuotaSnapshotReadsFiveHourAndOneWeekWindows() {
        val snapshot = JSONObject(
            """{"limitId":"codex","planType":"prolite","fiveHours":{"usedPercent":6,"windowDurationMins":300,"resetsAt":"2026-05-25T11:51:54Z"},"oneWeek":{"usedPercent":16,"windowDurationMins":10080,"resetsAt":"2026-05-31T00:41:21Z"}}""",
        ).toAccountQuotaSnapshot()

        assertTrue(snapshot.limitId == "codex")
        assertTrue(snapshot.planType == "prolite")
        assertTrue(snapshot.fiveHours?.usedPercent == 6)
        assertTrue(snapshot.fiveHours?.windowDurationMins == 300)
        assertTrue(snapshot.oneWeek?.usedPercent == 16)
        assertTrue(snapshot.oneWeek?.windowDurationMins == 10080)
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

    @Test
    fun buildUploadImageFailureMessagePrefersFriendlyBridgeMessage() {
        val message = buildUploadImageFailureMessage(
            statusCode = 413,
            payload = """{"error":"image-too-large","maxMegabytes":64,"message":"图片过大，当前上限 64 MB。"}""",
        )

        assertTrue(message == "图片过大，当前上限 64 MB。")
    }

    @Test
    fun buildUploadImageFailureMessageFallsBackForUnsupportedMimeType() {
        val message = buildUploadImageFailureMessage(
            statusCode = 400,
            payload = """{"error":"invalid-image-upload","message":"unsupported-image-mime-type"}""",
        )

        assertTrue(message == "当前只支持 JPG、PNG、WEBP、GIF、BMP 图片。")
    }

    @Test
    fun buildAccountQuotaFailureMessageUsesFriendlyRestartHint() {
        val message = buildAccountQuotaFailureMessage(
            statusCode = 503,
            payload = """{"error":"bridge-restarting","message":"bridge is restarting"}""",
        )

        assertTrue(message == "bridge 正在重启，暂时无法刷新额度。")
    }
}
