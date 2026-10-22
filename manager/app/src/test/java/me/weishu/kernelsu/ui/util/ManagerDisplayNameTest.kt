package me.weishu.kernelsu.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ManagerDisplayNameTest {
    @Test
    fun disabledSyncPreservesDefaultNameAndStatus() {
        assertEquals("SterSU", managerDisplayName("NHSU", "SterSU", false))
        assertEquals("SterSU active", managerStatusLabel("SterSU active", "SterSU", "NHSU", false))
    }

    @Test
    fun emptyOrWhitespaceNameRestoresDefault() {
        listOf("", "   ", "\n\t").forEach { name ->
            assertEquals("SterSU", managerDisplayName(name, "SterSU", true))
            assertEquals("SterSU active", managerStatusLabel("SterSU active", "SterSU", name, true))
        }
    }

    @Test
    fun nameIsTrimmedWithoutChangingInternalCharacters() {
        assertEquals("My Manager", managerDisplayName("  My Manager  ", "SterSU", true))
        assertEquals("My Manager active", managerStatusLabel("SterSU active", "SterSU", " My Manager ", true))
    }

    @Test
    fun localizedBrandedStatusKeepsItsOriginalStatusText() {
        assertEquals(
            "NHSU \u5df2\u6fc0\u6d3b",
            managerStatusLabel("SterSU \u5df2\u6fc0\u6d3b", "SterSU", "NHSU", true),
        )
    }

    @Test
    fun statusWithoutBrandKeepsTheLocalizedText() {
        assertEquals("NHSU \u00b7 Funktioniert", managerStatusLabel("Funktioniert", "SterSU", "NHSU", true))
        assertEquals("NHSU \u00b7 Running normally", managerStatusLabel("Running normally", "SterSU", "NHSU", true))
    }

    @Test
    fun defaultNameDoesNotAddRedundantBrandToStatus() {
        assertEquals("Running normally", managerStatusLabel("Running normally", "SterSU", " SterSU ", true))
    }

    @Test
    fun specialCharactersAndLongNamesAreLiteralText() {
        val name = "A.(B)[C] %1\$s / ".padEnd(40, 'x')
        assertEquals(name + " active", managerStatusLabel("SterSU active", "SterSU", name, true))
    }

    @Test
    fun syncDestinationsAreIndependent() {
        assertEquals("NHSU", managerDisplayName("NHSU", "SterSU", true))
        assertEquals("SterSU", managerDisplayName("NHSU", "SterSU", false))
        assertEquals("SterSU active", managerStatusLabel("SterSU active", "SterSU", "NHSU", false))
        assertEquals("NHSU active", managerStatusLabel("SterSU active", "SterSU", "NHSU", true))
    }
}
