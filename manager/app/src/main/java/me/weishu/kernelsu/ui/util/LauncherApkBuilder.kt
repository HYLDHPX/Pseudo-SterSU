package me.weishu.kernelsu.ui.util

import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.GeneralSecurityException
import java.security.PrivateKey
import java.security.ProviderException
import java.security.SignatureException
import java.security.cert.X509Certificate
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal const val LAUNCHER_LABEL_PLACEHOLDER = "__STERSU_LAUNCHER_LABEL__"

/** Only patches the literal label in our own template, never user-supplied APKs. */
internal fun replaceLauncherManifestLabel(manifest: ByteArray, name: String): ByteArray {
    require(name.isNotBlank() && name.length <= 80 && name.none { it.isISOControl() })
    val input = ByteBuffer.wrap(manifest).order(ByteOrder.LITTLE_ENDIAN)
    require(manifest.size >= 36 && input.getShort(0).toInt() == 3 && input.getInt(4) == manifest.size)
    val start = input.getShort(2).toInt() and 0xffff
    require(start == 8 && input.getShort(start).toInt() == 1)
    val headerSize = input.getShort(start + 2).toInt() and 0xffff
    val chunkSize = input.getInt(start + 4)
    val count = input.getInt(start + 8)
    val flags = input.getInt(start + 16)
    val stringsStart = input.getInt(start + 20)
    require(headerSize == 28 && chunkSize in 28..(manifest.size - start))
    require(count in 1..4096 && headerSize + count * 4 <= chunkSize)
    require(input.getInt(start + 12) == 0 && input.getInt(start + 24) == 0) // No styled strings.
    require(stringsStart in (headerSize + count * 4) until chunkSize)
    fun length(at: Int, utf8: Boolean): Pair<Int, Int> {
        require(at in start until start + chunkSize)
        val first = if (utf8) input.get(at).toInt() and 0xff else input.getShort(at).toInt() and 0xffff
        val size = if (utf8) 1 else 2
        val mask = if (utf8) 0x80 else 0x8000
        if (first and mask == 0) return first to (at + size)
        require(at + size * 2 <= start + chunkSize)
        val second = if (utf8) input.get(at + 1).toInt() and 0xff else input.getShort(at + 2).toInt() and 0xffff
        return (((first and (mask - 1)) shl (size * 8)) or second) to (at + size * 2)
    }
    val utf8 = flags and 0x100 != 0
    val strings = (0 until count).map { index ->
        val offset = input.getInt(start + headerSize + index * 4)
        require(offset in 0 until chunkSize - stringsStart)
        val (chars, position) = length(start + stringsStart + offset, utf8)
        val (bytes, data) = if (utf8) length(position, true) else chars * 2 to position
        require(bytes >= 0 && data.toLong() + bytes + (if (utf8) 1 else 2) <= start.toLong() + chunkSize)
        String(manifest, data, bytes, if (utf8) Charsets.UTF_8 else Charsets.UTF_16LE)
    }
    require(strings.count { it == LAUNCHER_LABEL_PLACEHOLDER } == 1)
    val data = ByteArrayOutputStream()
    val offsets = strings.map { old ->
        val value = if (old == LAUNCHER_LABEL_PLACEHOLDER) name else old
        require(value.length < 0x8000)
        val offset = data.size()
        data.write(value.length and 0xff)
        data.write(value.length ushr 8)
        data.write(value.toByteArray(Charsets.UTF_16LE))
        data.write(0)
        data.write(0)
        offset
    }
    while (data.size() % 4 != 0) data.write(0)
    val newSize = headerSize + count * 4 + data.size()
    val pool = ByteBuffer.allocate(newSize).order(ByteOrder.LITTLE_ENDIAN)
    pool.putShort(1).putShort(28).putInt(newSize).putInt(count).putInt(0)
        .putInt(flags and 0x101.inv()).putInt(headerSize + count * 4).putInt(0)
    offsets.forEach(pool::putInt)
    pool.put(data.toByteArray())
    val result = manifest.copyOfRange(0, start) + pool.array() + manifest.copyOfRange(start + chunkSize, manifest.size)
    ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN).putInt(4, result.size)
    return result
}

