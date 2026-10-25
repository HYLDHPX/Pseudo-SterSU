package me.weishu.kernelsu.ui.util

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeCommentsTest {
    @Test fun absentOrUnsafeServiceAddressesAreNotEnabled() {
        listOf("", "http://comments.example.com", "javascript:alert(1)", "file:///data/adb",
            "https://user:password@example.com", "https://example.com?token=secret", "https://example.com#key",
            "https://appassets.androidplatform.net").forEach { assertNull(it, normalizeWalineServerUrl(it)) }
    }

    @Test fun validHttpsServiceSupportsSubpathsAndRemovesTrailingSlashes() {
        assertEquals("https://comments.example.com/waline", normalizeWalineServerUrl(" https://comments.example.com/waline/ "))
        assertEquals("https://waline.nh24.top", normalizeWalineServerUrl("https://waline.nh24.top/"))
    }

    @Test fun differentKindsNeverShareTheirDiscussion() {
        assertEquals("/stersu/theme/sidebar", themeCommentPath("sidebar", false))
        assertEquals("/stersu/style/sidebar", themeCommentPath("sidebar", true))
    }

    @Test fun subjectIdsCannotEscapeTheThreadNamespace() {
        listOf("", "../secret", "theme?other=1", "theme#other", "a/b", "x".repeat(81)).forEach {
            assertTrue(runCatching { themeCommentPath(it, false) }.isFailure)
        }
    }

    @Test fun navigationOnlyRecognizesTheExactLocalPageOriginAndPath() {
        assertTrue(isThemeCommentsPage(THEME_COMMENTS_PAGE + "?path=test"))
        listOf("http://appassets.androidplatform.net/comments/index.html",
            "https://appassets.androidplatform.net.evil.test/comments/index.html",
            "https://user@appassets.androidplatform.net/comments/index.html",
            "https://appassets.androidplatform.net:443/comments/index.html",
            "https://appassets.androidplatform.net/comments/other.html").forEach {
            assertFalse(it, isThemeCommentsPage(it))
        }
    }

    @Test fun assetWhitelistDoesNotExposeOtherManagerAssets() {
        assertEquals("text/html", themeCommentAssetMimeType("index.html"))
        assertEquals("text/javascript", themeCommentAssetMimeType("vendor/waline.js"))
        assertEquals("text/javascript", themeCommentAssetMimeType("auth.js"))
        assertEquals("text/javascript", themeCommentAssetMimeType("network.js"))
        assertEquals("font/ttf", themeCommentAssetMimeType("font.ttf"))
        listOf("../theme-store/catalog-v1.json", "vendor/../../secret", "LICENSE", "index.html/extra", "").forEach {
            assertNull(themeCommentAssetMimeType(it))
        }
    }

    @Test fun popupHostAcceptsEquivalentUrlSerializationWithoutChangingTheThreadOrServer() {
        val expected = "$THEME_COMMENTS_PAGE?server=https%3A%2F%2Fcomments.example.com&path=%2Fstersu%2Ftheme%2Ftest&appearance=a%20b~c"
        assertTrue(isSameThemeCommentsPage(expected + "#reply", expected))
        assertTrue(isSameThemeCommentsPage("$THEME_COMMENTS_PAGE?appearance=a+b%7Ec&path=%2fstersu%2ftheme%2ftest&server=https%3a%2f%2fcomments.example.com", expected))
        listOf(null, "about:blank", expected.replace("comments.example.com", "other.example.com"),
            expected.replace("%2Ftheme%2Ftest", "%2Ftheme%2Fother"), "$expected&server=other",
            "$expected&extra=1", "$expected&%73erver=other", expected.replace("appearance=a%20b~c", "appearance=%ZZ"),
            expected.replace("appassets.androidplatform.net", "evil.example.com"))
            .forEach { assertFalse(it, isSameThemeCommentsPage(it, expected)) }
    }

    @Test fun androidArgbColorsAreConvertedToCssRgbaIncludingSurfaceAlpha() {
        assertEquals("#123456ff", themeCommentCssColor(0xff123456.toInt()))
        assertEquals("#12345680", themeCommentCssColor(0x80123456.toInt()))
        assertEquals("#00000000", themeCommentCssColor(0))
    }

    @Test fun commentWebViewKeepsItsUnprivilegedBoundary() {
        val screen = File("src/main/java/me/weishu/kernelsu/ui/screen/themestore/ThemeCommentsScreen.kt").readText()
        assertFalse(screen.contains("addJavascriptInterface"))
        assertFalse(screen.contains("import me.weishu.kernelsu.ui.webui"))
        listOf("allowFileAccess = false", "allowContentAccess = false", "MIXED_CONTENT_NEVER_ALLOW",
            "setGeolocationEnabled(false)", "setAcceptThirdPartyCookies(this, false)", "request.deny()",
            "request.hasGesture()", "isSameThemeCommentsPage(request.url.toString(), url)", "it.destroy()").forEach {
            assertTrue("Missing browser boundary: $it", screen.contains(it))
        }
    }

    @Test fun threadPathIsRetainedWhenUpdatingWalineAppearance() {
        val client = File("src/main/assets/theme-comments/comments.js").readText()
        assertTrue(client.contains("instance?.update({ dark: next, path: commentPath })"))
        assertTrue(client.contains("login: 'disable'"))
        assertTrue(client.contains("account.connect()"))
        assertTrue(client.contains("setAppearance(appearance)"))
        assertTrue(client.contains("imageUploader: false"))
        assertTrue(client.contains("instance?.destroy()"))
        val html = File("src/main/assets/theme-comments/index.html").readText()
        assertFalse(html.contains("unpkg.com"))
        assertFalse(html.contains("addJavascriptInterface"))
    }

    @Test fun emailAccountLivesInMyAndHasNoPopupOrProviderLogin() {
        val screen = File("src/main/java/me/weishu/kernelsu/ui/screen/themestore/ThemeCommentsScreen.kt").readText()
        val profile = File("src/main/java/me/weishu/kernelsu/ui/screen/themestore/ThemeStoreProfileScreen.kt").readText()
        val auth = File("src/main/assets/theme-comments/auth.js").readText()
        val session = File("src/main/assets/theme-comments/account-session.js").readText()
        assertTrue(profile.contains("navigator.push(Route.ThemeCommentAccount)"))
        assertTrue(screen.contains("if (accountMode)"))
        assertTrue(screen.contains("setSupportMultipleWindows(false)"))
        assertTrue(screen.contains("message.data == \"open-my\""))
        assertFalse(screen.contains("ThemeCommentLoginBrowser"))
        assertFalse(screen.contains("ThemeCommentBrowserSession"))
        assertFalse(auth.contains("window.open("))
        assertFalse(auth.contains("acceptWebLogin"))
        assertFalse(auth.contains("acceptBrowserLogin"))
        assertTrue(session.contains("credentials: 'omit'"))
        assertTrue(session.contains("redirect: 'error'"))
        assertTrue(session.contains("STERSU_WALINE_USER:"))
        assertTrue(session.contains("api('token', 'POST'"))
        assertTrue(session.contains("api('user', 'POST'"))
        assertTrue(session.contains("api('user', 'PUT'"))
        assertTrue(session.contains("api('token', 'GET'"))
        assertEquals("text/javascript", themeCommentAssetMimeType("account-session.js"))
    }

    @Test fun fastLoadingObservesTheRealReadAndDoesNotBlockOnAccountRestore() {
        val client = File("src/main/assets/theme-comments/comments.js").readText()
        assertTrue(client.contains("observeCommentReads(serverURL"))
        assertTrue(client.contains("void account.restore()"))
        assertFalse(client.contains("await account.restore()"))
        assertFalse(client.contains("type: 'count'"))
        assertTrue(client.contains(".wl-operation .wl-btn"))
        val auth = File("src/main/assets/theme-comments/auth.js").readText()
        assertTrue(auth.contains("guardSubmission"))
        assertTrue(auth.contains("event.stopImmediatePropagation()"))
        val screen = File("src/main/java/me/weishu/kernelsu/ui/screen/themestore/ThemeCommentsScreen.kt").readText()
        assertTrue(screen.contains("commentReads.read("))
        assertTrue(screen.contains("commentReads.close()"))
        assertTrue(screen.contains("consent:dns-v1:"))
        val network = File("src/main/java/me/weishu/kernelsu/ui/util/ThemeCommentReadClient.kt").readText()
        assertTrue(network.contains("dns(ThemeCommentDns())"))
        assertTrue(network.contains("followRedirects(false)"))
        assertFalse(network.contains("Dns.SYSTEM"))
        assertFalse(network.contains("hostnameVerifier("))
        assertFalse(network.contains("sslSocketFactory("))
    }
}
