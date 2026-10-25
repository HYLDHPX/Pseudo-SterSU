package me.weishu.kernelsu.ui.util

import com.android.apksig.ApkVerifier
import java.io.File
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.security.Security
import java.security.Signature
import java.security.SignatureException
import java.security.SignatureSpi
import java.security.cert.X509Certificate
import java.util.Properties
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LauncherSigningCompatibilityTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun signingKey(): KeyStore.PrivateKeyEntry {
        val store = File(temporary.newFolder(), "disposable.p12")
        val keytool = File(System.getProperty("java.home"), "bin/keytool" + if (File.separatorChar == '\\') ".exe" else "")
        val process = ProcessBuilder(keytool.path, "-genkeypair", "-alias", "test", "-keyalg", "RSA",
            "-keysize", "2048", "-dname", "CN=Disposable launcher compatibility test", "-validity", "2",
            "-keystore", store.path, "-storetype", "PKCS12", "-storepass", "test-only-password", "-noprompt")
            .redirectErrorStream(true).start()
        val diagnostic = process.inputStream.bufferedReader().readText()
        assertEquals(diagnostic, 0, process.waitFor())
        val keys = KeyStore.getInstance("PKCS12").apply {
            store.inputStream().use { load(it, "test-only-password".toCharArray()) }
        }
        return keys.getEntry("test", KeyStore.PasswordProtection("test-only-password".toCharArray())) as KeyStore.PrivateKeyEntry
    }

    private fun unsigned(): File {
        val template = File("build/generated/launcherTemplate/launcher/template.apk")
        val icon = ZipFile(template).use { zip ->
            val png = zip.entries().asSequence().single { it.name.startsWith("res/") && it.name.endsWith(".png") }
            zip.getInputStream(png).readBytes()
        }
        return temporary.newFile("unsigned.apk").also { output ->
            template.inputStream().use { writeLauncherArchive(it, output, "SterSU compatibility test", icon) }
        }
    }

    private fun verifyWithSdk(output: File) {
        val properties = Properties().apply {
            File("../local.properties").inputStream().use { load(it) }
        }
        val sdk = File(System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
            ?: checkNotNull(properties.getProperty("sdk.dir")))
        val suffix = if (File.separatorChar == '\\') ".exe" else ""
        val tools = checkNotNull(File(sdk, "build-tools").listFiles()?.filter {
            File(it, "lib/apksigner.jar").isFile && File(it, "zipalign$suffix").isFile
        }?.maxByOrNull { it.name })
        val java = File(System.getProperty("java.home"), "bin/java$suffix")
        listOf(
            listOf(java.path, "-jar", File(tools, "lib/apksigner.jar").path,
                "verify", "--min-sdk-version", "31", "--verbose", output.path),
            listOf(File(tools, "zipalign$suffix").path, "-c", "4", output.path),
        ).forEach { command ->
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val diagnostic = process.inputStream.bufferedReader().readText()
            assertEquals(diagnostic, 0, process.waitFor())
        }
    }

    @Test fun failedDefaultProviderCanRetryAnOpaqueKeyWithoutChangingTheCertificate() {
        val key = signingKey()
        val opaque = OpaqueLauncherKey(key.privateKey)
        assertNull(opaque.encoded)
        val broken = TestSigningProvider("SterSUTestBrokenSigner", BrokenLauncherSignature::class.java)
        val working = TestSigningProvider("SterSUTestDeviceSigner", WorkingLauncherSignature::class.java)
        val before = Security.getProviders().map { it.name }
        synchronized(Security::class.java) {
            try {
                BrokenLauncherSignature.calls.set(0)
                Security.insertProviderAt(broken, 1)
                Security.addProvider(working)
                val configuredOrder = Security.getProviders().map { it.name }
                val input = unsigned()
                val original = input.readBytes()
                val output = temporary.newFile("signed.apk")
                signLauncherArchive(input, output, opaque, key.certificate as X509Certificate)
                assertTrue("The default-provider failure was exercised", BrokenLauncherSignature.calls.get() > 0)
                val verified = ApkVerifier.Builder(output).setMinCheckedPlatformVersion(31).build().verify()
                assertTrue(verified.errors.toString(), verified.isVerifiedUsingV2Scheme && verified.isVerified)
                assertArrayEquals(key.certificate.encoded, verified.signerCertificates.single().encoded)
                assertArrayEquals(original, input.readBytes())
                assertEquals(configuredOrder, Security.getProviders().map { it.name })
                verifyWithSdk(output)
                val tampered = temporary.newFile("tampered.apk").apply {
                    writeBytes(output.readBytes().also { it[128] = (it[128].toInt() xor 1).toByte() })
                }
                assertFalse(ApkVerifier.Builder(tampered).setMinCheckedPlatformVersion(31).build().verify().isVerified)
            } finally {
                Security.removeProvider(broken.name)
                Security.removeProvider(working.name)
            }
        }
        assertEquals(before, Security.getProviders().map { it.name })
    }

    @Test fun unavailableKeyDoesNotLeaveAnInstallableOrPartialOutput() {
        val key = signingKey()
        val output = temporary.newFile("failed.apk")
        output.writeText("previous artifact")
        assertThrows(Exception::class.java) {
            signLauncherArchive(unsigned(), output, OpaqueLauncherKey(key.privateKey), key.certificate as X509Certificate)
        }
        assertFalse(output.exists())
    }

    @Test fun aMismatchedCertificateCannotProduceAnEntryOrReplaceTheOriginalKey() {
        val first = signingKey()
        val second = signingKey()
        val output = temporary.newFile("wrong-certificate.apk")
        assertThrows(Exception::class.java) {
            signLauncherArchive(unsigned(), output, first.privateKey, second.certificate as X509Certificate)
        }
        assertFalse(output.exists())
    }
}

