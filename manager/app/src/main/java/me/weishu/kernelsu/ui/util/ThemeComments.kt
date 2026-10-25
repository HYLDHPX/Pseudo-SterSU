package me.weishu.kernelsu.ui.util

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

internal const val THEME_COMMENTS_ORIGIN = "https://appassets.androidplatform.net"
internal const val THEME_COMMENTS_PAGE = "$THEME_COMMENTS_ORIGIN/comments/index.html"

/** Android uses ARGB; CSS eight-digit colors use RGBA. */
internal fun themeCommentCssColor(argb: Int): String =
    String.format(Locale.ROOT, "#%08x", (argb shl 8) or (argb ushr 24))

internal fun normalizeWalineServerUrl(value: String): String? = runCatching {
    val uri = URI(value.trim())
    require(uri.scheme == "https" && !uri.host.isNullOrBlank())
    require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
    require(uri.host != "appassets.androidplatform.net")
    uri.toASCIIString().trimEnd('/')
}.getOrNull()

internal fun themeCommentPath(subjectId: String, interfaceStyle: Boolean): String {
    require(Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,79}").matches(subjectId)) { "Invalid comment subject" }
    val kind = if (interfaceStyle) "style" else "theme"
    return "/stersu/$kind/$subjectId"
}

internal fun isThemeCommentsPage(url: String): Boolean = runCatching {
    val uri = URI(url)
    uri.scheme == "https" && uri.host == "appassets.androidplatform.net" &&
        uri.port == -1 && uri.rawUserInfo == null && uri.path == "/comments/index.html"
}.getOrDefault(false)

/** WebView may serialize a URL differently; compare decoded parameters, not raw strings. */
internal fun isSameThemeCommentsPage(actual: String?, expected: String): Boolean = runCatching {
    if (actual == null || !isThemeCommentsPage(actual) || !isThemeCommentsPage(expected)) return false
    fun parameters(url: String): Map<String, String> {
        val query = URI(url).rawQuery ?: return emptyMap()
        fun decode(value: String) = URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        val entries = query.split('&').map {
            require(it.isNotEmpty())
            decode(it.substringBefore('=')) to decode(it.substringAfter('=', ""))
        }
        require(entries.map { it.first }.distinct().size == entries.size)
        return entries.toMap()
    }
    parameters(actual) == parameters(expected)
}.getOrDefault(false)

internal fun themeCommentAssetMimeType(path: String): String? = when (path) {
    "index.html" -> "text/html"
    "comments.js", "auth.js", "account-session.js", "network.js", "vendor/waline.js" -> "text/javascript"
    "comments.css", "vendor/waline.css" -> "text/css"
    "font.ttf" -> "font/ttf"
    else -> null
}
