package com.openai.codexmobile.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.openai.codexmobile.data.SavedBridgeConnection
import com.openai.codexmobile.ui.TestTags

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    paddingValues: PaddingValues,
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
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .testTag(TestTags.SettingsScreen)
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "管理连接、默认行为和本地诊断信息",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.SettingsConnectionsCard),
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
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "已保存连接",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "常用 bridge 地址和令牌在这里切换。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalIconButton(
                        onClick = onAddSavedConnection,
                        modifier = Modifier.testTag(TestTags.SettingsConnectionsAddButton),
                    ) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = "新增连接")
                    }
                }
                savedConnections.forEach { connection ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TestTags.SettingsConnectionsItemPrefix + connection.id),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = connection.name.ifBlank { "未命名连接" },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = connection.endpoint.ifBlank { "未配置桥接地址" },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = if (connection.authToken.isBlank()) "令牌：未配置" else "令牌：已配置",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (connection.id == selectedConnectionId) {
                                    Button(onClick = {}, enabled = false) {
                                        Text("当前使用")
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { onSelectSavedConnection(connection.id) },
                                        modifier = Modifier.testTag(
                                            TestTags.SettingsConnectionsSelectPrefix + connection.id,
                                        ),
                                    ) {
                                        Text("设为当前")
                                    }
                                }
                                OutlinedButton(
                                    onClick = { onDeleteSavedConnection(connection.id) },
                                    enabled = savedConnections.size > 1,
                                    modifier = Modifier.testTag(
                                        TestTags.SettingsConnectionsDeletePrefix + connection.id,
                                    ),
                                ) {
                                    Text("删除")
                                }
                            }
                        }
                    }
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "默认参数",
                    style = MaterialTheme.typography.titleMedium,
                )
                items.forEach { (label, value) ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(text = label, style = MaterialTheme.typography.labelLarge)
                            Text(text = value, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                OutlinedTextField(
                    value = selectedConnectionName,
                    onValueChange = onConnectionNameChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.SettingsConnectionNameField),
                    singleLine = true,
                    label = { Text("当前连接名称") },
                )
                OutlinedTextField(
                    value = endpointInput,
                    onValueChange = onEndpointChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.SettingsEndpointField),
                    singleLine = true,
                    label = { Text("桥接地址") },
                )
                Text(
                    text = "模拟器建议使用 http://10.0.2.2:8787；真机请填写 Windows 电脑的局域网地址。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = authTokenInput,
                    onValueChange = onAuthTokenChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.SettingsAuthTokenField),
                    singleLine = true,
                    label = { Text("Bridge Token") },
                )
                OutlinedTextField(
                    value = cwdInput,
                    onValueChange = onCwdChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.SettingsCwdField),
                    singleLine = true,
                    label = { Text("默认工作目录") },
                )
                OutlinedTextField(
                    value = modelInput,
                    onValueChange = onModelChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.SettingsModelField),
                    singleLine = true,
                    label = { Text("默认模型") },
                )
                SettingButtonRow(
                    title = "推理强度",
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
                SettingButtonRow(
                    title = "速度",
                    currentValue = serviceTierInput,
                    options = listOf(
                        SettingOption("普通", "default", TestTags.SettingsServiceDefaultButton),
                        SettingOption("快速", "fast", TestTags.SettingsServiceFastButton),
                    ),
                    onValueChange = onServiceTierChange,
                )
                SettingButtonRow(
                    title = "审批模式",
                    currentValue = approvalModeInput,
                    options = listOf(
                        SettingOption("手动", "manual", TestTags.SettingsApprovalManualButton),
                        SettingOption("自动", "auto", TestTags.SettingsApprovalAutoButton),
                    ),
                    onValueChange = onApprovalModeChange,
                )
                SettingButtonRow(
                    title = "文件权限",
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
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.SettingsLogsCard),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "应用日志",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "保留最近一段本地日志，便于真机排查连接、会话和实时流问题。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalIconButton(
                        onClick = onRefreshLogs,
                        modifier = Modifier.testTag(TestTags.SettingsRefreshLogsButton),
                    ) {
                        Icon(imageVector = Icons.Filled.Refresh, contentDescription = "刷新日志")
                    }
                    FilledTonalIconButton(
                        onClick = onClearLogs,
                        modifier = Modifier.testTag(TestTags.SettingsClearLogsButton),
                    ) {
                        Icon(imageVector = Icons.Filled.Delete, contentDescription = "清空日志")
                    }
                    FilledTonalIconButton(
                        onClick = { onCopyLogs(diagnosticsLog) },
                        modifier = Modifier.testTag(TestTags.SettingsCopyLogsButton),
                    ) {
                        Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = "复制日志")
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    SelectionContainer {
                        Text(
                            text = diagnosticsLog,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.SettingsBackButton),
        ) {
            Text("返回")
        }
    }
}

private data class SettingOption(
    val label: String,
    val value: String,
    val testTag: String,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingButtonRow(
    title: String,
    currentValue: String,
    options: List<SettingOption>,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            options.forEach { option ->
                if (currentValue == option.value) {
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.testTag(option.testTag),
                    ) {
                        Text(option.label)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onValueChange(option.value) },
                        modifier = Modifier.testTag(option.testTag),
                    ) {
                        Text(option.label)
                    }
                }
            }
        }
    }
}
