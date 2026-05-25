package com.openai.codexmobile.ui.screen

import com.openai.codexmobile.model.SessionActivityEntry

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

    data class Image(
        val altText: String,
        val source: String,
    ) : TranscriptPart

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
    val rawBody: String,
    val parts: List<TranscriptPart>,
)

internal val TranscriptBubble.copyText: String
    get() = rawBody

private val ExecutionActivityTitles = setOf(
    "计划更新",
    "推理摘要",
    "命令执行",
    "文件修改",
    "文件修改进度",
    "工具调用进度",
    "网页搜索",
    "查看图片",
    "图片生成",
    "进入审查模式",
    "退出审查模式",
    "上下文压缩",
)

private val ExecutionActivityTitlePrefixes = listOf(
    "工具调用 ",
    "动态工具 ",
    "协作调用 ",
)

internal sealed interface TranscriptDisplayItem {
    data class BubbleItem(
        val bubble: TranscriptBubble,
    ) : TranscriptDisplayItem

    data class ExecutionGroup(
        val activities: List<TranscriptBubble>,
    ) : TranscriptDisplayItem
}

internal val TranscriptBubble.prefersExpandedByDefault: Boolean
    get() = speaker == TranscriptSpeaker.User || (speaker == TranscriptSpeaker.Assistant && kind == TranscriptBubbleKind.Message)

internal val TranscriptBubble.summaryLine: String
    get() = title?.takeIf { it.isNotBlank() }
        ?: parts.firstNotNullOfOrNull { part ->
            when (part) {
                is TranscriptPart.Text -> part.text
                    .lineSequence()
                    .firstOrNull { it.isNotBlank() }
                    ?.trim()
                    ?.take(120)
                is TranscriptPart.Image -> part.altText.ifBlank { "图片" }
                is TranscriptPart.CodeBlock -> part.language?.takeIf { it.isNotBlank() } ?: "代码块"
            }
        }
        ?: label

internal val TranscriptBubble.belongsToExecutionProcess: Boolean
    get() = speaker == TranscriptSpeaker.System &&
        !prefersExpandedByDefault &&
        when (kind) {
            TranscriptBubbleKind.ToolRequest,
            TranscriptBubbleKind.ToolResult,
            -> true

            TranscriptBubbleKind.Status -> title?.let(::isExecutionActivityTitle) == true
            TranscriptBubbleKind.Message -> false
        }

internal val TranscriptDisplayItem.ExecutionGroup.summaryLine: String
    get() {
        if (activities.isEmpty()) {
            return "无步骤"
        }
        val titles = activities.map { it.summaryLine }
        return when {
            titles.size == 1 -> titles.first()
            titles.size == 2 -> titles.joinToString(" · ")
            else -> "${titles.take(2).joinToString(" · ")} 等 ${titles.size} 项"
        }
    }

internal fun parseTranscriptBubbles(transcript: String): List<TranscriptBubble> {
    if (transcript.isBlank()) {
        return emptyList()
    }

    val seeds = mutableListOf<TranscriptBubbleSeed>()
    transcript
        .trim()
        .split(Regex("\\r?\\n\\s*\\r?\\n"))
        .forEach { block ->
            val normalized = block.trim()
            if (normalized.isBlank()) {
                return@forEach
            }

            val parsed = parseTranscriptBlockSeed(normalized)
            if (parsed != null) {
                seeds += parsed
            } else if (seeds.isEmpty()) {
                seeds += buildStatusBubbleSeed(normalized)
            } else {
                val previous = seeds.removeAt(seeds.lastIndex)
                seeds += previous.copy(
                    rawBody = buildString {
                        append(previous.rawBody)
                        if (previous.rawBody.isNotBlank()) {
                            append("\n\n")
                        }
                        append(normalized)
                    },
                )
            }
        }

    return seeds.map(::materializeTranscriptBubble)
}

