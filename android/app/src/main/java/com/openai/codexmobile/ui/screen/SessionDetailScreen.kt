package com.openai.codexmobile.ui.screen

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.openai.codexmobile.DraftSessionUiState
import com.openai.codexmobile.PendingImageAttachmentUiState
import com.openai.codexmobile.PendingApprovalUiState
import com.openai.codexmobile.SessionRealtimeUiState
import com.openai.codexmobile.data.ApprovalDecision
import com.openai.codexmobile.model.SessionDetail
import com.openai.codexmobile.ui.TestTags

private enum class SessionConfigEditor {
    Cwd,
    Model,
    ReasoningEffort,
    ServiceTier,
}

@Composable
fun SessionDetailScreen(
    paddingValues: PaddingValues,
    sessionDetail: SessionDetail?,
    draftSession: DraftSessionUiState?,
    sessionRealtimeState: SessionRealtimeUiState,
    queuedInputs: List<String>,
    draftMessage: String,
    pendingImageAttachment: PendingImageAttachmentUiState?,
    isLoading: Boolean,
    onDraftMessageChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onClearPendingImageAttachment: () -> Unit,
    onSend: () -> Unit,
    onApprovalDecision: (ApprovalDecision) -> Unit,
    onUpdateCwd: (String) -> Unit,
    onUpdateModel: (String) -> Unit,
    onUpdateReasoningEffort: (String) -> Unit,
    onUpdateServiceTier: (String) -> Unit,
    onRefreshSession: () -> Unit,
) {
    val detail = remember(sessionDetail, draftSession) {
        sessionDetail ?: draftSession?.toDraftDetail()
    }
    val transcriptScrollState = rememberScrollState()
    var statusExpanded by rememberSaveable { mutableStateOf(false) }
    var activeEditor by rememberSaveable { mutableStateOf<SessionConfigEditor?>(null) }

    LaunchedEffect(
        detail?.transcriptPreview,
        sessionRealtimeState.lastEventText,
        sessionRealtimeState.pendingApproval?.requestId?.toString(),
    ) {
        transcriptScrollState.animateScrollTo(transcriptScrollState.maxValue)
    }

    ConfigEditorDialogs(
        activeEditor = activeEditor,
        detail = detail,
        onDismiss = { activeEditor = null },
        onUpdateCwd = onUpdateCwd,
        onUpdateModel = onUpdateModel,
        onUpdateReasoningEffort = onUpdateReasoningEffort,
        onUpdateServiceTier = onUpdateServiceTier,
    )

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
                    .verticalScroll(transcriptScrollState),
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
                )
            }
        }
        pendingImageAttachment?.let { attachment ->
            PendingImageAttachmentCard(
                attachment = attachment,
                onClear = onClearPendingImageAttachment,
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
                enabled = !isLoading && detail != null && (draftMessage.isNotBlank() || pendingImageAttachment != null),
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

@Composable
private fun PendingImageAttachmentCard(
    attachment: PendingImageAttachmentUiState,
    onClear: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.SessionDetailPendingImageCard),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = Icons.Filled.Image, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "已附加图片", style = MaterialTheme.typography.labelLarge)
                Text(text = attachment.displayName, style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(
                onClick = onClear,
                modifier = Modifier.testTag(TestTags.SessionDetailClearImageButton),
            ) {
                Text("移除")
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
) {
    val bubbles = parseTranscriptBubbles(transcript)
    if (bubbles.isEmpty()) {
        Text(
            text = "这里会显示会话内容、实时回复和结束状态。",
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        bubbles.forEach { bubble ->
            Box(modifier = Modifier.fillMaxWidth()) {
                val isUser = bubble.speaker == TranscriptSpeaker.User
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
                        Text(text = bubble.label, style = MaterialTheme.typography.labelLarge)
                        bubble.title?.let { title ->
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        bubble.parts.forEach { part ->
                            when (part) {
                                is TranscriptPart.Text -> {
                                    Text(
                                        text = part.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }

                                is TranscriptPart.CodeBlock -> {
                                    CodeBlockCard(part)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlockCard(part: TranscriptPart.CodeBlock) {
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
            part.language?.let { language ->
                Text(
                    text = language,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = part.code,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
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
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                    )
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
            append("发送首条消息后，bridge 才会真正创建这个会话。")
        },
        cwd = cwd,
        model = model,
        approvalMode = approvalMode,
        reasoningEffort = reasoningEffort,
        serviceTier = serviceTier,
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

private fun localizedStatusLabel(status: String): String {
    return when (status) {
        "draft" -> "草稿"
        "running" -> "进行中"
        "awaiting_approval" -> "待审批"
        "error" -> "出错"
        else -> "空闲"
    }
}
