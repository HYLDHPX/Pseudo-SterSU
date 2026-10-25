package me.weishu.kernelsu.ui.util

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test

class ThemeCommentDnsTest {
    private val host = "comments.example.test"
    private fun type(query: ByteArray) = ((query[query.size - 4].toInt() and 255) shl 8) or
        (query[query.size - 3].toInt() and 255)
    private fun id(query: ByteArray) = ((query[0].toInt() and 255) shl 8) or (query[1].toInt() and 255)
    private fun response(query: ByteArray, ttl: Int = 30, empty: Boolean = false): ByteArray =
        ByteArrayOutputStream().apply {
            DataOutputStream(this).apply {
                writeShort(id(query)); writeShort(0x8180); writeShort(1)
                writeShort(if (empty) 0 else 1); writeShort(0); writeShort(0)
                write(query, 12, query.size - 12)
                if (!empty) {
                    writeShort(0xc00c); writeShort(type(query)); writeShort(1); writeInt(ttl)
                    val address = if (type(query) == 1) byteArrayOf(1, 2, 3, 4)
                        else ByteArray(16).apply { this[0] = 0x20; this[1] = 1; this[15] = 1 }
                    writeShort(address.size); write(address)
                }
            }
        }.toByteArray()

    @Test fun usesExactlyTheThreeRequestedResolvers() {
        assertEquals(listOf("8.8.8.8", "1.1.1.1", "114.114.114.114"), ThemeCommentDns.SERVERS)
        val seen = ConcurrentHashMap.newKeySet<String>()
        val dns = ThemeCommentDns(exchange = { server, _ -> seen += server; throw UnknownHostException("offline") })
        assertTrue(runCatching { dns.lookup(host) }.exceptionOrNull() is UnknownHostException)
        assertEquals(ThemeCommentDns.SERVERS.toSet(), seen)
    }

    @Test fun decodesBothFamiliesAndHonorsPositiveTtl() {
        val clock = AtomicLong(0)
        val online = AtomicBoolean(true)
        val dns = ThemeCommentDns(exchange = { _, query ->
            if (!online.get()) throw UnknownHostException("offline")
            response(query)
        }, now = clock::get)
        assertEquals(setOf(4, 16), dns.lookup(host).map { it.address.size }.toSet())
        online.set(false)
        assertTrue(dns.lookup(host).isNotEmpty())
        clock.addAndGet(TimeUnit.SECONDS.toNanos(31))
        assertTrue(runCatching { dns.lookup(host) }.isFailure)
    }

    @Test fun aMissingIpv6RecordDoesNotDisableIpv4Caching() {
        val online = AtomicBoolean(true)
        val dns = ThemeCommentDns(exchange = { _, query ->
            if (!online.get()) throw UnknownHostException("offline")
            response(query, empty = type(query) == 28)
        })
        assertEquals("1.2.3.4", dns.lookup(host).single().hostAddress)
        online.set(false)
        assertEquals("1.2.3.4", dns.lookup(host).single().hostAddress)
    }

    @Test fun ipv6OnlyServicesRemainUsable() {
        val dns = ThemeCommentDns(exchange = { _, query -> response(query, empty = type(query) == 1) })
        assertEquals(16, dns.lookup(host).single().address.size)
    }

