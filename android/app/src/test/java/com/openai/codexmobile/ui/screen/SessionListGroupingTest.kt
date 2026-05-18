package com.openai.codexmobile.ui.screen

import com.openai.codexmobile.model.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionListGroupingTest {
    @Test
    fun groupsSessionsByCwd() {
        val sessions = listOf(
            SessionSummary(
                id = "a",
                title = "A",
                subtitle = "one",
                lastUpdated = "2026-05-19T10:00:00Z",
                cwd = "D:\\workspace\\alpha",
                model = "gpt-5.5",
            ),
            SessionSummary(
                id = "b",
                title = "B",
                subtitle = "two",
                lastUpdated = "2026-05-19T11:00:00Z",
                cwd = "D:\\workspace\\beta",
                model = "gpt-5.5",
            ),
            SessionSummary(
                id = "c",
                title = "C",
                subtitle = "three",
                lastUpdated = "2026-05-19T12:00:00Z",
                cwd = "D:\\workspace\\alpha",
                model = "gpt-5.5",
            ),
        )

        val groups = groupSessionsByDirectory(sessions)

        assertEquals(2, groups.size)
        assertEquals("D:\\workspace\\alpha", groups[0].cwd)
        assertEquals(listOf("c", "a"), groups[0].sessions.map { it.id })
        assertEquals("D:\\workspace\\beta", groups[1].cwd)
    }
}
