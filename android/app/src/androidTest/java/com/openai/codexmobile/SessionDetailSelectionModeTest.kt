package com.openai.codexmobile

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.openai.codexmobile.model.SessionDetail
import com.openai.codexmobile.ui.TestTags
import com.openai.codexmobile.ui.screen.SessionDetailScreen
import org.junit.Rule
import org.junit.Test

class SessionDetailSelectionModeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun selectingTranscriptTextCanBeClearedByTappingInputField() {
        composeRule.setContent {
            MaterialTheme {
                SessionDetailScreen(
                    sessionDetail = SessionDetail(
                        id = "session-selection-test",
                        title = "选择模式测试",
                        subtitle = "验证文本选择进入和退出",
                        lastUpdated = "刚刚",
                        transcriptPreview = """
                            Codex：第一段正文，带一点长度方便长按。

                            第二段正文，确认跨段内容位于同一条消息中。
                        """.trimIndent(),
                    ),
                    draftSession = null,
                    sessionRealtimeState = SessionRealtimeUiState(),
                    queuedInputs = emptyList(),
                    draftMessage = "",
                    pendingImageAttachments = emptyList(),
                    bridgeEndpoint = "",
                    bridgeAuthToken = "",
                    isLoading = false,
                    onDraftMessageChange = {},
                    onPickImage = {},
                    onRemovePendingImageAttachment = {},
                    onRetryPendingImageAttachment = {},
                    onSend = {},
                    onApprovalDecision = {},
                    onUpdateCwd = {},
                    onUpdateModel = {},
                    onUpdateReasoningEffort = {},
                    onUpdateServiceTier = {},
                    onUpdateSandboxMode = {},
                    onRefreshSession = {},
                    onShowMessage = {},
                )
            }
        }

        val bubbleBodyTag = TestTags.SessionDetailTranscriptBubbleBodyPrefix +
            TestTags.SessionDetailTranscriptBubbleTogglePrefix + "0"

        composeRule.onNodeWithTag(bubbleBodyTag).assertIsNotSelected()
        composeRule.onNodeWithTag(bubbleBodyTag).performTouchInput {
            down(center)
            advanceEventTime(1_000)
            up()
        }
        composeRule.onNodeWithText("选择文本").performClick()
        composeRule.onNodeWithTag(bubbleBodyTag).assertIsSelected()

        composeRule.onNodeWithTag(TestTags.SessionDetailDraftField).performClick()
        composeRule.onNodeWithTag(bubbleBodyTag).assertIsNotSelected()
    }
}
