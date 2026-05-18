package com.openai.codexmobile

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.openai.codexmobile.ui.TestTags
import org.junit.Rule
import org.junit.Test

class SessionDetailReplayTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ReplayHarnessActivity>()

    @Test
    fun canReplayConnectionSessionListAndStructuredTranscript() {
        waitForTag(TestTags.ConnectionScreen)
        composeRule.onNodeWithTag(TestTags.ConnectionScreen).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.ConnectionConnectButton).performClick()

        waitForTag(TestTags.SessionListScreen)
        composeRule.onNodeWithTag(TestTags.SessionListFolderPrefix + "D:\\workspace\\codex-mobile")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.SessionListItemPrefix + "session-test-001").performClick()

        waitForTag(TestTags.SessionDetailScreen)
        composeRule.onNodeWithTag(TestTags.SessionDetailConfigModelButton).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.SessionDetailConfigReasoningButton).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.SessionDetailConfigServiceTierButton).assertIsDisplayed()
        composeRule.onAllNodesWithTag(TestTags.SessionDetailTranscript).assertCountEquals(1)
        composeRule.onAllNodesWithText("kotlin").assertCountEquals(1)
        composeRule.onAllNodesWithText("println(\"hello from test\")").assertCountEquals(1)
        composeRule.onAllNodesWithText("历史工具结果").assertCountEquals(1)
        composeRule.onAllNodesWithText("这条记录用于校验工具结果渲染。").assertCountEquals(1)

        waitForTag(TestTags.SessionDetailApprovalCard)
        composeRule.onAllNodesWithText("执行测试命令").assertCountEquals(1)
        composeRule.onNodeWithTag(TestTags.SessionDetailApproveButton).performClick()

        composeRule.onAllNodesWithText("批准（item/commandExecution/requestApproval）").assertCountEquals(1)
        waitUntilTagMissing(TestTags.SessionDetailApprovalCard)
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitUntilTagMissing(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
        }
    }
}
