package com.openai.codexmobile.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openai.codexmobile.model.BridgeConnectionState
import com.openai.codexmobile.ui.TestTags

@Composable
fun ConnectionScreen(
    paddingValues: PaddingValues,
    currentConnectionName: String,
    endpoint: String,
    connectionState: BridgeConnectionState,
    isLoading: Boolean,
    onEndpointChange: (String) -> Unit,
    onConnect: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val connected = connectionState is BridgeConnectionState.Connected

    Column(
        modifier = Modifier
            .testTag(TestTags.ConnectionScreen)
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
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
                    text = "Codex 移动端",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "连接你的 Windows bridge",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag(TestTags.ConnectionOpenSettingsButton),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "打开设置",
                )
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (connected) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = if (connected) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                            contentDescription = null,
                            tint = if (connected) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            text = when (connectionState) {
                                is BridgeConnectionState.Connected -> "已连接到 ${connectionState.endpoint}"
                                BridgeConnectionState.Disconnected -> "当前没有活动连接"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Text(
                    text = "当前连接配置：${currentConnectionName.ifBlank { "默认连接" }}",
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = onEndpointChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.ConnectionEndpointField),
                    label = { Text("桥接地址") },
                    supportingText = {
                        Text("模拟器建议使用 10.0.2.2:8787；真机请填写 Windows 电脑的局域网地址。")
                    },
                    singleLine = true,
                )
                Text(
                    text = "设置页会保存默认连接参数，也可以切换已保存连接。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onConnect,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.ConnectionConnectButton),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    } else {
                        Text("连接桥接服务")
                    }
                }
            }
        }
    }
}
