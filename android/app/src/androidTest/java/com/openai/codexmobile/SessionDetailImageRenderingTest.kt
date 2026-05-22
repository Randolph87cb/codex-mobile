package com.openai.codexmobile

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.openai.codexmobile.model.SessionDetail
import com.openai.codexmobile.ui.TestTags
import com.openai.codexmobile.ui.screen.SessionDetailScreen
import org.junit.Rule
import org.junit.Test

class SessionDetailImageRenderingTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pendingImagesShowAsThumbnailsAndCanOpenPreview() {
        composeRule.setContent {
            MaterialTheme {
                SessionDetailScreen(
                    paddingValues = PaddingValues(0.dp),
                    sessionDetail = sampleSessionDetail(),
                    draftSession = null,
                    sessionRealtimeState = SessionRealtimeUiState(),
                    queuedInputs = emptyList(),
                    draftMessage = "",
                    pendingImageAttachments = listOf(
                        PendingImageAttachmentUiState(
                            localId = "pending-1",
                            displayName = "screen-1.gif",
                            mimeType = "image/gif",
                            previewSource = "data:image/gif;base64,$SampleImageBase64",
                            uploadState = PendingImageUploadState.Uploaded,
                            stagedPath = "D:\\bridge\\pending-1.gif",
                        ),
                        PendingImageAttachmentUiState(
                            localId = "pending-2",
                            displayName = "screen-2.gif",
                            mimeType = "image/gif",
                            previewSource = "data:image/gif;base64,$SampleImageBase64",
                            uploadState = PendingImageUploadState.Uploaded,
                            stagedPath = "D:\\bridge\\pending-2.gif",
                        ),
                        PendingImageAttachmentUiState(
                            localId = "pending-3",
                            displayName = "screen-3.gif",
                            mimeType = "image/gif",
                            previewSource = "data:image/gif;base64,$SampleImageBase64",
                            uploadState = PendingImageUploadState.Uploaded,
                            stagedPath = "D:\\bridge\\pending-3.gif",
                        ),
                        PendingImageAttachmentUiState(
                            localId = "pending-4",
                            displayName = "screen-4.gif",
                            mimeType = "image/gif",
                            previewSource = "data:image/gif;base64,$SampleImageBase64",
                            uploadState = PendingImageUploadState.Uploaded,
                            stagedPath = "D:\\bridge\\pending-4.gif",
                        ),
                        PendingImageAttachmentUiState(
                            localId = "pending-5",
                            displayName = "screen-5.gif",
                            mimeType = "image/gif",
                            previewSource = "data:image/gif;base64,$SampleImageBase64",
                            uploadState = PendingImageUploadState.Uploaded,
                            stagedPath = "D:\\bridge\\pending-5.gif",
                        ),
                    ),
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

        composeRule.onNodeWithTag(TestTags.SessionDetailPendingImageRow).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.SessionDetailPendingImageThumbnailPrefix + "pending-1").assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.SessionDetailPendingImageRow).performScrollToNode(
            hasTestTag(TestTags.SessionDetailPendingImageThumbnailPrefix + "pending-5"),
        )
        composeRule.onNodeWithTag(TestTags.SessionDetailPendingImageThumbnailPrefix + "pending-5").assertIsDisplayed()

        composeRule.onNodeWithTag(TestTags.SessionDetailPendingImageThumbnailPrefix + "pending-5").performClick()
        composeRule.onNodeWithTag(TestTags.SessionDetailImagePreviewDialog).assertIsDisplayed()
    }

    @Test
    fun pendingUploadingAndFailedImagesDisableSendAndExposeRetry() {
        composeRule.setContent {
            MaterialTheme {
                SessionDetailScreen(
                    paddingValues = PaddingValues(0.dp),
                    sessionDetail = sampleSessionDetail(),
                    draftSession = null,
                    sessionRealtimeState = SessionRealtimeUiState(),
                    queuedInputs = emptyList(),
                    draftMessage = "继续处理",
                    pendingImageAttachments = listOf(
                        PendingImageAttachmentUiState(
                            localId = "pending-uploading",
                            displayName = "screen-1.gif",
                            mimeType = "image/gif",
                            previewSource = "data:image/gif;base64,$SampleImageBase64",
                            uploadState = PendingImageUploadState.Uploading,
                        ),
                        PendingImageAttachmentUiState(
                            localId = "pending-failed",
                            displayName = "screen-2.gif",
                            mimeType = "image/gif",
                            previewSource = "data:image/gif;base64,$SampleImageBase64",
                            uploadState = PendingImageUploadState.Failed,
                            uploadError = "上传失败",
                        ),
                    ),
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

        composeRule.onNodeWithTag(TestTags.SessionDetailSendButton).assertIsNotEnabled()
        composeRule.onNodeWithTag(
            TestTags.SessionDetailPendingImageRetryButtonPrefix + "pending-failed",
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(
            TestTags.SessionDetailPendingImageErrorPrefix + "pending-failed",
        ).assertIsDisplayed()
    }

    @Test
    fun transcriptImageCanBePreviewedAndSaved() {
        composeRule.setContent {
            MaterialTheme {
                var message by remember { mutableStateOf("") }
                Column {
                    SessionDetailScreen(
                        paddingValues = PaddingValues(0.dp),
                        sessionDetail = sampleSessionDetail(
                            transcript = """
                                Codex：这是原图预览
                                ![sample.gif](data:image/gif;base64,$SampleImageBase64)
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
                        onShowMessage = { value -> message = value },
                    )
                    Text(message)
                }
            }
        }

        val imageTag = TestTags.SessionDetailTranscriptImagePrefix +
            TestTags.SessionDetailTranscriptBubbleTogglePrefix + "0_1"
        composeRule.onNodeWithTag(imageTag).performClick()
        composeRule.onNodeWithTag(TestTags.SessionDetailImagePreviewDialog).assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(TestTags.SessionDetailImagePreviewSaveButton)
                    .assertIsDisplayed()
                    .assertIsEnabled()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(TestTags.SessionDetailImagePreviewSaveButton).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("图片已保存：", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("图片已保存：", substring = true).assertCountEquals(1)
    }

    private fun sampleSessionDetail(transcript: String = "Codex：准备显示图片") = SessionDetail(
        id = "session-image-test",
        title = "图片测试",
        subtitle = "用于验证图片渲染",
        lastUpdated = "刚刚",
        transcriptPreview = transcript,
    )

    private companion object {
        const val SampleImageBase64 =
            "R0lGODdhAQABAIAAAP///////ywAAAAAAQABAAACAkQBADs="
    }
}
