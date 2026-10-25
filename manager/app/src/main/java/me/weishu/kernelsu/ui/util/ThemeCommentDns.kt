package me.weishu.kernelsu.ui.util

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.IDN
import java.net.InetAddress
import java.net.UnknownHostException
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.Dns

/** Only used by comment reads. Never delegates to the device DNS or changes it. */
internal class ThemeCommentDns(
    private val exchange: (String, ByteArray) -> ByteArray = ::exchangeCommentDns,
    private val now: () -> Long = System::nanoTime,
) : Dns {
    private data class Cached(val addresses: List<InetAddress>, val expires: Long)
    private val cache = LinkedHashMap<String, Cached>()
    private val random = SecureRandom()

    @Synchronized
    override fun lookup(hostname: String): List<InetAddress> {
        val host = try {
            IDN.toASCII(hostname.trimEnd('.')).lowercase(Locale.ROOT).also {
                require(it.length in 1..253 && it.split('.').all { label ->
                    label.length in 1..63 && label.all { c -> c.isLetterOrDigit() || c == '-' }
                })
            }
        } catch (error: IllegalArgumentException) {
            throw UnknownHostException("Invalid comment hostname").apply { initCause(error) }
        }
        cache[host]?.takeIf { it.expires > now() }?.let { return it.addresses.toList() }
        cache.remove(host)
        val completions = ExecutorCompletionService<CommentDnsAnswer>(executor)
        val jobs = SERVERS.map { server -> completions.submit(Callable {
            // Query both families; an unavailable family must not discard a working one.
            val answers = listOf(1, 28).mapNotNull { type ->
                if (Thread.currentThread().isInterrupted) return@mapNotNull null
                runCatching {
                    val id = random.nextInt(65536)
                    val query = CommentDnsPacket.query(host, type, id)
                    CommentDnsPacket.read(exchange(server, query), host, type, id)
                }.getOrNull()
            }
            CommentDnsAnswer(answers.flatMap { it.addresses }.distinct(), answers.filter { it.addresses.isNotEmpty() }.minOfOrNull { it.ttl } ?: 0)
        }) }
        try {
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(2300)
            repeat(SERVERS.size) {
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) throw UnknownHostException("Comment DNS timed out")
                val completed = completions.poll(remaining, TimeUnit.NANOSECONDS)
                    ?: throw UnknownHostException("Comment DNS timed out")
                val result = completed.get()
                if (result.addresses.isNotEmpty()) {
                    // Honor zero/short TTLs, cap positive caching at 60s; no stale or negative cache.
                    if (result.ttl > 0) {
                        if (cache.size >= 32) cache.remove(cache.keys.first())
                        cache[host] = Cached(result.addresses, now() + TimeUnit.SECONDS.toNanos(result.ttl.coerceAtMost(60)))
                    }
                    return result.addresses.toList()
                }
            }
            throw UnknownHostException("All configured comment DNS servers failed")
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw UnknownHostException("Comment DNS interrupted").apply { initCause(error) }
        } finally {
            jobs.forEach { it.cancel(true) }
        }
    }

    companion object {
        val SERVERS = listOf("8.8.8.8", "1.1.1.1", "114.114.114.114")
        private val executor = Executors.newFixedThreadPool(6) { task ->
            Thread(task, "comment-dns").apply { isDaemon = true }
        }
    }
}

private fun exchangeCommentDns(server: String, query: ByteArray): ByteArray = DatagramSocket().use { socket ->
    // Parse numeric literals without invoking InetAddress.getByName/system DNS.
    val address = InetAddress.getByAddress(server.split('.').map { it.toInt().toByte() }.toByteArray())
    socket.soTimeout = 700
    socket.connect(address, 53)
    socket.send(DatagramPacket(query, query.size))
    val packet = DatagramPacket(ByteArray(4096), 4096)
    socket.receive(packet)
    packet.data.copyOf(packet.length)
}

internal data class CommentDnsAnswer(val addresses: List<InetAddress>, val ttl: Long)

/** Bounded DNS wire codec (RFC 1035); rejects unrelated/invalid/truncated answers. */
internal object CommentDnsPacket {
    fun query(host: String, type: Int, id: Int): ByteArray = ByteArrayOutputStream().apply {
        DataOutputStream(this).apply {
            writeShort(id); writeShort(0x0100); writeShort(1)
            writeShort(0); writeShort(0); writeShort(0)
            host.split('.').forEach { label -> writeByte(label.length); write(label.toByteArray(Charsets.US_ASCII)) }
            writeByte(0); writeShort(type); writeShort(1)
        }
    }.toByteArray()

    fun read(bytes: ByteArray, host: String, type: Int, id: Int): CommentDnsAnswer {
        fun u16(offset: Int): Int {
            require(offset >= 0 && offset + 2 <= bytes.size)
            return ((bytes[offset].toInt() and 255) shl 8) or (bytes[offset + 1].toInt() and 255)
        }
        fun name(start: Int): Pair<String, Int> {
            var offset = start
            var end = -1
            val labels = mutableListOf<String>()
            val visited = mutableSetOf<Int>()
            while (true) {
                require(offset in bytes.indices && visited.add(offset) && visited.size <= 128)
                val length = bytes[offset].toInt() and 255
                when {
                    length == 0 -> return labels.joinToString(".").lowercase(Locale.ROOT) to
                        (if (end >= 0) end else offset + 1)
                    length and 0xc0 == 0xc0 -> {
                        val pointer = u16(offset) and 0x3fff
                        require(pointer < offset)
                        if (end < 0) end = offset + 2
                        offset = pointer
                    }
                    else -> {
                        require(length <= 63 && offset + 1 + length <= bytes.size)
                        labels += String(bytes, offset + 1, length, Charsets.US_ASCII)
                        require(labels.sumOf { it.length + 1 } <= 254)
                        offset += length + 1
                    }
                }
            }
        }
        require(bytes.size >= 12 && u16(0) == id)
        val flags = u16(2)
        require(flags and 0x8000 != 0 && flags and 0x7a0f == 0 && u16(4) == 1)
        val question = name(12)
        require(question.first == host && u16(question.second) == type && u16(question.second + 2) == 1)
        var offset = question.second + 4
        data class Record(val owner: String, val type: Int, val start: Int, val size: Int, val ttl: Long)
        val records = mutableListOf<Record>()
        repeat(u16(6)) {
            val owner = name(offset)
            offset = owner.second
            val recordType = u16(offset)
            val klass = u16(offset + 2)
            val ttl = (u16(offset + 4).toLong() shl 16) or u16(offset + 6).toLong()
            val size = u16(offset + 8)
            offset += 10
            require(offset + size <= bytes.size)
            if (klass == 1) records += Record(owner.first, recordType, offset, size, ttl)
            offset += size
        }
        val owners = mutableSetOf(host)
        var ttl = Long.MAX_VALUE
        repeat(records.size) {
            records.filter { it.type == 5 && it.owner in owners }.forEach { record ->
                val alias = name(record.start)
                require(alias.second == record.start + record.size)
                owners += alias.first
                ttl = minOf(ttl, record.ttl)
            }
        }
        val addresses = records.filter { it.owner in owners && it.type == type && it.size == if (type == 1) 4 else 16 }
            .map { record ->
                ttl = minOf(ttl, record.ttl)
                InetAddress.getByAddress(host, bytes.copyOfRange(record.start, record.start + record.size))
            }
        return CommentDnsAnswer(addresses, if (addresses.isEmpty()) 0 else ttl)
    }
}
