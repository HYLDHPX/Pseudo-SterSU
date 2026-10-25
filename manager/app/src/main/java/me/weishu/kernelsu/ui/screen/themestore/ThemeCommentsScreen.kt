package me.weishu.kernelsu.ui.screen.themestore

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream
import java.util.Locale
import me.weishu.kernelsu.BuildConfig
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.passthrough.MaterialIcon as Icon
import me.weishu.kernelsu.ui.component.passthrough.MaterialText as Text
import me.weishu.kernelsu.ui.component.store.StoreEmptyState
import me.weishu.kernelsu.ui.component.store.StorePanel
import me.weishu.kernelsu.ui.component.store.StoreScaffold
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.theme.isInDarkTheme
import me.weishu.kernelsu.ui.theme.LocalImmersiveBackgroundActive
import me.weishu.kernelsu.ui.theme.LocalUiPassthroughSettings
import me.weishu.kernelsu.ui.util.APP_FONT_PREFERENCE_KEYS
import me.weishu.kernelsu.ui.util.AppFontPreset
import me.weishu.kernelsu.ui.util.THEME_COMMENTS_PAGE
import me.weishu.kernelsu.ui.util.THEME_COMMENTS_ORIGIN
import me.weishu.kernelsu.ui.util.ThemeCommentReadClient
import me.weishu.kernelsu.ui.util.appFontFile
import me.weishu.kernelsu.ui.util.isSameThemeCommentsPage
import me.weishu.kernelsu.ui.util.normalizeWalineServerUrl
import me.weishu.kernelsu.ui.util.readAppFontState
import me.weishu.kernelsu.ui.util.themeCommentAssetMimeType
import me.weishu.kernelsu.ui.util.themeCommentCssColor
import me.weishu.kernelsu.ui.util.themeCommentPath
import org.json.JSONObject

@Composable
internal fun ThemeCommentsEntry(subjectId: String, subjectName: String, interfaceStyle: Boolean = false) {
    val navigator = LocalNavigator.current
    StorePanel {
        Text(stringResource(R.string.theme_comments_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.theme_comments_summary), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = dropUnlessResumed {
                navigator.push(Route.ThemeComments(subjectId, subjectName, interfaceStyle))
            },
        ) { Text(stringResource(R.string.theme_comments_open)) }
    }
}

@Composable
fun ThemeCommentsScreen(subjectId: String, subjectName: String, interfaceStyle: Boolean, accountMode: Boolean = false) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val serverUrl = remember { normalizeWalineServerUrl(BuildConfig.WALINE_SERVER_URL) }
    val path = remember(subjectId, interfaceStyle, accountMode) {
        if (accountMode) "" else runCatching { themeCommentPath(subjectId, interfaceStyle) }.getOrNull()
    }
    val preferences = remember(context) { context.getSharedPreferences("theme_comments", 0) }
    var consent by remember(serverUrl) {
        mutableStateOf(serverUrl != null && preferences.getBoolean("consent:dns-v1:$serverUrl", false))
    }
    var webView by remember { mutableStateOf<WebView?>(null) }
    StoreScaffold(
        title = stringResource(if (accountMode) R.string.theme_comments_account_title else R.string.theme_comments_title),
        onBack = dropUnlessResumed { navigator.pop() },
        maxContentWidth = if (accountMode) 600.dp else 840.dp,
        actions = {
            if (consent && serverUrl != null && path != null) IconButton(onClick = {
                webView?.reload()
            }) {
                Icon(Icons.Outlined.Refresh, stringResource(R.string.theme_comments_retry))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!accountMode) Text(subjectName, Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (path == null || serverUrl == null || !consent) Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            ) { when {
                path == null -> StoreEmptyState(Icons.Rounded.ChatBubbleOutline,
                    stringResource(R.string.theme_comments_failed), stringResource(R.string.theme_comments_invalid_subject))
                serverUrl == null -> StoreEmptyState(Icons.Rounded.ChatBubbleOutline,
                    stringResource(R.string.theme_comments_unconfigured), stringResource(R.string.theme_comments_unconfigured_summary))
                !consent -> StoreEmptyState(Icons.Rounded.ChatBubbleOutline,
                    stringResource(R.string.theme_comments_privacy_title),
                    stringResource(R.string.theme_comments_privacy, serverUrl),
                    stringResource(R.string.theme_comments_agree), {
                        preferences.edit().putBoolean("consent:dns-v1:$serverUrl", true).apply()
                        consent = true
                    })
            } } else ThemeCommentsWebView(serverUrl, path, accountMode, Modifier.weight(1f),
                onViewChange = { webView = it },
                onOpenMy = dropUnlessResumed { navigator.push(Route.ThemeStoreMy) })
        }
    }
}

