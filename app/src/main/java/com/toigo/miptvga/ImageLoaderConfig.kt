package com.toigo.miptvga

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private const val ImageDiskCacheMaxSizeBytes = 100L * 1024L * 1024L
private const val ImageMemoryCacheFraction = 0.25
private const val ImageDiskCacheSubdirectory = "coil_image_cache"
private const val ImageHttpConnectTimeoutSeconds = 15L
private const val ImageHttpReadTimeoutSeconds = 30L

@OptIn(coil.annotation.ExperimentalCoilApi::class)
internal object ImageLoaderConfig {

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
        clearMemoryCache(context)
        clearDiskCache(context)
    }

    fun diskCacheSizeBytes(context: Context): Long {
        val loader = get(context)
        return loader.diskCache?.size ?: 0L
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
