package com.toigo.miptvga

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val ImageDiskCacheMaxSizeBytes = 100L * 1024L * 1024L
private const val ImageMemoryCacheFraction = 0.25
private const val ImageDiskCacheSubdirectory = "coil_image_cache"
private const val ImageHttpConnectTimeoutSeconds = 15L
private const val ImageHttpReadTimeoutSeconds = 30L

internal fun clearImageDiskCache(disk: DiskCache): CleanupResult {
    val before = disk.size
    disk.clear()
    return CleanupResult(when {
        disk.size > 0L -> CleanupStatus.PENDING
        before > 0L -> CleanupStatus.COMPLETED
        else -> CleanupStatus.EMPTY
    })
}

@OptIn(coil.annotation.ExperimentalCoilApi::class)
internal object ImageLoaderConfig {
    private val _cleanup = MutableStateFlow<CleanupResult?>(null)
    val cleanup = _cleanup.asStateFlow()

    @Volatile
    private var imageLoader: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        imageLoader?.let { return it }
        return synchronized(this) {
            imageLoader?.let { return@synchronized it }
            createImageLoader(context.applicationContext).also { imageLoader = it }
        }
    }

    fun clearDiskCache(context: Context) {
        val loader = get(context)
        loader.diskCache?.clear()
    }

    fun clearMemoryCache(context: Context) {
        val loader = get(context)
        loader.memoryCache?.clear()
    }

    fun clearAllCaches(context: Context) {
        clearForMaintenance(context)
    }

    @Synchronized
    fun clearForMaintenance(context: Context): CleanupResult {
        val result = try {
            imageLoader?.memoryCache?.clear()
            // Measuring or clearing a genuinely unused cache must not create it.
            if (!File(context.applicationContext.cacheDir, ImageDiskCacheSubdirectory).exists()) {
                CleanupResult(CleanupStatus.EMPTY)
            } else {
                val disk = get(context).diskCache ?: error("Caché de disco no disponible")
                // Coil protects active snapshots/edits; never recursively delete its live files.
                clearImageDiskCache(disk)
            }
        } catch (_: Exception) { CleanupResult(CleanupStatus.FAILED) }
        _cleanup.value = result
        return result
    }

    @Synchronized
    fun checkPendingCleanup(): CleanupResult? {
        if (_cleanup.value?.status == CleanupStatus.PENDING) {
            _cleanup.value = try {
                if (imageLoader?.diskCache?.size == 0L) CleanupResult(CleanupStatus.COMPLETED) else _cleanup.value
            } catch (_: Exception) { CleanupResult(CleanupStatus.FAILED) }
        }
        return _cleanup.value
    }

    private fun createImageLoader(appContext: Context): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(ImageHttpConnectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(ImageHttpReadTimeoutSeconds, TimeUnit.SECONDS)
            .build()

        return ImageLoader.Builder(appContext)
            .crossfade(200)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder(appContext)
                    .maxSizePercent(ImageMemoryCacheFraction)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(appContext.cacheDir.resolve(ImageDiskCacheSubdirectory))
                    .maxSizeBytes(ImageDiskCacheMaxSizeBytes)
                    .build()
            }
            .okHttpClient(okHttpClient)
            .build()
    }
}
