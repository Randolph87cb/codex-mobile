package com.openai.codexmobile.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        assertEquals(listOf(TranscriptPart.Text("工作目录：D:\\workspace\\codex-mobile")), bubbles[0].parts)
        assertEquals(TranscriptSpeaker.User, bubbles[1].speaker)
        assertEquals(listOf(TranscriptPart.Text("你好")), bubbles[1].parts)
        assertEquals(TranscriptSpeaker.Assistant, bubbles[2].speaker)
        assertEquals(listOf(TranscriptPart.Text("你好，我在。")), bubbles[2].parts)
        assertEquals(TranscriptSpeaker.System, bubbles[3].speaker)
        assertEquals(listOf(TranscriptPart.Text("命令已结束")), bubbles[3].parts)
    }

    @Test
    fun parseTranscriptBubblesRecognizesToolResultsAndCodeBlocks() {
        val transcript = """
            Codex：下面是脚本：
            
            ```powershell
            Get-ChildItem
            ```
            
            审批结果：approve（item/commandExecution/requestApproval）
            请求 ID：req-1
        """.trimIndent()

        val bubbles = parseTranscriptBubbles(transcript)

        assertEquals(2, bubbles.size)
        assertEquals(TranscriptSpeaker.Assistant, bubbles[0].speaker)
        assertEquals(2, bubbles[0].parts.size)
        assertEquals(TranscriptPart.Text("下面是脚本："), bubbles[0].parts[0])
        assertEquals(
            TranscriptPart.CodeBlock(
                code = "Get-ChildItem",
                language = "powershell",
            ),
            bubbles[0].parts[1],
        )
        assertEquals(TranscriptBubbleKind.ToolResult, bubbles[1].kind)
        assertEquals("approve（item/commandExecution/requestApproval）", bubbles[1].title)
        assertTrue(bubbles[1].parts.single() is TranscriptPart.Text)
    }

    @Test
    fun parseTranscriptBubblesSplitsSystemOperationTitleAndDefaultsItToCollapsed() {
        val transcript = """
            系统：命令执行
            状态：已完成
            命令：npm test
        """.trimIndent()

        val bubble = parseTranscriptBubbles(transcript).single()

        assertEquals(TranscriptSpeaker.System, bubble.speaker)
        assertEquals(TranscriptBubbleKind.Status, bubble.kind)
        assertEquals("命令执行", bubble.title)
        assertEquals(
            listOf(
                TranscriptPart.Text(
                    """
                    状态：已完成
                    命令：npm test
                    """.trimIndent(),
                ),
            ),
            bubble.parts,
        )
        assertTrue(!bubble.prefersExpandedByDefault)
        assertEquals("命令执行", bubble.summaryLine)
    }
}
