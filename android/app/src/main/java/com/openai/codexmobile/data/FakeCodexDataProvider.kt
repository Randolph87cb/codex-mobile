package com.openai.codexmobile.data

import com.openai.codexmobile.model.BridgeConnectionState
import com.openai.codexmobile.model.SessionDetail
import com.openai.codexmobile.model.SessionSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class FakeCodexDataProvider : CodexDataProvider {
    private var connectionState: BridgeConnectionState = BridgeConnectionState.Disconnected

    private val sessions = listOf(
        SessionSummary(
            id = "session-001",
            title = "Compose 骨架初始化",
            subtitle = "占位会话列表项",
            lastUpdated = "刚刚更新",
            status = "idle",
        ),
        SessionSummary(
            id = "session-002",
            title = "桥接协议探索",
            subtitle = "预留给后续原生桥接集成",
            lastUpdated = "12 分钟前更新",
            status = "idle",
        ),
    )

    override suspend fun connect(endpoint: String): BridgeConnectionState {
        delay(300)
        connectionState = BridgeConnectionState.Connected(endpoint)
        return connectionState
    }

    override suspend fun disconnect() {
        delay(100)
        connectionState = BridgeConnectionState.Disconnected
    }

    override suspend fun currentConnection(): BridgeConnectionState = connectionState

    override suspend fun createSession(request: CreateSessionRequest): SessionDetail {
        delay(150)
        return SessionDetail(
            id = "session-created-fake",
            title = "模拟创建的会话",
            subtitle = "${request.model} • ${request.approvalMode}",
            lastUpdated = "刚刚更新",
            transcriptPreview = "工作目录：${request.cwd}",
            status = "idle",
        )
    }

    override suspend fun sendInput(sessionId: String, text: String) {
        delay(100)
    }

    override fun observeSessionEvents(sessionId: String): Flow<SessionStreamEvent> = emptyFlow()

    override suspend fun listSessions(): List<SessionSummary> {
        delay(150)
        return sessions
    }

    override suspend fun getSessionDetail(sessionId: String): SessionDetail? {
        delay(150)
        val session = sessions.firstOrNull { it.id == sessionId } ?: return null
        return SessionDetail(
            id = session.id,
            title = session.title,
            subtitle = session.subtitle,
            lastUpdated = session.lastUpdated,
            transcriptPreview = "这里是占位详情页，后续会替换成真实会话内容和工具输出。",
            status = session.status,
        )
    }
}
