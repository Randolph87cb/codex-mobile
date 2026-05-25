package com.openai.codexmobile.ui.screen

import com.openai.codexmobile.model.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

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

    @Test
    fun sortsDirectoryGroupsByNewestSessionFirst() {
        val sessions = listOf(
            SessionSummary(
                id = "a",
                title = "A",
                subtitle = "gpt-5.5 • 空闲 • D:\\workspace\\alpha",
                lastUpdated = "2026-05-19T10:00:00Z",
                cwd = "D:\\workspace\\alpha",
            ),
            SessionSummary(
                id = "b",
                title = "B",
                subtitle = "gpt-5.5 • 空闲 • D:\\workspace\\beta",
                lastUpdated = "2026-05-19T11:00:00Z",
                cwd = "D:\\workspace\\beta",
            ),
        )

        val groups = groupSessionsByDirectory(sessions)

        assertEquals(listOf("D:\\workspace\\beta", "D:\\workspace\\alpha"), groups.map { it.cwd })
    }

    @Test
    fun buildCompactSessionSubtitleOmitsRepeatedDirectory() {
        val session = SessionSummary(
            id = "a",
            title = "A",
            subtitle = "gpt-5.5 • 空闲 • D:\\workspace\\codex-mobile",
            lastUpdated = "2026-05-19T11:00:00Z",
            cwd = "D:\\workspace\\codex-mobile",
        )

        assertEquals(
            "gpt-5.5 • 空闲",
            buildCompactSessionSubtitle(session, "D:\\workspace\\codex-mobile"),
        )
    }

    @Test
    fun formatSessionLastUpdatedUsesClockTimeForSameDay() {
        assertEquals(
            "17:35",
            formatSessionLastUpdated(
                lastUpdated = "2026-05-26T09:35:00Z",
                now = Instant.parse("2026-05-26T12:00:00Z"),
                zoneId = ZoneId.of("Asia/Shanghai"),
            ),
        )
    }

    @Test
    fun formatSessionLastUpdatedUsesDaysAndWeeksForOlderSessions() {
        assertEquals(
            "1天前",
            formatSessionLastUpdated(
                lastUpdated = "2026-05-25T09:35:00Z",
                now = Instant.parse("2026-05-26T12:00:00Z"),
                zoneId = ZoneId.of("Asia/Shanghai"),
            ),
        )
        assertEquals(
            "2天前",
            formatSessionLastUpdated(
                lastUpdated = "2026-05-24T09:35:00Z",
                now = Instant.parse("2026-05-26T12:00:00Z"),
                zoneId = ZoneId.of("Asia/Shanghai"),
            ),
        )
        assertEquals(
            "2周前",
            formatSessionLastUpdated(
                lastUpdated = "2026-05-12T09:35:00Z",
                now = Instant.parse("2026-05-26T12:00:00Z"),
                zoneId = ZoneId.of("Asia/Shanghai"),
            ),
        )
    }
}
