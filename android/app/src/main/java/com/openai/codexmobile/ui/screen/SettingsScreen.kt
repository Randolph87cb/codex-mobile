package com.openai.codexmobile.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    paddingValues: PaddingValues,
    items: List<Pair<String, String>>,
    endpointInput: String,
    authTokenInput: String,
    cwdInput: String,
    modelInput: String,
    approvalModeInput: String,
    onEndpointChange: (String) -> Unit,
    onAuthTokenChange: (String) -> Unit,
    onCwdChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onApprovalModeChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
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
                    modifier = Modifier.fillMaxWidth(),
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
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Bridge Token") },
                    visualTransformation = PasswordVisualTransformation(),
                )
                Text(
                    text = "如果 bridge 启用了 token 鉴权，这里填写 Bearer token 的值，不要包含 Bearer 前缀。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = cwdInput,
                    onValueChange = onCwdChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("默认工作目录") },
                )
                Text(
                    text = "新建会话时默认使用这个 Windows 路径。启用 cwd 白名单时，这里必须落在允许目录内。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = modelInput,
                    onValueChange = onModelChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("默认模型") },
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "审批模式",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (approvalModeInput == "manual") {
                            Button(onClick = {}, enabled = false) {
                                Text("手动审批")
                            }
                        } else {
                            OutlinedButton(onClick = { onApprovalModeChange("manual") }) {
                                Text("手动审批")
                            }
                        }
                        if (approvalModeInput == "auto") {
                            Button(onClick = {}, enabled = false) {
                                Text("自动审批")
                            }
                        } else {
                            OutlinedButton(onClick = { onApprovalModeChange("auto") }) {
                                Text("自动审批")
                            }
                        }
                    }
                }
            }
        }
        Button(onClick = onBack) {
            Text("返回")
        }
    }
}
