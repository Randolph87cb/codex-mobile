package com.openai.codexmobile.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openai.codexmobile.model.SessionDetail

@Composable
fun SessionDetailScreen(
    paddingValues: PaddingValues,
    sessionDetail: SessionDetail?,
    draftMessage: String,
    isLoading: Boolean,
    onDraftMessageChange: (String) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
) {
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
                        ?: "这里会显示会话内容和 Codex 的回复。",
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