/** Models a non-exportable device key; only its own signing provider can operate on it. */
class OpaqueLauncherKey(val delegate: PrivateKey) : PrivateKey {
    override fun getAlgorithm() = "RSA"
    override fun getFormat(): String? = null
    override fun getEncoded(): ByteArray? = null
}

@Suppress("DEPRECATION")
class TestSigningProvider(name: String, implementation: Class<out SignatureSpi>) : Provider(name, 1.0, "Test only") {
    init {
        put("Signature.SHA256withRSA", implementation.name)
        put("Signature.SHA256withRSA SupportedKeyClasses", OpaqueLauncherKey::class.java.name)
    }
}

class BrokenLauncherSignature : SignatureSpi() {
    override fun engineInitSign(key: PrivateKey) {
        if (key !is OpaqueLauncherKey) throw InvalidKeyException("Not a device key")
    }
    override fun engineInitVerify(key: PublicKey): Unit = throw InvalidKeyException("Signing only")
    override fun engineUpdate(value: Byte) = Unit
    override fun engineUpdate(value: ByteArray, offset: Int, length: Int) = Unit
    override fun engineSign(): ByteArray {
        calls.incrementAndGet()
        throw SignatureException("Simulated default-provider failure after initialization")
    }
    override fun engineVerify(signature: ByteArray) = false
    @Deprecated("Legacy JCA parameter") override fun engineSetParameter(name: String, value: Any?) = Unit
    @Deprecated("Legacy JCA parameter") override fun engineGetParameter(name: String): Any? = null
    companion object { val calls = AtomicInteger() }
}

class WorkingLauncherSignature : SignatureSpi() {
    private var signer: Signature? = null
    override fun engineInitSign(key: PrivateKey) {
        if (key !is OpaqueLauncherKey) throw InvalidKeyException("Not a device key")
        signer = Signature.getInstance("SHA256withRSA", "SunRsaSign").apply { initSign(key.delegate) }
    }
    override fun engineInitVerify(key: PublicKey): Unit = throw InvalidKeyException("Signing only")
    override fun engineUpdate(value: Byte) { checkNotNull(signer).update(value) }
    override fun engineUpdate(value: ByteArray, offset: Int, length: Int) { checkNotNull(signer).update(value, offset, length) }
    override fun engineSign(): ByteArray = checkNotNull(signer).sign()
    override fun engineVerify(signature: ByteArray) = false
    @Deprecated("Legacy JCA parameter") override fun engineSetParameter(name: String, value: Any?) = Unit
    @Deprecated("Legacy JCA parameter") override fun engineGetParameter(name: String): Any? = null
}