internal fun buildTranscriptDisplayItems(
    transcript: String,
    liveActivities: List<SessionActivityEntry> = emptyList(),
): List<TranscriptDisplayItem> {
    val bubbles = parseTranscriptBubbles(transcript)
    val items = mutableListOf<TranscriptDisplayItem>()
    val pendingActivities = mutableListOf<TranscriptBubble>()

    fun flushActivities() {
        if (pendingActivities.isEmpty()) {
            return
        }
        items += TranscriptDisplayItem.ExecutionGroup(pendingActivities.toList())
        pendingActivities.clear()
    }

    bubbles.forEach { bubble ->
        if (bubble.belongsToExecutionProcess) {
            pendingActivities += bubble
        } else {
            flushActivities()
            items += TranscriptDisplayItem.BubbleItem(bubble)
        }
    }
    flushActivities()

    val liveActivityBubbles = liveActivities.map { it.toTranscriptBubble() }
    if (liveActivityBubbles.isNotEmpty()) {
        val lastItem = items.lastOrNull()
        if (lastItem is TranscriptDisplayItem.ExecutionGroup) {
            items[items.lastIndex] = lastItem.copy(
                activities = lastItem.activities + liveActivityBubbles,
            )
        } else {
            items += TranscriptDisplayItem.ExecutionGroup(liveActivityBubbles)
        }
    }

    return items
}

private data class TranscriptBubbleSeed(
    val speaker: TranscriptSpeaker,
    val kind: TranscriptBubbleKind,
    val label: String,
    val rawBody: String,
    val preferTitleLine: Boolean = false,
)

private fun parseTranscriptBlockSeed(block: String): TranscriptBubbleSeed? {
    if (block.isBlank()) {
        return null
    }

    return when {
        block.startsWith("你：") -> TranscriptBubbleSeed(
            speaker = TranscriptSpeaker.User,
            kind = TranscriptBubbleKind.Message,
            label = "你",
            rawBody = block.removePrefix("你：").trim(),
        )

        block.startsWith("Codex：") -> TranscriptBubbleSeed(
            speaker = TranscriptSpeaker.Assistant,
            kind = TranscriptBubbleKind.Message,
            label = "Codex",
            rawBody = block.removePrefix("Codex：").trim(),
        )

        block.startsWith("系统：") -> TranscriptBubbleSeed(
            speaker = TranscriptSpeaker.System,
            kind = TranscriptBubbleKind.Status,
            label = "系统",
            rawBody = block.removePrefix("系统：").trim(),
            preferTitleLine = true,
        )

        block.startsWith("等待审批：") -> TranscriptBubbleSeed(
            kind = TranscriptBubbleKind.ToolRequest,
            speaker = TranscriptSpeaker.System,
            label = "系统",
            rawBody = block.removePrefix("等待审批：").trim(),
        )

        block.startsWith("审批结果：") -> TranscriptBubbleSeed(
            kind = TranscriptBubbleKind.ToolResult,
            speaker = TranscriptSpeaker.System,
            label = "系统",
            rawBody = block.removePrefix("审批结果：").trim(),
        )

        else -> null
    }
}

private fun materializeTranscriptBubble(seed: TranscriptBubbleSeed): TranscriptBubble {
    return when (seed.kind) {
        TranscriptBubbleKind.ToolRequest,
        TranscriptBubbleKind.ToolResult,
        -> buildStructuredSystemBubble(
            speaker = seed.speaker,
            kind = seed.kind,
            label = seed.label,
            rawBody = seed.rawBody,
        )

        TranscriptBubbleKind.Message,
        TranscriptBubbleKind.Status,
        -> buildBubble(
            speaker = seed.speaker,
            kind = seed.kind,
            label = seed.label,
            rawBody = seed.rawBody,
            preferTitleLine = seed.preferTitleLine,
        )
    }
}

private fun buildStructuredSystemBubble(
    speaker: TranscriptSpeaker,
    kind: TranscriptBubbleKind,
    label: String,
    rawBody: String,
): TranscriptBubble {
    val lines = rawBody.lines()
    val title = lines.firstOrNull()?.trim().orEmpty()
    val details = lines.drop(1).joinToString("\n").trim()
    val parts = if (details.isBlank()) {
        emptyList()
    } else {
        parseTranscriptParts(details)
    }

    return TranscriptBubble(
        speaker = speaker,
        kind = kind,
        label = label,
        title = title.ifBlank { null },
        rawBody = rawBody,
        parts = parts,
    )
}

