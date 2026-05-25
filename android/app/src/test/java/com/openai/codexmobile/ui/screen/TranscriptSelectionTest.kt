package com.openai.codexmobile.ui.screen

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptSelectionTest {
    @Test
    fun touchInsideActiveBubbleKeepsSelection() {
        val containerBounds = Rect(
            left = 20f,
            top = 40f,
            right = 420f,
            bottom = 840f,
        )
        val bubbleBounds = Rect(
            left = 36f,
            top = 120f,
            right = 260f,
            bottom = 320f,
        )

        val shouldClear = shouldClearTranscriptSelection(
            activeBubbleBoundsInRoot = bubbleBounds,
            containerBoundsInRoot = containerBounds,
            touchPositionInContainer = Offset(40f, 100f),
        )

        assertFalse(shouldClear)
    }

    @Test
    fun touchOutsideActiveBubbleClearsSelection() {
        val containerBounds = Rect(
            left = 20f,
            top = 40f,
            right = 420f,
            bottom = 840f,
        )
        val bubbleBounds = Rect(
            left = 36f,
            top = 120f,
            right = 260f,
            bottom = 320f,
        )

        val shouldClear = shouldClearTranscriptSelection(
            activeBubbleBoundsInRoot = bubbleBounds,
            containerBoundsInRoot = containerBounds,
            touchPositionInContainer = Offset(320f, 120f),
        )

        assertTrue(shouldClear)
    }

    @Test
    fun missingLayoutBoundsDoesNotClearSelection() {
        assertFalse(
            shouldClearTranscriptSelection(
                activeBubbleBoundsInRoot = null,
                containerBoundsInRoot = null,
                touchPositionInContainer = Offset.Zero,
            ),
        )
    }
}
