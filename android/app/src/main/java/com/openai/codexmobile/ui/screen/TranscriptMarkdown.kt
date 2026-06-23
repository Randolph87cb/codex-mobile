package com.openai.codexmobile.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.openai.codexmobile.ui.theme.codexTranscriptColors

private const val MarkdownLinkTag = "markdown_link"

internal sealed interface MarkdownBlock {
    data class Heading(
        val level: Int,
        val text: String,
    ) : MarkdownBlock

    data class Paragraph(
        val text: String,
    ) : MarkdownBlock

    data class Quote(
        val text: String,
    ) : MarkdownBlock

    data class ListBlock(
        val ordered: Boolean,
        val items: List<String>,
    ) : MarkdownBlock
}

@Composable
internal fun MarkdownTextBlock(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    bridgeEndpoint: String = "",
    sessionCwd: String? = null,
    onShowMessage: (String) -> Unit = {},
    onFileDownloadRequest: (TranscriptFileDownloadRequest) -> Unit = {},
    fillWidth: Boolean = true,
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    val blockWidthModifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier
    Column(
        modifier = if (fillWidth) modifier.fillMaxWidth() else modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> MarkdownHeading(
                    block = block,
                    modifier = blockWidthModifier,
                    bridgeEndpoint = bridgeEndpoint,
                    sessionCwd = sessionCwd,
                    onShowMessage = onShowMessage,
                    onFileDownloadRequest = onFileDownloadRequest,
                )

                is MarkdownBlock.Paragraph -> MarkdownAnnotatedText(
                    text = block.text,
                    style = style,
                    modifier = blockWidthModifier,
                    bridgeEndpoint = bridgeEndpoint,
                    sessionCwd = sessionCwd,
                    onShowMessage = onShowMessage,
                    onFileDownloadRequest = onFileDownloadRequest,
                )

                is MarkdownBlock.Quote -> MarkdownQuote(
                    text = block.text,
                    style = style,
                    modifier = blockWidthModifier,
                    bridgeEndpoint = bridgeEndpoint,
                    sessionCwd = sessionCwd,
                    onShowMessage = onShowMessage,
                    onFileDownloadRequest = onFileDownloadRequest,
                )

                is MarkdownBlock.ListBlock -> MarkdownList(
                    block = block,
                    style = style,
                    modifier = blockWidthModifier,
                    bridgeEndpoint = bridgeEndpoint,
                    sessionCwd = sessionCwd,
                    onShowMessage = onShowMessage,
                    onFileDownloadRequest = onFileDownloadRequest,
                )
            }
        }
    }
}

@Composable
private fun MarkdownHeading(
    block: MarkdownBlock.Heading,
    modifier: Modifier = Modifier,
    bridgeEndpoint: String,
    sessionCwd: String?,
    onShowMessage: (String) -> Unit,
    onFileDownloadRequest: (TranscriptFileDownloadRequest) -> Unit,
) {
    val style = when (block.level) {
        1 -> MaterialTheme.typography.headlineSmall
        2 -> MaterialTheme.typography.titleLarge
        3 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }
    MarkdownAnnotatedText(
        text = block.text,
        style = style,
        modifier = modifier,
        bridgeEndpoint = bridgeEndpoint,
        sessionCwd = sessionCwd,
        onShowMessage = onShowMessage,
        onFileDownloadRequest = onFileDownloadRequest,
    )
}

@Composable
private fun MarkdownQuote(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    bridgeEndpoint: String,
    sessionCwd: String?,
    onShowMessage: (String) -> Unit,
    onFileDownloadRequest: (TranscriptFileDownloadRequest) -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "▍",
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 1.dp),
        )
        MarkdownAnnotatedText(
            text = text,
            style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.weight(1f),
            bridgeEndpoint = bridgeEndpoint,
            sessionCwd = sessionCwd,
            onShowMessage = onShowMessage,
            onFileDownloadRequest = onFileDownloadRequest,
        )
    }
}

