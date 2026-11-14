package com.toigo.miptvga

import android.app.ActivityManager
import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.database.VersionTable
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import java.util.concurrent.Executors
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PlaybackCacheHighMemoryBytes = 150L * 1024L * 1024L
private const val PlaybackCacheLowMemoryBytes = 96L * 1024L * 1024L
private const val HighMemoryThresholdBytes = 2L * 1024L * 1024L * 1024L
private const val HttpConnectTimeoutMillis = 20_000
private const val HttpReadTimeoutMillis = 45_000
private const val CacheDirectoryName = PlaybackCacheDirectory

@androidx.annotation.OptIn(UnstableApi::class)
internal object PlaybackCache {
    private val _cleanup = MutableStateFlow<CleanupResult?>(null)
    val cleanup = _cleanup.asStateFlow()
    private val releasePolicy = CacheMaintenanceCoordinator { _cleanup.value = it }
    private var databaseProvider: StandaloneDatabaseProvider? = null
    // Application-owned, serial maintenance; never holds an Activity or a stream.
    private val maintenance = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "miptvga-cache-release").apply { isDaemon = true }
    }

    fun acquireReader() = synchronized(this) { releasePolicy.acquire() }

    fun releaseReader(context: Context) = synchronized(this) {
        scheduleRelease(context, releasePolicy.releaseReader())
    }

    /** Normal lifecycle teardown preserves cached media and preferences. */
    fun release(context: Context) = synchronized(this) {
        scheduleRelease(context, releasePolicy.request(clear = false))
    }

    private fun scheduleRelease(context: Context, action: CacheReleasePolicy.Action) {
        if (action == CacheReleasePolicy.Action.NONE) return
        val appContext = context.applicationContext
        maintenance.execute {
            synchronized(this) {
                // A replacement Activity/player may have acquired a reader since scheduling.
                val ready = releasePolicy.request(clear = action == CacheReleasePolicy.Action.CLEAR)
                try {
                    performRelease(appContext, ready)
                } catch (_: Exception) {
                    Log.w("MiptvgaPlayback", "No se pudo completar la liberación de caché")
                }
            }
        }
    }
    @Volatile
    @androidx.annotation.OptIn(UnstableApi::class)
    private var simpleCache: SimpleCache? = null

    @androidx.annotation.OptIn(UnstableApi::class)
    fun createDataSourceFactory(
        context: Context,
        requestHeaders: Map<String, String> = emptyMap(),
        useCache: Boolean = true,
        trace: PlaybackTrace? = null
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
            .apply { trace?.let { setTransferListener(it.transferListener) } }

        return if (useCache) {
            CacheDataSource.Factory()
                .setCache(getCache(appContext))
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } else {
            upstreamFactory
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    fun clear(context: Context) {
        releaseAndClear(context)
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    fun releaseAndClear(context: Context): Boolean = requestClear(context).status in setOf(CleanupStatus.COMPLETED, CleanupStatus.EMPTY)

    fun requestClear(context: Context): CleanupResult = synchronized(this) {
        val action = releasePolicy.request(clear = true)
        try { performRelease(context, action) }
        catch (_: Exception) { Log.w("MiptvgaPlayback", "No se pudo completar la limpieza de caché") }
        releasePolicy.result ?: CleanupResult(CleanupStatus.FAILED)
    }

    private fun performRelease(context: Context, action: CacheReleasePolicy.Action) {
        releasePolicy.execute(action, release = {
            // No active reader remains. This also runs on the application-owned executor after exit.
            simpleCache?.release()
            simpleCache = null
            databaseProvider?.close()
            databaseProvider = null
        }, erase = { deleteCacheWithIndex(context.applicationContext) })
    }

    private fun deleteCacheWithIndex(context: Context): CleanupResult {
        val directory = File(context.cacheDir, CacheDirectoryName)
        val before = CacheFiles().measure(directory)
        if (before !is StorageSize.Ready) return CleanupResult(CleanupStatus.FAILED)
        if (!directory.exists()) return CleanupResult(CleanupStatus.EMPTY)
        val uid = directory.listFiles()?.firstOrNull { it.name.matches(Regex("[0-9a-fA-F]{1,16}\\.uid")) }
            ?.name?.removeSuffix(".uid")
        val provider = if (uid != null) StandaloneDatabaseProvider(context) else null
        return try {
            // Deletes the UID-specific Media3 indices too, never the whole shared database.
            SimpleCache.delete(directory, provider)
            val filesGone = !directory.exists() && CacheFiles().measure(directory) == StorageSize.Ready(0)
            val indicesGone = provider == null || listOf(VersionTable.FEATURE_CACHE_CONTENT_METADATA,
                VersionTable.FEATURE_CACHE_FILE_METADATA).all {
                VersionTable.getVersion(provider.readableDatabase, it, uid!!) == VersionTable.VERSION_UNSET
            }
            CleanupResult(when {
                !filesGone || !indicesGone -> CleanupStatus.FAILED
                before.bytes == 0L -> CleanupStatus.EMPTY
                else -> CleanupStatus.COMPLETED
            })
        } finally { provider?.close() }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun getCache(context: Context): SimpleCache {
        simpleCache?.let { return it }

        return synchronized(this) {
            simpleCache?.let { return@synchronized it }

            val cacheDirectory = File(context.cacheDir, CacheDirectoryName).apply {
                if (!exists()) mkdirs()
            }

            val maxCacheSize = resolveCacheSize(context)
            val provider = StandaloneDatabaseProvider(context)
            val cache = SimpleCache(
                cacheDirectory,
                LeastRecentlyUsedCacheEvictor(maxCacheSize),
                provider
            )
            databaseProvider = provider
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
