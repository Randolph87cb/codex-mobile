package com.openai.codexmobile.ui.screen

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptMarkdownTest {
    @Test
    fun parseMarkdownBlocksRecognizesHeadingsListsQuotesAndParagraphs() {
        val blocks = parseMarkdownBlocks(
            """
            ## 标题

            第一段

            - one
            - two

            > 引用
            > 第二行
            """.trimIndent(),
        )

        assertEquals(4, blocks.size)
        assertEquals(MarkdownBlock.Heading(level = 2, text = "标题"), blocks[0])
        assertEquals(MarkdownBlock.Paragraph(text = "第一段"), blocks[1])
        assertEquals(
            MarkdownBlock.ListBlock(
                ordered = false,
                items = listOf("one", "two"),
            ),
            blocks[2],
        )
        assertEquals(
            MarkdownBlock.Quote(
                text = "引用\n第二行",
            ),
            blocks[3],
        )
    }

    @Test
    fun buildMarkdownAnnotatedStringKeepsInlineFormattingContent() {
        val annotated = buildMarkdownAnnotatedString(
            text = "**加粗** 和 `代码` 还有 [链接](https://example.com)",
            linkColor = Color.Unspecified,
            inlineCodeBackground = Color(0x1F000000),
            inlineCodeColor = Color.Black,
        )

        assertEquals("加粗 和 代码 还有 链接", annotated.text)
        assertTrue(
            annotated.getStringAnnotations(
                tag = "markdown_link",
                start = 0,
                end = annotated.length,
            ).any { it.item == "https://example.com" },
        )
    }
}
