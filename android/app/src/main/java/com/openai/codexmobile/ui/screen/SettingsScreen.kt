package com.openai.codexmobile.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openai.codexmobile.data.SavedBridgeConnection
import com.openai.codexmobile.ui.TestTags

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    items: List<Pair<String, String>>,
    selectedConnectionName: String,
    savedConnections: List<SavedBridgeConnection>,
    selectedConnectionId: String,
    endpointInput: String,
    authTokenInput: String,
    cwdInput: String,
    modelInput: String,
    approvalModeInput: String,
    reasoningEffortInput: String,
    serviceTierInput: String,
    sandboxModeInput: String,
    diagnosticsLog: String,
    onConnectionNameChange: (String) -> Unit,
    onAddSavedConnection: () -> Unit,
    onSelectSavedConnection: (String) -> Unit,
    onDeleteSavedConnection: (String) -> Unit,
    onEndpointChange: (String) -> Unit,
    onAuthTokenChange: (String) -> Unit,
    onCwdChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onApprovalModeChange: (String) -> Unit,
    onReasoningEffortChange: (String) -> Unit,
    onServiceTierChange: (String) -> Unit,
    onSandboxModeChange: (String) -> Unit,
    onRefreshLogs: () -> Unit,
    onClearLogs: () -> Unit,
    onCopyLogs: (String) -> Unit,
    onBack: () -> Unit,
    onNavigateToConnect: () -> Unit,
    onNavigateToSessions: () -> Unit,
) {
    Scaffold(
        modifier = Modifier
            .testTag(TestTags.SettingsScreen)
            .fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "设置",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(TestTags.SettingsBackButton),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            SettingsBottomBar(
                onNavigateToConnect = onNavigateToConnect,
                onNavigateToSessions = onNavigateToSessions,
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
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                SettingsSectionHeader(text = "Connection Management")
                SettingsCard(modifier = Modifier.testTag(TestTags.SettingsConnectionsCard)) {
                    SavedBridgeSummaryRow(
                        count = savedConnections.size,
                        onAddSavedConnection = onAddSavedConnection,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
                    SettingsTextFieldBlock(
                        label = "Connection Name",
                        value = selectedConnectionName,
                        onValueChange = onConnectionNameChange,
                        testTag = TestTags.SettingsConnectionNameField,
                        icon = Icons.Filled.Router,
                        placeholder = "默认连接",
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
                    SettingsTextFieldBlock(
                        label = "Current Endpoint",
                        value = endpointInput,
                        onValueChange = onEndpointChange,
                        testTag = TestTags.SettingsEndpointField,
                        icon = Icons.Filled.Link,
                        placeholder = "http://10.0.2.2:8787",
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
                    SettingsTextFieldBlock(
                        label = "Access Token",
                        value = authTokenInput,
                        onValueChange = onAuthTokenChange,
                        testTag = TestTags.SettingsAuthTokenField,
                        icon = Icons.Filled.Key,
                        placeholder = "Bridge Token",
                    )
                    if (savedConnections.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            savedConnections.forEach { connection ->
                                SavedConnectionRow(
                                    connection = connection,
                                    selected = connection.id == selectedConnectionId,
                                    canDelete = savedConnections.size > 1,
                                    onSelectSavedConnection = onSelectSavedConnection,
                                    onDeleteSavedConnection = onDeleteSavedConnection,
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingsSectionHeader(text = "Default Preferences")
                SettingsCard {
                    SettingsTextFieldBlock(
                        label = "Default CWD",
                        value = cwdInput,
                        onValueChange = onCwdChange,
                        testTag = TestTags.SettingsCwdField,
                        icon = Icons.Filled.Folder,
                        placeholder = "默认工作目录",
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
                    SettingsTextFieldBlock(
                        label = "Model Selection",
                        value = modelInput,
                        onValueChange = onModelChange,
                        testTag = TestTags.SettingsModelField,
                        icon = Icons.Filled.Memory,
                        placeholder = "gpt-5",
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
                    SettingsSegmentedRow(
                        title = "思考强度",
                        subtitle = "调整模型推理的专注程度",
                        icon = Icons.Filled.Tune,
                        currentValue = reasoningEffortInput,
                        options = listOf(
                            SettingOption("极低", "minimal", TestTags.SettingsReasoningMinimalButton),
                            SettingOption("低", "low", TestTags.SettingsReasoningLowButton),
                            SettingOption("中", "medium", TestTags.SettingsReasoningMediumButton),
                            SettingOption("高", "high", TestTags.SettingsReasoningHighButton),
                            SettingOption("最高", "xhigh", TestTags.SettingsReasoningXHighButton),
                        ),
                        onValueChange = onReasoningEffortChange,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
                    SettingsSegmentedRow(
                        title = "Response Speed",
                        subtitle = "Prioritize latency vs token density",
                        icon = Icons.Filled.Speed,
                        currentValue = serviceTierInput,
                        options = listOf(
                            SettingOption("普通", "default", TestTags.SettingsServiceDefaultButton),
                            SettingOption("快速", "fast", TestTags.SettingsServiceFastButton),
                        ),
                        onValueChange = onServiceTierChange,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
                    SettingsSegmentedRow(
                        title = "审批模式",
                        subtitle = "控制高风险操作的确认方式",
                        icon = Icons.Filled.Security,
                        currentValue = approvalModeInput,
                        options = listOf(
                            SettingOption("手动", "manual", TestTags.SettingsApprovalManualButton),
                            SettingOption("自动", "auto", TestTags.SettingsApprovalAutoButton),
                        ),
                        onValueChange = onApprovalModeChange,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
                    SettingsSegmentedRow(
                        title = "文件权限",
                        subtitle = "限制 Codex 可访问的本地文件范围",
                        icon = Icons.Filled.Folder,
                        currentValue = sandboxModeInput,
                        options = listOf(
                            SettingOption("只读", "read-only", TestTags.SettingsSandboxReadOnlyButton),
                            SettingOption("工作区可写", "workspace-write", TestTags.SettingsSandboxWorkspaceWriteButton),
                            SettingOption("完全访问", "danger-full-access", TestTags.SettingsSandboxDangerButton),
                        ),
                        onValueChange = onSandboxModeChange,
                    )
                }
            }

            if (items.isNotEmpty()) {
                item {
                    SettingsSectionHeader(text = "Current Defaults")
                    SettingsCard {
                        items.forEachIndexed { index, (label, value) ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
                                )
                            }
                            ReadOnlyPreferenceRow(label = label, value = value)
                        }
                    }
                }
            }

            item {
                DiagnosticsSection(
                    diagnosticsLog = diagnosticsLog,
                    onRefreshLogs = onRefreshLogs,
                    onClearLogs = onClearLogs,
                    onCopyLogs = onCopyLogs,
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(bottom = 2.dp),
    )
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SavedBridgeSummaryRow(
    count: Int,
    onAddSavedConnection: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAddSavedConnection)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsIconTile(icon = Icons.Filled.Router)
            Column {
                Text("Saved Bridges", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    text = "$count endpoints configured",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(
            onClick = onAddSavedConnection,
            modifier = Modifier.testTag(TestTags.SettingsConnectionsAddButton),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "新增连接",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SavedConnectionRow(
    connection: SavedBridgeConnection,
    selected: Boolean,
    canDelete: Boolean,
    onSelectSavedConnection: (String) -> Unit,
    onDeleteSavedConnection: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f))
            .testTag(TestTags.SettingsConnectionsItemPrefix + connection.id)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (selected) Color(0xFF22C55E) else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(999.dp),
                ),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = connection.name.ifBlank { "未命名连接" },
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = connection.endpoint.ifBlank { "未配置桥接地址" },
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Button(onClick = {}, enabled = false) {
                Text("当前")
            }
        } else {
            OutlinedButton(
                onClick = { onSelectSavedConnection(connection.id) },
                modifier = Modifier.testTag(TestTags.SettingsConnectionsSelectPrefix + connection.id),
            ) {
                Text("切换")
            }
        }
        IconButton(
            onClick = { onDeleteSavedConnection(connection.id) },
            enabled = canDelete,
            modifier = Modifier.testTag(TestTags.SettingsConnectionsDeletePrefix + connection.id),
        ) {
            Icon(
                imageVector = Icons.Filled.DeleteOutline,
                contentDescription = "删除连接",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsTextFieldBlock(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    testTag: String,
    icon: ImageVector,
    placeholder: String,
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            placeholder = { Text(placeholder) },
            shape = RoundedCornerShape(10.dp),
        )
    }
}

@Composable
private fun ReadOnlyPreferenceRow(label: String, value: String) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class SettingOption(
    val label: String,
    val value: String,
    val testTag: String,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsSegmentedRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    currentValue: String,
    options: List<SettingOption>,
    onValueChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsIconTile(icon = icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FlowRow(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            options.forEach { option ->
                val active = currentValue == option.value
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable(enabled = !active) { onValueChange(option.value) }
                        .testTag(option.testTag)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsIconTile(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun DiagnosticsSection(
    diagnosticsLog: String,
    onRefreshLogs: () -> Unit,
    onClearLogs: () -> Unit,
    onCopyLogs: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "LOCAL DIAGNOSTIC LOGS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            letterSpacing = 0.5.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(
                onClick = onRefreshLogs,
                modifier = Modifier
                    .size(32.dp)
                    .testTag(TestTags.SettingsRefreshLogsButton),
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "刷新日志",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(
                onClick = { onCopyLogs(diagnosticsLog) },
                modifier = Modifier
                    .size(32.dp)
                    .testTag(TestTags.SettingsCopyLogsButton),
            ) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "复制日志",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(
                onClick = onClearLogs,
                modifier = Modifier
                    .size(32.dp)
                    .testTag(TestTags.SettingsClearLogsButton),
            ) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = "清空日志",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .testTag(TestTags.SettingsLogsCard),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(12.dp),
    ) {
        SelectionContainer {
            Box(modifier = Modifier.padding(12.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (diagnosticsLog.isBlank()) {
                        LogLineText("INFO", "暂无本地诊断日志")
                    } else {
                        diagnosticsLog
                            .lineSequence()
                            .filter { it.isNotBlank() }
                            .forEach { line ->
                                val level = when {
                                    line.contains("ERROR", ignoreCase = true) -> "ERROR"
                                    line.contains("WARN", ignoreCase = true) -> "WARN"
                                    line.contains("DEBUG", ignoreCase = true) -> "DEBUG"
                                    else -> "INFO"
                                }
                                LogLineText(level = level, message = line)
                            }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "_",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFBAC8F7),
                        )
                    }
                }
            }
        }
    }
    Text(
        text = "Client Build: debug",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun LogLineText(level: String, message: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = level,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = when (level) {
                "ERROR" -> Color(0xFFFF4D4D)
                "DEBUG" -> Color(0xFFBAC8F7)
                "WARN" -> Color(0xFFFFDEA5)
                else -> Color(0xFF4ADE80)
            },
        )
        Text(
            text = message,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = Color(0xFFBAC8F7),
        )
    }
}

@Composable
private fun SettingsBottomBar(
    onNavigateToConnect: () -> Unit,
    onNavigateToSessions: () -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = false,
            onClick = onNavigateToConnect,
            icon = {
                Icon(
                    imageVector = Icons.Filled.Link,
                    contentDescription = "连接",
                )
            },
            label = { Text("连接") },
        )
        NavigationBarItem(
            selected = false,
            onClick = onNavigateToSessions,
            icon = {
                Icon(
                    imageVector = Icons.Filled.Forum,
                    contentDescription = "线程",
                )
            },
            label = { Text("线程") },
        )
        NavigationBarItem(
            selected = true,
            onClick = {},
            icon = {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "设置",
                )
            },
            label = { Text("设置", fontWeight = FontWeight.Bold) },
        )
    }
}
