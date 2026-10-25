package me.weishu.kernelsu.ui.util

import java.io.ByteArrayOutputStream
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Read-only, comment-service-only transport; official login and all writes remain in WebView. */
internal class ThemeCommentReadClient(serverUrl: String, private val client: OkHttpClient = sharedClient) : AutoCloseable {
    private val endpoint = "${requireNotNull(normalizeWalineServerUrl(serverUrl))}/api/comment".toHttpUrl()
    private val calls = ConcurrentHashMap.newKeySet<Call>()
    @Volatile private var closed = false

    data class Reply(val status: Int, val reason: String, val headers: Map<String, String>, val body: ByteArray)

    fun read(url: String, method: String, headers: Map<String, String>): Reply? {
        if (!matchesThemeCommentRead(url, method, endpoint.toString())) return null
        val request = Request.Builder().url(url).header("Origin", THEME_COMMENTS_ORIGIN)
            .header("Cache-Control", "no-store").apply {
                headers.forEach { (name, value) ->
                    if (name.lowercase() in setOf("accept", "accept-language", "authorization", "user-agent")) header(name, value)
                }
            }.build()
        val call = client.newCall(request)
        calls += call
        try {
            if (closed) call.cancel()
            call.execute().use { response ->
                // WebResourceResponse cannot represent redirects. Never redirect a bearer header.
                require(response.code in 200..299 || response.code in 400..599)
                require(response.body.contentLength() <= MAX_BYTES)
                val output = ByteArrayOutputStream()
                response.body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val size = input.read(buffer)
                        if (size < 0) break
                        require(output.size() + size <= MAX_BYTES)
                        output.write(buffer, 0, size)
                    }
                }
                val responseHeaders = response.headers.toMultimap().filterKeys {
                    it.lowercase() !in setOf("set-cookie", "content-length", "content-encoding", "transfer-encoding", "connection")
                }.mapValues { it.value.joinToString(", ") }
                return Reply(response.code, response.message.ifBlank { "Comment service" }, responseHeaders, output.toByteArray())
            }
        } catch (_: Exception) {
            // Fail closed: a failed fixed-DNS read must not silently use the system resolver.
            return Reply(502, "Comment service unavailable", mapOf(
                "Content-Type" to "application/json", "Cache-Control" to "no-store",
                "Access-Control-Allow-Origin" to THEME_COMMENTS_ORIGIN,
            ), "{\"errno\":502,\"errmsg\":\"Comment network unavailable\"}".toByteArray())
        } finally { calls -= call }
    }

    override fun close() {
        closed = true
        calls.forEach { it.cancel() }
    }

    companion object {
        private const val MAX_BYTES = 2 * 1024 * 1024
        // Private client: no global interceptors, cookies, disk cache, proxy or TLS overrides.
        private val sharedClient by lazy {
            OkHttpClient.Builder().dns(ThemeCommentDns()).proxy(Proxy.NO_PROXY)
                .connectTimeout(5, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS)
                .callTimeout(12, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
        }
    }
}

internal fun matchesThemeCommentRead(url: String, method: String, endpoint: String): Boolean = runCatching {
    val expected = endpoint.toHttpUrl()
    val actual = url.toHttpUrl()
    method == "GET" && actual.scheme == "https" && actual.username.isEmpty() && actual.password.isEmpty() &&
        actual.host == expected.host && actual.port == expected.port && actual.encodedPath == expected.encodedPath &&
        actual.fragment == null
}.getOrDefault(false)
