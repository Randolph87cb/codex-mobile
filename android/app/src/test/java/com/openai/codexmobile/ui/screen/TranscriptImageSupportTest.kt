package com.openai.codexmobile.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptImageSupportTest {
    @Test
    fun resolveTranscriptImageUrlPrefixesRelativeBridgePath() {
        assertEquals(
            "http://10.0.2.2:8787/api/image/file?path=abc",
            resolveTranscriptImageUrl(
                source = "/api/image/file?path=abc",
                bridgeEndpoint = "http://10.0.2.2:8787",
            ),
        )
    }

    @Test
    fun resolveTranscriptImageUrlKeepsAbsoluteHttpSource() {
        assertEquals(
            "https://example.com/image.png",
            resolveTranscriptImageUrl(
                source = "https://example.com/image.png",
                bridgeEndpoint = "http://10.0.2.2:8787",
            ),
        )
    }

    @Test
    fun calculateInSampleSizeDownscalesLargeImages() {
        assertEquals(4, calculateInSampleSize(width = 4_096, height = 4_096, maxDimension = 1_024))
        assertEquals(1, calculateInSampleSize(width = 800, height = 600, maxDimension = 1_024))
    }
}
