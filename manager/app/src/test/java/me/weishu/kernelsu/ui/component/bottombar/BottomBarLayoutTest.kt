package me.weishu.kernelsu.ui.component.bottombar

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomBarLayoutTest {
    @Test
    fun fourTabsKeepTheirPreferredSizeWhenThereIsRoom() {
        assertEquals(312.dp, floatingBottomBarWidth(361.dp, 4))
        assertEquals(312.dp, floatingBottomBarWidth(560.dp, 4))
    }

    @Test
    fun fiveTabsStayInsideAPhoneWithBothSideMargins() {
        // A 360dp window leaves 328dp after the 16dp side margins.
        assertEquals(328.dp, floatingBottomBarWidth(328.dp, 5))
        assertEquals(388.dp, floatingBottomBarWidth(440.dp, 5))
    }

    @Test
    fun resizingAndDisplayCutoutsNeverPushATabOutsideThePanel() {
        listOf(240, 280, 320, 360, 393, 480, 600).forEach { windowWidth ->
            listOf(0, 24, 48).forEach { cutout ->
                val available = (windowWidth - 32 - cutout).dp
                listOf(4, 5).forEach { tabs ->
                    val panel = floatingBottomBarWidth(available, tabs)
                    val item = (panel - 8.dp) / tabs
                    assertTrue(panel <= available)
                    assertTrue(item > 0.dp)
                    assertEquals(panel.value, (item * tabs + 8.dp).value, 0.001f)
                }
            }
        }
    }

    @Test
    fun transientZeroWidthDuringResizeDoesNotProduceANegativeSize() {
        assertEquals(0.dp, floatingBottomBarWidth(0.dp, 4))
        assertEquals(0.dp, floatingBottomBarWidth((-1).dp, 5))
    }
}
