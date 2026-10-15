package me.weishu.kernelsu.ui.screen.module

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModuleProvidedBannerTest {
    @Test
    fun acceptsModuleRelativeImagesAndHttpUrls() {
        assertEquals(
            ModuleBannerSource.Local("example.module", "images/banner.webp"),
            moduleBannerSource("example.module", " images/banner.webp "),
        )
        assertEquals(
            ModuleBannerSource.Remote("https://example.org/banner.png"),
            moduleBannerSource("example.module", "https://example.org/banner.png"),
        )
        assertEquals(
            ModuleBannerSource.Remote("http://example.org/banner.png"),
            moduleBannerSource("example.module", "http://example.org/banner.png"),
        )
    }

    @Test
    fun rejectsTraversalAbsolutePathsAndUnsupportedSchemes() {
        val invalid = listOf(
            "",
            "../banner.png",
            "images/../banner.png",
            "images//banner.png",
            "/data/adb/secret.png",
            "images\\banner.png",
            "file:///data/adb/modules/example.module/banner.png",
            "ftp://example.org/banner.png",
            "https://user:password@example.org/banner.png",
        )
        invalid.forEach { assertNull(it, moduleBannerSource("example.module", it)) }
        assertNull(moduleBannerSource("../other", "banner.png"))
    }
}
