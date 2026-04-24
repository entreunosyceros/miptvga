package com.toigo.miptvga

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

private const val PlaybackCacheMaxSizeBytes = 96L * 1024L * 1024L
private const val HttpConnectTimeoutMillis = 20_000
private const val HttpReadTimeoutMillis = 45_000

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
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(HttpConnectTimeoutMillis)
            .setReadTimeoutMs(HttpReadTimeoutMillis)
            .setUserAgent("miptvga/1.0")

        val upstreamFactory = DefaultDataSource.Factory(appContext, httpDataSourceFactory)

        val baseFactory: DataSource.Factory = if (useCache) {
            CacheDataSource.Factory()
                .setCache(getCache(appContext))
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } else {
            upstreamFactory
        }

        if (requestHeaders.isEmpty()) {
            return baseFactory
        }

        return ResolvingDataSource.Factory(baseFactory) { dataSpec ->
            dataSpec.withRequestHeaders(requestHeaders)
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

            File(appContext.cacheDir, "media_stream_cache").deleteRecursively()
        }
    }

    @UnstableApi
    private fun getCache(context: Context): SimpleCache {
        simpleCache?.let { return it }

        return synchronized(this) {
            simpleCache?.let { return@synchronized it }

            val cacheDirectory = File(context.cacheDir, "media_stream_cache").apply {
                if (!exists()) mkdirs()
            }

            val cache = SimpleCache(
                cacheDirectory,
                LeastRecentlyUsedCacheEvictor(PlaybackCacheMaxSizeBytes),
                StandaloneDatabaseProvider(context)
            )
            simpleCache = cache
            cache
        }
    }
}

