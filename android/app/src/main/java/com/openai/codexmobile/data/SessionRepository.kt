package com.openai.codexmobile.data

import com.openai.codexmobile.model.SessionDetail
import com.openai.codexmobile.model.SessionSummary

interface SessionRepository {
    suspend fun listSessions(): List<SessionSummary>
    suspend fun getSessionDetail(sessionId: String): SessionDetail?
}
