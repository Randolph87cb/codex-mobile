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

                            审批结果：历史工具结果
                            这条记录用于校验工具结果渲染。
                        """.trimIndent(),
                    ),
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
                )
            }
        }

        composeRule.onAllNodesWithText("历史工具结果").assertCountEquals(1)
        composeRule.onAllNodesWithText("这条记录用于校验工具结果渲染。").assertCountEquals(0)
        composeRule.onNodeWithTag(TestTags.SessionDetailTranscriptBubbleTogglePrefix + "2").performClick()
        composeRule.onAllNodesWithText("这条记录用于校验工具结果渲染。").assertCountEquals(1)
    }
}
