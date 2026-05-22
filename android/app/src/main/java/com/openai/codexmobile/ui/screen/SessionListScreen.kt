package com.openai.codexmobile.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "会话",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = when (connectionState) {
                        is BridgeConnectionState.Connected -> "桥接在线 • ${connectionState.endpoint}"
                        BridgeConnectionState.Disconnected -> "桥接服务未连接"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (showArchivedSessions) "已归档会话" else "全部会话",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = sessions.size.toString(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            if (showArchivedSessions) {
                OutlinedButton(
                    onClick = { onShowArchivedSessionsChange(false) },
                    enabled = !isLoading,
                    modifier = Modifier.testTag(TestTags.SessionListFilterCurrentButton),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text("当前")
                }
                FilledTonalButton(
                    onClick = { },
                    enabled = false,
                    modifier = Modifier.testTag(TestTags.SessionListFilterArchivedButton),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text("已归档")
                }
            } else {
                FilledTonalButton(
                    onClick = { },
                    enabled = false,
                    modifier = Modifier.testTag(TestTags.SessionListFilterCurrentButton),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text("当前")
                }
                OutlinedButton(
                    onClick = { onShowArchivedSessionsChange(true) },
                    enabled = !isLoading,
                    modifier = Modifier.testTag(TestTags.SessionListFilterArchivedButton),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text("已归档")
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(bottom = 96.dp),
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

            if (!showArchivedSessions) {
                FloatingActionButton(
                    onClick = {
                        draftDirectory = currentCwd
                        showCreateDialog = true
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .testTag(TestTags.SessionListCreateDraftButton),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = "新建草稿线程")
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onDisconnect,
            modifier = Modifier
                .align(Alignment.End)
                .testTag(TestTags.SessionListDisconnectButton),
            shape = RoundedCornerShape(999.dp),
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
        shape = RoundedCornerShape(22.dp),
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
                    "可以先用右下角按钮新建草稿线程。默认目录：${currentCwd.ifBlank { "未配置" }}"
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.SessionListFolderPrefix + group.cwd),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    modifier = Modifier.padding(9.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildFolderLabel(group.cwd),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = group.sessions.size.toString(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (!showArchivedSessions) {
                TextButton(
                    onClick = { onCreateDraft(group.cwd) },
                    enabled = !isLoading,
                    modifier = Modifier.testTag(TestTags.SessionListFolderCreatePrefix + group.cwd),
                ) {
                    Text("新建")
                }
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        group.sessions.forEach { session ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.SessionListItemPrefix + session.id)
                    .clickable { onOpenSession(session.id) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Code,
                            contentDescription = null,
                            modifier = Modifier.padding(10.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = session.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "会话 ID: ${session.id}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SessionStatusBadge(status = session.status)
                        Text(
                            text = session.lastUpdated,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
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
                                    imageVector = if (showArchivedSessions) {
                                        Icons.Filled.Unarchive
                                    } else {
                                        Icons.Filled.Archive
                                    },
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

@Composable
private fun SessionStatusBadge(status: String) {
    val (label, containerColor, contentColor) = when (status) {
        "running" -> Triple(
            "在线",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        "error" -> Triple(
            "异常",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
        "draft" -> Triple(
            "草稿",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> Triple(
            "空闲",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
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
