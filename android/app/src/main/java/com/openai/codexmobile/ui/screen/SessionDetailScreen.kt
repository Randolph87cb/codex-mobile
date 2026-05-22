package com.openai.codexmobile.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.openai.codexmobile.DraftSessionUiState
import com.openai.codexmobile.PendingImageAttachmentUiState
import com.openai.codexmobile.PendingImageUploadState
import com.openai.codexmobile.PendingApprovalUiState
import com.openai.codexmobile.SessionRealtimeUiState
import com.openai.codexmobile.data.ApprovalDecision
import com.openai.codexmobile.model.SessionDetail
import com.openai.codexmobile.ui.TestTags
import kotlinx.coroutines.launch

private enum class SessionConfigEditor {
    Cwd,
    Model,
    ReasoningEffort,
    ServiceTier,
    SandboxMode,
}

private data class ImagePreviewState(
    val title: String,
    val source: String,
)

private data class FileDownloadDialogState(
    val request: TranscriptFileDownloadRequest,
    val progress: TranscriptFileDownloadProgress,
    val isRunning: Boolean = true,
    val resultMessage: String? = null,
    val errorMessage: String? = null,
)

private data class IndexedTranscriptImage(
    val index: Int,
    val part: TranscriptPart.Image,
)

private val TranscriptInlineImageWidth = 92.dp
private val TranscriptInlineImageHeight = 118.dp
private val PendingImagePreviewWidth = 92.dp
private val PendingImagePreviewHeight = 80.dp
private val TranscriptBodyTextStyle = TextStyle(
    fontSize = 14.sp,
    lineHeight = 18.sp,
)

