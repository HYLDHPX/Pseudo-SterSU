package me.weishu.kernelsu.ui.util

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeStoreSidebarTest {
    private fun encode(config: SidebarWidgetConfig = SidebarWidgetConfig()): JSONObject =
        sidebarThemeToJson(config) { id, _ ->
            JSONObject().put("asset", JSONObject().put("path", "assets/sidebar_$id.png"))
        }

    @Test
    fun sidebarRoundTripIncludesLayoutWidgetsBothPalettesAndImagesWithoutWeatherSecrets() {
        val original = SidebarWidgetConfig(
            clockStyle = SidebarClockStyle.AuroraDigits,
            imageUriString = "content://private/avatar.png",
            imageShape = SidebarImageShape.Diamond,
            navigationPosition = SidebarNavigationPosition.Bottom,
            navigationOrder = SIDEBAR_NAVIGATION_IDS.reversed(),
            iconOnlyNavigation = true,
            showDividers = false,
            backgroundOpacity = 0.45f,
            side = SidebarSide.Right,
            material = SidebarMaterial.NeumorphicGlass,
            palettes = SidebarPalettes().withPalette(SidebarMaterial.NeumorphicGlass, false,
                defaultSidebarPalette(SidebarMaterial.NeumorphicGlass, false).copy(background = 0xFF123456)),
            homeLayout = SidebarHomeLayout.StatusCards,
            homeCards = SidebarHomeCards().withStyle(SidebarHomeCardId.StatusHero, true,
                defaultSidebarHomeCardStyle(true).copy(imageUri = "content://private/card.png", inheritImage = false)),
            weatherApi = SidebarWeatherConfig(enabled = true, url = "https://private.example/api?key=secret",
                headerName = "Authorization", headerValue = "private-weather-token", latitude = "34.12"),
        ).normalized()
        val json = encode(original)
        validateSidebarThemeJson(json)
        listOf("private.example", "private-weather-token", "content://", "latitude", "weatherApi", "Authorization")
            .forEach { assertFalse(it, json.toString().contains(it)) }
        val receiverWeather = SidebarWeatherConfig(url = "https://receiver.example/weather", headerValue = "receiver-token")
        val restored = sidebarThemeFromJson(JSONObject(json.toString()), SidebarWidgetConfig(weatherApi = receiverWeather)) { id, _ ->
            "file:///restored/$id.png"
        }
        assertEquals(original.copy(
            imageUriString = "file:///restored/avatar.png",
            homeCards = original.homeCards.copy(overrides = original.homeCards.overrides.mapValues { (id, card) ->
                card.copy(imageUri = "file:///restored/$id.png")
            }),
            weatherApi = receiverWeather,
        ), restored)
        assertEquals(2, sidebarThemeImageOwners(json).size)
    }

    @Test
    fun everyClockMaterialAndSideSurvivesRoundTrip() {
        SidebarClockStyle.entries.forEach { clock ->
            SidebarMaterial.entries.forEach { material ->
                SidebarSide.entries.forEach { side ->
                    val source = SidebarWidgetConfig(clockStyle = clock, material = material, side = side).normalized()
                    val restored = sidebarThemeFromJson(encode(source), SidebarWidgetConfig()) { _, _ -> error("No images expected") }
                    assertEquals(source.clockStyle, restored.clockStyle)
                    assertEquals(source.material, restored.material)
                    assertEquals(source.side, restored.side)
                }
            }
        }
    }

    @Test
    fun sidebarRejectsPrivateSettingsLocalImagesAndInvalidNavigation() {
        listOf<(JSONObject) -> Unit>(
            { it.put("weatherApi", JSONObject().put("headerValue", "secret")) },
            { it.put("backgroundOpacity", 1.5) },
            { it.put("side", "unsupported") },
            { it.put("navigationOrder", JSONArray(listOf("home", "home"))) },
            { it.put("navigationOrder", JSONArray(listOf("unknown"))) },
            { it.getJSONObject("images").put("avatar", JSONObject().put("uri", "file:///private/image.png")) },
            { it.getJSONObject("homeCards").put("status_hero.dark", JSONObject().put("image", "file:///private/image.png")) },
        ).forEach { mutate ->
            val json = encode().also(mutate)
            assertThrows(Exception::class.java) { validateSidebarThemeJson(json) }
        }
    }

    @Test
    fun sidebarsAndRendererAppearInPackageResourceCount() {
        val config = JSONObject().put("sidebar", encode())
            .put("interfaceStyle", JSONObject().put("mode", "sidebar_widget"))
        assertEquals(2, countConfiguredThemeStoreResources(config))
    }

    @Test
    fun themeImportUsesOneEditorAndDoesNotWriteWeatherConfiguration() {
        val style = File("src/main/java/me/weishu/kernelsu/ui/util/SidebarWidgetStyle.kt").readText()
        val writer = style.substringAfter("internal fun SharedPreferences.Editor.putSidebarWidgetAppearance(")
            .substringBefore("@Composable")
        assertFalse(writer.contains("writeSidebarWeatherConfig"))
        assertFalse(writer.contains(".edit("))
        val source = File("src/main/java/me/weishu/kernelsu/ui/util/ThemeStorePackage.kt").readText()
        assertTrue(source.contains("pendingSidebar?.let(editor::putSidebarWidgetAppearance)"))
        assertTrue(source.contains("pendingInterfaceStyle?.mode"))
        assertTrue(source.contains("if (!themePreferencesCommitted)"))
        assertTrue(source.contains("registry.cacheSharedArchive(shared)"))
    }
}
