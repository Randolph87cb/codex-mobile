package com.openai.codexmobile.service

import com.openai.codexmobile.data.SessionStreamEvent
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionWatchEventReducerTest {
    @Test
    fun assistantDonePostsDoneAndStops() {
        val decision = SessionWatchEventReducer.reduce(
            SessionStreamEvent.AssistantDone(
                sessionId = "session-1",
                turnStatus = "completed",
                turnId = "turn-1",
                errorMessage = null,
                timestamp = null,
            ),
        )

        assertTrue(decision.stopWatching)
        assertEquals(SessionWatchResult.Done, decision.result)
    }

    @Test
    fun toolRequestPostsApprovalAndStops() {
        val decision = SessionWatchEventReducer.reduce(
            SessionStreamEvent.ToolRequest(
                sessionId = "session-1",
                requestId = null,
                method = "item/commandExecution/requestApproval",
                paramsSummary = null,
                timestamp = null,
            ),
        )

        assertTrue(decision.stopWatching)
        assertEquals(
            SessionWatchResult.AwaitingApproval("item/commandExecution/requestApproval"),
            decision.result,
        )
    }

    @Test
    fun runStatusIdlePostsDoneAndStops() {
        val decision = SessionWatchEventReducer.reduce(
            SessionStreamEvent.RunStatus(
                sessionId = "session-1",
                status = "idle",
                timestamp = null,
            ),
        )

        assertTrue(decision.stopWatching)
        assertEquals(SessionWatchResult.Done, decision.result)
    }

    @Test
    fun runStatusRunningContinues() {
        val decision = SessionWatchEventReducer.reduce(
            SessionStreamEvent.RunStatus(
                sessionId = "session-1",
                status = "running",
                timestamp = null,
            ),
        )

        assertFalse(decision.stopWatching)
        assertEquals(null, decision.result)
    }

    @Test
    fun interruptedAndErrorStopWithDistinctResults() {
        val interrupted = SessionWatchEventReducer.reduce(
            SessionStreamEvent.RunInterrupted(
                sessionId = "session-1",
                status = "interrupted",
                timestamp = null,
            ),
        )
        val error = SessionWatchEventReducer.reduce(
            SessionStreamEvent.Error(
                sessionId = "session-1",
                message = "boom",
                timestamp = null,
            ),
        )

        assertTrue(interrupted.stopWatching)
        assertEquals(SessionWatchResult.Interrupted, interrupted.result)
        assertTrue(error.stopWatching)
        assertEquals(SessionWatchResult.Error("boom"), error.result)
    }

    @Test
    fun notificationSummaryUsesLastCodexReplyOnly() {
        val summary = SessionWatchNotificationSummary.extractLastCodexReply(
            """
            你：先解释一下

            Codex：第一段旧回复

            系统：命令执行

            Codex：最新回复第一行
            最新回复第二行
            最新回复第三行不应进入通知

            你：下一条输入
            """.trimIndent(),
        )

        assertEquals("最新回复第一行\n最新回复第二行", summary)
    }

    @Test
    fun notificationSummaryReturnsNullWhenNoCodexReply() {
        val summary = SessionWatchNotificationSummary.extractLastCodexReply(
            """
            你：只有用户消息
            系统：等待处理中
            """.trimIndent(),
        )

        assertNull(summary)
    }

    @Test
    fun notificationSummaryIsLengthLimited() {
        val summary = SessionWatchNotificationSummary.extractLastCodexReply(
            "Codex：" + "a".repeat(240),
        )

        assertEquals(180, summary?.length)
        assertTrue(summary?.endsWith("...") == true)
    }

    @Test
    fun resultNotificationIsSuppressedForVisibleForegroundSession() {
        val shouldPost = SessionWatchNotificationPolicy.shouldPostResultNotification(
            appInForeground = true,
            visibleSessionId = "session-1",
            resultSessionId = "session-1",
        )

        assertFalse(shouldPost)
    }

    @Test
    fun resultNotificationIsPostedWhenAppIsBackgrounded() {
        val shouldPost = SessionWatchNotificationPolicy.shouldPostResultNotification(
            appInForeground = false,
            visibleSessionId = "session-1",
            resultSessionId = "session-1",
        )

        assertTrue(shouldPost)
    }

    @Test
    fun resultNotificationIsPostedForDifferentForegroundSession() {
        val shouldPost = SessionWatchNotificationPolicy.shouldPostResultNotification(
            appInForeground = true,
            visibleSessionId = "session-2",
            resultSessionId = "session-1",
        )

        assertTrue(shouldPost)
    }

    @Test
    fun resultNotificationIsPostedWhenNoSessionIsVisible() {
        val shouldPost = SessionWatchNotificationPolicy.shouldPostResultNotification(
            appInForeground = true,
            visibleSessionId = null,
            resultSessionId = "session-1",
        )

        assertTrue(shouldPost)
    }

    @Test
    fun dismissedPresenceNotificationIsRestoredOnlyWhenPresenceIsActive() {
        assertTrue(SessionWatchDismissPolicy.shouldRestorePresenceNotification(presenceActive = true))
        assertFalse(SessionWatchDismissPolicy.shouldRestorePresenceNotification(presenceActive = false))
    }

    @Test
    fun watchPoolAddsMultipleSessionsWithoutCancellingExistingJobs() {
        val pool = SessionWatchJobPool()
        val firstJob = Job()
        val secondJob = Job()
        val duplicateJob = Job()

        assertTrue(pool.addIfAbsent("session-1", firstJob))
        assertTrue(pool.addIfAbsent("session-2", secondJob))
        assertFalse(pool.addIfAbsent("session-1", duplicateJob))

        assertEquals(listOf("session-1", "session-2"), pool.activeSessionIds())
        assertTrue(firstJob.isActive)
        assertTrue(secondJob.isActive)
        assertTrue(duplicateJob.isActive)

        duplicateJob.cancel()
        firstJob.cancel()
        secondJob.cancel()
    }

    @Test
    fun watchPoolRemovesOneSessionWithoutRemovingAnother() {
        val pool = SessionWatchJobPool()
        val firstJob = Job()
        val secondJob = Job()

        pool.addIfAbsent("session-1", firstJob)
        pool.addIfAbsent("session-2", secondJob)

        assertEquals(firstJob, pool.remove("session-1"))
        assertEquals(listOf("session-2"), pool.activeSessionIds())
        assertTrue(secondJob.isActive)

        firstJob.cancel()
        secondJob.cancel()
    }

    @Test
    fun watchPoolMarksTerminalSessionOnlyOnce() {
        val pool = SessionWatchJobPool()

        assertTrue(pool.markTerminalHandled("session-1"))
        assertFalse(pool.markTerminalHandled("session-1"))
        assertTrue(pool.markTerminalHandled("session-2"))
    }

    @Test
    fun pendingSessionWatchStoreCodecSanitizesBlankAndDuplicateSessionIds() {
        val sessionIds = PendingSessionWatchStoreCodec.sanitize(
            listOf(" session-1 ", "", "session-2", "session-1", "   "),
        )

        assertEquals(setOf("session-1", "session-2"), sessionIds)
    }

    @Test
    fun reconnectPolicyCapsAtMaximumDelay() {
        assertEquals(1_000L, SessionWatchReconnectPolicy.delayMillis(0))
        assertEquals(2_000L, SessionWatchReconnectPolicy.delayMillis(1))
        assertEquals(5_000L, SessionWatchReconnectPolicy.delayMillis(2))
        assertEquals(10_000L, SessionWatchReconnectPolicy.delayMillis(3))
        assertEquals(10_000L, SessionWatchReconnectPolicy.delayMillis(99))
    }

    @Test
    fun snapshotPolicyInfersTerminalResultFromDetailStatus() {
        assertEquals(SessionWatchResult.Done, SessionWatchSnapshotPolicy.resultForStatus("idle"))
        assertEquals(SessionWatchResult.Done, SessionWatchSnapshotPolicy.resultForStatus("completed"))
        assertEquals(SessionWatchResult.Error(null), SessionWatchSnapshotPolicy.resultForStatus("failed"))
        assertEquals(SessionWatchResult.Interrupted, SessionWatchSnapshotPolicy.resultForStatus("cancelled"))
        assertNull(SessionWatchSnapshotPolicy.resultForStatus("running"))
    }
}
