package me.weishu.kernelsu.ui.screen.settings

import me.weishu.kernelsu.ui.InterfaceStyle
import me.weishu.kernelsu.ui.util.ManagerPlugin
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsCatalogTest {
    @Test
    fun rainDesignIsVisibleOnlyWhenRainIsTheActiveInterfaceStyle() {
        InterfaceStyle.entries.forEach { style ->
            val entries = SettingsCatalog.entriesFor(SettingsCategory.Appearance, SettingsUiState(uiMode = style.value))
            assertEquals(style.name, style == InterfaceStyle.Rain, entries.any { it.key == "rain_design" })
        }
    }

    @Test
    fun pluginCountTracksInstalledAndEnabledEntries() {
        val base = SettingsUiState(uiMode = InterfaceStyle.Material.value)
        assertEquals(1, SettingsCatalog.visibleEntryCount(SettingsCategory.Plugins, base))
        val installed = base.copy(installedPluginIds = ManagerPlugin.entries.map { it.id }.toSet())
        assertEquals(9, SettingsCatalog.visibleEntryCount(SettingsCategory.Plugins, installed))
        assertEquals(
            10,
            SettingsCatalog.visibleEntryCount(
                SettingsCategory.Plugins,
                installed.copy(graphicsRendererFeatureEnabled = true),
            ),
        )
        val kpm = base.copy(
            runtimeModeResolved = true,
            isLkmMode = true,
            kpmBackend = "kpatch-next",
            isKpmManagementAvailable = true,
            isKpmCapabilityResolved = true,
        )
        assertEquals(1, SettingsCatalog.visibleEntryCount(SettingsCategory.Plugins, kpm))
        assertEquals(true, SettingsCatalog.entriesFor(SettingsCategory.MountAndHide, kpm).any { it.key == "kpm" })
    }

    @Test
    fun appearanceCountTracksInterfaceSpecificEntry() {
        val base = SettingsUiState(uiMode = InterfaceStyle.Material.value)
        assertEquals(3, SettingsCatalog.visibleEntryCount(SettingsCategory.Appearance, base))
        assertEquals(
            4,
            SettingsCatalog.visibleEntryCount(
                SettingsCategory.Appearance,
                base.copy(uiMode = InterfaceStyle.Miuix.value),
            ),
        )
        assertEquals(
            4,
            SettingsCatalog.visibleEntryCount(
                SettingsCategory.Appearance,
                base.copy(uiMode = InterfaceStyle.Pixel.value),
            ),
        )
    }

    @Test
    fun sidebarDesignIsOnlyDiscoverableForTheBeautifiedMiuixStyle() {
        for (style in InterfaceStyle.entries) {
            val state = SettingsUiState(uiMode = style.value)
            val expected = style == InterfaceStyle.SidebarWidget
            val entries = SettingsCatalog.entriesFor(SettingsCategory.Appearance, state)
            assertEquals(style.name, expected, state.isSidebarDesignEntryVisible)
            assertEquals(style.name, if (expected) 1 else 0, entries.count { it.key == "sidebar_design" })
        }
    }

    @Test
    fun sidebarDesignDisappearsAfterSwitchingAwayWithoutClearingTheSavedStyle() {
        val sidebar = SettingsUiState(
            uiMode = InterfaceStyle.SidebarWidget.value,
            activeInterfaceStyleId = "sidebar",
        )
        assertEquals(true, sidebar.isSidebarDesignEntryVisible)
        for (style in InterfaceStyle.entries.filterNot { it == InterfaceStyle.SidebarWidget }) {
            val switched = sidebar.copy(uiMode = style.value)
            assertEquals(style.name, false, switched.isSidebarDesignEntryVisible)
            assertEquals(style.name, false, SettingsCatalog.entriesFor(SettingsCategory.Appearance, switched)
                .any { it.key == "sidebar_design" })
        }
    }

    @Test
    fun rootAndMountControlsUseTheirOwnCategories() {
        val base = SettingsUiState()
        assertEquals(
            setOf("profile_template", "su_compat", "su_log", "adb_root", "soft_reboot", "auto_jailbreak"),
            SettingsCatalog.entriesFor(SettingsCategory.RootAndPermissions, base).map { it.key }.toSet(),
        )
        assertEquals(
            setOf("kernel_umount", "webview_umount", "default_umount", "selinux_hide", "avc_spoof", "path_config", "apkesu_hide"),
            SettingsCatalog.entriesFor(SettingsCategory.MountAndHide, base).map { it.key }.toSet(),
        )
    }

    @Test
    fun stealthEntriesAreBuiltinAndRemotePluginIsRetired() {
        val base = SettingsUiState()
        assertEquals(listOf("plugin_store"), SettingsCatalog.entriesFor(SettingsCategory.Plugins, base).map { it.key })
        val installed = base.copy(installedPluginIds = setOf(ManagerPlugin.RemoteManagementSuite.id))
        assertEquals(1, SettingsCatalog.visibleEntryCount(SettingsCategory.Plugins, installed))
        assertEquals(
            listOf("web_debugging", "stealth_mode", "stealth_mode_code"),
            SettingsCatalog.entriesFor(SettingsCategory.AppAndMaintenance, installed)
                .filter { it.key.startsWith("web_") || it.key.startsWith("stealth_") }
                .map { it.key },
        )
    }

    @Test
    fun unifiedStoreHasOneCategoryEntry() {
        val base = SettingsUiState()
        assertEquals(
            listOf("store"),
            SettingsCategory.entries.flatMap { SettingsCatalog.entriesFor(it, base) }
                .filter { it.key == "store" }
                .map { it.key },
        )
        assertEquals(
            SettingsCategory.Appearance,
            SettingsCatalog.entriesFor(SettingsCategory.Appearance, base)
                .single { it.key == "store" }.category,
        )
    }

    @Test
    fun pluginStoreIsAlwaysDiscoverableFromPlugins() {
        for (style in InterfaceStyle.entries) {
            val state = SettingsUiState(uiMode = style.value)
            assertEquals(
                listOf("plugin_store"),
                SettingsCatalog.entriesFor(SettingsCategory.Plugins, state).map { it.key },
            )
            assertEquals(
                0,
                SettingsCatalog.entriesFor(SettingsCategory.Appearance, state).count { it.key == "plugin_store" },
            )
        }
    }

    @Test
    fun homeAndManagerKeepsIdentitySettingsWithoutStandaloneHomeTitle() {
        assertEquals(
            listOf("manager_identity", "dynamic_manager", "support_card", "learn_card"),
            SettingsCatalog.entriesFor(SettingsCategory.HomeAndManager, SettingsUiState()).map { it.key },
        )
        assertEquals(
            false,
            SettingsCatalog.entriesFor(SettingsCategory.HomeAndManager, SettingsUiState())
                .any { it.key == "home_layout" },
        )
    }

    @Test
    fun mountAndHideDoesNotOfferBundledKpatchNext() {
        val entries = SettingsCatalog.entriesFor(SettingsCategory.MountAndHide, SettingsUiState())
        assertEquals(false, entries.any { it.key == "kpatch_next" || it.key == "kpatch_webui" })
    }

    @Test
    fun lkmPathConfigStaysSeparateFromGkiSusfs() {
        val lkm = SettingsUiState(isLkmMode = true, runtimeModeResolved = true)
        assertEquals(false, SettingsCatalog.entriesFor(SettingsCategory.MountAndHide, lkm).any { it.key == "path_config" })
        assertEquals(true, SettingsCatalog.entriesFor(SettingsCategory.MountAndHide, lkm.copy(isLkmMode = false)).any { it.key == "path_config" })
        assertEquals(true, SettingsCatalog.entriesFor(SettingsCategory.MountAndHide, lkm).any { it.key == "susfs_lkm" })
        assertEquals(false, SettingsCatalog.entriesFor(SettingsCategory.MountAndHide, lkm.copy(isLkmMode = false)).any { it.key == "susfs_lkm" })
        assertEquals(false, SettingsCatalog.entriesFor(SettingsCategory.MountAndHide, lkm.copy(isLateLoadMode = true)).any { it.key == "susfs_lkm" })
    }

}
