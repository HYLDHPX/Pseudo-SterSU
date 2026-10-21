package me.weishu.kernelsu.ui.util

import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.ui.screen.launchericon.launcherIconColumnCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmallWindowLayoutTest {
    @Test
    fun phoneFloatingWindowsDoNotLoseWidthToASidebar() {
        listOf(240f, 280f, 320f, 359f).forEach { width ->
            val window = AppWindowSize(width, 600f, 1f)
            assertTrue(window.compactNavigation)
            assertFalse(window.canShowSidebar)
        }
    }

    @Test
    fun shortLandscapeAndSplitWindowsUseCompactChrome() {
        listOf(240f, 320f, 479f).forEach { height ->
            assertTrue(AppWindowSize(840f, height, 1f).compactNavigation)
            assertFalse(shouldShowSplitPane(840f, height))
        }
    }

    @Test
    fun fullSizePhoneAndTabletKeepTheirUsualNavigation() {
        assertTrue(AppWindowSize(393f, 780f, 1f).canShowSidebar)
        assertTrue(AppWindowSize(840f, 960f, 1f).canShowSidebar)
        assertFalse(AppWindowSize(360f, 480f, 1f).compactNavigation)
    }

    @Test
    fun enlargedTextGetsMoreHorizontalSpace() {
        assertTrue(AppWindowSize(393f, 780f, 1.3f).compactNavigation)
        assertFalse(AppWindowSize(840f, 960f, 2f).compactNavigation)
    }

    @Test
    fun resizingReevaluatesLayoutInBothDirections() {
        val large = AppWindowSize(840f, 960f, 1f)
        val floating = large.copy(widthDp = 280f, heightDp = 360f)
        assertTrue(large.canShowSidebar)
        assertFalse(floating.canShowSidebar)
        assertTrue(floating.copy(widthDp = 840f, heightDp = 960f).canShowSidebar)
    }

    @Test
    fun dialogContentNeverExceedsTheAvailableHeight() {
        listOf(0f, 80f, 160f, 240f, 320f, 480f, 960f).forEach { height ->
            listOf(1f, 1.3f, 2f).forEach { scale ->
                val content = dialogContentHeight(height, scale)
                assertTrue(content >= 0f)
                assertTrue(content <= height)
            }
        }
        assertTrue(dialogContentHeight(320f, 1f) < 320f)
        assertTrue(dialogContentHeight(480f, 1.5f) < dialogContentHeight(480f, 1f))
    }

    @Test
    fun iconGridReducesColumnsInNarrowWindows() {
        assertEquals(2, launcherIconColumnCount(240.dp))
        assertEquals(3, launcherIconColumnCount(320.dp))
        assertEquals(4, launcherIconColumnCount(360.dp))
        assertEquals(6, launcherIconColumnCount(840.dp))
    }
}
