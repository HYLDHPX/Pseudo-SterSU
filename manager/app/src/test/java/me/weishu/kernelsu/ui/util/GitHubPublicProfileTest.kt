package me.weishu.kernelsu.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubPublicProfileTest {
    @Test
    fun parsesPublicUserAndOptionalFields() {
        val profile = parseGitHubPublicProfile(
            """{"login":"Example-User","type":"User","name":"Example Name","bio":"Hello","avatar_url":"https://avatars.githubusercontent.com/u/123?v=4"}""",
            "example-user",
        )
        assertEquals("Example-User", profile.login)
        assertEquals("Example Name", profile.name)
        assertEquals("Hello", profile.bio)
        assertTrue(isAllowedGitHubAvatarUrl(profile.avatarUrl!!))

        val minimal = parseGitHubPublicProfile(
            """{"login":"example-user","type":"User","name":null,"bio":null,"avatar_url":null}""",
            "example-user",
        )
        assertEquals("", minimal.name)
        assertEquals("", minimal.bio)
        assertNull(minimal.avatarUrl)
    }

    @Test
    fun rejectsDifferentAccountAndOrganization() {
        assertThrows(IllegalArgumentException::class.java) {
            parseGitHubPublicProfile(
                """{"login":"someone-else","type":"User","avatar_url":null}""",
                "example-user",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseGitHubPublicProfile(
                """{"login":"example-user","type":"Organization","avatar_url":null}""",
                "example-user",
            )
        }
    }

    @Test
    fun avatarOnlyAllowsGitHubAvatarHostOverHttps() {
        assertTrue(isAllowedGitHubAvatarUrl("https://avatars.githubusercontent.com/u/123?v=4"))
        assertFalse(isAllowedGitHubAvatarUrl("http://avatars.githubusercontent.com/u/123"))
        assertFalse(isAllowedGitHubAvatarUrl("https://avatars.githubusercontent.com.evil.example/u/123"))
        assertFalse(isAllowedGitHubAvatarUrl("https://avatars.githubusercontent.com:444/u/123"))
    }
}
