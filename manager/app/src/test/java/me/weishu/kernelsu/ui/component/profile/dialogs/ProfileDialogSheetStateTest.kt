package me.weishu.kernelsu.ui.component.profile.dialogs

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class ProfileDialogSheetStateTest {
    @Test
    fun partialOnlyConfigurationReproducesReportedCrash() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            SheetState(
                enabledValues = setOf(SheetValue.PartiallyExpanded),
                positionalThreshold = { 56f },
                velocityThreshold = { 125f },
                initialValue = SheetValue.Hidden,
            )
        }

        assertEquals("Expanded must be one of the enabled values.", error.message)
    }

    @Test
    fun profileDialogCanStartHidden() {
        val state = SheetState(
            enabledValues = profileDialogSheetValues,
            positionalThreshold = { 56f },
            velocityThreshold = { 125f },
            initialValue = SheetValue.Hidden,
        )

        assertEquals(SheetValue.Hidden, state.currentValue)
        assertFalse(state.isVisible)
    }

    @Test
    fun profileDialogRestoresBothHiddenAndExpandedStates() {
        val saver = SheetState.Saver(
            enabledValues = profileDialogSheetValues,
            positionalThreshold = { 56f },
            velocityThreshold = { 125f },
            confirmValueChange = { true },
        )

        val hidden = requireNotNull(saver.restore(SheetValue.Hidden))
        assertEquals(SheetValue.Hidden, hidden.currentValue)
        assertFalse(hidden.isVisible)

        val expanded = requireNotNull(saver.restore(SheetValue.Expanded))
        assertEquals(SheetValue.Expanded, expanded.currentValue)
        assertTrue(expanded.isVisible)
    }
}
