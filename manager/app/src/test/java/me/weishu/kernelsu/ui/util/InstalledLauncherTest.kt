package me.weishu.kernelsu.ui.util

import com.android.apksig.ApkVerifier
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.Properties
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InstalledLauncherTest {
    @get:Rule val temporary = TemporaryFolder()
    private val template get() = File("build/generated/launcherTemplate/launcher/template.apk")
    private fun manifest() = ZipFile(template).use { it.getInputStream(it.getEntry("AndroidManifest.xml")).readBytes() }
    private fun source(path: String) = File("src/main/java/me/weishu/kernelsu/$path").readText()

    @Test
    fun challengeRequiresTheExactNonceAndExpiresWithoutHidingAnything() {
        assertTrue(validLauncherChallenge("one-use-token", "one-use-token", 1_000, 1_001))
        assertTrue(validLauncherChallenge("x", "x", 1_000, 121_000))
        assertFalse(validLauncherChallenge("x", "wrong", 1_000, 1_001))
        assertFalse(validLauncherChallenge(null, null, 1_000, 1_001))
        assertFalse(validLauncherChallenge("", "", 1_000, 1_001))
        assertFalse(validLauncherChallenge("x", "x", 1_000, 999))
        assertFalse(validLauncherChallenge("x", "x", 1_000, 121_001))
        assertFalse(validLauncherChallenge("x", "x", Long.MIN_VALUE, 1_001))
        assertFalse(validLauncherChallenge("x", "x", 0, 1_001))
    }

    @Test
    fun binaryManifestKeepsAllOtherChunksAndSupportsUnicodeNames() {
        val original = manifest()
        val before = ByteBuffer.wrap(original).order(ByteOrder.LITTLE_ENDIAN)
        val oldPoolSize = before.getInt(12)
        listOf("SterSU", "我的入口 · 星辰 🌠", "x".repeat(80)).forEach { label ->
            val patched = replaceLauncherManifestLabel(original, label)
            val buffer = ByteBuffer.wrap(patched).order(ByteOrder.LITTLE_ENDIAN)
            val newPoolSize = buffer.getInt(12)
            assertEquals(patched.size, buffer.getInt(4))
            assertEquals(0, newPoolSize % 4)
            assertArrayEquals(original.copyOfRange(8 + oldPoolSize, original.size),
                patched.copyOfRange(8 + newPoolSize, patched.size))
            assertTrue(String(patched, Charsets.UTF_16LE).contains(label))
            assertFalse(String(patched, Charsets.UTF_16LE).contains(LAUNCHER_LABEL_PLACEHOLDER))
        }
    }

    @Test
    fun malformedTemplatesAndLabelsAreRejected() {
        listOf("", " ", "x".repeat(81), "bad\nlabel", "\u0000").forEach {
            assertThrows(IllegalArgumentException::class.java) { replaceLauncherManifestLabel(manifest(), it) }
        }
        assertThrows(IllegalArgumentException::class.java) { replaceLauncherManifestLabel(byteArrayOf(1), "name") }
        val invalid = manifest().apply { this[8] = 0 }
        assertThrows(IllegalArgumentException::class.java) { replaceLauncherManifestLabel(invalid, "name") }
    }

    @Test
    fun generatedApkContainsTheCustomIconAndAlignedResourceTable() {
        val output = temporary.newFile("entry.apk")
        val icon = byteArrayOf(1, 2, 3, 4)
        template.inputStream().use { writeLauncherArchive(it, output, "入口", icon) }
        ZipFile(output).use { zip ->
            val iconEntry = zip.entries().asSequence().single { it.name.startsWith("res/") && it.name.endsWith(".png") }
            assertArrayEquals(icon, zip.getInputStream(iconEntry).readBytes())
            assertEquals(ZipEntry.STORED, zip.getEntry("resources.arsc").method)
            assertNotNull(zip.getEntry("classes.dex"))
        }
        val bytes = output.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var at = 0
        var count = 0
        while (buffer.getInt(at) == 0x04034b50) {
            val nameLength = buffer.getShort(at + 26).toInt() and 0xffff
            val extraLength = buffer.getShort(at + 28).toInt() and 0xffff
            val data = at + 30 + nameLength + extraLength
            assertEquals("ZIP entry at $at", 0, data % 4)
            at = data + buffer.getInt(at + 18)
            count++
        }
        assertTrue(count >= 4)
    }

    @Test
    fun v2SignatureVerifiesAndDetectsTampering() {
        val directory = temporary.newFolder("signing")
        val store = File(directory, "test-only.p12")
        val keytool = File(System.getProperty("java.home"), "bin/keytool" + if (File.separatorChar == '\\') ".exe" else "")
        val process = ProcessBuilder(keytool.path, "-genkeypair", "-alias", "test", "-keyalg", "RSA",
            "-keysize", "2048", "-dname", "CN=Disposable launcher test", "-validity", "2",
            "-keystore", store.path, "-storetype", "PKCS12", "-storepass", "test-only-password", "-noprompt")
            .redirectErrorStream(true).start()
        val diagnostic = process.inputStream.bufferedReader().readText()
        assertEquals(diagnostic, 0, process.waitFor())
        val keys = KeyStore.getInstance("PKCS12").apply { store.inputStream().use { load(it, "test-only-password".toCharArray()) } }
        val key = keys.getEntry("test", KeyStore.PasswordProtection("test-only-password".toCharArray())) as KeyStore.PrivateKeyEntry
        val unsigned = File(directory, "unsigned.apk")
        val signed = File(directory, "signed.apk")
        val png = ZipFile(template).use { zip ->
            val icon = zip.entries().asSequence().single { it.name.startsWith("res/") && it.name.endsWith(".png") }
            zip.getInputStream(icon).readBytes()
        }
        template.inputStream().use { writeLauncherArchive(it, unsigned, "测试入口 🌠", png) }
        signLauncherArchive(unsigned, signed, key.privateKey, key.certificate as X509Certificate)
        val verified = ApkVerifier.Builder(signed).setMinCheckedPlatformVersion(31).build().verify()
        assertTrue(verified.errors.toString(), verified.isVerified)
        assertTrue(verified.isVerifiedUsingV2Scheme)
        assertArrayEquals(key.certificate.encoded, verified.signerCertificates.single().encoded)
        // Parse the finished, signed artifact with the SDK too, not only our own code.
        val properties = Properties().apply {
            File("../local.properties").takeIf(File::isFile)?.inputStream()?.use { load(it) }
        }
        val sdk = File(System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
            ?: checkNotNull(properties.getProperty("sdk.dir")))
        val suffix = if (File.separatorChar == '\\') ".exe" else ""
        val buildTools = checkNotNull(File(sdk, "build-tools").listFiles()?.filter {
            File(it, "aapt2$suffix").isFile
        }?.maxByOrNull { it.name })
        fun sdkCommand(name: String, vararg arguments: String): String {
            val tool = ProcessBuilder(listOf(File(buildTools, name + suffix).path) + arguments)
                .redirectErrorStream(true).start()
            val result = tool.inputStream.bufferedReader().readText()
            assertEquals(result, 0, tool.waitFor())
            return result
        }
        val badging = sdkCommand("aapt2", "dump", "badging", signed.path)
        assertTrue(badging, badging.contains("application-label:'测试入口 🌠'"))
        assertTrue(badging.contains("name='io.github.fixz.stersu.launcher'"))
        assertFalse(badging.contains("uses-permission:"))
        sdkCommand("zipalign", "-c", "4", signed.path)
        val tampered = File(directory, "tampered.apk")
        Files.copy(signed.toPath(), tampered.toPath())
        java.io.RandomAccessFile(tampered, "rw").use {
            val bytes = signed.readBytes()
            val offset = bytes.indices.first { at -> at + 4 < bytes.size &&
                bytes[at] == 'd'.code.toByte() && bytes[at + 1] == 'e'.code.toByte() &&
                bytes[at + 2] == 'x'.code.toByte() && bytes[at + 3] == '\n'.code.toByte() }
            it.seek(offset.toLong())
            it.writeByte(0)
        }
        assertFalse(ApkVerifier.Builder(tampered).setMinCheckedPlatformVersion(31).build().verify().isVerified)
    }

    @Test
    fun shellCannotSelectAnArbitraryTargetAndHasNoPermissions() {
        val manifest = File("../launcher/src/main/AndroidManifest.xml").readText()
        val shell = File("../launcher/src/main/java/me/weishu/kernelsu/launcher/LauncherActivity.java").readText()
        assertFalse(manifest.contains("uses-permission"))
        assertTrue(shell.contains("me.weishu.kernelsu.ui.util.LauncherEntryActivity"))
        assertFalse(shell.contains("getParcelableExtra"))
        assertFalse(shell.contains("putExtras("))
        assertTrue(File("../launcher/src/main/res/xml/shortcuts.xml").readText().contains("restore_manager_icon"))
    }

    @Test
    fun installationAndCancellationCannotHideTheMainActivity() {
        val screen = source("ui/screen/launchericon/LauncherIconScreen.kt")
        val entry = source("ui/util/InstalledLauncher.kt")
        assertTrue(screen.contains("canRequestPackageInstalls()"))
        assertTrue(screen.contains("InstalledLauncher.create(context, bitmap, managerName)"))
        assertFalse(screen.contains("createManagerShortcut"))
        assertTrue(entry.contains("status(context).trusted"))
        assertTrue(entry.contains("setCertificateSubject"))
        assertTrue(entry.contains("AndroidKeyStore"))
        assertTrue(entry.contains("clearChallenge(context) // One use"))
        val creation = entry.substringAfter("fun create(").substringBefore("fun installationIntent(")
        assertFalse(creation.contains("setComponentEnabledSetting"))
        assertFalse(creation.contains("INSTALLED_LAUNCHER_HIDDEN"))
        val bridge = entry.substringAfter("class LauncherEntryActivity")
        assertFalse(bridge.contains("COMPONENT_ENABLED_STATE_DISABLED"))
        assertFalse(entry.contains("StealthModeStore"))
        assertFalse(entry.contains("getRootShell"))
        val main = source("ui/MainActivity.kt")
        assertTrue(main.substringAfter("override fun onPostResume()").substringBefore("override fun onStart()")
            .contains("InstalledLauncher.acceptChallenge(this, challenge)"))
        assertTrue(source("ui/util/LauncherIconOption.kt").contains("InstalledLauncher.restore(appContext)"))
    }
}
