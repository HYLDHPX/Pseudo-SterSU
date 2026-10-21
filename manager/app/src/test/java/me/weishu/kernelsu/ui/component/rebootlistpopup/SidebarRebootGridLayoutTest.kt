package me.weishu.kernelsu.ui.component.rebootlistpopup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SidebarRebootGridLayoutTest {
    @Test
    fun regularMenuKeepsThreeEqualSquareTilesPerRow() {
        val layout = sidebarRebootGridLayout(288f, 1f)
        assertEquals(3, layout.columns)
        assertEquals((288f - 24f - 16f) / 3f, layout.tileHeightDp, 0.01f)
    }

    @Test
    fun largeTextGetsFewerColumnsInsteadOfClippedLabels() {
        assertEquals(2, sidebarRebootGridLayout(288f, 1.3f).columns)
        assertEquals(2, sidebarRebootGridLayout(288f, 2f).columns)
        assertEquals(1, sidebarRebootGridLayout(288f, 3f).columns)
    }

    @Test
    fun narrowMenuKeepsEnoughHeightForIconAndTwoLabelLines() {
        val layout = sidebarRebootGridLayout(96f, 2f)
        assertEquals(1, layout.columns)
        assertEquals(108f, layout.tileHeightDp, 0.01f)
    }

    @Test
    fun allWindowAndFontSizesKeepTheIconAndLabelBlockInsideTheTile() {
        for (width in listOf(0f, 96f, 180f, 220f, 264f, 288f)) {
            for (fontScale in listOf(0.85f, 1f, 1.3f, 2f, 3f)) {
                val layout = sidebarRebootGridLayout(width, fontScale)
                assertTrue(layout.columns in 1..3)
                assertTrue(layout.tileHeightDp >= 52f + 28f * fontScale.coerceAtLeast(1f))
                if (layout.columns > 1) {
                    val availableTileWidth = (width - 24f - 8f * (layout.columns - 1)) / layout.columns
                    assertTrue(layout.tileHeightDp <= availableTileWidth + 0.01f)
                }
            }
        }
    }
}
