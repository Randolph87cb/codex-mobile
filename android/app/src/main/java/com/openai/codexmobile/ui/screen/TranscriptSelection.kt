package com.openai.codexmobile.ui.screen

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

internal fun shouldClearTranscriptSelection(
    activeBubbleBoundsInRoot: Rect?,
    containerBoundsInRoot: Rect?,
    touchPositionInContainer: Offset,
): Boolean {
    if (activeBubbleBoundsInRoot == null || containerBoundsInRoot == null) {
        return false
    }
    val touchPositionInRoot = containerBoundsInRoot.topLeft + touchPositionInContainer
    return !activeBubbleBoundsInRoot.contains(touchPositionInRoot)
}
