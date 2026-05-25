package com.openai.codexmobile.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openai.codexmobile.AccountQuotaUiState
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    sessions: List<SessionSummary>,
    showArchivedSessions: Boolean,
    connectionState: BridgeConnectionState,
    accountQuota: AccountQuotaUiState,
    currentCwd: String,
    isLoading: Boolean,
    onOpenSession: (String) -> Unit,
    onShowArchivedSessionsChange: (Boolean) -> Unit,
    onArchiveSession: (String) -> Unit,
    onUnarchiveSession: (String) -> Unit,
    onCreateDraft: (String) -> Unit,
    onNavigateToConnect: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val groups = groupSessionsByDirectory(sessions)
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var draftDirectory by rememberSaveable(currentCwd) { mutableStateOf(currentCwd) }
    var pendingArchiveSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingArchiveSessionTitle by rememberSaveable { mutableStateOf("") }
    var collapsedFolders by rememberSaveable(showArchivedSessions) { mutableStateOf(setOf<String>()) }

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

    Scaffold(
        modifier = Modifier
            .testTag(TestTags.SessionListScreen)
            .fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "线程",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                actions = {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag(TestTags.SessionListOpenSettingsButton),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "更多",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            if (!showArchivedSessions) {
                FloatingActionButton(
                    onClick = {
                        draftDirectory = currentCwd
                        showCreateDialog = true
                    },
                    modifier = Modifier
                        .testTag(TestTags.SessionListCreateDraftButton),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = CircleShape,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "新建草稿线程",
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        },
        bottomBar = {
            SessionListBottomBar(
                onNavigateToConnect = onNavigateToConnect,
                onOpenSettings = onOpenSettings,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SessionListTab(
                        text = "现有",
                        count = sessions.size,
                        selected = !showArchivedSessions,
                        modifier = Modifier.testTag(TestTags.SessionListFilterCurrentButton),
                        onClick = { onShowArchivedSessionsChange(false) },
                    )
                    SessionListTab(
                        text = "归档线程",
                        count = null,
                        selected = showArchivedSessions,
                        modifier = Modifier.testTag(TestTags.SessionListFilterArchivedButton),
                        onClick = { onShowArchivedSessionsChange(true) },
                    )
                }
            }
            item {
                ConnectionSummaryStrip(connectionState = connectionState)
            }
            item {
                AccountQuotaCard(accountQuota = accountQuota)
            }

            if (groups.isEmpty()) {
                item {
                    EmptySessionState(
                        currentCwd = currentCwd,
                        showArchivedSessions = showArchivedSessions,
                    )
                }
            }

            items(groups, key = { it.cwd }) { group ->
                val isExpanded = group.cwd !in collapsedFolders
                SessionDirectorySection(
                    group = group,
                    showArchivedSessions = showArchivedSessions,
                    isLoading = isLoading,
                    isExpanded = isExpanded,
                    onToggleExpanded = {
                        collapsedFolders = if (isExpanded) {
                            collapsedFolders + group.cwd
                        } else {
                            collapsedFolders - group.cwd
                        }
                    },
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
    }
}

@Composable
private fun SessionListTab(
    text: String,
    count: Int?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = text,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp,
            )
            count?.let {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = it.toString(),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 2.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun ConnectionSummaryStrip(
    connectionState: BridgeConnectionState,
) {
    val connected = connectionState is BridgeConnectionState.Connected
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (connected) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = if (connected) Color(0xFF16A34A) else MaterialTheme.colorScheme.error,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = if (connected) "桥接在线" else "桥接服务未连接",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = when (connectionState) {
                        is BridgeConnectionState.Connected -> connectionState.endpoint
                        BridgeConnectionState.Disconnected -> "返回连接页重新建立 bridge 连接。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SessionListBottomBar(
    onNavigateToConnect: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = false,
            onClick = onNavigateToConnect,
            modifier = Modifier.testTag(TestTags.SessionListDisconnectButton),
            icon = {
                Icon(
                    imageVector = Icons.Filled.Link,
                    contentDescription = "连接",
                )
            },
            label = { Text("连接") },
        )
        NavigationBarItem(
            selected = true,
            onClick = {},
            icon = {
                Icon(
                    imageVector = Icons.Filled.Forum,
                    contentDescription = "线程",
                )
            },
            label = { Text("线程", fontWeight = FontWeight.Bold) },
        )
        NavigationBarItem(
            selected = false,
            onClick = onOpenSettings,
            icon = {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "设置",
                )
            },
            label = { Text("设置") },
        )
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
private fun SessionDirectorySection(
    group: SessionDirectoryGroup,
    showArchivedSessions: Boolean,
    isLoading: Boolean,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onOpenSession: (String) -> Unit,
    onCreateDraft: (String) -> Unit,
    onArchiveSession: (SessionSummary) -> Unit,
    onUnarchiveSession: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.SessionListFolderPrefix + group.cwd),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpanded() }
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (showArchivedSessions) Icons.Filled.Archive else Icons.Filled.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = buildFolderLabel(group.cwd),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = group.sessions.size.toString(),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!showArchivedSessions) {
                IconButton(
                    onClick = { onCreateDraft(group.cwd) },
                    enabled = !isLoading,
                    modifier = Modifier
                        .testTag(TestTags.SessionListFolderCreatePrefix + group.cwd)
                        .size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "新建草稿线程",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Icon(
                imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }

        if (isExpanded) {
            group.sessions.forEach { session ->
                SessionItemCard(
                    session = session,
                    showArchivedSessions = showArchivedSessions,
                    isLoading = isLoading,
                    onOpenSession = onOpenSession,
                    onArchiveSession = onArchiveSession,
                    onUnarchiveSession = onUnarchiveSession,
                )
            }
        }
    }
}

@Composable
private fun SessionItemCard(
    session: SessionSummary,
    showArchivedSessions: Boolean,
    isLoading: Boolean,
    onOpenSession: (String) -> Unit,
    onArchiveSession: (SessionSummary) -> Unit,
    onUnarchiveSession: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.SessionListItemPrefix + session.id)
            .clickable { onOpenSession(session.id) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = sessionIcon(session),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    SessionStatusBadge(status = session.status)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "ID: ${session.id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = session.lastUpdated,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(
                onClick = {
                    if (showArchivedSessions) {
                        onUnarchiveSession(session.id)
                    } else {
                        onArchiveSession(session)
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .size(28.dp)
                    .testTag(
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
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f),
                )
            }
        }
    }
}

@Composable
private fun SessionStatusBadge(status: String) {
    val (label, containerColor, contentColor) = when (status) {
        "running" -> Triple(
            "进行中",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        "awaiting_approval" -> Triple(
            "待审批",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        "error" -> Triple(
            "出错",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
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
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
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

private fun sessionIcon(session: SessionSummary): ImageVector {
    val text = "${session.title} ${session.cwd}".lowercase()
    return when {
        "k8s" in text || "cluster" in text || "node" in text -> Icons.Filled.Dns
        "cloud" in text || "bridge" in text || "infra" in text -> Icons.Filled.Cloud
        "code" in text || "repo" in text || "workspace" in text -> Icons.Filled.Code
        else -> Icons.Filled.Terminal
    }
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
