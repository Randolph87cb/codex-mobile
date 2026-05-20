package com.openai.codexmobile.ui.screen

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    onRefreshSession: () -> Unit,
    onShowMessage: (String) -> Unit,
    transcriptScrollState: ScrollState? = null,
) {
    val clipboardManager = LocalClipboardManager.current
    val detail = remember(sessionDetail, draftSession) {
        sessionDetail ?: draftSession?.toDraftDetail()
    }
    val currentTranscriptScrollState = transcriptScrollState ?: rememberScrollState()
    var statusExpanded by rememberSaveable { mutableStateOf(false) }
    var activeEditor by rememberSaveable { mutableStateOf<SessionConfigEditor?>(null) }
    var previousTranscriptScrollMax by remember { mutableIntStateOf(0) }
    var imagePreviewState by remember { mutableStateOf<ImagePreviewState?>(null) }
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
    ) {
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

    imagePreviewState?.let { preview ->
        ImagePreviewDialog(
            preview = preview,
            bridgeEndpoint = bridgeEndpoint,
            bridgeAuthToken = bridgeAuthToken,
            onDismiss = { imagePreviewState = null },
            onShowMessage = onShowMessage,
        )
    }

    Column(
        modifier = Modifier
            .testTag(TestTags.SessionDetailScreen)
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
        sessionRealtimeState.pendingApproval?.let { approval ->
            ApprovalActionCard(
                approval = approval,
                onApprovalDecision = onApprovalDecision,
            )
        }
        if (queuedInputs.isNotEmpty()) {
            QueuedInputCard(messages = queuedInputs)
        }
        Card(
            modifier = Modifier
                .testTag(TestTags.SessionDetailTranscript)
                .fillMaxWidth()
                .weight(1f),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag(TestTags.SessionDetailTranscriptScroll)
                    .verticalScroll(currentTranscriptScrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = detail?.subtitle ?: "请先从会话列表中选择一个会话。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = detail?.lastUpdated ?: "等待会话元数据",
                    style = MaterialTheme.typography.labelMedium,
                )
                TranscriptBubbleList(
                    transcript = detail?.transcriptPreview.orEmpty(),
                    liveActivities = sessionRealtimeState.liveExecutionActivities,
                    bridgeEndpoint = bridgeEndpoint,
                    bridgeAuthToken = bridgeAuthToken,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draftMessage,
                onValueChange = onDraftMessageChange,
                modifier = Modifier
                    .weight(1f)
                    .testTag(TestTags.SessionDetailDraftField),
                label = {
                    Text(
                        if (draftSession != null) {
                            "首条消息发送后才真正创建线程"
                        } else {
                            "发送给 Codex"
                        },
                    )
                },
                maxLines = 4,
            )
            OutlinedButton(
                onClick = onPickImage,
                enabled = !isLoading && detail != null,
                modifier = Modifier.testTag(TestTags.SessionDetailAttachImageButton),
            ) {
                Icon(imageVector = Icons.Filled.Image, contentDescription = null)
                Text(
                    text = "图片",
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Button(
                onClick = onSend,
                enabled = !isLoading &&
                    detail != null &&
                    (draftMessage.isNotBlank() || pendingImageAttachments.isNotEmpty()) &&
                    !hasPendingUploadBlockers,
                modifier = Modifier.testTag(TestTags.SessionDetailSendButton),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                } else {
                    Text(if (draftSession != null) "开始" else "发送")
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.SessionDetailPendingImageCard),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag(TestTags.SessionDetailPendingImageTray),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(imageVector = Icons.Filled.Image, contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "已附加图片（${attachments.size}）", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = when {
                            failedCount > 0 -> "有 $failedCount 张上传失败，请重试或移除。"
                            uploadingCount > 0 -> "原图预上传中，还剩 $uploadingCount 张。"
                            else -> "原图已预上传，发送时只引用 bridge 路径。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            LazyRow(
                modifier = Modifier.testTag(TestTags.SessionDetailPendingImageRow),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
    Card(
        modifier = Modifier.width(104.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TranscriptImageThumbnail(
                source = attachment.previewSource,
                title = attachment.displayName,
                bridgeEndpoint = bridgeEndpoint,
                bridgeAuthToken = bridgeAuthToken,
                modifier = Modifier.testTag(TestTags.SessionDetailPendingImageThumbnailPrefix + attachment.localId),
                onOpen = onOpen,
            )
            Text(
                text = attachment.displayName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.testTag(TestTags.SessionDetailPendingImageStatusPrefix + attachment.localId),
                ) {
                    when (attachment.uploadState) {
                        PendingImageUploadState.Uploading -> {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                            Text("上传中", style = MaterialTheme.typography.labelSmall)
                        }

                        PendingImageUploadState.Uploaded -> {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Text("已就绪", style = MaterialTheme.typography.labelSmall)
                        }

                        PendingImageUploadState.Failed -> {
                            Icon(
                                imageVector = Icons.Filled.Error,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Text("失败", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                TextButton(
                    onClick = onRemove,
                    modifier = Modifier.testTag(TestTags.SessionDetailClearImageButton + "_" + attachment.localId),
                ) {
                    Text("移除")
                }
            }
            if (attachment.uploadState == PendingImageUploadState.Failed) {
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.SessionDetailPendingImageRetryButtonPrefix + attachment.localId),
                ) {
                    Text("重试")
                }
            }
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
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.SessionDetailStatusStrip),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.SessionDetailStatusButton)
                    .clickable { onToggleExpanded() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatusGlyph(icon = statusIcon, label = localizedStatusLabel(detail?.status ?: "idle"))
                StatusGlyph(
                    icon = connectionIcon,
                    label = if (isDraft) "草稿" else if (sessionRealtimeState.isConnected) "实时流" else "快照",
                )
                StatusGlyph(icon = queueIcon, label = if (queuedInputs.isEmpty()) "无排队" else "排队 ${queuedInputs.size}")
                if (sessionRealtimeState.pendingApproval != null) {
                    StatusGlyph(icon = Icons.Filled.HourglassTop, label = "待审批")
                }
                Box(modifier = Modifier.weight(1f))
                IconButton(onClick = onToggleExpanded) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "收起状态详情" else "展开状态详情",
                    )
                }
            }
            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.SessionDetailStatusDetails),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
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
                        OutlinedButton(
                            onClick = onRefreshSession,
                            modifier = Modifier.testTag(TestTags.SessionDetailStatusRefreshButton),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                            )
                            Text(
                                text = "立即同步",
                                modifier = Modifier.padding(start = 8.dp),
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

@Composable
private fun StatusGlyph(
    icon: ImageVector,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null)
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
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

        SessionConfigEditor.SandboxMode -> Unit
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
private fun QueuedInputCard(
    messages: List<String>,
) {
    val queueScrollState = rememberScrollState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.SessionDetailQueuedInputsCard),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "排队中的消息（${messages.size}）",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "当前轮结束后会按顺序自动发送。",
                style = MaterialTheme.typography.bodySmall,
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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEachIndexed { index, item ->
            when (item) {
                is TranscriptDisplayItem.BubbleItem -> TranscriptBubbleCard(
                    bubble = item.bubble,
                    toggleTag = TestTags.SessionDetailTranscriptBubbleTogglePrefix + index,
                    bridgeEndpoint = bridgeEndpoint,
                    bridgeAuthToken = bridgeAuthToken,
                    onCopyText = onCopyText,
                    onCopyCode = onCopyCode,
                    onOpenImagePreview = onOpenImagePreview,
                )

                is TranscriptDisplayItem.ExecutionGroup -> ExecutionProcessCard(
                    index = index,
                    group = item,
                    bridgeEndpoint = bridgeEndpoint,
                    bridgeAuthToken = bridgeAuthToken,
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
            TranscriptSpeaker.Assistant -> MaterialTheme.colorScheme.secondaryContainer
            TranscriptSpeaker.System -> when (bubble.kind) {
                TranscriptBubbleKind.ToolRequest -> MaterialTheme.colorScheme.tertiaryContainer
                TranscriptBubbleKind.ToolResult -> MaterialTheme.colorScheme.surfaceVariant
                TranscriptBubbleKind.Status,
                TranscriptBubbleKind.Message,
                -> MaterialTheme.colorScheme.surfaceVariant
            }
        }
        Card(
            modifier = Modifier
                .align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart)
                .fillMaxWidth(0.92f),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (isCollapsible) {
                    TranscriptToggleHeader(
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
    onCopyText: (String) -> Unit,
    onCopyCode: (String) -> Unit,
    onOpenImagePreview: (String, String) -> Unit,
) {
    var expanded by rememberSaveable(index, group.summaryLine) { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.92f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TranscriptToggleHeader(
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
    onCopyText: (String) -> Unit,
    onCopyCode: (String) -> Unit,
    onOpenImagePreview: (String, String) -> Unit,
) {
    var expanded by rememberSaveable(toggleTag, bubble.summaryLine) { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TranscriptToggleHeader(
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .testTag(toggleTag)
                .clickable(onClick = onToggle),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
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
            modifier = Modifier.testTag(copyTag),
        ) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = "复制消息",
            )
        }
        Icon(
            imageVector = if (expanded) {
                Icons.Default.KeyboardArrowUp
            } else {
                Icons.Default.KeyboardArrowDown
            },
            contentDescription = if (expanded) "收起消息" else "展开消息",
        )
    }
}

@Composable
private fun TranscriptStaticHeader(
    label: String,
    title: String?,
    copyTag: String,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
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
            modifier = Modifier.testTag(copyTag),
        ) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = "复制消息",
            )
        }
    }
}

@Composable
private fun TranscriptPartsColumn(
    parts: List<TranscriptPart>,
    bridgeEndpoint: String,
    bridgeAuthToken: String,
    testTagPrefix: String,
    onCopyCode: (String) -> Unit,
    onOpenImagePreview: (String, String) -> Unit,
) {
    parts.forEachIndexed { index, part ->
        when (part) {
            is TranscriptPart.Text -> {
                MarkdownTextBlock(
                    text = part.text,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            is TranscriptPart.Image -> {
                TranscriptImageThumbnail(
                    source = part.source,
                    title = part.altText,
                    bridgeEndpoint = bridgeEndpoint,
                    bridgeAuthToken = bridgeAuthToken,
                    modifier = Modifier.testTag(TestTags.SessionDetailTranscriptImagePrefix + "${testTagPrefix}_$index"),
                    onOpen = {
                        onOpenImagePreview(part.altText, part.source)
                    },
                )
            }

            is TranscriptPart.CodeBlock -> {
                CodeBlockCard(
                    part = part,
                    copyTag = TestTags.SessionDetailCodeBlockCopyPrefix + "${testTagPrefix}_$index",
                    onCopyCode = onCopyCode,
                )
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
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
                    modifier = Modifier.testTag(copyTag),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "复制代码",
                    )
                }
            }
            SelectionContainer {
                Text(
                    text = part.code,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall,
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
private fun TranscriptImageThumbnail(
    source: String,
    title: String,
    bridgeEndpoint: String,
    bridgeAuthToken: String,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                            .heightIn(min = 96.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                }

                is TranscriptImageLoadState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp),
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
                            .heightIn(min = 96.dp, max = 220.dp),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
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
private fun ApprovalActionCard(
    approval: PendingApprovalUiState,
    onApprovalDecision: (ApprovalDecision) -> Unit,
) {
    val approvalSummaryScrollState = rememberScrollState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.SessionDetailApprovalCard),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "待审批操作",
                style = MaterialTheme.typography.titleMedium,
            )
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
                    SelectionContainer {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                        )
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
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.SessionDetailApproveForSessionButton),
            ) {
                Text("本会话都批准")
            }
            Button(
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
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.SessionDetailRejectAndInterruptButton),
            ) {
                Text("拒绝并中断")
            }
        }
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
