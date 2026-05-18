package com.openai.codexmobile.ui.screen

internal enum class TranscriptSpeaker {
    User,
    Assistant,
    System,
}

internal data class TranscriptBubble(
    val speaker: TranscriptSpeaker,
    val text: String,
)

internal fun parseTranscriptBubbles(transcript: String): List<TranscriptBubble> {
    if (transcript.isBlank()) {
        return emptyList()
    }

    return transcript
        .trim()
        .split(Regex("\\r?\\n\\s*\\r?\\n"))
        .mapNotNull { block ->
            val normalized = block.trim()
            when {
                normalized.startsWith("你：") -> TranscriptBubble(
                    speaker = TranscriptSpeaker.User,
                    text = normalized.removePrefix("你：").trim(),
                )

                normalized.startsWith("Codex：") -> TranscriptBubble(
                    speaker = TranscriptSpeaker.Assistant,
                    text = normalized.removePrefix("Codex：").trim(),
                )

                normalized.startsWith("系统：") -> TranscriptBubble(
                    speaker = TranscriptSpeaker.System,
                    text = normalized.removePrefix("系统：").trim(),
                )

                normalized.isNotBlank() -> TranscriptBubble(
                    speaker = TranscriptSpeaker.System,
                    text = normalized,
                )

                else -> null
            }
        }
}
