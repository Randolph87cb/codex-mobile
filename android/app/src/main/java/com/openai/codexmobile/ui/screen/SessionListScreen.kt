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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
    showArchivedSessions: Boolean,
    connectionState: BridgeConnectionState,
    currentCwd: String,
    isLoading: Boolean,
    onOpenSession: (String) -> Unit,
    onShowArchivedSessionsChange: (Boolean) -> Unit,
    onArchiveSession: (String) -> Unit,
    onUnarchiveSession: (String) -> Unit,
    onCreateDraft: (String) -> Unit,
    onDisconnect: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val groups = groupSessionsByDirectory(sessions)
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var draftDirectory by rememberSaveable(currentCwd) { mutableStateOf(currentCwd) }
    var pendingArchiveSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingArchiveSessionTitle by rememberSaveable { mutableStateOf("") }

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

    if (pendingArchiveSessionId != null) {
        AlertDialog(
            modifier = Modifier.testTag(TestTags.SessionListArchiveDialog),
            onDismissRequest = {
                pendingArchiveSessionId = null
                pendingArchiveSessionTitle = ""
            },
            title = { Text("归档会话") },
            text = {
                Text("确认归档“${pendingArchiveSessionTitle.ifBlank { "这条会话" }}”吗？归档后会从当前列表移到“已归档”。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingArchiveSessionId?.let(onArchiveSession)
                        pendingArchiveSessionId = null
                        pendingArchiveSessionTitle = ""
                    },
                    modifier = Modifier.testTag(TestTags.SessionListArchiveDialogConfirmButton),
                ) {
                    Text("归档")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingArchiveSessionId = null
                        pendingArchiveSessionTitle = ""
                    },
                ) {
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (showArchivedSessions) {
                OutlinedButton(
                    onClick = { onShowArchivedSessionsChange(false) },
                    enabled = !isLoading,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(TestTags.SessionListFilterCurrentButton),
                ) {
                    Text("当前")
                }
                FilledTonalButton(
                    onClick = { },
                    enabled = false,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(TestTags.SessionListFilterArchivedButton),
                ) {
                    Text("已归档")
                }
            } else {
                FilledTonalButton(
                    onClick = { },
                    enabled = false,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(TestTags.SessionListFilterCurrentButton),
                ) {
                    Text("当前")
                }
                OutlinedButton(
                    onClick = { onShowArchivedSessionsChange(true) },
                    enabled = !isLoading,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(TestTags.SessionListFilterArchivedButton),
                ) {
                    Text("已归档")
                }
            }
        }
        Button(
            onClick = {
                draftDirectory = currentCwd
                showCreateDialog = true
            },
            enabled = !isLoading && !showArchivedSessions,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.SessionListCreateDraftButton),
        ) {
            if (isLoading) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            } else {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Text(
                    text = if (showArchivedSessions) "归档列表不支持新建草稿" else "新建草稿线程",
                    modifier = Modifier.padding(start = 8.dp),
                )
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
                    EmptySessionState(
                        currentCwd = currentCwd,
                        showArchivedSessions = showArchivedSessions,
                    )
                }
            }

            items(groups, key = { it.cwd }) { group ->
                SessionDirectoryCard(
                    group = group,
                    showArchivedSessions = showArchivedSessions,
                    isLoading = isLoading,
                    onOpenSession = onOpenSession,
                    onCreateDraft = onCreateDraft,
                    onArchiveSession = { session ->
                        pendingArchiveSessionId = session.id
                        pendingArchiveSessionTitle = session.title
                    },
                    onUnarchiveSession = onUnarchiveSession,
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
    showArchivedSessions: Boolean,
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
                text = if (showArchivedSessions) "还没有归档会话" else "还没有会话",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (showArchivedSessions) {
                    "归档后的线程会显示在这里。"
                } else {
                    "可以先用“新建草稿线程”选择目录。默认目录：${currentCwd.ifBlank { "未配置" }}"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SessionDirectoryCard(
    group: SessionDirectoryGroup,
    showArchivedSessions: Boolean,
    isLoading: Boolean,
    onOpenSession: (String) -> Unit,
    onCreateDraft: (String) -> Unit,
    onArchiveSession: (SessionSummary) -> Unit,
    onUnarchiveSession: (String) -> Unit,
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
                if (!showArchivedSessions) {
                    TextButton(
                        onClick = { onCreateDraft(group.cwd) },
                        enabled = !isLoading,
                        modifier = Modifier.testTag(TestTags.SessionListFolderCreatePrefix + group.cwd),
                    ) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                        Text("新建", modifier = Modifier.padding(start = 4.dp))
                    }
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
                            FilledTonalIconButton(
                                onClick = {
                                    if (showArchivedSessions) {
                                        onUnarchiveSession(session.id)
                                    } else {
                                        onArchiveSession(session)
                                    }
                                },
                                enabled = !isLoading,
                                modifier = Modifier.testTag(
                                    if (showArchivedSessions) {
                                        TestTags.SessionListUnarchiveButtonPrefix + session.id
                                    } else {
                                        TestTags.SessionListArchiveButtonPrefix + session.id
                                    },
                                ),
                            ) {
                                Icon(
                                    imageVector = if (showArchivedSessions) Icons.Filled.Unarchive else Icons.Filled.Archive,
                                    contentDescription = if (showArchivedSessions) "恢复归档" else "归档",
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
