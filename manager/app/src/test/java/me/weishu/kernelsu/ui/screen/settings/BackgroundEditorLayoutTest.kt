package me.weishu.kernelsu.ui.screen.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundEditorLayoutTest {
    @Test
    fun phonesRemainSingleColumn() {
        listOf(320f, 360f, 412f, 600f, 839f).forEach { width ->
            assertFalse(useBackgroundEditorTwoPane(width, 900f, 1f))
        }
    }

    @Test
    fun tabletsUseTwoPanesWhenBothDimensionsHaveRoom() {
        assertTrue(useBackgroundEditorTwoPane(840f, 480f, 1f))
        assertTrue(useBackgroundEditorTwoPane(1100f, 760f, 1.2f))
    }

    @Test
    fun shortLandscapeAndSplitWindowsStayScrollable() {
        assertFalse(useBackgroundEditorTwoPane(1000f, 479f, 1f))
        assertFalse(useBackgroundEditorTwoPane(1200f, 320f, 1f))
        assertFalse(useBackgroundEditorTwoPane(500f, 600f, 1f))
    }

    @Test
    fun enlargedTextGetsFullWidthInsteadOfNarrowColumns() {
        assertTrue(useBackgroundEditorTwoPane(900f, 600f, 1.35f))
        assertFalse(useBackgroundEditorTwoPane(900f, 600f, 1.36f))
        assertFalse(useBackgroundEditorTwoPane(1200f, 900f, 2f))
    }
}
