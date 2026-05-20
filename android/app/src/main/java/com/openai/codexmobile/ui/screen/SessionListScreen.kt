package com.openai.codexmobile.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
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
        .map { (cwd, items) ->
            SessionDirectoryGroup(
                cwd = cwd,
                sessions = items.sortedByDescending { it.lastUpdated },
            )
        }
        .sortedWith(
            compareByDescending<SessionDirectoryGroup> { it.sessions.firstOrNull()?.lastUpdated.orEmpty() }
                .thenBy { it.cwd.lowercase() },
        )
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
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "会话",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = if (connectionState is BridgeConnectionState.Connected) {
                                Icons.Filled.CloudDone
                            } else {
                                Icons.Filled.CloudOff
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = when (connectionState) {
                                is BridgeConnectionState.Connected -> "桥接地址：${connectionState.endpoint}"
                                BridgeConnectionState.Disconnected -> "桥接服务未连接"
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            FilledTonalIconButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag(TestTags.SessionListOpenSettingsButton),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "设置",
                )
            }
        }
        Button(
            onClick = {
                draftDirectory = currentCwd
                showCreateDialog = true
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.SessionListCreateDraftButton),
        ) {
            if (isLoading) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            } else {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Text("新建草稿线程", modifier = Modifier.padding(start = 8.dp))
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
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
        OutlinedButton(
            onClick = onDisconnect,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.SessionListDisconnectButton),
        ) {
            Icon(imageVector = Icons.Filled.PowerSettingsNew, contentDescription = null)
            Text("断开连接", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun EmptySessionState(
    currentCwd: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
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
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                    Text("新建", modifier = Modifier.padding(start = 4.dp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                group.sessions.forEach { session ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TestTags.SessionListItemPrefix + session.id)
                            .clickable { onOpenSession(session.id) },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = session.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = buildCompactSessionSubtitle(session, group.cwd),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = session.lastUpdated,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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

internal fun buildCompactSessionSubtitle(
    session: SessionSummary,
    groupCwd: String,
): String {
    val suffix = " • ${session.cwd}"
    return if (
        session.cwd.isNotBlank() &&
        session.cwd == groupCwd &&
        session.subtitle.endsWith(suffix)
    ) {
        session.subtitle.removeSuffix(suffix)
    } else {
        session.subtitle
    }
}
