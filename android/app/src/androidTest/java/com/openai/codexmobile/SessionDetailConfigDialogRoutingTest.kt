package com.openai.codexmobile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.openai.codexmobile.model.SessionDetail
import com.openai.codexmobile.ui.TestTags
import com.openai.codexmobile.ui.screen.SessionDetailScreen
import com.openai.codexmobile.ui.theme.CodexMobileTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SessionDetailConfigDialogRoutingTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reasoningChoiceInvokesReasoningCallbackAndSandboxChoiceInvokesSandboxCallback() {
        val modelUpdates = mutableListOf<String>()
        val reasoningUpdates = mutableListOf<String>()
        val sandboxUpdates = mutableListOf<String>()

        composeRule.setContent {
            CodexMobileTheme {
                SessionDetailScreen(
                    paddingValues = androidx.compose.foundation.layout.PaddingValues(),
                    sessionDetail = sampleDetail(),
                    draftSession = null,
                    sessionRealtimeState = SessionRealtimeUiState(),
                    queuedInputs = emptyList(),
                    draftMessage = "",
                    pendingImageAttachments = emptyList(),
                    bridgeEndpoint = "http://10.0.2.2:8787",
                    bridgeAuthToken = "",
                    isLoading = false,
                    onDraftMessageChange = {},
                    onPickImage = {},
                    onRemovePendingImageAttachment = {},
                    onRetryPendingImageAttachment = {},
                    onSend = {},
                    onApprovalDecision = {},
                    onUpdateCwd = {},
                    onUpdateModel = { modelUpdates += it },
                    onUpdateReasoningEffort = { reasoningUpdates += it },
                    onUpdateServiceTier = {},
                    onUpdateSandboxMode = { sandboxUpdates += it },
                    onRefreshSession = {},
                    onShowMessage = {},
                )
            }
        }

        composeRule.onNodeWithTag(TestTags.SessionDetailStatusButton).performClick()
        composeRule.onNodeWithTag(TestTags.SessionDetailConfigReasoningButton).assertIsDisplayed()

        composeRule.onNodeWithTag(TestTags.SessionDetailConfigReasoningButton).performClick()
        composeRule.onNodeWithText("高").performClick()

        assertEquals(listOf("high"), reasoningUpdates)
        assertTrue(modelUpdates.isEmpty())
        assertTrue(sandboxUpdates.isEmpty())

        composeRule.onNodeWithTag(TestTags.SessionDetailConfigSandboxButton).performClick()
        composeRule.onNodeWithText("完全访问").performClick()

        assertEquals(listOf("high"), reasoningUpdates)
        assertEquals(listOf("danger-full-access"), sandboxUpdates)
        assertTrue(modelUpdates.isEmpty())
    }

    @Test
    fun updatedSessionDetailRecomposesExpandedConfigButtons() {
        var detail by mutableStateOf(sampleDetail())

        composeRule.setContent {
            CodexMobileTheme {
                SessionDetailScreen(
                    paddingValues = androidx.compose.foundation.layout.PaddingValues(),
                    sessionDetail = detail,
                    draftSession = null,
                    sessionRealtimeState = SessionRealtimeUiState(),
                    queuedInputs = emptyList(),
                    draftMessage = "",
                    pendingImageAttachments = emptyList(),
                    bridgeEndpoint = "http://10.0.2.2:8787",
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

        composeRule.onNodeWithTag(TestTags.SessionDetailStatusButton).performClick()
        composeRule.onNodeWithTag(TestTags.SessionDetailConfigReasoningButton)
            .assertTextContains("推理 中")
        composeRule.onNodeWithTag(TestTags.SessionDetailConfigSandboxButton)
            .assertTextContains("权限 工作区可写")

        composeRule.runOnUiThread {
            detail = detail.copy(
                reasoningEffort = "high",
                sandboxMode = "danger-full-access",
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TestTags.SessionDetailConfigReasoningButton)
            .assertTextContains("推理 高")
        composeRule.onNodeWithTag(TestTags.SessionDetailConfigSandboxButton)
            .assertTextContains("权限 完全访问")
    }

    @Test
    fun choosingReasoningAndSandboxUpdatesVisibleConfigButtons() {
        var detail by mutableStateOf(sampleDetail())

        composeRule.setContent {
            CodexMobileTheme {
                SessionDetailScreen(
                    paddingValues = androidx.compose.foundation.layout.PaddingValues(),
                    sessionDetail = detail,
                    draftSession = null,
                    sessionRealtimeState = SessionRealtimeUiState(),
                    queuedInputs = emptyList(),
                    draftMessage = "",
                    pendingImageAttachments = emptyList(),
                    bridgeEndpoint = "http://10.0.2.2:8787",
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
                    onUpdateReasoningEffort = { value ->
                        detail = detail.copy(reasoningEffort = value)
                    },
                    onUpdateServiceTier = {},
                    onUpdateSandboxMode = { value ->
                        detail = detail.copy(sandboxMode = value)
                    },
                    onRefreshSession = {},
                    onShowMessage = {},
                )
            }
        }

        composeRule.onNodeWithTag(TestTags.SessionDetailStatusButton).performClick()
        composeRule.onNodeWithTag(TestTags.SessionDetailConfigReasoningButton).performClick()
        composeRule.onNodeWithText("高").performClick()
        composeRule.onNodeWithTag(TestTags.SessionDetailConfigReasoningButton)
            .assertTextContains("推理 高")

        composeRule.onNodeWithTag(TestTags.SessionDetailConfigSandboxButton).performClick()
        composeRule.onNodeWithText("完全访问").performClick()
        composeRule.onNodeWithTag(TestTags.SessionDetailConfigSandboxButton)
            .assertTextContains("权限 完全访问")
        composeRule.onNodeWithTag(TestTags.SessionDetailConfigReasoningButton)
            .assertTextContains("推理 高")
    }

    private fun sampleDetail(): SessionDetail {
        return SessionDetail(
            id = "dialog-test-session",
            title = "测试会话",
            subtitle = "gpt-5.5 • 手动 • 空闲",
            lastUpdated = "2026-05-19T14:00:00Z",
            transcriptPreview = "工作目录：D:\\workspace\\codex-mobile",
            cwd = "D:\\workspace\\codex-mobile",
            model = "gpt-5.5",
            approvalMode = "manual",
            reasoningEffort = "medium",
            serviceTier = "default",
            sandboxMode = "workspace-write",
            status = "idle",
        )
    }
}
