package me.weishu.kernelsu.ui.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.ksuApp
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private const val MAX_GITHUB_PROFILE_BYTES = 64 * 1024
private const val MAX_GITHUB_AVATAR_BYTES = 2 * 1024 * 1024

internal data class GitHubPublicProfile(
    val login: String,
    val name: String,
    val bio: String,
    val avatarUrl: String?,
)

internal fun parseGitHubPublicProfile(json: String, requestedLogin: String): GitHubPublicProfile {
    val root = JSONObject(json)
    require(root.optString("type") == "User") { "GitHub account is not a user" }
    val login = root.getString("login")
    require(isValidCloudThemeGithubLogin(login) && login.equals(requestedLogin, ignoreCase = true)) {
        "GitHub returned a different account"
    }
    val avatarUrl = root.optString("avatar_url")
        .takeUnless { root.isNull("avatar_url") || it.isBlank() }
    if (avatarUrl != null) require(isAllowedGitHubAvatarUrl(avatarUrl)) {
        "GitHub avatar URL is unsupported"
    }
    return GitHubPublicProfile(
        login = login,
        name = root.optString("name").takeUnless { root.isNull("name") }.orEmpty(),
        bio = root.optString("bio").takeUnless { root.isNull("bio") }.orEmpty(),
        avatarUrl = avatarUrl,
    )
}

internal fun isAllowedGitHubAvatarUrl(rawUrl: String): Boolean {
    val url = rawUrl.toHttpUrlOrNull() ?: return false
    return url.scheme == "https" && url.host == "avatars.githubusercontent.com" &&
        url.port == 443 && url.username.isEmpty() && url.password.isEmpty()
}

private fun java.io.InputStream.readLimitedBytes(maxBytes: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val size = read(buffer)
        if (size < 0) break
        if (output.size() + size > maxBytes) throw IOException("GitHub response is too large")
        output.write(buffer, 0, size)
    }
    return output.toByteArray()
}

suspend fun syncThemeAuthorProfileFromGitHub(
    context: Context,
    requestedLogin: String,
    client: OkHttpClient = ksuApp.okhttpClient,
): ThemeAuthorProfile = withContext(Dispatchers.IO) {
    val login = normalizeCloudThemeGithubLogin(requestedLogin)
    val profileRequest = Request.Builder()
        .url("https://api.github.com/users/$login")
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .build()
    val githubProfile = client.newCall(profileRequest).execute().use { response ->
        require(response.request.url.scheme == "https" &&
            response.request.url.host == "api.github.com") { "GitHub API redirected outside GitHub" }
        if (!response.isSuccessful) {
            throw IOException("GitHub profile HTTP ${response.code}")
        }
        response.body.byteStream().use { input ->
            parseGitHubPublicProfile(
                input.readLimitedBytes(MAX_GITHUB_PROFILE_BYTES).toString(Charsets.UTF_8),
                login,
            )
        }
    }

    val avatarFile = githubProfile.avatarUrl?.let { url ->
        val file = File.createTempFile("github-avatar-", ".image", context.cacheDir)
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                require(isAllowedGitHubAvatarUrl(response.request.url.toString())) {
                    "GitHub avatar redirected outside GitHub"
                }
                if (!response.isSuccessful ||
                    !response.header("Content-Type").orEmpty().startsWith("image/", ignoreCase = true)
                ) throw IOException("GitHub avatar could not be loaded")
                response.body.byteStream().use { input ->
                    FileOutputStream(file).use { output ->
                        output.write(input.readLimitedBytes(MAX_GITHUB_AVATAR_BYTES))
                    }
                }
            }
            file
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
    }
    try {
        val saved = saveThemeAuthorProfile(
            context = context,
            profile = ThemeAuthorProfile(
                displayName = githubProfile.name.ifBlank { githubProfile.login },
                realName = githubProfile.name,
                bio = githubProfile.bio,
            ),
            avatarSource = avatarFile?.let(Uri::fromFile),
            removeAvatar = avatarFile == null,
            githubLogin = githubProfile.login,
        )
        if (!saved.success) throw saved.error ?: IOException("Could not save GitHub profile")
        saved.profile ?: error("Saved GitHub profile is missing")
    } finally {
        avatarFile?.delete()
    }
}
