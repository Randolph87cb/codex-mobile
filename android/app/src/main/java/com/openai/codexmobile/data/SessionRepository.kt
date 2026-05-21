package com.openai.codexmobile.data

import com.openai.codexmobile.model.SessionDetail
import com.openai.codexmobile.model.SessionSummary

interface SessionRepository {
    suspend fun listSessions(archived: Boolean = false): List<SessionSummary>
    suspend fun getSessionDetail(sessionId: String): SessionDetail?
    suspend fun archiveSession(sessionId: String)
    suspend fun unarchiveSession(sessionId: String)
}
