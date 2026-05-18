package com.openai.codexmobile.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openai.codexmobile.SessionRealtimeUiState
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
    onBack: () -> Unit,
) {
    val transcriptScrollState = rememberScrollState()

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
                Text(
                    text = sessionDetail?.transcriptPreview
                        ?: "这里会显示会话内容、实时回复和结束状态。",
                    style = MaterialTheme.typography.bodyMedium,
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
