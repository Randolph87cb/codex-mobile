package com.openai.codexmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.openai.codexmobile.data.SavedBridgeConnection
import com.openai.codexmobile.model.BridgeConnectionState
import com.openai.codexmobile.model.SessionSummary
import com.openai.codexmobile.ui.screen.ConnectionScreen
import com.openai.codexmobile.ui.screen.SessionDraftScreen
import com.openai.codexmobile.ui.screen.SessionListScreen
import com.openai.codexmobile.ui.screen.SettingsScreen
import com.openai.codexmobile.ui.theme.CodexMobileTheme

class PrimaryScreensShowcaseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screen = intent.getStringExtra("screen") ?: "connection"

        setContent {
            CodexMobileTheme(darkTheme = false) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                ) { paddingValues ->
                    when (screen) {
                        "sessions" -> SessionsShowcase(paddingValues = paddingValues)
                        "draft" -> DraftShowcase(paddingValues = paddingValues)
                        "settings" -> SettingsShowcase(paddingValues = paddingValues)
                        else -> ConnectionShowcase(paddingValues = paddingValues)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionShowcase(
    paddingValues: androidx.compose.foundation.layout.PaddingValues,
) {
    ConnectionScreen(
        paddingValues = paddingValues,
        currentConnectionName = "办公室桥接",
        endpoint = "ws://10.0.0.12:8080",
        connectionState = BridgeConnectionState.Connected(
            endpoint = "ws://10.0.0.12:8080",
            service = "Codex Bridge",
            runnerMode = "LAN",
        ),
        isLoading = false,
        onEndpointChange = {},
        onConnect = {},
        onOpenSettings = {},
    )
}

@Composable
private fun SessionsShowcase(
    paddingValues: androidx.compose.foundation.layout.PaddingValues,
) {
    SessionListScreen(
        paddingValues = paddingValues,
        sessions = showcaseSessions(),
        showArchivedSessions = false,
        connectionState = BridgeConnectionState.Connected(
            endpoint = "ws://10.0.0.12:8080",
            service = "Codex Bridge",
            runnerMode = "LAN",
        ),
        currentCwd = "D:\\workspace\\codex-mobile",
        isLoading = false,
        onOpenSession = {},
        onShowArchivedSessionsChange = {},
        onArchiveSession = {},
        onUnarchiveSession = {},
        onCreateDraft = {},
        onNavigateToConnect = {},
        onOpenSettings = {},
    )
}

@Composable
private fun DraftShowcase(
    paddingValues: androidx.compose.foundation.layout.PaddingValues,
) {
    SessionDraftScreen(
        paddingValues = paddingValues,
        draftSession = DraftSessionUiState(
            cwd = "D:\\workspace\\codex-mobile",
            model = "gpt-5.5",
            approvalMode = "auto",
            reasoningEffort = "medium",
            serviceTier = "default",
            sandboxMode = "danger-full-access",
        ),
        draftMessage = "检查 Android 端页面迁移后的导航、附件和模型设置交互。",
        pendingImageAttachments = listOf(
            PendingImageAttachmentUiState(
                localId = "showcase-image",
                displayName = "ui-reference.png",
                mimeType = "image/png",
                previewSource = "",
                uploadState = PendingImageUploadState.Uploaded,
                stagedPath = "D:\\workspace\\codex-mobile\\.tmp\\ui-reference.png",
            ),
        ),
        isLoading = false,
        onDraftMessageChange = {},
        onPickImage = {},
        onRemovePendingImageAttachment = {},
        onRetryPendingImageAttachment = {},
        onSend = {},
        onUpdateCwd = {},
        onUpdateModel = {},
        onUpdateReasoningEffort = {},
        onUpdateServiceTier = {},
        onUpdateSandboxMode = {},
        onOpenSettings = {},
        onBack = {},
    )
}

@Composable
private fun SettingsShowcase(
    paddingValues: androidx.compose.foundation.layout.PaddingValues,
) {
    val connections = listOf(
        SavedBridgeConnection(
            id = "office",
            name = "办公室桥接",
            endpoint = "ws://10.0.0.12:8080",
            authToken = "showcase-token",
        ),
        SavedBridgeConnection(
            id = "tailscale",
            name = "Tailscale",
            endpoint = "ws://100.64.1.10:8080",
            authToken = "",
        ),
    )
    SettingsScreen(
        paddingValues = paddingValues,
        items = listOf(
            "桥接模式" to "真实桥接",
            "当前连接" to "办公室桥接",
            "默认工作目录" to "D:\\workspace\\codex-mobile",
            "默认模型" to "gpt-5.5",
            "推理强度" to "中",
            "速度档位" to "默认",
            "文件权限" to "完全访问",
        ),
        selectedConnectionName = "办公室桥接",
        savedConnections = connections,
        selectedConnectionId = "office",
        endpointInput = "ws://10.0.0.12:8080",
        authTokenInput = "showcase-token",
        cwdInput = "D:\\workspace\\codex-mobile",
        modelInput = "gpt-5.5",
        approvalModeInput = "auto",
        reasoningEffortInput = "medium",
        serviceTierInput = "default",
        sandboxModeInput = "danger-full-access",
        diagnosticsLog = """
            [10:11:04] INFO  bridge connected: ws://10.0.0.12:8080
            [10:12:39] INFO  loaded 5 sessions from bridge
            [10:14:03] DEBUG settings showcase rendered
        """.trimIndent(),
        onConnectionNameChange = {},
        onAddSavedConnection = {},
        onSelectSavedConnection = {},
        onDeleteSavedConnection = {},
        onEndpointChange = {},
        onAuthTokenChange = {},
        onCwdChange = {},
        onModelChange = {},
        onApprovalModeChange = {},
        onReasoningEffortChange = {},
        onServiceTierChange = {},
        onSandboxModeChange = {},
        onRefreshLogs = {},
        onClearLogs = {},
        onCopyLogs = {},
        onBack = {},
        onNavigateToConnect = {},
        onNavigateToSessions = {},
    )
}

private fun showcaseSessions(): List<SessionSummary> {
    return listOf(
        SessionSummary(
            id = "a1b2c3d4",
            title = "支付系统改造",
            subtitle = "会话 ID: a1b2c3d4 • 在线",
            lastUpdated = "10:24",
            cwd = "D:\\workspace\\client-project",
            model = "gpt-5.5",
            approvalMode = "auto",
            reasoningEffort = "medium",
            serviceTier = "default",
            sandboxMode = "danger-full-access",
            status = "running",
        ),
        SessionSummary(
            id = "e5f6g7h8",
            title = "用户中心重构",
            subtitle = "会话 ID: e5f6g7h8 • 空闲",
            lastUpdated = "昨天 18:32",
            cwd = "D:\\workspace\\client-project",
            model = "gpt-5.5",
            approvalMode = "auto",
            reasoningEffort = "high",
            serviceTier = "default",
            sandboxMode = "danger-full-access",
            status = "idle",
        ),
        SessionSummary(
            id = "i9j0k1l2",
            title = "数据迁移脚本",
            subtitle = "会话 ID: i9j0k1l2 • 空闲",
            lastUpdated = "05-16 09:11",
            cwd = "D:\\workspace\\client-project",
            model = "gpt-5.5",
            approvalMode = "auto",
            reasoningEffort = "medium",
            serviceTier = "default",
            sandboxMode = "danger-full-access",
            status = "idle",
        ),
        SessionSummary(
            id = "m3n4o5p6",
            title = "K8s 故障排查",
            subtitle = "会话 ID: m3n4o5p6 • 在线",
            lastUpdated = "10:18",
            cwd = "D:\\workspace\\infra",
            model = "gpt-5.5",
            approvalMode = "auto",
            reasoningEffort = "medium",
            serviceTier = "default",
            sandboxMode = "danger-full-access",
            status = "running",
        ),
        SessionSummary(
            id = "q7r8s9t0",
            title = "日志分析助手",
            subtitle = "会话 ID: q7r8s9t0 • 空闲",
            lastUpdated = "昨天 17:05",
            cwd = "D:\\workspace\\infra",
            model = "gpt-5.5",
            approvalMode = "auto",
            reasoningEffort = "medium",
            serviceTier = "default",
            sandboxMode = "danger-full-access",
            status = "idle",
        ),
    )
}
