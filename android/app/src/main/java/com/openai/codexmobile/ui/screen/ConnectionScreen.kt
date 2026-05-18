package com.openai.codexmobile.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.openai.codexmobile.model.BridgeConnectionState
import com.openai.codexmobile.ui.TestTags

@Composable
fun ConnectionScreen(
    paddingValues: PaddingValues,
    endpoint: String,
    connectionState: BridgeConnectionState,
    isLoading: Boolean,
    onEndpointChange: (String) -> Unit,
    onConnect: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .testTag(TestTags.ConnectionScreen)
            .fillMaxSize()
            .padding(paddingValues)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Codex 移动端桥接客户端",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = when (connectionState) {
                is BridgeConnectionState.Connected -> "已连接到 ${connectionState.endpoint}"
                BridgeConnectionState.Disconnected -> "当前没有活动连接"
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = onEndpointChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.ConnectionEndpointField),
                    label = { Text("桥接地址") },
                    singleLine = true,
                )
                Text(
                    text = "模拟器请使用 10.0.2.2:8787；真机请填写 Windows 电脑的局域网地址。设置页会保存默认连接参数。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = onConnect,
                    enabled = !isLoading,
                    modifier = Modifier.testTag(TestTags.ConnectionConnectButton),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    } else {
                        Text("连接桥接服务")
                    }
                }
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag(TestTags.ConnectionOpenSettingsButton),
                ) {
                    Text("打开设置")
                }
            }
        }
    }
}
