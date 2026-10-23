package me.weishu.kernelsu.ui.screen.themestore

import android.annotation.SuppressLint
import android.content.Intent
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.webkit.WebViewAssetLoader
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
import me.weishu.kernelsu.ui.util.THEME_COMMENTS_PAGE
import me.weishu.kernelsu.ui.util.isThemeCommentsPage
import me.weishu.kernelsu.ui.util.normalizeWalineServerUrl
import me.weishu.kernelsu.ui.util.themeCommentAssetMimeType
import me.weishu.kernelsu.ui.util.themeCommentPath

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
fun ThemeCommentsScreen(subjectId: String, subjectName: String, interfaceStyle: Boolean) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val serverUrl = remember { normalizeWalineServerUrl(BuildConfig.WALINE_SERVER_URL) }
    val path = remember(subjectId, interfaceStyle) {
        runCatching { themeCommentPath(subjectId, interfaceStyle) }.getOrNull()
    }
    val preferences = remember(context) { context.getSharedPreferences("theme_comments", 0) }
    var consent by remember(serverUrl) {
        mutableStateOf(serverUrl != null && preferences.getBoolean("consent:$serverUrl", false))
    }
    var webView by remember { mutableStateOf<WebView?>(null) }
    StoreScaffold(
        title = stringResource(R.string.theme_comments_title),
        onBack = dropUnlessResumed { navigator.pop() },
        maxContentWidth = 840.dp,
        actions = {
            if (consent && serverUrl != null && path != null) IconButton(onClick = { webView?.reload() }) {
                Icon(Icons.Outlined.Refresh, stringResource(R.string.theme_comments_retry))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(subjectName, Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
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
                        preferences.edit().putBoolean("consent:$serverUrl", true).apply()
                        consent = true
                    })
            } } else ThemeCommentsWebView(serverUrl, path, Modifier.weight(1f), onViewChange = { webView = it })
        }
    }
}

/** Unprivileged comment browser. Never use the module WebUI helper or its JavaScript bridges here. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ThemeCommentsWebView(
    serverUrl: String,
    path: String,
    modifier: Modifier,
    onViewChange: (WebView?) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val dark by rememberUpdatedState(isInDarkTheme())
    val background = MaterialTheme.colorScheme.background.toArgb()
    val language = Locale.getDefault().toLanguageTag()
    val url = remember(serverUrl, path, language) {
        Uri.parse(THEME_COMMENTS_PAGE).buildUpon().appendQueryParameter("server", serverUrl)
            .appendQueryParameter("path", path).appendQueryParameter("lang", language)
            .appendQueryParameter("dark", dark.toString()).build().toString()
    }
    val assetLoader = remember(context) {
        WebViewAssetLoader.Builder().addPathHandler("/comments/") { assetPath ->
            val mimeType = themeCommentAssetMimeType(assetPath)
            val stream = if (mimeType != null) runCatching {
                context.assets.open("theme-comments/$assetPath")
            }.getOrNull() else null
            if (stream != null) WebResourceResponse(mimeType, "UTF-8", stream)
            else WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(byteArrayOf()))
        }.build()
    }
    var view by remember { mutableStateOf<WebView?>(null) }
    var loading by remember(url) { mutableStateOf(true) }
    var failed by remember(url) { mutableStateOf(false) }
    DisposableEffect(view, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view?.onResume()
                Lifecycle.Event.ON_PAUSE -> view?.onPause()
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
                    setBackgroundColor(background)
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
                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest) = request.deny()
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                            assetLoader.shouldInterceptRequest(request.url)

                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            if (isThemeCommentsPage(request.url.toString()) &&
                                request.url.toString().substringBefore('#') == url) return false
                            if (request.isForMainFrame && request.hasGesture() && request.url.scheme == "https" &&
                                request.url.userInfo == null && request.url.host != "appassets.androidplatform.net") {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                            }
                            return true
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            loading = false
                            view.evaluateJavascript("window.stersuComments?.setDarkMode($dark)", null)
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
            update = { it.setBackgroundColor(background); it.evaluateJavascript("window.stersuComments?.setDarkMode($dark)", null) },
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