    @Test(timeout = 2000) fun aSlowResolverDoesNotBlockAWorkingResolver() {
        val dns = ThemeCommentDns(exchange = { server, query ->
            if (server != "114.114.114.114") { Thread.sleep(1200); throw UnknownHostException("timeout") }
            response(query)
        })
        val start = System.nanoTime()
        assertTrue(dns.lookup(host).isNotEmpty())
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 600)
    }

    @Test fun zeroTtlIsNotCachedAndLargeTtlIsCappedAtOneMinute() {
        for (ttl in listOf(0, 86400)) {
            val clock = AtomicLong(0)
            val online = AtomicBoolean(true)
            val dns = ThemeCommentDns(exchange = { _, query ->
                if (!online.get()) throw UnknownHostException("offline")
                response(query, ttl)
            }, now = clock::get)
            dns.lookup(host)
            online.set(false)
            if (ttl != 0) clock.addAndGet(TimeUnit.SECONDS.toNanos(61))
            assertTrue(runCatching { dns.lookup(host) }.isFailure)
        }
    }

    @Test fun failedLookupIsRetriedInsteadOfNegativeCached() {
        var offline = true
        val dns = ThemeCommentDns(exchange = { _, query ->
            if (offline) throw UnknownHostException("offline")
            response(query)
        })
        assertTrue(runCatching { dns.lookup(host) }.isFailure)
        offline = false
        assertTrue(dns.lookup(host).isNotEmpty())
    }

    @Test fun unsafeHostnamesAreRejectedBeforeSendingAnyPacket() {
        val dns = ThemeCommentDns(exchange = { _, _ -> fail("Network must not be used"); byteArrayOf() })
        listOf("", "https://example.test", "example.test/path", "a..b", "a".repeat(64) + ".test").forEach {
            assertTrue(it, runCatching { dns.lookup(it) }.exceptionOrNull() is UnknownHostException)
        }
    }

    @Test fun answerMustMatchTransactionQuestionAndRecordType() {
        val query = CommentDnsPacket.query(host, 1, 123)
        val bytes = response(query)
        assertEquals("1.2.3.4", CommentDnsPacket.read(bytes, host, 1, 123).addresses.single().hostAddress)
        listOf(
            { CommentDnsPacket.read(bytes, host, 1, 124) },
            { CommentDnsPacket.read(bytes, "other.test", 1, 123) },
            { CommentDnsPacket.read(bytes, host, 28, 123) },
            { CommentDnsPacket.read(bytes.copyOf(bytes.size - 1), host, 1, 123) },
            { CommentDnsPacket.read(bytes.copyOf(5), host, 1, 123) },
            { CommentDnsPacket.read(bytes.clone().apply { this[2] = 0x83.toByte() }, host, 1, 123) },
            { CommentDnsPacket.read(bytes.clone().apply { this[3] = 0x83.toByte() }, host, 1, 123) },
        ).forEach { assertTrue(runCatching(it).isFailure) }
    }

    @Test fun cyclicCompressionPointersCannotHangTheParser() {
        val query = CommentDnsPacket.query(host, 1, 123)
        val bytes = response(query)
        val owner = query.size
        bytes[owner] = 0xc0.toByte(); bytes[owner + 1] = owner.toByte()
        assertTrue(runCatching { CommentDnsPacket.read(bytes, host, 1, 123) }.isFailure)
    }

    @Test fun unrelatedAnswerOwnerCannotSupplyAnAddress() {
        val query = CommentDnsPacket.query(host, 1, 123)
        val bytes = response(query)
        // Point at the second label: example.test is not the queried owner.
        bytes[query.size + 1] = (12 + "comments".length + 1).toByte()
        assertTrue(CommentDnsPacket.read(bytes, host, 1, 123).addresses.isEmpty())
    }

    @Test fun followsCnameAnswersAndUsesTheShorterAliasTtl() {
        val query = CommentDnsPacket.query(host, 1, 123)
        val alias = CommentDnsPacket.query("edge.example.test", 1, 0).let { it.copyOfRange(12, it.size - 4) }
        val bytes = ByteArrayOutputStream().apply {
            DataOutputStream(this).apply {
                writeShort(123); writeShort(0x8180); writeShort(1); writeShort(2); writeShort(0); writeShort(0)
                write(query, 12, query.size - 12)
                writeShort(0xc00c); writeShort(5); writeShort(1); writeInt(5); writeShort(alias.size); write(alias)
                write(alias); writeShort(1); writeShort(1); writeInt(300); writeShort(4); write(byteArrayOf(1, 2, 3, 4))
            }
        }.toByteArray()
        val result = CommentDnsPacket.read(bytes, host, 1, 123)
        assertEquals("1.2.3.4", result.addresses.single().hostAddress)
        assertEquals(5L, result.ttl)
    }
}
