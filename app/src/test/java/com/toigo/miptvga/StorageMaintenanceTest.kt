package com.toigo.miptvga

import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StorageMaintenanceTest {
    @get:Rule val temp = TemporaryFolder()
    private fun file(path: String, bytes: Int): File = File(temp.root, path).apply {
        parentFile!!.mkdirs(); writeBytes(ByteArray(bytes) { 42 })
    }

    @Test fun missingAndEmptyDirectoriesAreReallyZeroAndMeasurementCreatesNothing() {
        val files = CacheFiles()
        assertEquals(StorageSize.Ready(0), files.measure(File(temp.root, "missing")))
        assertEquals(StorageSize.Ready(0), files.measure(temp.newFolder("empty")))
        assertFalse(File(temp.root, "missing").exists())
    }

    @Test fun nestedFilesAndEmptyFilesHaveExactSizes() {
        file("cache/a", 7); file("cache/sub/b", 2050); file("cache/sub/deep/empty", 0)
        assertEquals(StorageSize.Ready(2057), CacheFiles().measure(File(temp.root, "cache")))
    }

    @Test fun categoriesAreDisjointAndDoNotExcludeSubstringMatches() {
        file("cache/$PlaybackCacheDirectory/a", 11)
        file("cache/$ImageCacheDirectory/a", 13)
        file("cache/temp/$PlaybackCacheDirectory/a", 17)
        file("cache/${ImageCacheDirectory}_extra", 19)
        val info = readCacheStorage(File(temp.root, "cache"))
        assertEquals(StorageSize.Ready(11), info.playback)
        assertEquals(StorageSize.Ready(13), info.images)
        assertEquals(StorageSize.Ready(36), info.temporary)
        assertEquals(StorageSize.Ready(60), info.total)
    }

    @Test fun listFailureIsAnErrorNotZeroAndCleanupCannotClaimSuccess() {
        val data = file("cache/a", 15)
        val files = CacheFiles(object : MaintenanceFileAccess() {
            override fun children(file: File): Array<File> = throw IOException("injected access denial")
        })
        assertEquals(StorageSize.Error, files.measure(data.parentFile!!))
        assertEquals(CleanupStatus.FAILED, files.clear(data.parentFile!!).status)
        assertTrue(data.exists())
    }

    @Test fun fileReadFailureIsAnErrorNotZero() {
        file("cache/a", 15)
        val files = CacheFiles(object : MaintenanceFileAccess() {
            override fun length(file: File): Long = throw IOException("injected read failure")
        })
        assertEquals(StorageSize.Error, files.measure(File(temp.root, "cache")))
    }

    @Test fun missingDirectoryBehindUnreadableParentIsNotReportedAsEmpty() {
        val files = CacheFiles(object : MaintenanceFileAccess() {
            override fun children(file: File): Array<File> = throw IOException("injected access denial")
        })
        assertEquals(StorageSize.Error, files.measure(File(temp.root, "missing")))
    }

    @Test fun failedDeleteLeavesFilesAndReportsFailure() {
        val data = file("cache/a", 20)
        val files = CacheFiles(object : MaintenanceFileAccess() {
            override fun delete(file: File) = false
        })
        assertEquals(CleanupStatus.FAILED, files.clear(data.parentFile!!).status)
        assertEquals(StorageSize.Ready(20), files.measure(data.parentFile!!))
    }

    @Test fun completedCleanupRemovesNestedFilesAndRefreshesSizes() {
        file("cache/a", 20); file("cache/nested/b", 30)
        val root = File(temp.root, "cache")
        assertEquals(StorageSize.Ready(50), CacheFiles().measure(root))
        assertEquals(CleanupStatus.COMPLETED, CacheFiles().clear(root).status)
        assertEquals(StorageSize.Ready(0), CacheFiles().measure(root))
        assertEquals(CleanupStatus.EMPTY, CacheFiles().clear(root).status)
    }

    @Test fun temporaryCleanupProtectsOwnedCachesAndAllPersistentData() {
        val playback = file("cache/$PlaybackCacheDirectory/span", 11)
        val images = file("cache/$ImageCacheDirectory/image", 13)
        file("cache/temporary/a", 17)
        val prefs = file("shared_prefs/miptvga_prefs.xml", 21)
        val list = file("files/saved.m3u", 31)
        val config = file("files/config", 41)
        val original = listOf(prefs, list, config).associateWith { it.readBytes() }
        assertEquals(CleanupStatus.COMPLETED, CacheFiles().clear(File(temp.root, "cache"), OwnedCacheDirectories).status)
        assertEquals(11L, playback.length()); assertEquals(13L, images.length())
        original.forEach { (file, bytes) -> assertArrayEquals(bytes, file.readBytes()) }
    }

    @Test fun symlinkInsideCacheCannotDeleteOrMeasurePersistentFiles() {
        val prefs = file("persistent/prefs", 27)
        val cache = temp.newFolder("cache")
        Files.createSymbolicLink(File(cache, "link").toPath(), prefs.parentFile!!.toPath())
        assertEquals(StorageSize.Error, CacheFiles().measure(cache))
        assertEquals(CleanupStatus.FAILED, CacheFiles().clear(cache).status)
        assertEquals(27L, prefs.length())
    }

    @Test fun rootSymlinkCannotEscapeCacheBoundary() {
        val prefs = file("persistent/prefs", 27)
        val link = File(temp.root, "cache")
        Files.createSymbolicLink(link.toPath(), prefs.parentFile!!.toPath())
        assertEquals(StorageSize.Error, CacheFiles().measure(link))
        assertEquals(CleanupStatus.FAILED, CacheFiles().clear(link).status)
        assertEquals(27L, prefs.length())
    }

    @Test fun oneCategoryReadErrorDoesNotHideOtherCategorySizes() {
        file("cache/$PlaybackCacheDirectory/span", 12)
        file("cache/$ImageCacheDirectory/image", 18)
        val files = CacheFiles(object : MaintenanceFileAccess() {
            override fun children(file: File): Array<File> =
                if (file.name == ImageCacheDirectory) throw IOException("injected failure") else super.children(file)
        })
        val snapshot = readCacheStorage(File(temp.root, "cache"), files)
        assertEquals(StorageSize.Ready(12), snapshot.playback)
        assertEquals(StorageSize.Error, snapshot.images)
        assertEquals(StorageSize.Ready(0), snapshot.temporary)
        assertEquals(StorageSize.Error, snapshot.total)
    }

    @Test fun pendingZeroAndErrorHaveDistinctLabelsAndBinaryUnits() {
        assertEquals("Calculando…", StorageSize.Pending.label())
        assertEquals("Error de lectura", StorageSize.Error.label())
        assertEquals("0 B", StorageSize.Ready(0).label())
        assertTrue(1024L.formatStorageSize().endsWith("KiB"))
        assertTrue((1024L * 1024).formatStorageSize().endsWith("MiB"))
        assertTrue((1024L * 1024 * 1024).formatStorageSize().endsWith("GiB"))
        assertEquals(StorageSize.Pending, StorageInfo.Empty.total)
    }

    @Test fun deferredCleanupRunsOnlyAfterLastReaderAndPublishesAfterDeletion() {
        val data = file("cache/span", 100)
        val observed = mutableListOf<Pair<CleanupStatus, StorageSize>>()
        val gate = CacheMaintenanceCoordinator { observed.add(it.status to CacheFiles().measure(data.parentFile!!)) }
        gate.acquire(); gate.acquire()
        fun execute(action: CacheReleasePolicy.Action) = gate.execute(action, {}, { CacheFiles().clear(data.parentFile!!) })
        execute(gate.request(true))
        assertTrue(data.exists())
        execute(gate.releaseReader())
        assertTrue(data.exists())
        execute(gate.releaseReader())
        assertFalse(data.exists())
        assertEquals(listOf(CleanupStatus.PENDING to StorageSize.Ready(100),
            CleanupStatus.COMPLETED to StorageSize.Ready(0)), observed)
    }

    @Test fun replacementReaderBetweenSchedulingAndExecutionKeepsFilesProtected() {
        val data = file("cache/span", 100)
        val gate = CacheMaintenanceCoordinator {}
        gate.acquire(); gate.request(true)
        val scheduled = gate.releaseReader()
        assertEquals(CacheReleasePolicy.Action.CLEAR, scheduled)
        gate.acquire()
        val ready = gate.request(clear = scheduled == CacheReleasePolicy.Action.CLEAR)
        gate.execute(ready, { fail("Active reader must prevent release") }, { fail("Active reader must prevent erase"); CleanupResult(CleanupStatus.FAILED) })
        assertTrue(data.exists())
        gate.execute(gate.releaseReader(), {}, { CacheFiles().clear(data.parentFile!!) })
        assertFalse(data.exists())
    }

    @Test fun normalReleaseAndReaderRecreationPreserveCacheAndPreferences() {
        val data = file("cache/span", 100)
        val prefs = file("shared_prefs/prefs", 25)
        val gate = CacheMaintenanceCoordinator {}
        gate.acquire()
        gate.request(false)
        var released = 0
        gate.execute(gate.releaseReader(), { released++ }, { fail("Normal release must not erase"); CleanupResult(CleanupStatus.FAILED) })
        gate.acquire()
        assertEquals(1, released)
        assertEquals(100L, data.length()); assertEquals(25L, prefs.length())
    }

    @Test fun releaseFailurePublishesFailureWithoutDeletingCache() {
        val data = file("cache/span", 100)
        val observed = mutableListOf<CleanupStatus>()
        val gate = CacheMaintenanceCoordinator { observed.add(it.status) }
        try {
            gate.execute(gate.request(true), { throw IOException("injected release failure") }, { CacheFiles().clear(data.parentFile!!) })
            fail("Failure must propagate")
        } catch (_: IOException) { }
        assertEquals(listOf(CleanupStatus.FAILED), observed)
        assertTrue(data.exists())
    }

    @Test fun failedEraseIsNotPublishedAsCompleted() {
        val data = file("cache/span", 100)
        val files = CacheFiles(object : MaintenanceFileAccess() { override fun delete(file: File) = false })
        val gate = CacheMaintenanceCoordinator {}
        gate.execute(gate.request(true), {}, { files.clear(data.parentFile!!) })
        assertEquals(CleanupStatus.FAILED, gate.result?.status)
        assertTrue(data.exists())
    }
}
