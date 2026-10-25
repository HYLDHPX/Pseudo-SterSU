package me.weishu.kernelsu.ui.util

import com.android.apksig.apk.ApkUtils
import com.android.apksig.internal.apk.ApkSigningBlockUtils
import com.android.apksig.internal.apk.SignatureAlgorithm
import com.android.apksig.internal.util.Pair
import com.android.apksig.util.DataSinks
import com.android.apksig.util.DataSources
import com.android.apksig.util.RunnablesExecutor
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.GeneralSecurityException
import java.security.PrivateKey
import java.security.ProviderException
import java.security.Security
import java.security.Signature
import java.security.SignatureException
import java.security.cert.X509Certificate

/** V2-only compatibility path for our own freshly written, permission-free launcher template. */
internal fun signLauncherWithDeviceProviders(unsigned: File, output: File, key: PrivateKey, certificate: X509Certificate) {
    require(key.algorithm.equals("RSA", ignoreCase = true) && certificate.publicKey.algorithm.equals("RSA", ignoreCase = true))
    require(unsigned.length() in 1..16 * 1024 * 1024L)
    RandomAccessFile(unsigned, "r").use { file ->
        val source = DataSources.asDataSource(file)
        val zip = ApkUtils.findZipSections(source)
        val offset = zip.zipCentralDirectoryOffset
        require(offset + zip.zipCentralDirectorySizeBytes == zip.zipEndOfCentralDirectoryOffset)
        val contents = source.slice(0, offset)
        val directory = source.slice(offset, zip.zipCentralDirectorySizeBytes)
        val eocd = ByteBuffer.allocate(zip.zipEndOfCentralDirectory.remaining()).order(ByteOrder.LITTLE_ENDIAN)
            .apply { put(zip.zipEndOfCentralDirectory.duplicate()); flip() }
        val algorithm = SignatureAlgorithm.RSA_PKCS1_V1_5_WITH_SHA256
        // Keep Google's chunked APK digest and block encoders; only JCA provider selection differs.
        val digest = checkNotNull(ApkSigningBlockUtils.computeContentDigests(
            RunnablesExecutor.SINGLE_THREADED, setOf(algorithm.contentDigestAlgorithm),
            contents, directory, DataSources.asDataSource(eocd.duplicate()),
        )[algorithm.contentDigestAlgorithm])
        fun sequence(vararg bytes: ByteArray): ByteArray =
            ApkSigningBlockUtils.encodeAsSequenceOfLengthPrefixedElements(bytes)
        fun algorithmValue(value: ByteArray): ByteArray =
            ApkSigningBlockUtils.encodeAsSequenceOfLengthPrefixedPairsOfIntAndLengthPrefixedBytes(
                listOf(Pair.of(algorithm.id, value)),
            )
        val signedData = sequence(algorithmValue(digest), sequence(certificate.encoded), byteArrayOf())
        val signature = signLauncherData(key, certificate, signedData)
        val signer = sequence(signedData, algorithmValue(signature), certificate.publicKey.encoded)
        val block = ApkSigningBlockUtils.generateApkSigningBlock(listOf(Pair.of(sequence(sequence(signer)), 0x7109871a)))
        ApkUtils.setZipEocdCentralDirectoryOffset(eocd, offset + block.size)
        output.outputStream().use { stream ->
            val sink = DataSinks.asDataSink(stream)
            contents.feed(0, contents.size(), sink)
            stream.write(block)
            directory.feed(0, directory.size(), sink)
            DataSources.asDataSource(eocd).feed(0, eocd.remaining().toLong(), sink)
        }
    }
}

private fun signLauncherData(key: PrivateKey, certificate: X509Certificate, data: ByteArray): ByteArray {
    val algorithm = "SHA256withRSA"
    val providers = Security.getProviders().filter { it.getService("Signature", algorithm) != null }
    val failure = SignatureException("No available provider can sign with the existing launcher key")
    for (provider in providers) {
        try {
            val signature = Signature.getInstance(algorithm, provider).run {
                initSign(key)
                update(data)
                sign()
            }
            val valid = providers.any { verifier ->
                try {
                    Signature.getInstance(algorithm, verifier).run {
                        initVerify(certificate.publicKey)
                        update(data)
                        verify(signature)
                    }
                } catch (error: GeneralSecurityException) { false }
                catch (error: ProviderException) { false }
            }
            if (valid) return signature
            throw SignatureException("Launcher key does not match its certificate")
        } catch (error: GeneralSecurityException) { failure.addSuppressed(error) }
        catch (error: ProviderException) { failure.addSuppressed(error) }
    }
    throw failure
}