@Composable
private fun MarkdownList(
    block: MarkdownBlock.ListBlock,
    style: TextStyle,
    modifier: Modifier = Modifier,
    bridgeEndpoint: String,
    sessionCwd: String?,
    onShowMessage: (String) -> Unit,
    onFileDownloadRequest: (TranscriptFileDownloadRequest) -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        block.items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = if (block.ordered) "${index + 1}." else "•",
                    style = style,
                )
                MarkdownAnnotatedText(
                    text = item,
                    style = style,
                    modifier = Modifier.weight(1f),
                    bridgeEndpoint = bridgeEndpoint,
                    sessionCwd = sessionCwd,
                    onShowMessage = onShowMessage,
                    onFileDownloadRequest = onFileDownloadRequest,
                )
            }
        }
    }
}

@Composable
private fun MarkdownAnnotatedText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    bridgeEndpoint: String,
    sessionCwd: String?,
    onShowMessage: (String) -> Unit,
    onFileDownloadRequest: (TranscriptFileDownloadRequest) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val transcriptColors = codexTranscriptColors()
    val annotated = remember(
        text,
        transcriptColors.link,
        transcriptColors.inlineCodeBackground,
        transcriptColors.inlineCodeContent,
    ) {
        buildMarkdownAnnotatedString(
            text = text,
            linkColor = transcriptColors.link,
            inlineCodeBackground = transcriptColors.inlineCodeBackground,
            inlineCodeColor = transcriptColors.inlineCodeContent,
        )
    }
    val content: @Composable () -> Unit = {
        ClickableText(
            text = annotated,
            modifier = modifier,
            style = style,
            onClick = { offset ->
                val link = annotated.getStringAnnotations(
                    tag = MarkdownLinkTag,
                    start = offset,
                    end = offset,
                ).firstOrNull()?.item ?: return@ClickableText
                val localDownloadRequest = resolveTranscriptFileDownloadRequest(
                    source = link,
                    bridgeEndpoint = bridgeEndpoint,
                    sessionCwd = sessionCwd,
                )
                if (localDownloadRequest != null) {
                    onFileDownloadRequest(localDownloadRequest)
                    return@ClickableText
                }

                val resolvedUrl = resolveMarkdownExternalUrl(link, bridgeEndpoint) ?: run {
                    onShowMessage("暂不支持的链接。")
                    return@ClickableText
                }
                runCatching {
                    uriHandler.openUri(resolvedUrl)
                }.onFailure { error ->
                    onShowMessage(error.message ?: "打开链接失败。")
                }
            },
        )
    }
    content()
}

internal fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val normalized = text.trim()
    if (normalized.isBlank()) {
        return emptyList()
    }

    val lines = normalized.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraphLines = mutableListOf<String>()
    var index = 0

    fun flushParagraph() {
        val paragraph = paragraphLines.joinToString("\n").trim()
        if (paragraph.isNotBlank()) {
            blocks += MarkdownBlock.Paragraph(paragraph)
        }
        paragraphLines.clear()
    }

    while (index < lines.size) {
        val line = lines[index]
        val trimmedLine = line.trim()
        if (trimmedLine.isBlank()) {
            flushParagraph()
            index += 1
            continue
        }

        val headingMatch = HeadingRegex.matchEntire(trimmedLine)
        if (headingMatch != null) {
            flushParagraph()
            blocks += MarkdownBlock.Heading(
                level = headingMatch.groupValues[1].length.coerceIn(1, 4),
                text = headingMatch.groupValues[2].trim(),
            )
            index += 1
            continue
        }

        val unorderedMatch = UnorderedListRegex.matchEntire(trimmedLine)
        if (unorderedMatch != null) {
            flushParagraph()
            val items = mutableListOf<String>()
            var cursor = index
            while (cursor < lines.size) {
                val listMatch = UnorderedListRegex.matchEntire(lines[cursor].trim()) ?: break
                items += listMatch.groupValues[1].trim()
                cursor += 1
            }
            blocks += MarkdownBlock.ListBlock(
                ordered = false,
                items = items,
            )
            index = cursor
            continue
        }

        val orderedMatch = OrderedListRegex.matchEntire(trimmedLine)
        if (orderedMatch != null) {
            flushParagraph()
            val items = mutableListOf<String>()
            var cursor = index
            while (cursor < lines.size) {
                val listMatch = OrderedListRegex.matchEntire(lines[cursor].trim()) ?: break
                items += listMatch.groupValues[1].trim()
                cursor += 1
            }
            blocks += MarkdownBlock.ListBlock(
                ordered = true,
                items = items,
            )
            index = cursor
            continue
        }

        val quoteMatch = QuoteRegex.matchEntire(trimmedLine)
        if (quoteMatch != null) {
            flushParagraph()
            val quoteLines = mutableListOf<String>()
            var cursor = index
            while (cursor < lines.size) {
                val match = QuoteRegex.matchEntire(lines[cursor].trim()) ?: break
                quoteLines += match.groupValues[1]
                cursor += 1
            }
            blocks += MarkdownBlock.Quote(quoteLines.joinToString("\n").trim())
            index = cursor
            continue
        }

        paragraphLines += line
        index += 1
    }

    flushParagraph()
    return blocks
}

