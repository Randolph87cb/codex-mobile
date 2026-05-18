package com.openai.codexmobile.ui.screen

internal enum class TranscriptSpeaker {
    User,
    Assistant,
    System,
}

internal enum class TranscriptBubbleKind {
    Message,
    ToolRequest,
    ToolResult,
    Status,
}

internal sealed interface TranscriptPart {
    data class Text(val text: String) : TranscriptPart

    data class CodeBlock(
        val code: String,
        val language: String? = null,
    ) : TranscriptPart
}

internal data class TranscriptBubble(
    val speaker: TranscriptSpeaker,
    val kind: TranscriptBubbleKind,
    val label: String,
    val title: String? = null,
    val parts: List<TranscriptPart>,
)

internal fun parseTranscriptBubbles(transcript: String): List<TranscriptBubble> {
    if (transcript.isBlank()) {
        return emptyList()
    }

    val bubbles = mutableListOf<TranscriptBubble>()
    transcript
        .trim()
        .split(Regex("\\r?\\n\\s*\\r?\\n"))
        .forEach { block ->
            val normalized = block.trim()
            if (normalized.isBlank()) {
                return@forEach
            }

            val parsed = parseTranscriptBlock(normalized)
            if (parsed != null) {
                bubbles += parsed
            } else if (bubbles.isEmpty()) {
                bubbles += buildBubble(
                    speaker = TranscriptSpeaker.System,
                    kind = TranscriptBubbleKind.Status,
                    label = "系统",
                    body = normalized,
                )
            } else {
                val previous = bubbles.removeAt(bubbles.lastIndex)
                bubbles += previous.copy(
                    parts = previous.parts + parseTranscriptParts(normalized),
                )
            }
        }

    return bubbles
}

private fun parseTranscriptBlock(block: String): TranscriptBubble? {
    if (block.isBlank()) {
        return null
    }

    return when {
        block.startsWith("你：") -> buildBubble(
            speaker = TranscriptSpeaker.User,
            kind = TranscriptBubbleKind.Message,
            label = "你",
            body = block.removePrefix("你：").trim(),
        )

        block.startsWith("Codex：") -> buildBubble(
            speaker = TranscriptSpeaker.Assistant,
            kind = TranscriptBubbleKind.Message,
            label = "Codex",
            body = block.removePrefix("Codex：").trim(),
        )

        block.startsWith("系统：") -> buildBubble(
            speaker = TranscriptSpeaker.System,
            kind = TranscriptBubbleKind.Status,
            label = "系统",
            body = block.removePrefix("系统：").trim(),
        )

        block.startsWith("等待审批：") -> buildStructuredSystemBubble(
            kind = TranscriptBubbleKind.ToolRequest,
            titlePrefix = "等待审批：",
            body = block,
        )

        block.startsWith("审批结果：") -> buildStructuredSystemBubble(
            kind = TranscriptBubbleKind.ToolResult,
            titlePrefix = "审批结果：",
            body = block,
        )

        else -> null
    }
}

private fun buildStructuredSystemBubble(
    kind: TranscriptBubbleKind,
    titlePrefix: String,
    body: String,
): TranscriptBubble {
    val content = body.removePrefix(titlePrefix).trim()
    val lines = content.lines()
    val title = lines.firstOrNull()?.trim().orEmpty()
    val details = lines.drop(1).joinToString("\n").trim()
    val parts = if (details.isBlank()) {
        emptyList()
    } else {
        parseTranscriptParts(details)
    }

    return TranscriptBubble(
        speaker = TranscriptSpeaker.System,
        kind = kind,
        label = "系统",
        title = title.ifBlank { null },
        parts = parts,
    )
}

private fun buildBubble(
    speaker: TranscriptSpeaker,
    kind: TranscriptBubbleKind,
    label: String,
    body: String,
): TranscriptBubble {
    return TranscriptBubble(
        speaker = speaker,
        kind = kind,
        label = label,
        parts = parseTranscriptParts(body),
    )
}

private fun parseTranscriptParts(body: String): List<TranscriptPart> {
    val trimmed = body.trim()
    if (trimmed.isBlank()) {
        return emptyList()
    }

    val regex = Regex("(?s)```([A-Za-z0-9_+\\-.#]*)\\r?\\n(.*?)\\r?\\n```")
    val parts = mutableListOf<TranscriptPart>()
    var cursor = 0

    for (match in regex.findAll(trimmed)) {
        val before = trimmed.substring(cursor, match.range.first).trim()
        if (before.isNotBlank()) {
            parts += TranscriptPart.Text(before)
        }

        val language = match.groupValues[1].trim().ifBlank { null }
        val code = match.groupValues[2].trimEnd()
        if (code.isNotBlank()) {
            parts += TranscriptPart.CodeBlock(code = code, language = language)
        }
        cursor = match.range.last + 1
    }

    val remaining = trimmed.substring(cursor).trim()
    if (remaining.isNotBlank()) {
        parts += TranscriptPart.Text(remaining)
    }

    return parts.ifEmpty { listOf(TranscriptPart.Text(trimmed)) }
}
