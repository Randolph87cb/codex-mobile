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
}
