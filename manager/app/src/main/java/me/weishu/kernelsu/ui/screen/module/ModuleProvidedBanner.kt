package me.weishu.kernelsu.ui.screen.module

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.topjohnwu.superuser.io.SuFile
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.data.model.Module
import me.weishu.kernelsu.ksuApp
import okhttp3.Request

private const val MAX_BANNER_BYTES = 8 * 1024 * 1024
private const val MAX_BANNER_SIDE = 1200
private const val FAILED_BANNER_RETRY_MILLIS = 60_000L
private val validModuleId = Regex("^[A-Za-z][A-Za-z0-9._-]+$")

internal sealed interface ModuleBannerSource {
    data class Local(val moduleId: String, val relativePath: String) : ModuleBannerSource
    data class Remote(val url: String) : ModuleBannerSource
}

// KernelSU-Next uses the module.prop "banner" value as either a module-relative
// image path or an HTTP(S) URL. Keep local paths inside the module directory.
internal fun moduleBannerSource(moduleId: String, rawBanner: String): ModuleBannerSource? {
    val banner = rawBanner.trim()
    if (banner.isEmpty() || banner.length > 2048 || banner.any(Char::isISOControl)) return null

    if (banner.contains("://")) {
        val uri = runCatching { URI(banner) }.getOrNull() ?: return null
        if (
            (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null
        ) {
            return ModuleBannerSource.Remote(banner)
        }
        return null
    }

    if (moduleId.length > 128 || !validModuleId.matches(moduleId)) return null
    if (banner.startsWith('/') || '\\' in banner || ':' in banner) return null
    if (banner.split('/').any { it.isEmpty() || it == "." || it == ".." }) return null
    return ModuleBannerSource.Local(moduleId, banner)
}

internal data class ModuleCardBackground(
    val bitmap: Bitmap?,
    val customEntry: ModuleCardWallpaperEntry?,
)

@Composable
internal fun rememberModuleCardBackground(
    module: Module,
    customEntry: ModuleCardWallpaperEntry?,
    customLoad: ModuleWallpaperBitmapLoadState,
): ModuleCardBackground {
    val bannerBitmap = rememberModuleProvidedBannerBitmap(
        module = module,
        enabled = customEntry == null || customLoad.failed,
    )
    return ModuleCardBackground(
        bitmap = customLoad.bitmap ?: bannerBitmap,
        customEntry = if (customLoad.bitmap != null) customEntry else null,
    )
}

@Composable
private fun rememberModuleProvidedBannerBitmap(module: Module, enabled: Boolean): Bitmap? {
    val source = remember(module.id, module.banner) {
        moduleBannerSource(module.id, module.banner)
    }
    var bitmap by remember(source, module.versionCode, enabled) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(source, module.versionCode, enabled) {
        bitmap = if (enabled && source != null) {
            loadModuleBannerBitmap(source, module.versionCode)
        } else {
            null
        }
    }
    return bitmap
}

private val bannerMemoryCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
}
private val failedBannerCache = LruCache<String, Long>(128)
private val bannerClient by lazy {
    ksuApp.okhttpClient.newBuilder().callTimeout(10, TimeUnit.SECONDS).build()
}

private suspend fun loadModuleBannerBitmap(
    source: ModuleBannerSource,
    versionCode: Int,
): Bitmap? = withContext(Dispatchers.IO) {
    val key = source.toString() + "|" + versionCode
    synchronized(bannerMemoryCache) {
        bannerMemoryCache.get(key)
    }?.takeUnless(Bitmap::isRecycled)?.let { return@withContext it }

    val now = SystemClock.elapsedRealtime()
    val failedAt = synchronized(failedBannerCache) { failedBannerCache.get(key) }
    if (failedAt != null && now - failedAt < FAILED_BANNER_RETRY_MILLIS) {
        return@withContext null
    }

    val bytes = try {
        when (source) {
            is ModuleBannerSource.Local -> readLocalBanner(source)
            is ModuleBannerSource.Remote -> readRemoteBanner(source.url)
        }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        null
    }
    val decoded = try {
        bytes?.let(::decodeBanner)
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        null
    }
    if (decoded == null) {
        synchronized(failedBannerCache) { failedBannerCache.put(key, now) }
    } else {
        synchronized(bannerMemoryCache) { bannerMemoryCache.put(key, decoded) }
        synchronized(failedBannerCache) { failedBannerCache.remove(key) }
    }
    decoded
}

private fun readLocalBanner(source: ModuleBannerSource.Local): ByteArray? {
    for (root in listOf("/data/adb/modules", "/data/adb/modules_update")) {
        val path = root + "/" + source.moduleId + "/" + source.relativePath
        val bytes = runCatching {
            SuFile(path).newInputStream().use(InputStream::readBannerBytes)
        }.getOrNull()
        if (bytes != null) return bytes
    }
    return null
}

private fun readRemoteBanner(url: String): ByteArray? {
    val request = Request.Builder().url(url).header("Accept", "image/*").build()
    return bannerClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return@use null
        val body = response.body
        if (body.contentLength() > MAX_BANNER_BYTES) return@use null
        body.byteStream().use(InputStream::readBannerBytes)
    }
}

private fun InputStream.readBannerBytes(): ByteArray? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (output.size() + count > MAX_BANNER_BYTES) return null
        output.write(buffer, 0, count)
    }
    return output.toByteArray().takeIf(ByteArray::isNotEmpty)
}

private fun decodeBanner(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (
        bounds.outWidth / sampleSize > MAX_BANNER_SIDE ||
        bounds.outHeight / sampleSize > MAX_BANNER_SIDE
    ) {
        sampleSize *= 2
    }
    return BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sampleSize },
    )
}
