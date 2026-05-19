package com.openai.codexmobile.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.openai.codexmobile.ui.TestTags

@Composable
fun SettingsScreen(
    paddingValues: PaddingValues,
    items: List<Pair<String, String>>,
    endpointInput: String,
    authTokenInput: String,
    cwdInput: String,
    modelInput: String,
    approvalModeInput: String,
    reasoningEffortInput: String,
    serviceTierInput: String,
    diagnosticsLog: String,
    onEndpointChange: (String) -> Unit,
    onAuthTokenChange: (String) -> Unit,
    onCwdChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onApprovalModeChange: (String) -> Unit,
    onReasoningEffortChange: (String) -> Unit,
    onServiceTierChange: (String) -> Unit,
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
            .padding(24.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items.forEach { (label, value) ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = label, style = MaterialTheme.typography.labelLarge)
                        Text(text = value, style = MaterialTheme.typography.bodyLarge)
                    }
                }
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
                        SettingOption("手动审批", "manual", TestTags.SettingsApprovalManualButton),
                        SettingOption("自动审批", "auto", TestTags.SettingsApprovalAutoButton),
                    ),
                    onValueChange = onApprovalModeChange,
                )
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
                    OutlinedButton(
                        onClick = onRefreshLogs,
                        modifier = Modifier.testTag(TestTags.SettingsRefreshLogsButton),
                    ) {
                        Text("刷新日志")
                    }
                    OutlinedButton(
                        onClick = onClearLogs,
                        modifier = Modifier.testTag(TestTags.SettingsClearLogsButton),
                    ) {
                        Text("清空日志")
                    }
                }
                OutlinedButton(
                    onClick = { onCopyLogs(diagnosticsLog) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.SettingsCopyLogsButton),
                ) {
                    Text("复制日志")
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.SettingsLogsCard),
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
        Button(
            onClick = onBack,
            modifier = Modifier.testTag(TestTags.SettingsBackButton),
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
