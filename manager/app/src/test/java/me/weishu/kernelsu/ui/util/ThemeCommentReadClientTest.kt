package me.weishu.kernelsu.ui.util

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class ThemeCommentReadClientTest {
    private val server = "https://comments.example.test/waline"
    private val url = "$server/api/comment?path=%2Fstersu%2Ftheme%2Ftest"
    private val clients = mutableListOf<OkHttpClient>()

    @After fun closeClients() {
        clients.forEach { it.dispatcher.cancelAll(); it.dispatcher.executorService.shutdown(); it.connectionPool.evictAll() }
    }

    private fun client(code: Int = 200, body: String = "{}", check: (Request) -> Unit = {}): OkHttpClient =
        OkHttpClient.Builder().addInterceptor { chain ->
            check(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_2).code(code).message("Test")
                .header("Access-Control-Allow-Origin", "*").header("Set-Cookie", "must-not-store=true")
                .body(body.toResponseBody()).build()
        }.build().also { clients += it }

    @Test fun matchesOnlyExactHttpsCommentReadsAndSupportsSubpaths() {
        val endpoint = "$server/api/comment"
        assertTrue(matchesThemeCommentRead(url, "GET", endpoint))
        listOf("POST", "PUT", "DELETE", "OPTIONS").forEach { assertFalse(matchesThemeCommentRead(url, it, endpoint)) }
        listOf("http://comments.example.test/waline/api/comment", "$server/api/token", "$server/ui/login",
            "$server/api/comment/1", "$server/api/comment/", "https://other.test/waline/api/comment",
            "https://user@comments.example.test/waline/api/comment", "https://comments.example.test:444/waline/api/comment",
            "$url#fragment").forEach { assertFalse(it, matchesThemeCommentRead(it, "GET", endpoint)) }
    }

    @Test fun preservesHostAndBearerButNeverForwardsCookiesOrRewritesTlsToAnIp() {
        val network = ThemeCommentReadClient(server, client { request ->
            assertEquals(url, request.url.toString())
            assertEquals("Bearer test-token", request.header("Authorization"))
            assertEquals(THEME_COMMENTS_ORIGIN, request.header("Origin"))
            assertEquals("no-store", request.header("Cache-Control"))
            assertNull(request.header("Cookie"))
        })
        val result = network.read(url, "GET", mapOf("Authorization" to "Bearer test-token", "Cookie" to "private=1"))!!
        assertEquals(200, result.status)
        assertEquals("*", result.headers.entries.first { it.key.equals("Access-Control-Allow-Origin", true) }.value)
        assertFalse(result.headers.keys.any { it.equals("Set-Cookie", true) })
    }

    @Test fun otherRequestsAreNotReplayedOrIntercepted() {
        val network = ThemeCommentReadClient(server, client { fail("Must remain in WebView") })
        assertNull(network.read(url, "POST", emptyMap()))
        assertNull(network.read("$server/api/token", "GET", emptyMap()))
        assertNull(network.read("$server/ui/login", "GET", emptyMap()))
    }

    @Test fun redirectsAndOversizedBodiesFailClosed() {
        for ((code, body) in listOf(302 to "redirect", 200 to "x".repeat(2 * 1024 * 1024 + 1))) {
            val network = ThemeCommentReadClient(server, client(code, body))
            assertEquals(502, network.read(url, "GET", emptyMap())!!.status)
        }
    }

    @Test fun networkFailureDoesNotFallBackToTheWebViewSystemResolver() {
        val network = ThemeCommentReadClient(server, client { throw IOException("DNS failed") })
        val reply = network.read(url, "GET", emptyMap())!!
        assertEquals(502, reply.status)
        assertFalse(String(reply.body).contains("DNS failed"))
        assertEquals(THEME_COMMENTS_ORIGIN, reply.headers["Access-Control-Allow-Origin"])
    }

    @Test fun preservesServerErrorsForTheRetryUi() {
        val network = ThemeCommentReadClient(server, client(503, "{\"errno\":503}"))
        assertEquals(503, network.read(url, "GET", emptyMap())!!.status)
    }

    @Test fun liveFixedDnsReadOnlyCheckWhenExplicitlyRequested() {
        val configured = System.getenv("STERSU_COMMENTS_LIVE_DNS_SERVER")
        assumeTrue("Opt-in live network check", !configured.isNullOrBlank())
        val dns = ThemeCommentDns()
        val host = java.net.URI(configured!!).host
        val start = System.nanoTime()
        val addresses = dns.lookup(host)
        println("Comment DNS cold: ${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)}ms, families=${addresses.map { it.address.size }}")
        val warm = System.nanoTime()
        assertEquals(addresses, dns.lookup(host))
        println("Comment DNS cached: ${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - warm)}ms")
        ThemeCommentReadClient(configured).use { network ->
            val requestStart = System.nanoTime()
            val reply = network.read("$configured/api/comment?path=%2Fstersu%2Ftheme%2Fstersu-connection-check&page=1&pageSize=10&sortBy=insertedAt_desc", "GET", emptyMap())!!
            assertEquals(200, reply.status)
            assertTrue(String(reply.body).contains("\"errno\":0"))
            println("Fixed-DNS HTTPS read: ${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - requestStart)}ms, bytes=${reply.body.size}")
        }
    }
}
