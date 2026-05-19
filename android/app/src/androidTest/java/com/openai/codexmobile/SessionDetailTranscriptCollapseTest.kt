package com.openai.codexmobile

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.openai.codexmobile.model.SessionActivityEntry
import com.openai.codexmobile.model.SessionDetail
import com.openai.codexmobile.ui.TestTags
import com.openai.codexmobile.ui.screen.SessionDetailScreen
import org.junit.Rule
import org.junit.Test

class SessionDetailTranscriptCollapseTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun nonTextTranscriptBubbleIsCollapsedByDefaultAndExpandsOnClick() {
        composeRule.setContent {
            MaterialTheme {
                SessionDetailScreen(
                    paddingValues = PaddingValues(0.dp),
                    sessionDetail = SessionDetail(
                        id = "session-collapse-test",
                        title = "折叠测试",
                        subtitle = "用于验证默认折叠",
                        lastUpdated = "刚刚",
                        transcriptPreview = """
                            你：请展示结果

                            Codex：这里是一段普通文字回复。

                            系统：命令执行
                            命令：npm test

                            系统：文件编辑
                            文件：MainActivity.kt
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

        composeRule.onAllNodesWithText("执行过程").assertCountEquals(1)
        composeRule.onAllNodesWithText("命令：npm test").assertCountEquals(0)

        composeRule.onNodeWithTag(TestTags.SessionDetailExecutionGroupTogglePrefix + "2").performClick()
        composeRule.onAllNodesWithText("命令执行").assertCountEquals(1)
        composeRule.onAllNodesWithText("命令：npm test").assertCountEquals(0)

        composeRule.onNodeWithTag(TestTags.SessionDetailExecutionEntryTogglePrefix + "2_0").performClick()
        composeRule.onAllNodesWithText("命令：npm test").assertCountEquals(1)
    }

    @Test
    fun liveExecutionActivitiesRenderAsSingleExecutionProcess() {
        composeRule.setContent {
            MaterialTheme {
                SessionDetailScreen(
                    paddingValues = PaddingValues(0.dp),
                    sessionDetail = SessionDetail(
                        id = "session-live-activity-test",
                        title = "实时活动测试",
                        subtitle = "用于验证实时执行过程",
                        lastUpdated = "刚刚",
                        transcriptPreview = """
                            你：更新文档

                            Codex：我先检查一下。
                        """.trimIndent(),
                    ),
                    draftSession = null,
                    sessionRealtimeState = SessionRealtimeUiState(
                        liveExecutionActivities = listOf(
                            SessionActivityEntry(
                                stableId = "reasoning-1",
                                itemType = "reasoning",
                                itemId = "reasoning-1",
                                title = "推理摘要",
                                body = "README 大体跟上了。\n还要检查 docs/api.md。",
                                summary = "还要检查 docs/api.md。",
                                transcriptBlock = "系统：推理摘要\nREADME 大体跟上了。\n还要检查 docs/api.md。",
                                updatedAt = "2026-05-19T20:00:00.000Z",
                            ),
                        ),
                    ),
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

        composeRule.onAllNodesWithText("执行过程").assertCountEquals(1)
        composeRule.onAllNodesWithText("README 大体跟上了。\n还要检查 docs/api.md。").assertCountEquals(0)

        composeRule.onNodeWithTag(TestTags.SessionDetailExecutionGroupTogglePrefix + "2").performClick()
        composeRule.onAllNodesWithText("推理摘要").assertCountEquals(2)
        composeRule.onNodeWithTag(TestTags.SessionDetailExecutionEntryTogglePrefix + "2_0").performClick()
        composeRule.onAllNodesWithText("README 大体跟上了。\n还要检查 docs/api.md。").assertCountEquals(1)
    }
}
