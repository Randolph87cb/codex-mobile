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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.openai.codexmobile.model.BridgeConnectionState
import com.openai.codexmobile.model.SessionSummary
import com.openai.codexmobile.ui.TestTags

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
    Column(
        modifier = Modifier
            .testTag(TestTags.SessionListScreen)
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "会话列表",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = when (connectionState) {
                is BridgeConnectionState.Connected -> "桥接地址：${connectionState.endpoint}"
                BridgeConnectionState.Disconnected -> "桥接服务未连接"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(sessions, key = { it.id }) { session ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.SessionListItemPrefix + session.id)
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
        }
        Button(
            onClick = onCreateSession,
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.SessionListCreateButton),
        ) {
            if (isLoading) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            } else {
                Text("新建会话")
            }
        }
        Button(
            onClick = onOpenSettings,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.SessionListOpenSettingsButton),
        ) {
            Text("设置")
        }
        Button(
            onClick = onDisconnect,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.SessionListDisconnectButton),
        ) {
            Text("断开连接")
        }
    }
}
