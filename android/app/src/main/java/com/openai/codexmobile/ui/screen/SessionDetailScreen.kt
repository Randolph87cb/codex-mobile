package com.openai.codexmobile.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openai.codexmobile.PendingApprovalUiState
import com.openai.codexmobile.SessionRealtimeUiState
import com.openai.codexmobile.data.ApprovalDecision
import com.openai.codexmobile.model.SessionDetail

@Composable
fun SessionDetailScreen(
    paddingValues: PaddingValues,
    sessionDetail: SessionDetail?,
    sessionRealtimeState: SessionRealtimeUiState,
    draftMessage: String,
    isLoading: Boolean,
    onDraftMessageChange: (String) -> Unit,
    onSend: () -> Unit,
    onApprovalDecision: (ApprovalDecision) -> Unit,
    onBack: () -> Unit,
) {
    val transcriptScrollState = rememberScrollState()

    LaunchedEffect(
        sessionDetail?.transcriptPreview,
        sessionRealtimeState.lastEventText,
        sessionRealtimeState.pendingApproval?.requestId?.toString(),
    ) {
        transcriptScrollState.animateScrollTo(transcriptScrollState.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "会话详情",
            style = MaterialTheme.typography.headlineMedium,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "实时状态",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = sessionRealtimeState.connectionText,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "运行状态：${sessionRealtimeState.statusText}",
                    style = MaterialTheme.typography.bodyMedium,
                )
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
                if (sessionRealtimeState.statusText == "进行中") {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
        sessionRealtimeState.pendingApproval?.let { approval ->
            ApprovalActionCard(
                approval = approval,
                onApprovalDecision = onApprovalDecision,
            )
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(transcriptScrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = sessionDetail?.title ?: "未选择会话",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = sessionDetail?.subtitle ?: "请先从会话列表中选择一个会话。",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = sessionDetail?.lastUpdated ?: "等待会话元数据",
                    style = MaterialTheme.typography.labelLarge,
                )
                TranscriptBubbleList(
                    transcript = sessionDetail?.transcriptPreview.orEmpty(),
                )
            }
        }
        OutlinedTextField(
            value = draftMessage,
            onValueChange = onDraftMessageChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("发送给 Codex") },
        )
        Button(
            onClick = onSend,
            enabled = !isLoading && sessionDetail != null && draftMessage.isNotBlank(),
        ) {
            if (isLoading) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            } else {
                Text("发送")
            }
        }
        Button(onClick = onBack) {
            Text("返回会话列表")
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
                    TranscriptSpeaker.System -> MaterialTheme.colorScheme.surfaceVariant
                }
                Card(
                    modifier = Modifier
                        .align(
                            if (isUser) {
                                androidx.compose.ui.Alignment.CenterEnd
                            } else {
                                androidx.compose.ui.Alignment.CenterStart
                            },
                        )
                        .widthIn(max = 320.dp),
                    colors = CardDefaults.cardColors(containerColor = backgroundColor),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = when (bubble.speaker) {
                                TranscriptSpeaker.User -> "你"
                                TranscriptSpeaker.Assistant -> "Codex"
                                TranscriptSpeaker.System -> "系统"
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = bubble.text,
                            style = MaterialTheme.typography.bodyMedium,
                        )
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "待审批操作",
                style = MaterialTheme.typography.titleMedium,
            )
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
            Button(
                onClick = { onApprovalDecision(ApprovalDecision.Approve) },
                enabled = !approval.isSubmitting,
            ) {
                Text("批准")
            }
            Button(
                onClick = { onApprovalDecision(ApprovalDecision.ApproveForSession) },
                enabled = !approval.isSubmitting,
            ) {
                Text("本会话都批准")
            }
            Button(
                onClick = { onApprovalDecision(ApprovalDecision.Reject) },
                enabled = !approval.isSubmitting,
            ) {
                Text("拒绝")
            }
            Button(
                onClick = { onApprovalDecision(ApprovalDecision.RejectAndInterrupt) },
                enabled = !approval.isSubmitting,
            ) {
                Text("拒绝并中断")
            }
        }
    }
}
