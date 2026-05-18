package com.openai.codexmobile.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptBubbleTest {
    @Test
    fun parseTranscriptBubblesSplitsConversationIntoSpeakerBlocks() {
        val transcript = """
            工作目录：D:\workspace\codex-mobile
            
            你：你好
            
            Codex：你好，我在。
            
            系统：命令已结束
        """.trimIndent()

        val bubbles = parseTranscriptBubbles(transcript)

        assertEquals(4, bubbles.size)
        assertEquals(TranscriptSpeaker.System, bubbles[0].speaker)
        assertEquals("工作目录：D:\\workspace\\codex-mobile", bubbles[0].text)
        assertEquals(TranscriptSpeaker.User, bubbles[1].speaker)
        assertEquals("你好", bubbles[1].text)
        assertEquals(TranscriptSpeaker.Assistant, bubbles[2].speaker)
        assertEquals("你好，我在。", bubbles[2].text)
        assertEquals(TranscriptSpeaker.System, bubbles[3].speaker)
        assertEquals("命令已结束", bubbles[3].text)
    }
}
