package com.openai.codexmobile.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openai.codexmobile.model.BridgeConnectionState
import com.openai.codexmobile.model.SessionSummary

@Composable
fun SessionListScreen(
    paddingValues: PaddingValues,
    sessions: List<SessionSummary>,
    connectionState: BridgeConnectionState,
    isLoading: Boolean,
    onOpenSession: (String) -> Unit,
    onCreateSession: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "会话列表",
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        item {
            Text(
                text = when (connectionState) {
                    is BridgeConnectionState.Connected -> "桥接地址：${connectionState.endpoint}"
                    BridgeConnectionState.Disconnected -> "桥接服务未连接"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        items(sessions, key = { it.id }) { session ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSession(session.id) },
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(text = session.title, style = MaterialTheme.typography.titleMedium)
                    Text(text = session.subtitle, style = MaterialTheme.typography.bodyMedium)
                    Text(text = session.lastUpdated, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        item {
            Button(
                onClick = onCreateSession,
                enabled = !isLoading,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                } else {
                    Text("新建会话")
                }
            }
        }
        item {
            Button(onClick = onOpenSettings) {
                Text("设置")
            }
        }
        item {
            Button(onClick = onDisconnect) {
                Text("断开连接")
            }
        }
    }
}
