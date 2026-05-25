package com.openai.codexmobile.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class TranscriptColors(
    val assistantBubbleContainer: Color,
    val assistantBubbleBorder: Color,
    val userBubbleContainer: Color,
    val userBubbleBorder: Color,
    val systemBubbleContainer: Color,
    val systemBubbleBorder: Color,
    val toolBubbleContainer: Color,
    val codeBlockContainer: Color,
    val codeBlockBorder: Color,
    val bubbleContent: Color,
    val bubbleMutedContent: Color,
    val codeBlockContent: Color,
    val inlineCodeBackground: Color,
    val inlineCodeContent: Color,
    val link: Color,
)

internal val LightTranscriptColors = TranscriptColors(
    assistantBubbleContainer = Color(0xFFEAF1F8),
    assistantBubbleBorder = Color(0xFFD5E0EA),
    userBubbleContainer = Color(0xFFFFF1F3),
    userBubbleBorder = Color(0xFFF3D4D9),
    systemBubbleContainer = Color(0xFFF3F4F5),
    systemBubbleBorder = Color(0xFFC3C6CF),
    toolBubbleContainer = Color(0xFFF3F4F5),
    codeBlockContainer = Color(0xFFF8FAFC),
    codeBlockBorder = Color(0xFFC3C6CF),
    bubbleContent = Color(0xFF191C1D),
    bubbleMutedContent = Color(0xFF43474E),
    codeBlockContent = Color(0xFF191C1D),
    inlineCodeBackground = Color(0x1F000000),
    inlineCodeContent = Color(0xFF191C1D),
    link = Color(0xFF356AC3),
)

internal val DarkTranscriptColors = TranscriptColors(
    assistantBubbleContainer = Color(0xFF1B2430),
    assistantBubbleBorder = Color(0xFF31435A),
    userBubbleContainer = Color(0xFF40252B),
    userBubbleBorder = Color(0xFF6A3A43),
    systemBubbleContainer = Color(0xFF20262D),
    systemBubbleBorder = Color(0xFF39424D),
    toolBubbleContainer = Color(0xFF20262D),
    codeBlockContainer = Color(0xFF111827),
    codeBlockBorder = Color(0xFF263244),
    bubbleContent = Color(0xFFF0F4FA),
    bubbleMutedContent = Color(0xFFC7D2E3),
    codeBlockContent = Color(0xFFEAF2FF),
    inlineCodeBackground = Color(0xFF243246),
    inlineCodeContent = Color(0xFFEAF2FF),
    link = Color(0xFF8FB8FF),
)

val LocalTranscriptColors = staticCompositionLocalOf { LightTranscriptColors }

@Composable
@ReadOnlyComposable
fun codexTranscriptColors(): TranscriptColors = LocalTranscriptColors.current