private fun buildBubble(
    speaker: TranscriptSpeaker,
    kind: TranscriptBubbleKind,
    label: String,
    rawBody: String,
    preferTitleLine: Boolean = false,
): TranscriptBubble {
    val normalizedBody = rawBody.trim()
    val titleAndParts = if (preferTitleLine) {
        splitTitleAndParts(normalizedBody)
    } else {
        null to parseTranscriptParts(normalizedBody)
    }
    return TranscriptBubble(
        speaker = speaker,
        kind = kind,
        label = label,
        title = titleAndParts.first,
        rawBody = normalizedBody,
        parts = titleAndParts.second,
    )
}

private fun buildStatusBubbleSeed(body: String): TranscriptBubbleSeed {
    return TranscriptBubbleSeed(
        speaker = TranscriptSpeaker.System,
        kind = TranscriptBubbleKind.Status,
        label = "系统",
        rawBody = body,
        preferTitleLine = true,
    )
}

private fun splitTitleAndParts(body: String): Pair<String?, List<TranscriptPart>> {
    val normalized = body.trim()
    if (normalized.isBlank()) {
        return null to emptyList()
    }

    val lines = normalized.lines()
    if (lines.size <= 1) {
        val singleLine = lines.firstOrNull()?.trim().orEmpty()
        return if (isExecutionActivityTitle(singleLine)) {
            singleLine to emptyList()
        } else {
            null to parseTranscriptParts(normalized)
        }
    }

    val title = lines.first().trim().ifBlank { null }
    val details = lines.drop(1).joinToString("\n").trim()
    val parts = if (details.isBlank()) {
        emptyList()
    } else {
        parseTranscriptParts(details)
    }
    return title to parts
}

private fun isExecutionActivityTitle(title: String): Boolean {
    return title in ExecutionActivityTitles ||
        ExecutionActivityTitlePrefixes.any { prefix -> title.startsWith(prefix) }
}

private fun parseTranscriptParts(body: String): List<TranscriptPart> {
    val trimmed = body.trim()
    if (trimmed.isBlank()) {
        return emptyList()
    }

    val regex = Regex("(?s)```([A-Za-z0-9_+\\-.#]*)\\r?\\n(.*?)\\r?\\n```|!\\[(.*?)]\\((.*?)\\)")
    val parts = mutableListOf<TranscriptPart>()
    var cursor = 0

    for (match in regex.findAll(trimmed)) {
        val before = trimmed.substring(cursor, match.range.first).trim()
        if (before.isNotBlank()) {
            parts += TranscriptPart.Text(before)
        }

        val code = match.groupValues[2]
        if (code.isNotBlank()) {
            val language = match.groupValues[1].trim().ifBlank { null }
            val normalizedCode = code.trimEnd()
            if (normalizedCode.isNotBlank()) {
                parts += TranscriptPart.CodeBlock(code = normalizedCode, language = language)
            }
        } else {
            val altText = match.groupValues[3].trim().ifBlank { "图片" }
            val source = match.groupValues[4].trim()
            if (source.isNotBlank()) {
                parts += TranscriptPart.Image(
                    altText = altText,
                    source = source,
                )
            }
        }
        cursor = match.range.last + 1
    }

    val remaining = trimmed.substring(cursor).trim()
    if (remaining.isNotBlank()) {
        parts += TranscriptPart.Text(remaining)
    }

    return parts.ifEmpty { listOf(TranscriptPart.Text(trimmed)) }
}

private fun SessionActivityEntry.toTranscriptBubble(): TranscriptBubble {
    val parts = body
        ?.takeIf { it.isNotBlank() }
        ?.let(::parseTranscriptParts)
        ?: emptyList()
    return TranscriptBubble(
        speaker = TranscriptSpeaker.System,
        kind = TranscriptBubbleKind.Status,
        label = "系统",
        title = title,
        rawBody = buildString {
            append(title)
            if (!body.isNullOrBlank()) {
                append("\n")
                append(body)
            }
        },
        parts = parts,
    )
}
