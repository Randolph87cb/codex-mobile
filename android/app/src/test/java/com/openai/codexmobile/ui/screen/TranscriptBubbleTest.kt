package com.openai.codexmobile.ui.screen

import com.openai.codexmobile.model.SessionActivityEntry
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
    fun parseTranscriptBubblesRecognizesImageMarkdown() {
        val transcript = """
            你：请看这两张图
            ![first.png](bridge-attachment://uploaded-1)
            ![second.png](data:image/png;base64,AAAA)
        """.trimIndent()

        val bubble = parseTranscriptBubbles(transcript).single()

        assertEquals(TranscriptSpeaker.User, bubble.speaker)
        assertEquals(
            listOf(
                TranscriptPart.Text("请看这两张图"),
                TranscriptPart.Image(
                    altText = "first.png",
                    source = "bridge-attachment://uploaded-1",
                ),
                TranscriptPart.Image(
                    altText = "second.png",
                    source = "data:image/png;base64,AAAA",
                ),
            ),
            bubble.parts,
        )
    }

    @Test
    fun parseTranscriptBubblesPreservesBlankLinesInsideSingleAssistantMessage() {
        val transcript = """
            Codex：## 结果

            - 第一项
            - 第二项

            继续补一句。

            你：收到
        """.trimIndent()

        val bubbles = parseTranscriptBubbles(transcript)

        assertEquals(2, bubbles.size)
        assertEquals(
            """
            ## 结果

            - 第一项
            - 第二项

            继续补一句。
            """.trimIndent(),
            bubbles[0].rawBody,
        )
        assertEquals(listOf(TranscriptPart.Text(bubbles[0].rawBody)), bubbles[0].parts)
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

    @Test
    fun buildTranscriptDisplayItemsGroupsConsecutiveExecutionActivities() {
        val transcript = """
            你：先跑一下

            系统：命令执行
            命令：npm test

            系统：文件编辑
            文件：app.kt

            Codex：执行完成。
        """.trimIndent()

        val items = buildTranscriptDisplayItems(transcript)

        assertEquals(3, items.size)
        assertTrue(items[0] is TranscriptDisplayItem.BubbleItem)
        assertTrue(items[1] is TranscriptDisplayItem.ExecutionGroup)
        assertTrue(items[2] is TranscriptDisplayItem.BubbleItem)

        val group = items[1] as TranscriptDisplayItem.ExecutionGroup
        assertEquals(listOf("命令执行", "文件编辑"), group.activities.map { it.summaryLine })
        assertEquals("命令执行 · 文件编辑", group.summaryLine)
    }

    @Test
    fun parseTranscriptBubblesTreatsSingleLineExecutionTitlesAsActivities() {
        val transcript = "系统：推理摘要"

        val bubble = parseTranscriptBubbles(transcript).single()

        assertEquals("推理摘要", bubble.title)
        assertTrue(bubble.parts.isEmpty())
        assertTrue(bubble.belongsToExecutionProcess)
    }

    @Test
    fun buildTranscriptDisplayItemsAppendsLiveActivitiesIntoExecutionGroup() {
        val transcript = """
            你：继续

            Codex：我先检查一下。
        """.trimIndent()

        val items = buildTranscriptDisplayItems(
            transcript = transcript,
            liveActivities = listOf(
                SessionActivityEntry(
                    stableId = "reasoning-1",
                    itemType = "reasoning",
                    itemId = "reasoning-1",
                    title = "推理摘要",
                    body = "README 大体跟上了。",
                    summary = "README 大体跟上了。",
                    transcriptBlock = "系统：推理摘要\nREADME 大体跟上了。",
                    updatedAt = "2026-05-19T10:05:00Z",
                ),
            ),
        )

        assertEquals(3, items.size)
        assertTrue(items[2] is TranscriptDisplayItem.ExecutionGroup)
        val group = items[2] as TranscriptDisplayItem.ExecutionGroup
        assertEquals(listOf("推理摘要"), group.activities.map { it.summaryLine })
        assertEquals("README 大体跟上了。", (group.activities.single().parts.single() as TranscriptPart.Text).text)
    }
}
