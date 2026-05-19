package com.openai.codexmobile

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.openai.codexmobile.model.SessionDetail
import com.openai.codexmobile.ui.screen.SessionDetailScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SessionDetailAutoScrollTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun transcriptDoesNotAutoScrollWhenUserHasScrolledUp() {
        lateinit var appendMessage: () -> Unit
        lateinit var scrollToTop: () -> Unit
        lateinit var scrollValue: () -> Int
        lateinit var scrollMaxValue: () -> Int

        composeRule.setContent {
            MaterialTheme {
                var detail by remember {
                    mutableStateOf(
                        buildSessionDetail(
                            transcript = buildTranscript(messageCount = 18),
                        ),
                    )
                }
                val scrollState = rememberScrollState()
                var shouldScrollToTop by remember { mutableStateOf(false) }

                LaunchedEffect(shouldScrollToTop) {
                    if (shouldScrollToTop) {
                        scrollState.scrollTo(0)
                        shouldScrollToTop = false
                    }
                }

                appendMessage = {
                    detail = detail.copy(
                        transcriptPreview = detail.transcriptPreview + "\n\nCodex：新增回复",
                        lastUpdated = detail.lastUpdated + "!",
                    )
                }
                scrollToTop = { shouldScrollToTop = true }
                scrollValue = { scrollState.value }
                scrollMaxValue = { scrollState.maxValue }

                SessionDetailScreen(
                    paddingValues = PaddingValues(0.dp),
                    sessionDetail = detail,
                    draftSession = null,
                    sessionRealtimeState = SessionRealtimeUiState(),
                    queuedInputs = emptyList(),
                    draftMessage = "",
                    pendingImageAttachment = null,
                    isLoading = false,
                    onDraftMessageChange = {},
                    onPickImage = {},
                    onClearPendingImageAttachment = {},
                    onSend = {},
                    onApprovalDecision = {},
                    onUpdateCwd = {},
                    onUpdateModel = {},
                    onUpdateReasoningEffort = {},
                    onUpdateServiceTier = {},
                    onUpdateSandboxMode = {},
                    onRefreshSession = {},
                    transcriptScrollState = scrollState,
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { scrollMaxValue() > 0 }
        composeRule.waitUntil(timeoutMillis = 5_000) { scrollValue() == scrollMaxValue() }
        composeRule.runOnIdle { scrollToTop() }
        composeRule.waitUntil(timeoutMillis = 5_000) { scrollValue() == 0 }

        composeRule.runOnIdle { appendMessage() }
        composeRule.waitUntil(timeoutMillis = 5_000) { scrollMaxValue() > 0 }

        composeRule.runOnIdle {
            assertTrue(scrollValue() < scrollMaxValue())
        }
    }

    @Test
    fun transcriptKeepsAutoScrollingWhenUserStaysAtBottom() {
        lateinit var appendMessage: () -> Unit
        lateinit var scrollValue: () -> Int
        lateinit var scrollMaxValue: () -> Int

        composeRule.setContent {
            MaterialTheme {
                var detail by remember {
                    mutableStateOf(
                        buildSessionDetail(
                            transcript = buildTranscript(messageCount = 18),
                        ),
                    )
                }
                val scrollState = rememberScrollState()

                appendMessage = {
                    detail = detail.copy(
                        transcriptPreview = detail.transcriptPreview + "\n\nCodex：新增回复",
                        lastUpdated = detail.lastUpdated + "!",
                    )
                }
                scrollValue = { scrollState.value }
                scrollMaxValue = { scrollState.maxValue }

                SessionDetailScreen(
                    paddingValues = PaddingValues(0.dp),
                    sessionDetail = detail,
                    draftSession = null,
                    sessionRealtimeState = SessionRealtimeUiState(),
                    queuedInputs = emptyList(),
                    draftMessage = "",
                    pendingImageAttachment = null,
                    isLoading = false,
                    onDraftMessageChange = {},
                    onPickImage = {},
                    onClearPendingImageAttachment = {},
                    onSend = {},
                    onApprovalDecision = {},
                    onUpdateCwd = {},
                    onUpdateModel = {},
                    onUpdateReasoningEffort = {},
                    onUpdateServiceTier = {},
                    onUpdateSandboxMode = {},
                    onRefreshSession = {},
                    transcriptScrollState = scrollState,
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { scrollMaxValue() > 0 }
        composeRule.waitUntil(timeoutMillis = 5_000) { scrollValue() == scrollMaxValue() }

        composeRule.runOnIdle { appendMessage() }
        composeRule.waitUntil(timeoutMillis = 5_000) { scrollValue() == scrollMaxValue() }
    }

    private fun buildSessionDetail(transcript: String): SessionDetail {
        return SessionDetail(
            id = "session-auto-scroll-test",
            title = "自动滚动测试",
            subtitle = "用于验证详情页滚动行为",
            lastUpdated = "刚刚",
            transcriptPreview = transcript,
        )
    }

    private fun buildTranscript(messageCount: Int): String {
        return buildString {
            repeat(messageCount) { index ->
                appendLine("你：第 ${index + 1} 条用户消息")
                appendLine()
                appendLine("Codex：第 ${index + 1} 条助手回复，内容足够长，用来撑开滚动区域。")
                if (index != messageCount - 1) {
                    appendLine()
                    appendLine()
                }
            }
        }
    }
}