internal fun buildMarkdownAnnotatedString(
    text: String,
    linkColor: Color,
    inlineCodeBackground: Color,
    inlineCodeColor: Color,
): AnnotatedString {
    return buildAnnotatedString {
        appendMarkdownInline(
            builder = this,
            text = text,
            linkColor = linkColor,
            inlineCodeBackground = inlineCodeBackground,
            inlineCodeColor = inlineCodeColor,
            autoLinkBarePaths = true,
        )
    }
}

private fun appendMarkdownInline(
    builder: AnnotatedString.Builder,
    text: String,
    linkColor: Color,
    inlineCodeBackground: Color,
    inlineCodeColor: Color,
    autoLinkBarePaths: Boolean,
) {
    var index = 0
    while (index < text.length) {
        val localPath = if (autoLinkBarePaths) findBareWindowsPath(text, index) else null
        when {
            localPath != null -> {
                appendMarkdownLink(
                    builder = builder,
                    label = localPath,
                    url = localPath,
                    linkColor = linkColor,
                    inlineCodeBackground = inlineCodeBackground,
                    inlineCodeColor = inlineCodeColor,
                    parseLabelMarkdown = false,
                )
                index += localPath.length
            }

            text[index] == '\\' && index + 1 < text.length -> {
                builder.append(text[index + 1])
                index += 2
            }

            text.startsWith("**", index) || text.startsWith("__", index) -> {
                val delimiter = text.substring(index, index + 2)
                val closing = text.indexOf(delimiter, startIndex = index + 2)
                if (closing > index + 2) {
                    builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    appendMarkdownInline(
                        builder = builder,
                        text = text.substring(index + 2, closing),
                        linkColor = linkColor,
                        inlineCodeBackground = inlineCodeBackground,
                        inlineCodeColor = inlineCodeColor,
                        autoLinkBarePaths = autoLinkBarePaths,
                    )
                    builder.pop()
                    index = closing + 2
                } else {
                    builder.append(text[index])
                    index += 1
                }
            }

            text.startsWith("~~", index) -> {
                val closing = text.indexOf("~~", startIndex = index + 2)
                if (closing > index + 2) {
                    builder.pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    appendMarkdownInline(
                        builder = builder,
                        text = text.substring(index + 2, closing),
                        linkColor = linkColor,
                        inlineCodeBackground = inlineCodeBackground,
                        inlineCodeColor = inlineCodeColor,
                        autoLinkBarePaths = autoLinkBarePaths,
                    )
                    builder.pop()
                    index = closing + 2
                } else {
                    builder.append(text[index])
                    index += 1
                }
            }

            text[index] == '`' -> {
                val closing = text.indexOf('`', startIndex = index + 1)
                if (closing > index + 1) {
                    builder.pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = inlineCodeBackground,
                            color = inlineCodeColor,
                        ),
                    )
                    builder.append(text.substring(index + 1, closing))
                    builder.pop()
                    index = closing + 1
                } else {
                    builder.append(text[index])
                    index += 1
                }
            }

            text[index] == '[' -> {
                val closingBracket = text.indexOf(']', startIndex = index + 1)
                val openParen = if (closingBracket > index) {
                    text.indexOf('(', startIndex = closingBracket + 1)
                } else {
                    -1
                }
                val closingParen = if (openParen == closingBracket + 1) {
                    text.indexOf(')', startIndex = openParen + 1)
                } else {
                    -1
                }
                if (closingBracket > index && openParen == closingBracket + 1 && closingParen > openParen + 1) {
                    val label = text.substring(index + 1, closingBracket)
                    val url = text.substring(openParen + 1, closingParen).trim()
                    appendMarkdownLink(
                        builder = builder,
                        label = label,
                        url = url,
                        linkColor = linkColor,
                        inlineCodeBackground = inlineCodeBackground,
                        inlineCodeColor = inlineCodeColor,
                    )
                    index = closingParen + 1
                } else {
                    builder.append(text[index])
                    index += 1
                }
            }

            text[index] == '*' || text[index] == '_' -> {
                val delimiter = text[index]
                val closing = text.indexOf(delimiter, startIndex = index + 1)
                if (closing > index + 1) {
                    builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    appendMarkdownInline(
                        builder = builder,
                        text = text.substring(index + 1, closing),
                        linkColor = linkColor,
                        inlineCodeBackground = inlineCodeBackground,
                        inlineCodeColor = inlineCodeColor,
                        autoLinkBarePaths = autoLinkBarePaths,
                    )
                    builder.pop()
                    index = closing + 1
                } else {
                    builder.append(text[index])
                    index += 1
                }
            }

            else -> {
                builder.append(text[index])
                index += 1
            }
        }
    }
}

