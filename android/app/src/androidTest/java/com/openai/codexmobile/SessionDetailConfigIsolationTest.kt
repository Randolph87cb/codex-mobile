package com.openai.codexmobile

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.openai.codexmobile.ui.TestTags
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SessionDetailConfigIsolationTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Before
    fun launchHarness() {
        ActivityScenario.launch<MainActivity>(replayHarnessIntent())
    }

    @Test
    fun reasoningAndSandboxSelectionsDoNotCrossUpdate() {
        waitForTag(TestTags.ConnectionScreen)
        composeRule.onNodeWithTag(TestTags.ConnectionConnectButton).performClick()

        waitForTag(TestTags.SessionListScreen)
        composeRule.onNodeWithTag(TestTags.SessionListItemPrefix + "session-test-001").performClick()

        waitForTag(TestTags.SessionDetailScreen)
        composeRule.onNodeWithTag(TestTags.SessionDetailStatusButton).performClick()
        waitForTag(TestTags.SessionDetailStatusDetails)

        composeRule.onNodeWithTag(TestTags.SessionDetailConfigReasoningButton).performClick()
        composeRule.onNodeWithText("高").performClick()
        composeRule.onNodeWithTag(TestTags.SessionDetailConfigReasoningButton)
            .assertTextContains("推理 高")
        composeRule.onNodeWithTag(TestTags.SessionDetailConfigSandboxButton)
            .assertTextContains("权限 完全访问")

        composeRule.onNodeWithTag(TestTags.SessionDetailConfigSandboxButton).performClick()
        composeRule.onNodeWithText("完全访问").performClick()
        composeRule.onNodeWithTag(TestTags.SessionDetailConfigSandboxButton)
            .assertTextContains("权限 完全访问")
        composeRule.onNodeWithTag(TestTags.SessionDetailConfigReasoningButton)
            .assertTextContains("推理 高")
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun replayHarnessIntent(): Intent {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return Intent().setClassName(context, "com.openai.codexmobile.ReplayHarnessActivity")
    }
}
