package me.weishu.kernelsu.ui.util

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Calendar
import java.util.Date
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.MainActivity

internal const val INSTALLED_LAUNCHER_HIDDEN = "installed_launcher_hidden"
private const val CHALLENGE_KEY = "installed_launcher_challenge"
private const val CHALLENGE_TIME_KEY = "installed_launcher_challenge_time"
private const val KEY_ALIAS = "stersu-launcher-only-v1"
private const val ENTRY_CLASS = "me.weishu.kernelsu.launcher.LauncherActivity"
private const val CHALLENGE_EXTRA = "launcher_challenge"

internal fun validLauncherChallenge(expected: String?, actual: String?, createdAt: Long, now: Long): Boolean =
    !expected.isNullOrBlank() && expected == actual && createdAt > 0 && now >= createdAt && now - createdAt in 0..120_000L

internal data class InstalledLauncherStatus(
    val installed: Boolean,
    val trusted: Boolean,
    val hidden: Boolean,
    val lastUpdateTime: Long? = null,
)

internal fun shouldAutoHideInstalledLauncher(previousUpdateTime: Long?, current: InstalledLauncherStatus): Boolean =
    current.installed && current.trusted && !current.hidden &&
        current.lastUpdateTime != null && current.lastUpdateTime > 0 && current.lastUpdateTime != previousUpdateTime

/** This is only a launcher replacement; it never changes root or stealth mode. */
internal object InstalledLauncher {
    private fun preferences(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private fun packageName(context: Context) = "${context.packageName}.launcher"
    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun status(context: Context): InstalledLauncherStatus {
        val hidden = preferences(context).getBoolean(INSTALLED_LAUNCHER_HIDDEN, false)
        val info = try {
            context.packageManager.getPackageInfo(packageName(context),
                PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_PERMISSIONS)
        } catch (_: PackageManager.NameNotFoundException) {
            return InstalledLauncherStatus(installed = false, trusted = false, hidden = hidden)
        }
        val trusted = runCatching {
            val expected = keyStore().getCertificate(KEY_ALIAS)?.encoded ?: return@runCatching false
            val signers = info.signingInfo?.apkContentsSigners.orEmpty()
            val component = ComponentName(packageName(context), ENTRY_CLASS)
            val activity = context.packageManager.getActivityInfo(component, 0)
            val disabledStates = setOf(PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED)
            info.requestedPermissions.isNullOrEmpty() && signers.size == 1 &&
                MessageDigest.isEqual(signers.single().toByteArray(), expected) &&
                activity.enabled && activity.exported && activity.applicationInfo.enabled &&
                context.packageManager.getApplicationEnabledSetting(packageName(context)) !in disabledStates &&
                context.packageManager.getComponentEnabledSetting(component) !in disabledStates
        }.getOrDefault(false)
        return InstalledLauncherStatus(installed = true, trusted = trusted, hidden = hidden,
            lastUpdateTime = info.lastUpdateTime)
    }

    /** Run off the UI thread. The per-install signing key never leaves AndroidKeyStore. */
    fun create(context: Context, bitmap: Bitmap, name: String): File {
        val current = status(context)
        check(!current.installed || current.trusted) { context.getString(R.string.launcher_install_conflict) }
        val store = keyStore()
        if (!store.containsAlias(KEY_ALIAS)) {
            val expiry = Calendar.getInstance().apply { add(Calendar.YEAR, 50) }.time
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply {
                initialize(
                    KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                        .setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA256)
                        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                        .setCertificateSubject(javax.security.auth.x500.X500Principal("CN=SterSU local launcher"))
                        .setCertificateSerialNumber(BigInteger.ONE)
                        .setCertificateNotBefore(Date(System.currentTimeMillis() - 86_400_000L))
                        .setCertificateNotAfter(expiry).build(),
                )
                generateKeyPair()
            }
        }
        val entry = keyStore().getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        val directory = File(context.cacheDir, "launcher-entry").apply { mkdirs() }
        val unsigned = File(directory, "unsigned.apk")
        val output = File(directory, "SterSU-entry.apk")
        try {
            val png = ByteArrayOutputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                it.toByteArray()
            }
            val label = name.trim().ifBlank { context.getString(R.string.app_name) }
            context.assets.open("launcher/template.apk").use {
                writeLauncherArchive(it, unsigned, label, png)
            }
            signLauncherArchive(unsigned, output, entry.privateKey, entry.certificate as X509Certificate)
            return output
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally {
            unsigned.delete()
        }
    }

    fun installationIntent(context: Context, file: File): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file),
            "application/vnd.android.package-archive",
        )
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun verifyAndHide(context: Context) {
        check(status(context).trusted) { context.getString(R.string.launcher_install_not_ready) }
        val nonce = ByteArray(32).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        check(preferences(context).edit().putString(CHALLENGE_KEY, nonce)
            .putLong(CHALLENGE_TIME_KEY, System.currentTimeMillis()).commit())
        try {
            context.startActivity(Intent().setComponent(ComponentName(packageName(context), ENTRY_CLASS))
                .putExtra(CHALLENGE_EXTRA, nonce))
        } catch (error: Exception) {
            clearChallenge(context)
            throw error
        }
    }

    private fun clearChallenge(context: Context) {
        preferences(context).edit().remove(CHALLENGE_KEY).remove(CHALLENGE_TIME_KEY).commit()
    }

    fun acceptChallenge(context: Context, actual: String?): Boolean {
        val prefs = preferences(context)
        val valid = validLauncherChallenge(prefs.getString(CHALLENGE_KEY, null), actual,
            prefs.getLong(CHALLENGE_TIME_KEY, 0L), System.currentTimeMillis())
        if (!valid) return false
        clearChallenge(context) // One use, including failed attempts to hide.
        if (!status(context).trusted) return false
        // Commit intent first. Any partial PackageManager failure restores the original icon.
        if (!prefs.edit().putBoolean(INSTALLED_LAUNCHER_HIDDEN, true).commit()) return false
        return runCatching {
            LauncherIconOption.entries.forEach {
                context.packageManager.setComponentEnabledSetting(
                    ComponentName(context.packageName, it.aliasClassName),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP,
                )
            }
            true
        }.getOrElse {
            restore(context)
            Log.e("InstalledLauncher", "Unable to hide launcher aliases", it)
            false
        }
    }

    fun restore(context: Context): Boolean {
        val option = LauncherIconOption.fromValue(preferences(context).getString(LauncherIconOption.PREF_KEY, null))
        return applyLauncherIcon(context, option)
    }
}

/** Explicit, always-enabled bridge: only launcher aliases are ever disabled. */
class LauncherEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("restore_launcher", false)) InstalledLauncher.restore(this)
        val challenge = intent.getStringExtra(CHALLENGE_EXTRA)
        startActivity(Intent(this, MainActivity::class.java)
            .putExtra(CHALLENGE_EXTRA, challenge)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
}

/** Restore the main icon if the entry is removed; never grant root or alter stealth state. */
class InstalledLauncherRemovalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.data?.schemeSpecificPart != "${context.packageName}.launcher" ||
            intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
        if (context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean(INSTALLED_LAUNCHER_HIDDEN, false)) {
            InstalledLauncher.restore(context)
        }
    }
}
