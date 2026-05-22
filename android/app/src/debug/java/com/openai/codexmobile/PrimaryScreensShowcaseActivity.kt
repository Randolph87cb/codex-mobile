package com.openai.codexmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.openai.codexmobile.model.BridgeConnectionState
import com.openai.codexmobile.model.SessionSummary
import com.openai.codexmobile.ui.screen.ConnectionScreen
import com.openai.codexmobile.ui.screen.SessionListScreen
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
        onDisconnect = {},
        onOpenSettings = {},
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
