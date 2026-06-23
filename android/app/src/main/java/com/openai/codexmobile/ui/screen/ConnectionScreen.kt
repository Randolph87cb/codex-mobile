package com.openai.codexmobile.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openai.codexmobile.data.SavedBridgeConnection
import com.openai.codexmobile.model.BridgeConnectionState
import com.openai.codexmobile.ui.TestTags

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(
    currentConnectionName: String,
    savedConnections: List<SavedBridgeConnection>,
    selectedConnectionId: String,
    endpoint: String,
    authToken: String,
    connectionState: BridgeConnectionState,
    isLoading: Boolean,
    onConnectionNameChange: (String) -> Unit,
    onEndpointChange: (String) -> Unit,
    onAuthTokenChange: (String) -> Unit,
    onAddSavedConnection: () -> Unit,
    onSelectSavedConnection: (String) -> Unit,
    onDeleteSavedConnection: (String) -> Unit,
    onConnect: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSessions: () -> Unit = {},
) {
    val connected = connectionState is BridgeConnectionState.Connected
    val resolvedName = currentConnectionName.ifBlank { "默认连接" }

    Scaffold(
        modifier = Modifier
            .testTag(TestTags.ConnectionScreen)
            .fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Codex Bridge",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag(TestTags.ConnectionOpenSettingsButton),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "打开设置",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            ConnectionBottomArea(
                connected = connected,
                isLoading = isLoading,
                onConnect = onConnect,
                onAddSavedConnection = onAddSavedConnection,
                onOpenSettings = onOpenSettings,
                onOpenSessions = onOpenSessions,
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
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "连接",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            item {
                BridgeConnectionNodeCard(
                    name = resolvedName,
                    endpoint = endpoint,
                    connected = connected,
                    onOpenSettings = onOpenSettings,
                )
            }

            item {
                CurrentConnectionEditor(
                    name = currentConnectionName,
                    endpoint = endpoint,
                    authToken = authToken,
                    onNameChange = onConnectionNameChange,
                    onEndpointChange = onEndpointChange,
                    onAuthTokenChange = onAuthTokenChange,
                )
            }

            item {
                SavedConnectionsSection(
                    savedConnections = savedConnections,
                    selectedConnectionId = selectedConnectionId,
                    onAddSavedConnection = onAddSavedConnection,
                    onSelectSavedConnection = onSelectSavedConnection,
                    onDeleteSavedConnection = onDeleteSavedConnection,
                )
            }
        }
    }
}

@Composable
private fun BridgeConnectionNodeCard(
    name: String,
    endpoint: String,
    connected: Boolean,
    onOpenSettings: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onOpenSettings)
            .padding(20.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(96.dp)
                .background(Color.White.copy(alpha = 0.06f), shape = CircleShape),
        )
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = if (connected) Color(0xFF4ADE80) else Color(0xFFFF6B6B),
                            shape = CircleShape,
                        ),
                )
                Text(
                    text = if (connected) "已就绪" else "断开连接",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ConnectionMetaRow(
                    icon = Icons.Filled.Person,
                    text = name,
                )
                ConnectionMetaRow(
                    icon = Icons.Filled.Lan,
                    text = endpoint.ifBlank { "未配置桥接端点" },
                )
                ConnectionMetaRow(icon = Icons.Filled.Key, text = "••••••••••••••••", dimmed = true)
            }
        }
    }
}

@Composable
private fun ConnectionMetaRow(
    icon: ImageVector,
    text: String,
    dimmed: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = Color.White.copy(alpha = if (dimmed) 0.62f else 0.84f),
        )
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = if (dimmed) 0.68f else 0.92f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CurrentConnectionEditor(
    name: String,
    endpoint: String,
    authToken: String,
    onNameChange: (String) -> Unit,
    onEndpointChange: (String) -> Unit,
    onAuthTokenChange: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "当前连接",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.ConnectionNameField),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Router, contentDescription = null)
                },
                placeholder = { Text("连接名称") },
            )
            OutlinedTextField(
                value = endpoint,
                onValueChange = onEndpointChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.ConnectionEndpointField),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Link,
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    if (endpoint.isNotBlank()) {
                        IconButton(onClick = { onEndpointChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "清空地址",
                            )
                        }
                    }
                },
                placeholder = { Text("http://10.0.2.2:8787") },
            )
            OutlinedTextField(
                value = authToken,
                onValueChange = onAuthTokenChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.ConnectionAuthTokenField),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Key, contentDescription = null)
                },
                trailingIcon = {
                    if (authToken.isNotBlank()) {
                        IconButton(onClick = { onAuthTokenChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "清空令牌",
                            )
                        }
                    }
                },
                placeholder = { Text("Bridge Token") },
            )
            Text(
                text = "模拟器建议使用 10.0.2.2，真机请填写 Windows 电脑的局域网地址。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SavedConnectionsSection(
    savedConnections: List<SavedBridgeConnection>,
    selectedConnectionId: String,
    onAddSavedConnection: () -> Unit,
    onSelectSavedConnection: (String) -> Unit,
    onDeleteSavedConnection: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.ConnectionSavedConnectionsCard),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "已保存连接",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${savedConnections.size} 个 bridge 端点",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = onAddSavedConnection,
                    modifier = Modifier.testTag(TestTags.ConnectionSavedAddButton),
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("新增")
                }
            }

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
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .testTag(TestTags.ConnectionSavedItemPrefix + connection.id)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(
                    color = if (selected) Color(0xFF22C55E) else MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
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
                modifier = Modifier.testTag(TestTags.ConnectionSavedSelectPrefix + connection.id),
            ) {
                Text("切换")
            }
        }
        IconButton(
            onClick = { onDeleteSavedConnection(connection.id) },
            enabled = canDelete,
            modifier = Modifier.testTag(TestTags.ConnectionSavedDeletePrefix + connection.id),
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
private fun ConnectionBottomArea(
    connected: Boolean,
    isLoading: Boolean,
    onConnect: () -> Unit,
    onAddSavedConnection: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSessions: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onConnect,
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag(TestTags.ConnectionConnectButton),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "正在连接中...",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White,
                )
            } else {
                Icon(
                    imageVector = if (connected) Icons.Filled.CloudDone else Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = Color.White,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (connected) "重新连接" else "尝试连接",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White,
                )
            }
        }

        OutlinedButton(
            onClick = onAddSavedConnection,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag(TestTags.ConnectionSavedAddBottomButton),
            shape = RoundedCornerShape(25.dp),
        ) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "新增连接",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
        }

        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            NavigationBarItem(
                selected = true,
                onClick = {},
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Link,
                        contentDescription = "连接",
                    )
                },
                label = { Text("连接", fontWeight = FontWeight.Bold) },
            )
            NavigationBarItem(
                selected = false,
                onClick = onOpenSessions,
                enabled = connected,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Forum,
                        contentDescription = "线程",
                    )
                },
                label = { Text("线程") },
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
}
