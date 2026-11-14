package com.toigo.miptvga

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.test.platform.app.InstrumentationRegistry
import coil.disk.DiskCache
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

/** Isolated fixture directories/database/preferences. Never clears the installed user's data. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(coil.annotation.ExperimentalCoilApi::class)
class MaintenanceCacheAndroidTest {
    private class FixtureContext(base: Context, val root: File, val prefix: String) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        override fun getDatabasePath(name: String): File = File(root, "databases/$name").apply { parentFile!!.mkdirs() }
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
            SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
            SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path, factory, errorHandler)
        override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("${prefix}_$name", mode)
    }

    @Test fun media3CleanupWaitsForReaderThenRemovesFilesAndKeepsPersistentPreferences() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val id = "maintenance_test_${UUID.randomUUID()}"
        val context = FixtureContext(base, File(base.cacheDir, id).apply { mkdirs() }, id)
        val store = LastPlaylistStore(context)
        val source = PlaylistSource(PlaylistSourceType.URL, "https://example.invalid/list.m3u", "Synthetic")
        store.saveSource(source)
        store.saveFavoriteIds(setOf("synthetic-favorite"))
        store.saveShowChannelLogos(false)
        val media = File(context.root, "source.bin").apply { writeBytes(ByteArray(8192) { 12 }) }
        var readerHeld = true
        PlaybackCache.acquireReader()
        try {
            val data = PlaybackCache.createDataSourceFactory(context).createDataSource()
            try {
                data.open(DataSpec(Uri.fromFile(media)))
                val buffer = ByteArray(1024)
                while (data.read(buffer, 0, buffer.size) != C.RESULT_END_OF_INPUT) { }
            } finally { data.close() }
            val directory = File(context.cacheDir, PlaybackCacheDirectory)
            assertTrue((CacheFiles().measure(directory) as StorageSize.Ready).bytes >= 8192L)
            assertEquals(CleanupStatus.PENDING, PlaybackCache.requestClear(context).status)
            assertTrue(directory.exists())
            PlaybackCache.releaseReader(context)
            readerHeld = false
            val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
            while (PlaybackCache.cleanup.value?.status == CleanupStatus.PENDING && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(20)
            assertEquals(CleanupStatus.COMPLETED, PlaybackCache.cleanup.value?.status)
            assertEquals(StorageSize.Ready(0), readCacheStorage(context.cacheDir).playback)
            CacheFiles().clear(context.cacheDir, OwnedCacheDirectories)
            assertEquals(source, store.readSource())
            assertEquals(setOf("synthetic-favorite"), store.readFavoriteIds())
            assertFalse(store.readShowChannelLogos())
            assertTrue(media.exists())
        } finally {
            if (readerHeld) PlaybackCache.releaseReader(context)
            context.getSharedPreferences("miptvga_prefs", Context.MODE_PRIVATE).edit().clear().commit()
            context.root.deleteRecursively()
        }
    }

    @Test fun coilActiveSnapshotDefersDeletionUntilCloseAndJournalIsNotImagePayload() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "maintenance_coil_test_${UUID.randomUUID()}").apply { mkdirs() }
        val disk = DiskCache.Builder().directory(root).maxSizeBytes(1024L * 1024).build()
        try {
            val editor = disk.openEditor("synthetic")!!
            editor.metadata.toFile().writeText("Synthetic metadata")
            editor.data.toFile().writeBytes(ByteArray(4096) { 9 })
            editor.commit()
            val snapshot = disk.openSnapshot("synthetic")!!
            val content = snapshot.data.toFile()
            try {
                assertEquals(CleanupStatus.PENDING, clearImageDiskCache(disk).status)
                assertTrue(content.exists())
                assertEquals(4096L, content.length())
            } finally { snapshot.close() }
            assertEquals(0L, disk.size)
            assertFalse(content.exists())
            assertEquals(CleanupStatus.EMPTY, clearImageDiskCache(disk).status)
            assertTrue(CacheFiles().measure(root) is StorageSize.Ready) // Journal overhead may remain.
        } finally { root.deleteRecursively() }
    }
}
