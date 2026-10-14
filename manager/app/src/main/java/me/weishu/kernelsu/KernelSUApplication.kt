package me.weishu.kernelsu

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.UserManager
import android.system.Os
import android.util.Log
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.lsposed.hiddenapibypass.HiddenApiBypass
import me.weishu.kernelsu.ui.util.AppLanguageManager
import me.weishu.kernelsu.ui.util.ManagerPlugin
import me.weishu.kernelsu.ui.util.ManagerPluginRegistry
import me.weishu.kernelsu.ui.util.reconcileLauncherIcon
import me.weishu.kernelsu.ui.util.execKsud
import me.weishu.kernelsu.stealth.StealthModeStore
import java.io.File
import java.util.Locale
import kotlin.concurrent.thread

lateinit var ksuApp: KernelSUApplication

class KernelSUApplication : Application(), ViewModelStoreOwner {

    companion object {
        private const val TAG = "KernelSUApplication"

        fun setEnableOnBackInvokedCallback(appInfo: ApplicationInfo, enable: Boolean): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
            return runCatching {
                HiddenApiBypass.addHiddenApiExemptions(
                    "Landroid/content/pm/ApplicationInfo;->setEnableOnBackInvokedCallback"
                )
                val applicationInfoClass = ApplicationInfo::class.java
                val method = applicationInfoClass.getDeclaredMethod("setEnableOnBackInvokedCallback", Boolean::class.javaPrimitiveType)
                method.isAccessible = true
                method.invoke(appInfo, enable)
            }.onFailure { error ->
                Log.w(TAG, "update predictive back callback failed", error)
            }.isSuccess
        }
    }

    lateinit var okhttpClient: OkHttpClient
    private val appViewModelStore by lazy { ViewModelStore() }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguageManager.wrapContext(base))
    }

    private fun isUserUnlocked(): Boolean =
        getSystemService(UserManager::class.java)?.isUserUnlocked == true

    override fun onCreate() {
        super.onCreate()
        ksuApp = this
        AppLanguageManager.syncPlatformLanguage(this)

        runCatching { Os.setenv("TMPDIR", cacheDir.absolutePath, true) }
            .onFailure { Log.w(TAG, "set TMPDIR failed", it) }
        okhttpClient = createOkHttpClient()

        if (!isUserUnlocked()) {
            return
        }

        reconcileLauncherIcon(this)
        retireLegacyWebManager()
        StealthModeStore.reconcileFromRootAsync(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val prefs = this.getSharedPreferences("settings", MODE_PRIVATE)
            val enable = prefs.getBoolean("enable_predictive_back", false)
            setEnableOnBackInvokedCallback(applicationInfo, enable)
        }

    }

    override val viewModelStore: ViewModelStore
        get() = appViewModelStore

    private fun retireLegacyWebManager() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        prefs.edit().putBoolean("web_manager_auto_start", false).apply()
        if (prefs.getBoolean("web_manager_retired_v1", false)) return
        thread(name = "retire-web-manager", isDaemon = true) {
            if (execKsud("web-manager disable", newShell = true)) {
                runCatching {
                    ManagerPluginRegistry(this).remove(ManagerPlugin.RemoteManagementSuite.id)
                }.onSuccess {
                    prefs.edit().putBoolean("web_manager_retired_v1", true).apply()
                }.onFailure { Log.w(TAG, "retire web manager plugin record failed", it) }
            }
        }
    }

    private fun createOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().cache(Cache(File(cacheDir, "okhttp"), 10 * 1024 * 1024))
            .addInterceptor { block ->
                block.proceed(
                    block.request().newBuilder()
                        .header("User-Agent", "KernelSU/${BuildConfig.VERSION_CODE}")
                        .header("Accept-Language", Locale.getDefault().toLanguageTag()).build()
                )
            }.build()
    }
}
