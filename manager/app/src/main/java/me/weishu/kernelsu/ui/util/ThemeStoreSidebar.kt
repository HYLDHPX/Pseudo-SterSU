package me.weishu.kernelsu.ui.util

import org.json.JSONArray
import org.json.JSONObject

/** Only visual configuration is portable. Weather endpoints, headers and keys stay on the device. */
internal fun sidebarThemeToJson(
    config: SidebarWidgetConfig,
    image: (String, String) -> JSONObject,
): JSONObject {
    val value = config.normalized()
    val images = JSONObject()
    value.imageUriString?.let { images.put("avatar", image("avatar", it)) }
    val cards = value.homeCards.copy(overrides = value.homeCards.overrides.mapValues { (id, card) ->
        card.imageUri?.let { images.put(id, image(id, it)) }
        card.copy(imageUri = null)
    })
    return JSONObject()
        .put("widgetType", value.widgetType.value)
        .put("clockStyle", value.clockStyle.value)
        .put("clockWeatherEnabled", value.clockWeatherEnabled)
        .put("weatherLabel", value.weatherLabel)
        .put("weatherTemperature", value.weatherTemperature)
        .put("imageShape", value.imageShape.value)
        .put("navigationPosition", value.navigationPosition.value)
        .put("navigationOrder", JSONArray(value.navigationOrder))
        .put("iconOnlyNavigation", value.iconOnlyNavigation)
        .put("showDividers", value.showDividers)
        .put("backgroundOpacity", value.backgroundOpacity)
        .put("side", value.side.value)
        .put("material", value.material.value)
        .put("palettes", JSONObject(encodeSidebarPalettes(value.palettes)))
        .put("homeLayout", value.homeLayout.value)
        .put("homeCards", JSONObject(encodeSidebarHomeCards(cards)))
        .put("images", images)
}

internal fun sidebarThemeImageOwners(sidebar: JSONObject?): Map<String, JSONObject> {
    val images = sidebar?.optJSONObject("images") ?: return emptyMap()
    return images.keys().asSequence().associateWith { images.getJSONObject(it) }
}

internal fun sidebarThemeFromJson(
    json: JSONObject,
    current: SidebarWidgetConfig,
    image: (String, JSONObject) -> String?,
): SidebarWidgetConfig {
    validateSidebarThemeJson(json)
    val importedImages = sidebarThemeImageOwners(json).mapValues { (id, owner) -> image(id, owner) }
    val cards = decodeSidebarHomeCards(json.getJSONObject("homeCards").toString())
    return SidebarWidgetConfig(
        widgetType = SidebarWidgetType.fromValue(json.getString("widgetType")),
        clockStyle = SidebarClockStyle.fromValue(json.getString("clockStyle")),
        clockWeatherEnabled = json.getBoolean("clockWeatherEnabled"),
        weatherLabel = json.getString("weatherLabel"),
        weatherTemperature = json.getString("weatherTemperature"),
        imageUriString = importedImages["avatar"],
        imageShape = SidebarImageShape.fromValue(json.getString("imageShape")),
        navigationPosition = SidebarNavigationPosition.fromValue(json.getString("navigationPosition")),
        navigationOrder = json.getJSONArray("navigationOrder").let { order ->
            (0 until order.length()).map(order::getString)
        },
        iconOnlyNavigation = json.getBoolean("iconOnlyNavigation"),
        showDividers = json.getBoolean("showDividers"),
        backgroundOpacity = json.getDouble("backgroundOpacity").toFloat(),
        side = SidebarSide.fromValue(json.getString("side")),
        weatherApi = current.weatherApi,
        material = SidebarMaterial.fromValue(json.getString("material")),
        palettes = decodeSidebarPalettes(json.getJSONObject("palettes").toString()),
        homeLayout = SidebarHomeLayout.fromValue(json.getString("homeLayout")),
        homeCards = cards.copy(overrides = cards.overrides.mapValues { (id, card) ->
            card.copy(imageUri = importedImages[id])
        }),
    ).normalized()
}

internal fun validateSidebarThemeJson(json: JSONObject) {
    val keys = setOf(
        "widgetType", "clockStyle", "clockWeatherEnabled", "weatherLabel", "weatherTemperature",
        "imageShape", "navigationPosition", "navigationOrder", "iconOnlyNavigation", "showDividers",
        "backgroundOpacity", "side", "material", "palettes", "homeLayout", "homeCards", "images",
    )
    require(json.keys().asSequence().toSet() == keys) { "Invalid sidebar theme settings" }
    fun enumValue(key: String, values: List<String>) {
        require(json.opt(key) is String && json.getString(key) in values) { "Invalid sidebar $key" }
    }
    enumValue("widgetType", SidebarWidgetType.entries.map { it.value })
    enumValue("clockStyle", SidebarClockStyle.entries.map { it.value })
    enumValue("imageShape", SidebarImageShape.entries.map { it.value })
    enumValue("navigationPosition", SidebarNavigationPosition.entries.map { it.value })
    enumValue("side", SidebarSide.entries.map { it.value })
    enumValue("material", SidebarMaterial.entries.map { it.value })
    enumValue("homeLayout", SidebarHomeLayout.entries.map { it.value })
    listOf("clockWeatherEnabled", "iconOnlyNavigation", "showDividers").forEach { key ->
        require(json.opt(key) is Boolean) { "Invalid sidebar $key" }
    }
    require(json.opt("weatherLabel") is String && json.getString("weatherLabel").length <= 24 &&
        json.opt("weatherTemperature") is String && json.getString("weatherTemperature").length <= 12) {
        "Invalid sidebar weather labels"
    }
    val opacity = json.opt("backgroundOpacity") as? Number
    require(opacity != null && opacity.toDouble().isFinite() && opacity.toDouble() in 0.0..1.0) {
        "Invalid sidebar background opacity"
    }
    val order = json.getJSONArray("navigationOrder")
    val navigation = (0 until order.length()).map { order.get(it) }
    require(navigation.size <= SIDEBAR_NAVIGATION_IDS.size && navigation.distinct().size == navigation.size &&
        navigation.all { it is String && it in SIDEBAR_NAVIGATION_IDS }) { "Invalid sidebar navigation order" }
    val palettes = json.getJSONObject("palettes")
    require(palettes.toString().length <= 8192) { "Sidebar palettes are too large" }
    val cards = json.getJSONObject("homeCards")
    require(cards.toString().length <= 128 * 1024) { "Sidebar card settings are too large" }
    val cardIds = SidebarHomeCardId.entries.flatMap { listOf("${it.value}.light", "${it.value}.dark") }.toSet()
    require(cards.keys().asSequence().all { it in cardIds }) { "Invalid sidebar card id" }
    cards.keys().forEach { key ->
        require(!cards.getJSONObject(key).has("image")) { "Sidebar images must be embedded" }
    }
    val images = json.getJSONObject("images")
    require(images.keys().asSequence().all { it == "avatar" || cards.has(it) }) { "Invalid sidebar image id" }
    sidebarThemeImageOwners(json).values.forEach { owner ->
        require(owner.keys().asSequence().toSet() == setOf("asset") && owner.opt("asset") is JSONObject) {
            "Sidebar images must be embedded"
        }
    }
}