@Composable
fun SessionDetailScreen(
    paddingValues: PaddingValues,
    sessionDetail: SessionDetail?,
    draftSession: DraftSessionUiState?,
    sessionRealtimeState: SessionRealtimeUiState,
    queuedInputs: List<String>,
    draftMessage: String,
    pendingImageAttachments: List<PendingImageAttachmentUiState>,
    bridgeEndpoint: String,
    bridgeAuthToken: String,
    isLoading: Boolean,
    onDraftMessageChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onRemovePendingImageAttachment: (String) -> Unit,
    onRetryPendingImageAttachment: (String) -> Unit,
    onSend: () -> Unit,
    onApprovalDecision: (ApprovalDecision) -> Unit,
    onUpdateCwd: (String) -> Unit,
    onUpdateModel: (String) -> Unit,
    onUpdateReasoningEffort: (String) -> Unit,
    onUpdateServiceTier: (String) -> Unit,
    onUpdateSandboxMode: (String) -> Unit,
    onUpdateGoal: (String, Long?) -> Unit = { _, _ -> },
    onPauseGoal: () -> Unit = {},
    onResumeGoal: () -> Unit = {},
    onClearGoal: () -> Unit = {},
    onRefreshSession: () -> Unit,
    onShowMessage: (String) -> Unit,
    transcriptScrollState: ScrollState? = null,
    autoScrollTranscript: Boolean = true,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val detail = remember(sessionDetail, draftSession) {
        sessionDetail ?: draftSession?.toDraftDetail()
    }
    val currentTranscriptScrollState = transcriptScrollState ?: rememberScrollState()
    var statusExpanded by rememberSaveable { mutableStateOf(false) }
    var activeEditor by rememberSaveable { mutableStateOf<SessionConfigEditor?>(null) }
    var goalEditorVisible by rememberSaveable { mutableStateOf(false) }
    var previousTranscriptScrollMax by remember { mutableIntStateOf(0) }
    var imagePreviewState by remember { mutableStateOf<ImagePreviewState?>(null) }
    var pendingFileDownloadRequest by remember { mutableStateOf<TranscriptFileDownloadRequest?>(null) }
    var fileDownloadDialogState by remember { mutableStateOf<FileDownloadDialogState?>(null) }
    val hasPendingUploadBlockers = pendingImageAttachments.any {
        it.uploadState == PendingImageUploadState.Uploading || it.uploadState == PendingImageUploadState.Failed
    }
    val copyToClipboard: (String, String) -> Unit = remember(clipboardManager, onShowMessage) {
        { content, successMessage ->
            clipboardManager.setText(AnnotatedString(content))
            onShowMessage(successMessage)
        }
    }

    LaunchedEffect(
        detail?.transcriptPreview,
        sessionRealtimeState.lastEventText,
        sessionRealtimeState.pendingApproval?.requestId?.toString(),
        autoScrollTranscript,
    ) {
        if (!autoScrollTranscript) {
            previousTranscriptScrollMax = currentTranscriptScrollState.maxValue
            return@LaunchedEffect
        }
        val shouldAutoScroll = previousTranscriptScrollMax == 0 ||
            previousTranscriptScrollMax - currentTranscriptScrollState.value <= 32
        if (shouldAutoScroll) {
            currentTranscriptScrollState.animateScrollTo(currentTranscriptScrollState.maxValue)
        }
        previousTranscriptScrollMax = currentTranscriptScrollState.maxValue
    }

    ConfigEditorDialogs(
        activeEditor = activeEditor,
        detail = detail,
        onDismiss = { activeEditor = null },
        onUpdateCwd = onUpdateCwd,
        onUpdateModel = onUpdateModel,
        onUpdateReasoningEffort = onUpdateReasoningEffort,
        onUpdateServiceTier = onUpdateServiceTier,
        onUpdateSandboxMode = onUpdateSandboxMode,
    )

    if (goalEditorVisible && detail != null && draftSession == null) {
        GoalEditorDialog(
            currentGoal = detail.goal,
            onDismiss = { goalEditorVisible = false },
            onConfirm = { objective, tokenBudget ->
                onUpdateGoal(objective, tokenBudget)
                goalEditorVisible = false
            },
        )
    }

    imagePreviewState?.let { preview ->
        ImagePreviewDialog(
            preview = preview,
            bridgeEndpoint = bridgeEndpoint,
            bridgeAuthToken = bridgeAuthToken,
            onDismiss = { imagePreviewState = null },
            onShowMessage = onShowMessage,
        )
    }
    pendingFileDownloadRequest?.let { request ->
        FileDownloadConfirmDialog(
            request = request,
            onDismiss = { pendingFileDownloadRequest = null },
            onConfirm = {
                pendingFileDownloadRequest = null
                fileDownloadDialogState = FileDownloadDialogState(
                    request = request,
                    progress = TranscriptFileDownloadProgress(
                        displayName = request.displayName,
                        stage = TranscriptFileDownloadStage.Preparing,
                    ),
                )
                coroutineScope.launch {
                    val result = runCatching {
                        saveTranscriptFile(
                            context = context,
                            request = request,
                            bridgeAuthToken = bridgeAuthToken,
                            onProgress = { progress ->
                                fileDownloadDialogState = fileDownloadDialogState?.copy(progress = progress)
                            },
                        )
                    }
                    result.onSuccess { message ->
                        onShowMessage(message)
                        fileDownloadDialogState = fileDownloadDialogState?.copy(
                            progress = fileDownloadDialogState?.progress?.copy(
                                stage = TranscriptFileDownloadStage.Saving,
                            ) ?: TranscriptFileDownloadProgress(
                                displayName = request.displayName,
                                stage = TranscriptFileDownloadStage.Saving,
                            ),
                            isRunning = false,
                            resultMessage = message,
                            errorMessage = null,
                        )
                    }.onFailure { error ->
                        fileDownloadDialogState = fileDownloadDialogState?.copy(
                            isRunning = false,
                            errorMessage = error.message ?: "下载失败。",
                            resultMessage = null,
                        )
                    }
                }
            },
        )
    }
    fileDownloadDialogState?.let { state ->
        FileDownloadProgressDialog(
            state = state,
            onDismiss = { fileDownloadDialogState = null },
        )
    }

    Column(
        modifier = Modifier
            .testTag(TestTags.SessionDetailScreen)
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusStrip(
            detail = detail,
            sessionRealtimeState = sessionRealtimeState,
            queuedInputs = queuedInputs,
            isDraft = draftSession != null,
            expanded = statusExpanded,
            onToggleExpanded = { statusExpanded = !statusExpanded },
            onOpenEditor = { activeEditor = it },
            onRefreshSession = onRefreshSession,
        )
        GoalCard(
            detail = detail,
            isDraft = draftSession != null,
            isLoading = isLoading,
            onEditGoal = { goalEditorVisible = true },
            onPauseGoal = onPauseGoal,
            onResumeGoal = onResumeGoal,
            onClearGoal = onClearGoal,
        )
        sessionRealtimeState.pendingApproval?.let { approval ->
            ApprovalActionCard(
                approval = approval,
                onApprovalDecision = onApprovalDecision,
            )
        }
        if (queuedInputs.isNotEmpty()) {
            QueuedInputCard(messages = queuedInputs)
        }
        Box(
            modifier = Modifier
                .testTag(TestTags.SessionDetailTranscript)
                .fillMaxWidth()
                .weight(1f, fill = false),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 2.dp, vertical = 2.dp)
                    .testTag(TestTags.SessionDetailTranscriptScroll)
                    .verticalScroll(currentTranscriptScrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TranscriptBubbleList(
                    transcript = detail?.transcriptPreview.orEmpty(),
                    liveActivities = sessionRealtimeState.liveExecutionActivities,
                    bridgeEndpoint = bridgeEndpoint,
                    bridgeAuthToken = bridgeAuthToken,
                    onShowMessage = onShowMessage,
                    onFileDownloadRequest = { request ->
                        pendingFileDownloadRequest = request
                    },
                    onCopyText = { copyToClipboard(it, "内容已复制到剪贴板。") },
                    onCopyCode = { copyToClipboard(it, "代码已复制到剪贴板。") },
                    onOpenImagePreview = { title, source ->
                        imagePreviewState = ImagePreviewState(
                            title = title,
                            source = source,
                        )
                    },
                )
            }
        }
        if (pendingImageAttachments.isNotEmpty()) {
            PendingImageAttachmentTray(
                attachments = pendingImageAttachments,
                bridgeEndpoint = bridgeEndpoint,
                bridgeAuthToken = bridgeAuthToken,
                onOpenImagePreview = { attachment ->
                    imagePreviewState = ImagePreviewState(
                        title = attachment.displayName,
                        source = attachment.previewSource,
                    )
                },
                onRemoveAttachment = onRemovePendingImageAttachment,
                onRetryAttachment = onRetryPendingImageAttachment,
            )
        }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 7.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilledTonalIconButton(
                    onClick = onPickImage,
                    enabled = !isLoading && detail != null,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag(TestTags.SessionDetailAttachImageButton),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = "添加图片",
                        modifier = Modifier.size(18.dp),
                    )
                }
                OutlinedTextField(
                    value = draftMessage,
                    onValueChange = onDraftMessageChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .testTag(TestTags.SessionDetailDraftField),
                    placeholder = {
                        Text(
                            if (draftSession != null) {
                                "首条消息发送后才真正创建线程"
                            } else {
                                "发送给 Codex"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    maxLines = 3,
                )
                Button(
                    onClick = onSend,
                    enabled = !isLoading &&
                        detail != null &&
                        (draftMessage.isNotBlank() || pendingImageAttachments.isNotEmpty()) &&
                        !hasPendingUploadBlockers,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                        .testTag(TestTags.SessionDetailSendButton),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (draftSession != null) "开始" else "发送",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PendingImageAttachmentTray(
    attachments: List<PendingImageAttachmentUiState>,
    bridgeEndpoint: String,
    bridgeAuthToken: String,
    onOpenImagePreview: (PendingImageAttachmentUiState) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onRetryAttachment: (String) -> Unit,
) {
    val uploadingCount = attachments.count { it.uploadState == PendingImageUploadState.Uploading }
    val failedCount = attachments.count { it.uploadState == PendingImageUploadState.Failed }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.SessionDetailPendingImageCard),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag(TestTags.SessionDetailPendingImageTray),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = null,
                        modifier = Modifier.padding(7.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(text = "已附加图片（${attachments.size}）", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = when {
                            failedCount > 0 -> "$failedCount 张待处理，点缩略图看原图。"
                            uploadingCount > 0 -> "$uploadingCount 张上传中，发送前会自动就绪。"
                            else -> "固定预览窗，点缩略图看原图。"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LazyRow(
                modifier = Modifier.testTag(TestTags.SessionDetailPendingImageRow),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                items(items = attachments, key = { it.localId }) { attachment ->
                    PendingImageThumbnailCard(
                        attachment = attachment,
                        bridgeEndpoint = bridgeEndpoint,
                        bridgeAuthToken = bridgeAuthToken,
                        onOpen = { onOpenImagePreview(attachment) },
                        onRetry = { onRetryAttachment(attachment.localId) },
                        onRemove = { onRemoveAttachment(attachment.localId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingImageThumbnailCard(
    attachment: PendingImageAttachmentUiState,
    bridgeEndpoint: String,
    bridgeAuthToken: String,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier.width(PendingImagePreviewWidth),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FixedPreviewImageCard(
                source = attachment.previewSource,
                title = attachment.displayName,
                bridgeEndpoint = bridgeEndpoint,
                bridgeAuthToken = bridgeAuthToken,
                previewHeight = PendingImagePreviewHeight,
                modifier = Modifier
                    .testTag(TestTags.SessionDetailPendingImageThumbnailPrefix + attachment.localId),
                onOpen = onOpen,
                showTitle = false,
            )
            Text(
                text = attachment.displayName,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(TestTags.SessionDetailPendingImageStatusPrefix + attachment.localId),
                ) {
                    when (attachment.uploadState) {
                        PendingImageUploadState.Uploading -> {
                            CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 2.dp)
                            Text("上传中", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }

                        PendingImageUploadState.Uploaded -> {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                            )
                            Text("已就绪", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }

                        PendingImageUploadState.Failed -> {
                            Icon(
                                imageVector = Icons.Filled.Error,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                "失败",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                modifier = Modifier.testTag(
                                    TestTags.SessionDetailPendingImageErrorPrefix + attachment.localId,
                                ),
                            )
                        }
                    }
                }
                Text(
                    text = if (attachment.uploadState == PendingImageUploadState.Failed) "重试" else "移除",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(
                            onClick = if (attachment.uploadState == PendingImageUploadState.Failed) {
                                onRetry
                            } else {
                                onRemove
                            },
                        )
                        .testTag(
                            if (attachment.uploadState == PendingImageUploadState.Failed) {
                                TestTags.SessionDetailPendingImageRetryButtonPrefix + attachment.localId
                            } else {
                                TestTags.SessionDetailClearImageButton + "_" + attachment.localId
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun ConversationHeader(
    detail: SessionDetail?,
    sessionRealtimeState: SessionRealtimeUiState,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = detail?.title ?: "等待会话",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = detail?.subtitle ?: "请先从会话列表中选择一个会话。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GoalMetricChip(
                text = if (sessionRealtimeState.isConnected) "在线" else "快照",
                containerColor = if (sessionRealtimeState.isConnected) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (sessionRealtimeState.isConnected) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = detail?.lastUpdated ?: "等待会话元数据",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusStrip(
    detail: SessionDetail?,
    sessionRealtimeState: SessionRealtimeUiState,
    queuedInputs: List<String>,
    isDraft: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onOpenEditor: (SessionConfigEditor) -> Unit,
    onRefreshSession: () -> Unit,
) {
    val statusIcon = when {
        isDraft -> Icons.Filled.Work
        detail?.status == "running" -> Icons.Filled.Bolt
        detail?.status == "awaiting_approval" -> Icons.Filled.HourglassTop
        detail?.status == "error" -> Icons.Filled.Error
        else -> Icons.Filled.CheckCircle
    }
    val connectionIcon = if (sessionRealtimeState.isConnected) {
        Icons.Filled.CloudDone
    } else {
        Icons.Filled.CloudOff
    }
    val queueIcon = if (queuedInputs.isEmpty()) Icons.Filled.CheckCircle else Icons.Filled.Schedule

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.SessionDetailStatusStrip),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.SessionDetailStatusButton)
                    .clickable { onToggleExpanded() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                SessionStatusMetric(
                    label = "会话",
                    value = localizedStatusLabel(detail?.status ?: "idle"),
                    icon = statusIcon,
                    modifier = Modifier.weight(1f),
                )
                StatusMetricDivider()
                SessionStatusMetric(
                    label = "连接",
                    value = if (isDraft) "草稿" else if (sessionRealtimeState.isConnected) "已连接" else "快照",
                    icon = connectionIcon,
                    modifier = Modifier.weight(1f),
                )
                StatusMetricDivider()
                SessionStatusMetric(
                    label = "排队",
                    value = if (queuedInputs.isEmpty()) "无排队" else "${queuedInputs.size} 条",
                    icon = queueIcon,
                    modifier = Modifier.weight(1f),
                )
                StatusMetricDivider()
                SessionStatusMetric(
                    label = "审批",
                    value = if (sessionRealtimeState.pendingApproval != null) "待处理" else "正常",
                    icon = if (sessionRealtimeState.pendingApproval != null) {
                        Icons.Filled.HourglassTop
                    } else {
                        Icons.Filled.CheckCircle
                    },
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onToggleExpanded,
                    modifier = Modifier
                        .padding(start = 0.dp)
                        .size(22.dp),
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "收起状态详情" else "展开状态详情",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.SessionDetailStatusDetails),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
                    Text(text = "连接：${sessionRealtimeState.connectionText}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "状态：${sessionRealtimeState.statusText}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = sessionRealtimeState.lastEventText ?: "等待实时事件。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    sessionRealtimeState.fallbackNotice?.let { notice ->
                        Text(
                            text = notice,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (!isDraft) {
                        FilledTonalIconButton(
                            onClick = onRefreshSession,
                            modifier = Modifier.testTag(TestTags.SessionDetailStatusRefreshButton),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "立即同步",
                            )
                        }
                    }
                    SessionConfigRow(
                        detail = detail,
                        onOpenEditor = onOpenEditor,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalCard(
    detail: SessionDetail?,
    isDraft: Boolean,
    isLoading: Boolean,
    onEditGoal: () -> Unit,
    onPauseGoal: () -> Unit,
    onResumeGoal: () -> Unit,
    onClearGoal: () -> Unit,
) {
    if (detail == null) {
        return
    }
    var expanded by rememberSaveable(detail.id, detail.goal?.objective, isDraft) { mutableStateOf(false) }
    val primaryStatusText = when {
        isDraft -> "待开始"
        detail.goalCapability == "unsupported" -> "不支持"
        detail.goal == null -> "未设置"
        else -> localizedGoalStatus(detail.goal.status)
    }
    val objectiveText = when {
        isDraft -> "先开始一次真实会话，再把长期目标挂到这个线程上。"
        detail.goalCapability == "unsupported" -> "当前 host 暂不支持目标模式。"
        detail.goal == null -> "给当前线程设一个明确目标后，手机端就能持续看到目标状态和预算变化。"
        else -> detail.goal.objective
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.SessionDetailGoalCard),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TranscriptLabelChip(
                    text = "目标",
                    icon = Icons.Filled.Flag,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    compact = true,
                )
                Text(
                    text = primaryStatusText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起目标详情" else "展开目标详情",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(18.dp),
                )
            }
            Text(
                text = objectiveText,
                modifier = Modifier.padding(end = 36.dp),
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (expanded) 3 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (expanded) {
                detail.goal?.let { goal ->
                    FlowRow(
                        modifier = Modifier.padding(end = 36.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        GoalMetricChip(
                            text = primaryStatusText,
                            containerColor = when (primaryStatusText) {
                                "进行中" -> MaterialTheme.colorScheme.primaryContainer
                                "已完成" -> MaterialTheme.colorScheme.tertiaryContainer
                                "待开始", "未设置" -> MaterialTheme.colorScheme.surfaceVariant
                                else -> MaterialTheme.colorScheme.secondaryContainer
                            },
                            contentColor = when (primaryStatusText) {
                                "进行中" -> MaterialTheme.colorScheme.onPrimaryContainer
                                "已完成" -> MaterialTheme.colorScheme.onTertiaryContainer
                                "待开始", "未设置" -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                            },
                        )
                        GoalMetricChip(text = formatTokenUsage(goal.tokensUsed))
                        goal.tokenBudget?.let { budget ->
                            GoalMetricChip(text = "预算 $budget")
                        }
                        GoalMetricChip(text = formatGoalDuration(goal.timeUsedSeconds))
                    }
                }
            }

            if (expanded && !isDraft && detail.goalCapability != "unsupported") {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onEditGoal,
                        enabled = !isLoading,
                        modifier = Modifier.testTag(
                            if (detail.goal == null) {
                                TestTags.SessionDetailGoalStartButton
                            } else {
                                TestTags.SessionDetailGoalEditButton
                            },
                        ),
                    ) {
                        Icon(imageVector = Icons.Filled.Flag, contentDescription = null)
                        Text(
                            text = if (detail.goal == null) "开始目标" else "编辑目标",
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    detail.goal?.let { goal ->
                        val paused = goal.status == "paused"
                        OutlinedButton(
                            onClick = if (paused) onResumeGoal else onPauseGoal,
                            enabled = !isLoading,
                            modifier = Modifier.testTag(
                                if (paused) {
                                    TestTags.SessionDetailGoalResumeButton
                                } else {
                                    TestTags.SessionDetailGoalPauseButton
                                },
                            ),
                        ) {
                            Icon(
                                imageVector = if (paused) Icons.Filled.Bolt else Icons.Filled.StopCircle,
                                contentDescription = null,
                            )
                            Text(
                                text = if (paused) "恢复" else "暂停",
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        OutlinedButton(
                            onClick = onClearGoal,
                            enabled = !isLoading,
                            modifier = Modifier.testTag(TestTags.SessionDetailGoalClearButton),
                        ) {
                            Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
                            Text(text = "清除", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalMetricChip(
    text: String,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
) {
    val resolvedContainerColor = if (containerColor == Color.Unspecified) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        containerColor
    }
    val resolvedContentColor = if (contentColor == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        contentColor
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = resolvedContainerColor,
        contentColor = resolvedContentColor,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun SessionStatusMetric(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(11.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusMetricDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(28.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.09f)),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionConfigRow(
    detail: SessionDetail?,
    onOpenEditor: (SessionConfigEditor) -> Unit,
) {
    if (detail == null) {
        return
    }

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.SessionDetailConfigRow),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = { onOpenEditor(SessionConfigEditor.Cwd) },
            modifier = Modifier.testTag(TestTags.SessionDetailConfigCwdButton),
        ) {
            Icon(imageVector = Icons.Filled.Work, contentDescription = null)
            Text(
                text = "目录",
                modifier = Modifier.padding(start = 8.dp),
                maxLines = 1,
            )
        }
        OutlinedButton(
            onClick = { onOpenEditor(SessionConfigEditor.Model) },
            modifier = Modifier.testTag(TestTags.SessionDetailConfigModelButton),
        ) {
            Icon(imageVector = Icons.Filled.Tune, contentDescription = null)
            Text(
                text = detail.model,
                modifier = Modifier.padding(start = 8.dp),
                maxLines = 1,
            )
        }
        OutlinedButton(
            onClick = { onOpenEditor(SessionConfigEditor.ReasoningEffort) },
            modifier = Modifier.testTag(TestTags.SessionDetailConfigReasoningButton),
        ) {
            Icon(imageVector = Icons.Filled.Bolt, contentDescription = null)
            Text(
                text = "推理 ${localizedReasoning(detail.reasoningEffort)}",
                modifier = Modifier.padding(start = 8.dp),
                maxLines = 1,
            )
        }
        OutlinedButton(
            onClick = { onOpenEditor(SessionConfigEditor.ServiceTier) },
            modifier = Modifier.testTag(TestTags.SessionDetailConfigServiceTierButton),
        ) {
            Icon(imageVector = Icons.Filled.Speed, contentDescription = null)
            Text(
                text = "速度 ${localizedService(detail.serviceTier)}",
                modifier = Modifier.padding(start = 8.dp),
                maxLines = 1,
            )
        }
        OutlinedButton(
            onClick = { onOpenEditor(SessionConfigEditor.SandboxMode) },
            modifier = Modifier.testTag(TestTags.SessionDetailConfigSandboxButton),
        ) {
            Text(
                text = "权限 ${localizedSandbox(detail.sandboxMode)}",
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ConfigEditorDialogs(
    activeEditor: SessionConfigEditor?,
    detail: SessionDetail?,
    onDismiss: () -> Unit,
    onUpdateCwd: (String) -> Unit,
    onUpdateModel: (String) -> Unit,
    onUpdateReasoningEffort: (String) -> Unit,
    onUpdateServiceTier: (String) -> Unit,
    onUpdateSandboxMode: (String) -> Unit,
) {
    val currentDetail = detail ?: return

    when (activeEditor) {
        SessionConfigEditor.Cwd -> TextInputConfigDialog(
            title = "更新目录",
            label = "工作目录",
            initialValue = currentDetail.cwd,
            onDismiss = onDismiss,
            onConfirm = {
                onUpdateCwd(it)
                onDismiss()
            },
        )

        SessionConfigEditor.Model -> TextInputConfigDialog(
            title = "更新模型",
            label = "模型",
            initialValue = currentDetail.model,
            onDismiss = onDismiss,
            onConfirm = {
                onUpdateModel(it)
                onDismiss()
            },
        )

        SessionConfigEditor.ReasoningEffort -> ChoiceConfigDialog(
            title = "选择推理强度",
            options = listOf(
                "minimal" to "极低",
                "low" to "低",
                "medium" to "中",
                "high" to "高",
                "xhigh" to "最高",
            ),
            onDismiss = onDismiss,
            onChoose = {
                onUpdateReasoningEffort(it)
                onDismiss()
            },
        )

        SessionConfigEditor.ServiceTier -> ChoiceConfigDialog(
            title = "选择速度",
            options = listOf(
                "default" to "普通",
                "fast" to "快速",
            ),
            onDismiss = onDismiss,
            onChoose = {
                onUpdateServiceTier(it)
                onDismiss()
            },
        )

        SessionConfigEditor.SandboxMode -> ChoiceConfigDialog(
            title = "选择文件权限",
            options = listOf(
                "read-only" to "只读",
                "workspace-write" to "工作区可写",
                "danger-full-access" to "完全访问",
            ),
            onDismiss = onDismiss,
            onChoose = {
                onUpdateSandboxMode(it)
                onDismiss()
            },
        )
        null -> Unit
    }
}

@Composable
private fun TextInputConfigDialog(
    title: String,
    label: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.trim()) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun ChoiceConfigDialog(
    title: String,
    options: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onChoose: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (value, label) ->
                    OutlinedButton(
                        onClick = { onChoose(value) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun GoalEditorDialog(
    currentGoal: com.openai.codexmobile.model.SessionGoalSnapshot?,
    onDismiss: () -> Unit,
    onConfirm: (String, Long?) -> Unit,
) {
    var objective by remember(currentGoal?.objective) { mutableStateOf(currentGoal?.objective.orEmpty()) }
    var tokenBudgetInput by remember(currentGoal?.tokenBudget) {
        mutableStateOf(currentGoal?.tokenBudget?.toString().orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (currentGoal == null) "开始目标" else "编辑目标") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = objective,
                    onValueChange = { objective = it },
                    label = { Text("目标内容") },
                    minLines = 3,
                    maxLines = 5,
                )
                OutlinedTextField(
                    value = tokenBudgetInput,
                    onValueChange = { value ->
                        tokenBudgetInput = value.filter { it.isDigit() }
                    },
                    label = { Text("Token 预算（可选）") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(objective.trim(), tokenBudgetInput.toLongOrNull())
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun QueuedInputCard(
    messages: List<String>,
) {
    val queueScrollState = rememberScrollState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.SessionDetailQueuedInputsCard),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TranscriptLabelChip(
                    text = "排队",
                    icon = Icons.Filled.Schedule,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "排队中的消息（${messages.size}）",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = "当前轮结束后会按顺序自动发送。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp)
                    .verticalScroll(queueScrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                messages.forEachIndexed { index, message ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Text(
                            text = "${index + 1}. $message",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TranscriptBubbleList(
    transcript: String,
    liveActivities: List<com.openai.codexmobile.model.SessionActivityEntry>,
    bridgeEndpoint: String,
    bridgeAuthToken: String,
    onShowMessage: (String) -> Unit,
    onFileDownloadRequest: (TranscriptFileDownloadRequest) -> Unit,
    onCopyText: (String) -> Unit,
    onCopyCode: (String) -> Unit,
    onOpenImagePreview: (String, String) -> Unit,
) {
    val items = buildTranscriptDisplayItems(
        transcript = transcript,
        liveActivities = liveActivities,
    )
    if (items.isEmpty()) {
        Text(
            text = "这里会显示会话内容、实时回复和结束状态。",
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEachIndexed { index, item ->
            when (item) {
                is TranscriptDisplayItem.BubbleItem -> TranscriptBubbleCard(
                    bubble = item.bubble,
                    toggleTag = TestTags.SessionDetailTranscriptBubbleTogglePrefix + index,
                    bridgeEndpoint = bridgeEndpoint,
                    bridgeAuthToken = bridgeAuthToken,
                    onShowMessage = onShowMessage,
                    onFileDownloadRequest = onFileDownloadRequest,
                    onCopyText = onCopyText,
                    onCopyCode = onCopyCode,
                    onOpenImagePreview = onOpenImagePreview,
                )

                is TranscriptDisplayItem.ExecutionGroup -> ExecutionProcessCard(
                    index = index,
                    group = item,
                    bridgeEndpoint = bridgeEndpoint,
                    bridgeAuthToken = bridgeAuthToken,
                    onShowMessage = onShowMessage,
                    onFileDownloadRequest = onFileDownloadRequest,
                    onCopyText = onCopyText,
                    onCopyCode = onCopyCode,
                    onOpenImagePreview = onOpenImagePreview,
                )
            }
        }
    }
}

@Composable
private fun TranscriptBubbleCard(
    bubble: TranscriptBubble,
    toggleTag: String,
    bridgeEndpoint: String,
    bridgeAuthToken: String,
    onShowMessage: (String) -> Unit,
    onFileDownloadRequest: (TranscriptFileDownloadRequest) -> Unit,
    onCopyText: (String) -> Unit,
    onCopyCode: (String) -> Unit,
    onOpenImagePreview: (String, String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        val isUser = bubble.speaker == TranscriptSpeaker.User
        val isCollapsible = !bubble.prefersExpandedByDefault
        var expanded by rememberSaveable(toggleTag, bubble.summaryLine, bubble.prefersExpandedByDefault) {
            mutableStateOf(bubble.prefersExpandedByDefault)
        }
        val backgroundColor = when (bubble.speaker) {
            TranscriptSpeaker.User -> MaterialTheme.colorScheme.primaryContainer
            TranscriptSpeaker.Assistant -> MaterialTheme.colorScheme.surfaceVariant
            TranscriptSpeaker.System -> when (bubble.kind) {
                TranscriptBubbleKind.ToolRequest -> MaterialTheme.colorScheme.tertiaryContainer
                TranscriptBubbleKind.ToolResult -> MaterialTheme.colorScheme.secondaryContainer
                TranscriptBubbleKind.Status,
                TranscriptBubbleKind.Message,
                -> MaterialTheme.colorScheme.surfaceVariant
            }
        }
        val contentColor = when (bubble.speaker) {
            TranscriptSpeaker.User -> MaterialTheme.colorScheme.onPrimaryContainer
            TranscriptSpeaker.Assistant -> MaterialTheme.colorScheme.onSurfaceVariant
            TranscriptSpeaker.System -> when (bubble.kind) {
                TranscriptBubbleKind.ToolRequest -> MaterialTheme.colorScheme.onTertiaryContainer
                TranscriptBubbleKind.ToolResult -> MaterialTheme.colorScheme.onSecondaryContainer
                TranscriptBubbleKind.Status,
                TranscriptBubbleKind.Message,
                -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        }
        Card(
            modifier = Modifier
                .align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart)
                .fillMaxWidth(if (isUser) 0.82f else 0.88f),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 8.dp,
                bottomEnd = if (isUser) 8.dp else 18.dp,
            ),
            colors = CardDefaults.cardColors(
                containerColor = backgroundColor,
                contentColor = contentColor,
            ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (isCollapsible) {
                    TranscriptToggleHeader(
                        bubble = bubble,
                        label = bubble.label,
                        title = bubble.summaryLine,
                        expanded = expanded,
                        toggleTag = toggleTag,
                        copyTag = TestTags.SessionDetailTranscriptBubbleCopyPrefix + toggleTag,
                        onCopy = { onCopyText(bubble.copyText) },
                        onToggle = { expanded = !expanded },
                    )
                } else {
                    TranscriptStaticHeader(
                        bubble = bubble,
                        label = bubble.label,
                        title = bubble.title,
                        copyTag = TestTags.SessionDetailTranscriptBubbleCopyPrefix + toggleTag,
                        onCopy = { onCopyText(bubble.copyText) },
                    )
                }

                if (!isCollapsible || expanded) {
                    TranscriptPartsColumn(
                        parts = bubble.parts,
                        bridgeEndpoint = bridgeEndpoint,
                        bridgeAuthToken = bridgeAuthToken,
                        onShowMessage = onShowMessage,
                        onFileDownloadRequest = onFileDownloadRequest,
                        testTagPrefix = toggleTag,
                        onCopyCode = onCopyCode,
                        onOpenImagePreview = onOpenImagePreview,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExecutionProcessCard(
    index: Int,
    group: TranscriptDisplayItem.ExecutionGroup,
    bridgeEndpoint: String,
    bridgeAuthToken: String,
    onShowMessage: (String) -> Unit,
    onFileDownloadRequest: (TranscriptFileDownloadRequest) -> Unit,
    onCopyText: (String) -> Unit,
    onCopyCode: (String) -> Unit,
    onOpenImagePreview: (String, String) -> Unit,
) {
    var expanded by rememberSaveable(index, group.summaryLine) { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.88f),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                TranscriptToggleHeader(
                    bubble = null,
                    label = "执行过程",
                    title = group.summaryLine,
                    expanded = expanded,
                    toggleTag = TestTags.SessionDetailExecutionGroupTogglePrefix + index,
                    copyTag = TestTags.SessionDetailTranscriptBubbleCopyPrefix + "execution_group_" + index,
                    onCopy = {
                        onCopyText(group.activities.joinToString("\n\n") { it.copyText })
                    },
                    onToggle = { expanded = !expanded },
                )

                if (expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.activities.forEachIndexed { activityIndex, bubble ->
                            ExecutionActivityCard(
                                toggleTag = TestTags.SessionDetailExecutionEntryTogglePrefix + "${index}_${activityIndex}",
                                bubble = bubble,
                                bridgeEndpoint = bridgeEndpoint,
                                bridgeAuthToken = bridgeAuthToken,
                                onShowMessage = onShowMessage,
                                onFileDownloadRequest = onFileDownloadRequest,
                                onCopyText = onCopyText,
                                onCopyCode = onCopyCode,
                                onOpenImagePreview = onOpenImagePreview,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExecutionActivityCard(
    toggleTag: String,
    bubble: TranscriptBubble,
    bridgeEndpoint: String,
    bridgeAuthToken: String,
    onShowMessage: (String) -> Unit,
    onFileDownloadRequest: (TranscriptFileDownloadRequest) -> Unit,
    onCopyText: (String) -> Unit,
    onCopyCode: (String) -> Unit,
    onOpenImagePreview: (String, String) -> Unit,
) {
    var expanded by rememberSaveable(toggleTag, bubble.summaryLine) { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TranscriptToggleHeader(
                bubble = bubble,
                label = bubble.label,
                title = bubble.summaryLine,
                expanded = expanded,
                toggleTag = toggleTag,
                copyTag = TestTags.SessionDetailTranscriptBubbleCopyPrefix + toggleTag,
                onCopy = { onCopyText(bubble.copyText) },
                onToggle = { expanded = !expanded },
            )

            if (expanded) {
                TranscriptPartsColumn(
                    parts = bubble.parts,
                    bridgeEndpoint = bridgeEndpoint,
                    bridgeAuthToken = bridgeAuthToken,
                    onShowMessage = onShowMessage,
                    onFileDownloadRequest = onFileDownloadRequest,
                    testTagPrefix = toggleTag,
                    onCopyCode = onCopyCode,
                    onOpenImagePreview = onOpenImagePreview,
                )
            }
        }
    }
}

@Composable
private fun TranscriptToggleHeader(
    bubble: TranscriptBubble?,
    label: String,
    title: String,
    expanded: Boolean,
    toggleTag: String,
    copyTag: String,
    onCopy: () -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .testTag(toggleTag)
                .clickable(onClick = onToggle),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TranscriptLabelChip(
                text = label,
                icon = bubble?.headerIcon(),
                containerColor = bubble?.headerContainerColor()
                    ?: MaterialTheme.colorScheme.primaryContainer,
                contentColor = bubble?.headerContentColor()
                    ?: MaterialTheme.colorScheme.onPrimaryContainer,
                compact = true,
                plain = true,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onCopy,
            modifier = Modifier
                .size(24.dp)
                .testTag(copyTag),
        ) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = "复制消息",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
            )
        }
        Icon(
            imageVector = if (expanded) {
                Icons.Default.KeyboardArrowUp
            } else {
                Icons.Default.KeyboardArrowDown
            },
            contentDescription = if (expanded) "收起消息" else "展开消息",
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun TranscriptStaticHeader(
    bubble: TranscriptBubble,
    label: String,
    title: String?,
    copyTag: String,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TranscriptLabelChip(
                text = label,
                icon = bubble.headerIcon(),
                containerColor = bubble.headerContainerColor(),
                contentColor = bubble.headerContentColor(),
                compact = true,
                plain = true,
            )
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(
            onClick = onCopy,
            modifier = Modifier
                .size(24.dp)
                .testTag(copyTag),
        ) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = "复制消息",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
            )
        }
    }
}

@Composable
private fun TranscriptPartsColumn(
    parts: List<TranscriptPart>,
    bridgeEndpoint: String,
    bridgeAuthToken: String,
    onShowMessage: (String) -> Unit,
    onFileDownloadRequest: (TranscriptFileDownloadRequest) -> Unit,
    testTagPrefix: String,
    onCopyCode: (String) -> Unit,
    onOpenImagePreview: (String, String) -> Unit,
) {
    val bodyTextStyle = MaterialTheme.typography.bodySmall.merge(TranscriptBodyTextStyle)
    var index = 0
    while (index < parts.size) {
        val part = parts[index]
        when (part) {
            is TranscriptPart.Text -> {
                MarkdownTextBlock(
                    text = part.text,
                    style = bodyTextStyle,
                    bridgeEndpoint = bridgeEndpoint,
                    onShowMessage = onShowMessage,
                    onFileDownloadRequest = onFileDownloadRequest,
                )
                index += 1
            }

            is TranscriptPart.Image -> {
                val images = buildList {
                    var nextIndex = index
                    while (nextIndex < parts.size) {
                        val nextPart = parts[nextIndex]
                        if (nextPart !is TranscriptPart.Image) {
                            break
                        }
                        add(IndexedTranscriptImage(index = nextIndex, part = nextPart))
                        nextIndex += 1
                    }
                }
                TranscriptImageGallery(
                    images = images,
                    bridgeEndpoint = bridgeEndpoint,
                    bridgeAuthToken = bridgeAuthToken,
                    testTagPrefix = testTagPrefix,
                    onOpenImagePreview = onOpenImagePreview,
                )
                index += images.size
            }

            is TranscriptPart.CodeBlock -> {
                CodeBlockCard(
                    part = part,
                    copyTag = TestTags.SessionDetailCodeBlockCopyPrefix + "${testTagPrefix}_$index",
                    onCopyCode = onCopyCode,
                )
                index += 1
            }
        }
    }
}

@Composable
private fun CodeBlockCard(
    part: TranscriptPart.CodeBlock,
    copyTag: String,
    onCopyCode: (String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                part.language?.let { language ->
                    Text(
                        text = language,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } ?: SpacerWidth()
                IconButton(
                    onClick = { onCopyCode(part.code) },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag(copyTag),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "复制代码",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            SelectionContainer {
                Text(
                    text = part.code,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun SpacerWidth() {
    Box(modifier = Modifier.size(1.dp))
}

@Composable
private fun TranscriptImageGallery(
    images: List<IndexedTranscriptImage>,
    bridgeEndpoint: String,
    bridgeAuthToken: String,
    testTagPrefix: String,
    onOpenImagePreview: (String, String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items = images, key = { it.index }) { image ->
            FixedPreviewImageCard(
                source = image.part.source,
                title = image.part.altText,
                bridgeEndpoint = bridgeEndpoint,
                bridgeAuthToken = bridgeAuthToken,
                previewHeight = TranscriptInlineImageHeight,
                modifier = Modifier
                    .width(TranscriptInlineImageWidth)
                    .testTag(TestTags.SessionDetailTranscriptImagePrefix + "${testTagPrefix}_${image.index}"),
                onOpen = { onOpenImagePreview(image.part.altText, image.part.source) },
                showTitle = false,
                containerColor = MaterialTheme.colorScheme.background,
            )
        }
    }
}

@Composable
private fun FixedPreviewImageCard(
    source: String,
    title: String,
    bridgeEndpoint: String,
    bridgeAuthToken: String,
    previewHeight: Dp,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    showTitle: Boolean = true,
    containerColor: Color = Color.Unspecified,
) {
    val resolvedContainerColor = if (containerColor == Color.Unspecified) {
        MaterialTheme.colorScheme.surface
    } else {
        containerColor
    }
    val imageState = rememberTranscriptImageState(
        source = source,
        bridgeEndpoint = bridgeEndpoint,
        bridgeAuthToken = bridgeAuthToken,
        maxDimension = TranscriptThumbnailMaxDimension,
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = resolvedContainerColor),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (imageState) {
                TranscriptImageLoadState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(previewHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                }

                is TranscriptImageLoadState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(previewHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(imageVector = Icons.Filled.Image, contentDescription = null)
                            Text(
                                text = imageState.message,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                is TranscriptImageLoadState.Success -> {
                    Image(
                        bitmap = imageState.image.bitmap,
                        contentDescription = title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(previewHeight),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            if (showTitle) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ImagePreviewDialog(
    preview: ImagePreviewState,
    bridgeEndpoint: String,
    bridgeAuthToken: String,
    onDismiss: () -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val imageState = rememberTranscriptImageState(
        source = preview.source,
        bridgeEndpoint = bridgeEndpoint,
        bridgeAuthToken = bridgeAuthToken,
        maxDimension = TranscriptPreviewMaxDimension,
    )
    var isSaving by remember(preview.source) { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.SessionDetailImagePreviewDialog),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = preview.title,
                    style = MaterialTheme.typography.titleMedium,
                )
                when (imageState) {
                    TranscriptImageLoadState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 220.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    is TranscriptImageLoadState.Error -> {
                        Text(
                            text = imageState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    is TranscriptImageLoadState.Success -> {
                        Image(
                            bitmap = imageState.image.bitmap,
                            contentDescription = preview.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 520.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("关闭")
                    }
                    Button(
                        onClick = {
                            val loadedImage = (imageState as? TranscriptImageLoadState.Success)?.image ?: return@Button
                            coroutineScope.launch {
                                isSaving = true
                                val message = runCatching {
                                    saveTranscriptImage(
                                        context = context,
                                        image = loadedImage,
                                        displayName = preview.title,
                                    )
                                }.getOrElse { error ->
                                    error.message ?: "保存图片失败。"
                                }
                                onShowMessage(message)
                                isSaving = false
                            }
                        },
                        enabled = imageState is TranscriptImageLoadState.Success && !isSaving,
                        modifier = Modifier.testTag(TestTags.SessionDetailImagePreviewSaveButton),
                    ) {
                        Text(if (isSaving) "保存中" else "保存")
                    }
                }
            }
        }
    }
}

@Composable
private fun FileDownloadConfirmDialog(
    request: TranscriptFileDownloadRequest,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag(TestTags.SessionDetailFileDownloadConfirmDialog),
        onDismissRequest = onDismiss,
        title = { Text("下载文件") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("确认下载这个文件吗？")
                Text(
                    text = request.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(TestTags.SessionDetailFileDownloadConfirmButton),
            ) {
                Text("下载")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun FileDownloadProgressDialog(
    state: FileDownloadDialogState,
    onDismiss: () -> Unit,
) {
    val progress = state.progress
    val fraction = calculateTranscriptFileDownloadFraction(progress)
    val title = when {
        state.errorMessage != null -> "下载失败"
        state.resultMessage != null -> "下载完成"
        else -> "正在下载"
    }
    val statusText = when {
        state.errorMessage != null -> state.errorMessage
        state.resultMessage != null -> state.resultMessage
        else -> when (progress.stage) {
            TranscriptFileDownloadStage.Preparing -> "准备中"
            TranscriptFileDownloadStage.Downloading -> "下载中"
            TranscriptFileDownloadStage.Saving -> "保存中"
        }
    }

    Dialog(
        onDismissRequest = {
            if (!state.isRunning) {
                onDismiss()
            }
        },
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.SessionDetailFileDownloadProgressDialog),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = state.request.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = statusText ?: "处理中",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.errorMessage != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (state.isRunning) {
                    if (fraction != null) {
                        CircularProgressIndicator(progress = { fraction })
                    } else {
                        CircularProgressIndicator()
                    }
                }
                if (progress.stage == TranscriptFileDownloadStage.Downloading || progress.stage == TranscriptFileDownloadStage.Saving) {
                    Text(
                        text = buildString {
                            append(formatTranscriptFileByteCount(progress.bytesDownloaded))
                            progress.totalBytes?.let { totalBytes ->
                                append(" / ")
                                append(formatTranscriptFileByteCount(totalBytes))
                                if (fraction != null) {
                                    append("  (${(fraction * 100).toInt()}%)")
                                }
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !state.isRunning,
                    ) {
                        Text(if (state.isRunning) "下载中" else "关闭")
                    }
                }
            }
        }
    }
}

@Composable
private fun ApprovalActionCard(
    approval: PendingApprovalUiState,
    onApprovalDecision: (ApprovalDecision) -> Unit,
) {
    val approvalSummaryScrollState = rememberScrollState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.SessionDetailApprovalCard),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TranscriptLabelChip(
                    text = "审批",
                    icon = Icons.Filled.HourglassTop,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = "待审批操作",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(approvalSummaryScrollState)
                    .testTag(TestTags.SessionDetailApprovalSummary),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = approval.method ?: "未知工具请求",
                    style = MaterialTheme.typography.bodyLarge,
                )
                approval.paramsSummary?.let { summary ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        SelectionContainer {
                            Text(
                                text = summary,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            Button(
                onClick = { onApprovalDecision(ApprovalDecision.Approve) },
                enabled = !approval.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.SessionDetailApproveButton),
            ) {
                Text("批准")
            }
            Button(
                onClick = { onApprovalDecision(ApprovalDecision.ApproveForSession) },
                enabled = !approval.isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.SessionDetailApproveForSessionButton),
            ) {
                Text("本会话都批准")
            }
            OutlinedButton(
                onClick = { onApprovalDecision(ApprovalDecision.Reject) },
                enabled = !approval.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.SessionDetailRejectButton),
            ) {
                Text("拒绝")
            }
            Button(
                onClick = { onApprovalDecision(ApprovalDecision.RejectAndInterrupt) },
                enabled = !approval.isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.SessionDetailRejectAndInterruptButton),
            ) {
                Text("拒绝并中断")
            }
        }
    }
}

@Composable
private fun TranscriptLabelChip(
    text: String,
    icon: ImageVector? = null,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    compact: Boolean = false,
    plain: Boolean = false,
) {
    if (plain) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 11.dp else 12.dp),
                    tint = contentColor.copy(alpha = 0.78f),
                )
            }
            Text(
                text = text,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.82f),
            )
        }
    } else {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = containerColor,
            contentColor = contentColor,
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = if (compact) 7.dp else 9.dp,
                    vertical = if (compact) 3.dp else 5.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(if (compact) 12.dp else 13.dp),
                    )
                }
                Text(
                    text = text,
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun TranscriptBubble.headerIcon(): ImageVector {
    return when (speaker) {
        TranscriptSpeaker.User -> Icons.Filled.Work
        TranscriptSpeaker.Assistant -> Icons.Filled.Bolt
        TranscriptSpeaker.System -> when (kind) {
            TranscriptBubbleKind.ToolRequest -> Icons.Filled.HourglassTop
            TranscriptBubbleKind.ToolResult -> Icons.Filled.CheckCircle
            TranscriptBubbleKind.Status,
            TranscriptBubbleKind.Message,
            -> Icons.Filled.Schedule
        }
    }
}

@Composable
private fun TranscriptBubble.headerContainerColor() = when (speaker) {
    TranscriptSpeaker.User -> MaterialTheme.colorScheme.primaryContainer
    TranscriptSpeaker.Assistant -> MaterialTheme.colorScheme.secondaryContainer
    TranscriptSpeaker.System -> when (kind) {
        TranscriptBubbleKind.ToolRequest -> MaterialTheme.colorScheme.tertiaryContainer
        TranscriptBubbleKind.ToolResult -> MaterialTheme.colorScheme.secondaryContainer
        TranscriptBubbleKind.Status,
        TranscriptBubbleKind.Message,
        -> MaterialTheme.colorScheme.surface
    }
}

@Composable
private fun TranscriptBubble.headerContentColor() = when (speaker) {
    TranscriptSpeaker.User -> MaterialTheme.colorScheme.onPrimaryContainer
    TranscriptSpeaker.Assistant -> MaterialTheme.colorScheme.onSecondaryContainer
    TranscriptSpeaker.System -> when (kind) {
        TranscriptBubbleKind.ToolRequest -> MaterialTheme.colorScheme.onTertiaryContainer
        TranscriptBubbleKind.ToolResult -> MaterialTheme.colorScheme.onSecondaryContainer
        TranscriptBubbleKind.Status,
        TranscriptBubbleKind.Message,
        -> MaterialTheme.colorScheme.onSurface
    }
}

private fun DraftSessionUiState.toDraftDetail(): SessionDetail {
    return SessionDetail(
        id = localId,
        title = "新会话（草稿）",
        subtitle = "首条消息发送后创建 • ${localizedApprovalMode(approvalMode)}",
        lastUpdated = "尚未创建",
        transcriptPreview = buildString {
            appendLine("工作目录：$cwd")
            appendLine("模型：$model")
            appendLine("推理强度：${localizedReasoning(reasoningEffort)}")
            appendLine("速度：${localizedService(serviceTier)}")
            appendLine("文件权限：${localizedSandbox(sandboxMode)}")
            append("发送首条消息后，bridge 才会真正创建这个会话。")
        },
        cwd = cwd,
        model = model,
        approvalMode = approvalMode,
        reasoningEffort = reasoningEffort,
        serviceTier = serviceTier,
        sandboxMode = sandboxMode,
        status = "draft",
    )
}

private fun localizedReasoning(value: String): String {
    return when (value) {
        "minimal" -> "极低"
        "low" -> "低"
        "high" -> "高"
        "xhigh" -> "最高"
        else -> "中"
    }
}

private fun localizedService(value: String): String {
    return when (value) {
        "default" -> "普通"
        "fast" -> "快速"
        else -> "普通"
    }
}

private fun localizedApprovalMode(mode: String): String {
    return when (mode) {
        "auto" -> "自动"
        else -> "手动"
    }
}

private fun localizedSandbox(mode: String): String {
    return when (mode) {
        "read-only" -> "只读"
        "danger-full-access" -> "完全访问"
        else -> "工作区可写"
    }
}

private fun localizedStatusLabel(status: String): String {
    return when (status) {
        "draft" -> "草稿"
        "running" -> "进行中"
        "awaiting_approval" -> "待审批"
        "error" -> "出错"
        else -> "空闲"
    }
}

private fun localizedGoalStatus(status: String): String {
    return when (status) {
        "paused" -> "已暂停"
        "complete" -> "已完成"
        "budgetLimited" -> "预算受限"
        "blocked" -> "已阻塞"
        "usageLimited" -> "额度受限"
        else -> "进行中"
    }
}

private fun formatTokenUsage(tokens: Long): String {
    return when {
        tokens >= 10_000 -> {
            val display = tokens / 1000.0 / 10.0
            "${display}万 tokens"
        }

        else -> "$tokens tokens"
    }
}

private fun formatGoalDuration(seconds: Long): String {
    if (seconds <= 0L) {
        return "0s"
    }
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainSeconds = seconds % 60
    return buildString {
        if (hours > 0) {
            append(hours)
            append("h")
        }
        if (minutes > 0) {
            append(minutes)
            append("m")
        }
        if (remainSeconds > 0 || length == 0) {
            append(remainSeconds)
            append("s")
        }
    }
}
