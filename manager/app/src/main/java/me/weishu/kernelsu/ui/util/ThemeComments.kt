package me.weishu.kernelsu.ui.util

import java.net.URI

internal const val THEME_COMMENTS_ORIGIN = "https://appassets.androidplatform.net"
internal const val THEME_COMMENTS_PAGE = "$THEME_COMMENTS_ORIGIN/comments/index.html"

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

internal fun themeCommentAssetMimeType(path: String): String? = when (path) {
    "index.html" -> "text/html"
    "comments.js", "vendor/waline.js" -> "text/javascript"
    "comments.css", "vendor/waline.css" -> "text/css"
    else -> null
}