/** Unprivileged comments; the local auth action channel never exposes the module WebUI bridge. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ThemeCommentsWebView(
    serverUrl: String,
    path: String,
    accountMode: Boolean,
    modifier: Modifier,
    onViewChange: (WebView?) -> Unit,
    onOpenMy: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val dark by rememberUpdatedState(isInDarkTheme())
    val colors = MaterialTheme.colorScheme
    val immersive = LocalImmersiveBackgroundActive.current
    val passthrough = LocalUiPassthroughSettings.current.normalized()
    var font by remember(context) { mutableStateOf(readAppFontState(context)) }
    DisposableEffect(context) {
        val prefs = context.getSharedPreferences("settings", 0)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in APP_FONT_PREFERENCE_KEYS) font = readAppFontState(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val appearance by rememberUpdatedState(JSONObject().apply {
        put("dark", dark)
        fun color(key: String, value: Color) = put(key, themeCommentCssColor(value.toArgb()))
        color("background", if (immersive) Color.Transparent else colors.background)
        color("surface", colors.surface)
        color("surfaceVariant", colors.surfaceVariant)
        color("surfaceHover", colors.surfaceContainerHighest)
        color("foreground", colors.onSurface)
        color("muted", colors.onSurfaceVariant)
        color("primary", colors.primary)
        color("onPrimary", colors.onPrimary)
        color("outline", colors.outlineVariant)
        color("error", colors.error)
        put("fontScale", LocalDensity.current.fontScale.toDouble())
        put("fontFamily", when (font.preset) {
            AppFontPreset.System -> "system-ui"
            AppFontPreset.SansSerif -> "sans-serif"
            AppFontPreset.Serif -> "serif"
            AppFontPreset.Monospace -> "monospace"
            AppFontPreset.Cursive -> "cursive"
            AppFontPreset.Custom -> if (font.isCustomActive) "SterSUCustom" else "system-ui"
        })
        put("fontVersion", font.customSha256 ?: "")
        put("textOpacity", (font.opacity * if (passthrough.enabled) passthrough.textOpacity else 1f).toDouble())
        put("iconOpacity", (if (passthrough.enabled) passthrough.iconOpacity else 1f).toDouble())
        put("imageOpacity", (if (passthrough.enabled) passthrough.imageOpacity else 1f).toDouble())
    }.toString())
    val language = Locale.getDefault().toLanguageTag()
    val url = remember(serverUrl, path, language, accountMode) {
        Uri.parse(THEME_COMMENTS_PAGE).buildUpon().appendQueryParameter("server", serverUrl)
            .appendQueryParameter("mode", if (accountMode) "account" else "comments")
            .appendQueryParameter("path", path).appendQueryParameter("lang", language)
            .appendQueryParameter("appearance", appearance)
            .appendQueryParameter("dark", dark.toString()).build().toString()
    }
    val assetLoader = remember(context) {
        WebViewAssetLoader.Builder().addPathHandler("/comments/") { assetPath ->
            val mimeType = themeCommentAssetMimeType(assetPath)
            val stream = if (mimeType != null) runCatching {
                if (assetPath == "font.ttf") {
                    require(readAppFontState(context).isCustomActive)
                    appFontFile(context).inputStream()
                } else context.assets.open("theme-comments/$assetPath")
            }.getOrNull() else null
            if (stream != null) WebResourceResponse(mimeType, "UTF-8", stream)
            else WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(byteArrayOf()))
        }.build()
    }
    val commentReads = remember(serverUrl) { ThemeCommentReadClient(serverUrl) }
    DisposableEffect(commentReads) { onDispose { commentReads.close() } }
    var view by remember { mutableStateOf<WebView?>(null) }
    var loading by remember(url) { mutableStateOf(true) }
    var failed by remember(url) { mutableStateOf(false) }
    val openMy by rememberUpdatedState(onOpenMy)
    DisposableEffect(view, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> { view?.onResume(); view?.evaluateJavascript("window.stersuComments?.restoreAccount()", null) }
                Lifecycle.Event.ON_PAUSE -> { view?.onPause() }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    Box(modifier.fillMaxSize()) {
        if (failed) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                StoreEmptyState(Icons.Rounded.ChatBubbleOutline, stringResource(R.string.theme_comments_failed),
                    stringResource(R.string.theme_comments_retry_summary), stringResource(R.string.theme_comments_retry),
                    { failed = false; loading = true })
            }
        } else AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                WebView(it).apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    // CSS receives the effective app scale; do not apply WebView's system scale twice.
                    settings.textZoom = 100
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        javaScriptCanOpenWindowsAutomatically = false
                        setSupportMultipleWindows(false)
                        setGeolocationEnabled(false)
                    }
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                        WebViewCompat.addWebMessageListener(this, "SterSUCommentAccount", setOf(THEME_COMMENTS_ORIGIN)) {
                                sourceView, message, origin, isMainFrame, _ ->
                            if (sourceView === view && !accountMode && isMainFrame &&
                                origin.toString().trimEnd('/') == THEME_COMMENTS_ORIGIN &&
                                isSameThemeCommentsPage(sourceView.url, url) && message.data == "open-my") openMy()
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest) = request.deny()
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                            assetLoader.shouldInterceptRequest(request.url)?.let { return it }
                            // Only GET /api/comment uses the fixed DNS transport, never login/passwords or writes.
                            val reply = commentReads.read(request.url.toString(), request.method, request.requestHeaders) ?: return null
                            return WebResourceResponse("application/json", "UTF-8", reply.status, reply.reason,
                                reply.headers, ByteArrayInputStream(reply.body))
                        }

                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            if (isSameThemeCommentsPage(request.url.toString(), url)) return false
                            if (request.isForMainFrame && request.hasGesture() && request.url.scheme == "https" &&
                                request.url.userInfo == null && request.url.host != "appassets.androidplatform.net") {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                            }
                            return true
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            loading = false
                            view.evaluateJavascript("window.stersuComments?.setAppearance($appearance)", null)
                        }

                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) { loading = false; failed = true }
                        }

                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                            if (request.isForMainFrame) { loading = false; failed = true }
                        }

                        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                            loading = false
                            failed = true
                            return true
                        }
                    }
                    view = this
                    onViewChange(this)
                    loadUrl(url)
                }
            },
            update = {
                it.evaluateJavascript("window.stersuComments?.setAppearance($appearance)", null)
            },
            onRelease = {
                onViewChange(null)
                view = null
                it.stopLoading()
                it.destroy()
            },
        )
        if (loading && !failed) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}
