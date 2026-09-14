package com.toigo.miptvga

import android.app.ActivityManager
import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

private const val PlaybackCacheHighMemoryBytes = 150L * 1024L * 1024L
private const val PlaybackCacheLowMemoryBytes = 96L * 1024L * 1024L
private const val HighMemoryThresholdBytes = 2L * 1024L * 1024L * 1024L
private const val HttpConnectTimeoutMillis = 20_000
private const val HttpReadTimeoutMillis = 45_000
private const val CacheDirectoryName = "media_stream_cache"

@UnstableApi
internal object PlaybackCache {
    @Volatile
    @UnstableApi
    private var simpleCache: SimpleCache? = null

    @UnstableApi
    fun createDataSourceFactory(
        context: Context,
        requestHeaders: Map<String, String> = emptyMap(),
        useCache: Boolean = true
    ): DataSource.Factory {
        val appContext = context.applicationContext
        val effectiveHeaders = ensureIptvHeaders(requestHeaders)
        val userAgent = effectiveHeaders.entries
            .firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
            ?.value
            ?: IptvDefaultUserAgent

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(HttpConnectTimeoutMillis)
            .setReadTimeoutMs(HttpReadTimeoutMillis)
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(effectiveHeaders)

        val upstreamFactory = DefaultDataSource.Factory(appContext, httpDataSourceFactory)

        return if (useCache) {
            CacheDataSource.Factory()
                .setCache(getCache(appContext))
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } else {
            upstreamFactory
        }
    }

    @UnstableApi
    fun clear(context: Context) {
        val appContext = context.applicationContext
        synchronized(this) {
            val cache = getCache(appContext)
            cache.keys.toList().forEach { key ->
                runCatching { cache.removeResource(key) }
            }
        }
    }

    @UnstableApi
    fun releaseAndClear(context: Context) {
        val appContext = context.applicationContext
        synchronized(this) {
            simpleCache?.let { cache ->
                runCatching {
                    cache.keys.toList().forEach { key ->
                        cache.removeResource(key)
                    }
                }
                runCatching { cache.release() }
                simpleCache = null
            }

            File(appContext.cacheDir, CacheDirectoryName).deleteRecursively()
        }
    }

    @UnstableApi
    fun cacheSizeBytes(context: Context): Long {
        val cacheDir = File(context.applicationContext.cacheDir, CacheDirectoryName)
        if (!cacheDir.exists()) return 0L
        return cacheDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    @UnstableApi
    private fun getCache(context: Context): SimpleCache {
        simpleCache?.let { return it }

        return synchronized(this) {
            simpleCache?.let { return@synchronized it }

            val cacheDirectory = File(context.cacheDir, CacheDirectoryName).apply {
                if (!exists()) mkdirs()
            }

            val maxCacheSize = resolveCacheSize(context)
            val cache = SimpleCache(
                cacheDirectory,
                LeastRecentlyUsedCacheEvictor(maxCacheSize),
                StandaloneDatabaseProvider(context)
            )
            simpleCache = cache
            cache
        }
    }

    private fun resolveCacheSize(context: Context): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)

        return if (memoryInfo.totalMem >= HighMemoryThresholdBytes) {
            PlaybackCacheHighMemoryBytes
        } else {
            PlaybackCacheLowMemoryBytes
        }
    }
}