/** Preserve the resource table's required uncompressed four-byte alignment. */
internal fun writeLauncherArchive(template: InputStream, output: File, name: String, icon: ByteArray) {
    require(icon.size in 1..(4 * 1024 * 1024))
    var manifestFound = false
    var iconFound = false
    var position = 0L
    val names = mutableSetOf<String>()
    ZipInputStream(template).use { source ->
        ZipOutputStream(output.outputStream()).use { target ->
            while (true) {
                val entry = source.nextEntry ?: break
                require(names.add(entry.name))
                if (entry.name.startsWith("META-INF/")) continue
                val original = source.readBytes()
                require(original.size <= 4 * 1024 * 1024)
                val content = when (entry.name) {
                    "AndroidManifest.xml" -> {
                        manifestFound = true
                        replaceLauncherManifestLabel(original, name)
                    }
                    // AAPT may shorten paths (e.g. res/vF.png). This template has exactly one PNG.
                    else -> if (entry.name.startsWith("res/") && entry.name.endsWith(".png")) {
                        check(!iconFound) { "Ambiguous launcher icon template" }
                        iconFound = true
                        icon
                    } else original
                }
                val nameBytes = entry.name.toByteArray(Charsets.UTF_8)
                val padding = ((4 - (position + 30 + nameBytes.size + 4) % 4) % 4).toInt()
                val aligned = ZipEntry(entry.name).apply {
                    method = ZipEntry.STORED
                    size = content.size.toLong()
                    compressedSize = size
                    crc = CRC32().apply { update(content) }.value
                    time = 315619200000L // 1980-01-02 stays in the DOS range in every timezone.
                    extra = byteArrayOf(0xfe.toByte(), 0xca.toByte(), padding.toByte(), 0) + ByteArray(padding)
                }
                target.putNextEntry(aligned)
                target.write(content)
                target.closeEntry()
                position += 30 + nameBytes.size + 4 + padding + content.size
            }
        }
    }
    check(manifestFound && iconFound) { "Invalid launcher template" }
}

internal fun signLauncherArchive(unsigned: File, output: File, key: PrivateKey, certificate: X509Certificate) {
    require(unsigned.canonicalFile != output.canonicalFile)
    try {
        try {
            val signer = ApkSigner.SignerConfig.Builder("launcher", key, listOf(certificate)).build()
            ApkSigner.Builder(listOf(signer)).setInputApk(unsigned).setOutputApk(output)
                .setMinSdkVersion(31).setV1SigningEnabled(false).setV2SigningEnabled(true)
                .setV3SigningEnabled(false).setV4SigningEnabled(false).build().sign()
            verifyLauncherArchive(output, certificate)
        } catch (first: Exception) {
            if (first !is GeneralSecurityException && first !is ProviderException) throw first
            // JCA does not try another provider if the chosen one fails after initSign.
            // Retry the same device key explicitly; never replace or export that key.
            try {
                signLauncherWithDeviceProviders(unsigned, output, key, certificate)
                verifyLauncherArchive(output, certificate)
            } catch (failure: Exception) {
                failure.addSuppressed(first)
                throw failure
            }
        }
    } catch (failure: Exception) {
        output.delete()
        throw failure
    }
}

private fun verifyLauncherArchive(output: File, certificate: X509Certificate) {
    val verified = ApkVerifier.Builder(output).setMinCheckedPlatformVersion(31).build().verify()
    if (!verified.isVerified || !verified.isVerifiedUsingV2Scheme || verified.signerCertificates.size != 1 ||
        !verified.signerCertificates.single().encoded.contentEquals(certificate.encoded)) {
        throw SignatureException("Generated launcher APK failed v2 verification: ${verified.errors}")
    }
}
