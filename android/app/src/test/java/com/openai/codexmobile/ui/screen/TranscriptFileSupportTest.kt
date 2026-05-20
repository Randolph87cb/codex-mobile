package com.openai.codexmobile.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranscriptFileSupportTest {
    @Test
    fun resolveTranscriptFileDownloadRequestSupportsBridgeFileScheme() {
        val request = resolveTranscriptFileDownloadRequest(
            source = "bridge-file://D%3A%5Cworkspace%5Ccodex-mobile%5Creport.md",
            bridgeEndpoint = "http://10.0.2.2:8787",
        )

        requireNotNull(request)
        assertEquals(
            "http://10.0.2.2:8787/api/file/download?path=D%3A%5Cworkspace%5Ccodex-mobile%5Creport.md",
            request.url,
        )
        assertEquals("report.md", request.displayName)
    }

    @Test
    fun resolveTranscriptFileDownloadRequestSupportsAbsoluteWindowsPathLinks() {
        val request = resolveTranscriptFileDownloadRequest(
            source = "D:\\workspace\\codex-mobile\\logs\\bridge.log",
            bridgeEndpoint = "http://10.0.2.2:8787",
        )

        requireNotNull(request)
        assertEquals(
            "http://10.0.2.2:8787/api/file/download?path=D%3A%5Cworkspace%5Ccodex-mobile%5Clogs%5Cbridge.log",
            request.url,
        )
        assertEquals("bridge.log", request.displayName)
    }

    @Test
    fun resolveTranscriptFileDownloadRequestIgnoresRegularWebLinks() {
        val request = resolveTranscriptFileDownloadRequest(
            source = "https://example.com/report.md",
            bridgeEndpoint = "http://10.0.2.2:8787",
        )

        assertNull(request)
    }

    @Test
    fun formatTranscriptFileByteCountUsesReadableUnits() {
        assertEquals("512 B", formatTranscriptFileByteCount(512))
        assertEquals("1.5 KB", formatTranscriptFileByteCount(1536))
        assertEquals("2 MB", formatTranscriptFileByteCount(2 * 1024 * 1024L))
    }

    @Test
    fun calculateTranscriptFileDownloadFractionReturnsNullWithoutKnownTotal() {
        val progress = TranscriptFileDownloadProgress(
            displayName = "report.md",
            stage = TranscriptFileDownloadStage.Downloading,
            bytesDownloaded = 1024,
            totalBytes = null,
        )

        assertNull(calculateTranscriptFileDownloadFraction(progress))
    }

    @Test
    fun calculateTranscriptFileDownloadFractionClampsToValidRange() {
        val progress = TranscriptFileDownloadProgress(
            displayName = "report.md",
            stage = TranscriptFileDownloadStage.Downloading,
            bytesDownloaded = 150,
            totalBytes = 100,
        )

        assertEquals(1f, calculateTranscriptFileDownloadFraction(progress) ?: 0f, 0.0001f)
    }
}
