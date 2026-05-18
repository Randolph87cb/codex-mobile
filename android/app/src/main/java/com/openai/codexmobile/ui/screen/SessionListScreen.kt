package com.openai.codexmobile.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.openai.codexmobile.model.BridgeConnectionState
import com.openai.codexmobile.model.SessionSummary
import com.openai.codexmobile.ui.TestTags

data class SessionDirectoryGroup(
    val cwd: String,
    val sessions: List<SessionSummary>,
)

fun groupSessionsByDirectory(sessions: List<SessionSummary>): List<SessionDirectoryGroup> {
    return sessions
        .groupBy { it.cwd.ifBlank { "未提供工作目录" } }
        .toList()
        .sortedBy { (cwd, _) -> cwd.lowercase() }
        .map { (cwd, items) ->
            SessionDirectoryGroup(
                cwd = cwd,
                sessions = items.sortedByDescending { it.lastUpdated },
            )
        }
}

@Composable
fun SessionListScreen(
    paddingValues: PaddingValues,
    sessions: List<SessionSummary>,
    connectionState: BridgeConnectionState,
    currentCwd: String,
    isLoading: Boolean,
    onOpenSession: (String) -> Unit,
    onCreateDraft: (String) -> Unit,
    onDisconnect: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val groups = groupSessionsByDirectory(sessions)
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var draftDirectory by rememberSaveable(currentCwd) { mutableStateOf(currentCwd) }

    if (showCreateDialog) {
        AlertDialog(
            modifier = Modifier.testTag(TestTags.SessionListCreateDialog),
            onDismissRequest = { showCreateDialog = false },
            title = { Text("选择目录后开始草稿线程") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("首条消息发送前不会真正创建会话。")
                    OutlinedTextField(
                        value = draftDirectory,
                        onValueChange = { draftDirectory = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TestTags.SessionListDraftDirectoryField),
                        singleLine = true,
                        label = { Text("工作目录") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCreateDialog = false
                        onCreateDraft(draftDirectory)
                    },
                    modifier = Modifier.testTag(TestTags.SessionListDraftDialogConfirmButton),
                ) {
                    Text("开始草稿")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = {
                    draftDirectory = currentCwd
                    showCreateDialog = true
                },
                enabled = !isLoading,
                modifier = Modifier
                    .weight(1f)
                    .testTag(TestTags.SessionListCreateDraftButton),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                } else {
                    Text("新建草稿线程")
                }
            }
            Button(
                onClick = onOpenSettings,
                modifier = Modifier
                    .weight(1f)
                    .testTag(TestTags.SessionListOpenSettingsButton),
            ) {
                Text("设置")
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (groups.isEmpty()) {
                item {
                    EmptySessionState(currentCwd = currentCwd)
                }
            }

            items(groups, key = { it.cwd }) { group ->
                SessionDirectoryCard(
                    group = group,
                    onOpenSession = onOpenSession,
                    onCreateDraft = onCreateDraft,
                )
            }
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

@Composable
private fun EmptySessionState(
    currentCwd: String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "还没有会话",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "可以先用“新建草稿线程”选择目录。默认目录：${currentCwd.ifBlank { "未配置" }}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SessionDirectoryCard(
    group: SessionDirectoryGroup,
    onOpenSession: (String) -> Unit,
    onCreateDraft: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.SessionListFolderPrefix + group.cwd),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = buildFolderLabel(group.cwd),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = group.cwd,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "线程数：${group.sessions.size}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                TextButton(
                    onClick = { onCreateDraft(group.cwd) },
                    modifier = Modifier.testTag(TestTags.SessionListFolderCreatePrefix + group.cwd),
                ) {
                    Text("在此新建")
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                group.sessions.forEach { session ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TestTags.SessionListItemPrefix + session.id)
                            .clickable { onOpenSession(session.id) },
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(text = session.title, style = MaterialTheme.typography.titleSmall)
                            Text(text = session.subtitle, style = MaterialTheme.typography.bodyMedium)
                            Text(text = session.lastUpdated, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

private fun buildFolderLabel(cwd: String): String {
    val trimmed = cwd.trim().trimEnd('\\', '/')
    if (trimmed.isBlank()) {
        return "未提供工作目录"
    }
    return trimmed.split('\\', '/').lastOrNull().orEmpty().ifBlank { trimmed }
}