private fun appendMarkdownLink(
    builder: AnnotatedString.Builder,
    label: String,
    url: String,
    linkColor: Color,
    inlineCodeBackground: Color,
    inlineCodeColor: Color,
    parseLabelMarkdown: Boolean = true,
) {
    builder.pushStringAnnotation(tag = MarkdownLinkTag, annotation = url)
    builder.pushStyle(
        SpanStyle(
            color = if (linkColor == Color.Unspecified) Color(0xFF356AC3) else linkColor,
            textDecoration = TextDecoration.Underline,
        ),
    )
    if (parseLabelMarkdown) {
        appendMarkdownInline(
            builder = builder,
            text = label,
            linkColor = linkColor,
            inlineCodeBackground = inlineCodeBackground,
            inlineCodeColor = inlineCodeColor,
            autoLinkBarePaths = false,
        )
    } else {
        builder.append(label)
    }
    builder.pop()
    builder.pop()
}

private fun findBareWindowsPath(text: String, startIndex: Int): String? {
    if (startIndex + 3 > text.length) {
        return null
    }
    val drive = text[startIndex]
    if (!drive.isLetter() || text[startIndex + 1] != ':') {
        return null
    }
    val firstSeparator = text[startIndex + 2]
    if (firstSeparator != '\\' && firstSeparator != '/') {
        return null
    }
    if (startIndex > 0 && isPathBodyChar(text[startIndex - 1])) {
        return null
    }

    var endIndex = startIndex + 3
    while (endIndex < text.length && isPathBodyChar(text[endIndex])) {
        endIndex += 1
    }
    while (endIndex > startIndex && isTrailingPathPunctuation(text[endIndex - 1])) {
        endIndex -= 1
    }
    return text.substring(startIndex, endIndex).takeIf { it.length > 3 }
}

private fun isPathBodyChar(value: Char): Boolean {
    return !value.isWhitespace() &&
        value != '`' &&
        value != '<' &&
        value != '>' &&
        value != '[' &&
        value != ']' &&
        value != '(' &&
        value != ')' &&
        value != '"' &&
        value != '\''
}

private fun isTrailingPathPunctuation(value: Char): Boolean {
    return value == '.' ||
        value == ',' ||
        value == ';' ||
        value == ':' ||
        value == '，' ||
        value == '。' ||
        value == '；' ||
        value == '：'
}

private val HeadingRegex = Regex("^(#{1,6})\\s+(.+)$")
private val UnorderedListRegex = Regex("^[-*+]\\s+(.+)$")
private val OrderedListRegex = Regex("^\\d+\\.\\s+(.+)$")
private val QuoteRegex = Regex("^>\\s?(.*)$")

private fun resolveMarkdownExternalUrl(
    source: String,
    bridgeEndpoint: String,
): String? {
    val trimmed = source.trim()
    if (trimmed.isBlank()) {
        return null
    }

    return when {
        trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed

        trimmed.startsWith("/") -> resolveTranscriptImageUrl(trimmed, bridgeEndpoint)
        else -> null
    }
}
