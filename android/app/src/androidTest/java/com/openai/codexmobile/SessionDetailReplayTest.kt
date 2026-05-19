package com.openai.codexmobile

import android.content.Intent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import com.openai.codexmobile.ui.TestTags
import org.junit.Rule
import org.junit.Test

class SessionDetailReplayTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Before
    fun launchHarness() {
        ActivityScenario.launch<MainActivity>(replayHarnessIntent())
    }

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
        composeRule.onNodeWithTag(TestTags.SessionDetailStatusButton).performClick()
        waitForTag(TestTags.SessionDetailStatusDetails)
        composeRule.onNodeWithTag(TestTags.SessionDetailConfigModelButton).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.SessionDetailConfigReasoningButton).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.SessionDetailConfigServiceTierButton).assertIsDisplayed()
        composeRule.onAllNodesWithTag(TestTags.SessionDetailTranscript).assertCountEquals(1)
        composeRule.onAllNodesWithText("kotlin").assertCountEquals(1)
        composeRule.onAllNodesWithText("println(\"hello from test\")").assertCountEquals(1)
        composeRule.onAllNodesWithText("执行过程").assertCountEquals(1)
        composeRule.onAllNodesWithText("历史工具结果").assertCountEquals(0)
        composeRule.onAllNodesWithText("这条记录用于校验工具结果渲染。").assertCountEquals(0)

        waitForTag(TestTags.SessionDetailApprovalCard)
        waitForText("执行测试命令", substring = true)
        composeRule.onNodeWithTag(TestTags.SessionDetailApproveButton).performClick()
        waitUntilTagMissing(TestTags.SessionDetailApprovalCard)
    }

    @Test
    fun settingsScreenCanShowAndOperateDiagnosticsLogs() {
        waitForTag(TestTags.ConnectionScreen)
        composeRule.onNodeWithTag(TestTags.ConnectionOpenSettingsButton).performClick()

        waitForTag(TestTags.SettingsScreen)
        composeRule.onNodeWithTag(TestTags.SettingsScreen)
            .performScrollToNode(hasTestTag(TestTags.SettingsLogsCard))
        composeRule.onNodeWithTag(TestTags.SettingsLogsCard).assertIsDisplayed()
        waitForText("启动 UI 回放模式。", substring = true)

        composeRule.onNodeWithTag(TestTags.SettingsCopyLogsButton).performClick()
        waitForText("日志已复制到剪贴板。")

        composeRule.onNodeWithTag(TestTags.SettingsClearLogsButton).performClick()
        waitForText("已清空应用日志。", substring = true)

        composeRule.onNodeWithTag(TestTags.SettingsRefreshLogsButton).performClick()
        waitForText("已清空应用日志。", substring = true)
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

    private fun waitForText(
        text: String,
        substring: Boolean = false,
    ) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun replayHarnessIntent(): Intent {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return Intent().setClassName(context, "com.openai.codexmobile.ReplayHarnessActivity")
    }
}
