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
        listOf("../theme-store/catalog-v1.json", "vendor/../../secret", "LICENSE", "index.html/extra", "").forEach {
            assertNull(themeCommentAssetMimeType(it))
        }
    }

    @Test fun commentWebViewKeepsItsUnprivilegedBoundary() {
        val screen = File("src/main/java/me/weishu/kernelsu/ui/screen/themestore/ThemeCommentsScreen.kt").readText()
        assertFalse(screen.contains("addJavascriptInterface"))
        assertFalse(screen.contains("import me.weishu.kernelsu.ui.webui"))
        listOf("allowFileAccess = false", "allowContentAccess = false", "MIXED_CONTENT_NEVER_ALLOW",
            "setGeolocationEnabled(false)", "setAcceptThirdPartyCookies(this, false)", "request.deny()",
            "request.hasGesture()", "substringBefore('#') == url", "it.destroy()").forEach {
            assertTrue("Missing browser boundary: $it", screen.contains(it))
        }
    }

    @Test fun threadPathIsRetainedWhenUpdatingWalineAppearance() {
        val client = File("src/main/assets/theme-comments/comments.js").readText()
        assertTrue(client.contains("instance?.update({ dark: next, path: commentPath })"))
        assertTrue(client.contains("login: 'disable'"))
        assertTrue(client.contains("imageUploader: false"))
        assertTrue(client.contains("instance?.destroy()"))
        val html = File("src/main/assets/theme-comments/index.html").readText()
        assertFalse(html.contains("unpkg.com"))
        assertFalse(html.contains("addJavascriptInterface"))
    }
}
