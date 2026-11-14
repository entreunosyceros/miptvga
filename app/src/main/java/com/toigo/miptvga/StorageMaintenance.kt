package com.toigo.miptvga

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Locale

internal const val PlaybackCacheDirectory = "media_stream_cache"
internal const val ImageCacheDirectory = "coil_image_cache"
internal val OwnedCacheDirectories = setOf(PlaybackCacheDirectory, ImageCacheDirectory)

internal sealed class StorageSize {
    data object Pending : StorageSize()
    data class Ready(val bytes: Long) : StorageSize()
    data object Error : StorageSize()
    fun label(): String = when (this) {
        Pending -> "Calculando…"
        Error -> "Error de lectura"
        is Ready -> bytes.formatStorageSize()
    }
}

internal data class StorageInfo(
    val playback: StorageSize = StorageSize.Pending,
    val images: StorageSize = StorageSize.Pending,
    val temporary: StorageSize = StorageSize.Pending
) {
    val total: StorageSize get() {
        val sizes = listOf(playback, images, temporary)
        return when {
            sizes.any { it == StorageSize.Error } -> StorageSize.Error
            sizes.any { it == StorageSize.Pending } -> StorageSize.Pending
            else -> StorageSize.Ready(sizes.sumOf { (it as StorageSize.Ready).bytes })
        }
    }
    companion object { val Empty = StorageInfo() }
}

internal fun Long.formatStorageSize(): String = when {
    this < 0 -> "Error de lectura"
    this < 1024 -> "$this B"
    this < 1024L * 1024 -> String.format(Locale.getDefault(), "%.1f KiB", this / 1024.0)
    this < 1024L * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MiB", this / (1024.0 * 1024))
    else -> String.format(Locale.getDefault(), "%.1f GiB", this / (1024.0 * 1024 * 1024))
}

internal enum class CleanupStatus { COMPLETED, EMPTY, PENDING, FAILED }
internal data class CleanupResult(val status: CleanupStatus) {
    fun label(): String = when (status) {
        CleanupStatus.COMPLETED -> "Limpieza completada"
        CleanupStatus.EMPTY -> "Caché de disco ya vacía"
        CleanupStatus.PENDING -> "Limpieza pendiente: recursos en uso"
        CleanupStatus.FAILED -> "Error de limpieza; no se ha completado"
    }
}

/** Injectable failure points; production still checks real files and never follows symlinks. */
internal open class MaintenanceFileAccess {
    open fun children(file: File): Array<File> = file.listFiles() ?: throw IOException("No se puede listar")
    open fun length(file: File): Long = FileInputStream(file).use { it.channel.size() }
    open fun delete(file: File): Boolean = file.delete()
}

/** Call only on a worker thread. Only immediate child names are excluded, not path substrings. */
internal class CacheFiles(private val access: MaintenanceFileAccess = MaintenanceFileAccess()) {
    fun measure(directory: File, excluded: Set<String> = emptySet()): StorageSize = try {
        StorageSize.Ready(bytes(root(directory), excluded))
    } catch (_: Exception) { StorageSize.Error }

    private fun present(file: File): Boolean {
        if (file.exists()) return true
        val parent = file.parentFile ?: return false
        if (parent.exists()) {
            if (!parent.canRead()) throw IOException("No se puede leer")
            if (access.children(parent).any { it.name == file.name }) throw IOException("No se puede acceder")
        }
        return false
    }

    private fun children(file: File): Array<File> {
        if (!file.canRead()) throw IOException("No se puede leer")
        return access.children(file)
    }

    private fun validate(file: File) {
        if (file.canonicalFile != file.absoluteFile) throw IOException("Enlace fuera del árbol de caché")
    }

    private fun root(directory: File): File {
        val absolute = directory.absoluteFile
        val parent = absolute.parentFile?.canonicalFile ?: throw IOException("Raíz no válida")
        return File(parent, absolute.name).also(::validate)
    }

    private fun bytes(file: File, excluded: Set<String> = emptySet()): Long {
        validate(file)
        if (!present(file)) return 0
        if (file.isDirectory) return children(file).filter { it.name !in excluded }.sumOf { bytes(it) }
        if (!file.isFile || !file.canRead()) throw IOException("No se puede leer")
        return access.length(file)
    }

    fun clear(directory: File, excluded: Set<String> = emptySet()): CleanupResult = try {
        val root = root(directory)
        val before = bytes(root, excluded)
        if (present(root)) {
            if (!root.isDirectory) throw IOException("No es un directorio")
            children(root).filter { it.name !in excluded }.forEach(::remove)
        }
        CleanupResult(if (before == 0L) CleanupStatus.EMPTY else CleanupStatus.COMPLETED)
    } catch (_: Exception) { CleanupResult(CleanupStatus.FAILED) }

    private fun remove(file: File) {
        validate(file)
        if (!present(file)) return
        if (file.isDirectory) children(file).forEach(::remove)
        if (!access.delete(file) && present(file)) throw IOException("No se puede borrar")
    }
}

/** Categories are disjoint; preferences/files/databases are deliberately outside this snapshot. */
internal fun readCacheStorage(cacheDirectory: File, files: CacheFiles = CacheFiles()): StorageInfo = StorageInfo(
    files.measure(File(cacheDirectory, PlaybackCacheDirectory)),
    files.measure(File(cacheDirectory, ImageCacheDirectory)),
    files.measure(cacheDirectory, OwnedCacheDirectories)
)

/** Same reader policy as phase 4, now with observable, truthful cleanup outcomes. */
internal class CacheMaintenanceCoordinator(private val publish: (CleanupResult) -> Unit) {
    private val policy = CacheReleasePolicy()
    var result: CleanupResult? = null
        private set
    fun acquire() = policy.acquire()
    fun releaseReader() = policy.releaseReader()
    fun request(clear: Boolean): CacheReleasePolicy.Action {
        val action = policy.request(clear)
        if (clear && action == CacheReleasePolicy.Action.NONE) report(CleanupResult(CleanupStatus.PENDING))
        return action
    }
    private fun report(value: CleanupResult) { result = value; publish(value) }
    fun execute(action: CacheReleasePolicy.Action, release: () -> Unit, erase: () -> CleanupResult) {
        if (action == CacheReleasePolicy.Action.NONE) return
        try {
            release()
            if (action == CacheReleasePolicy.Action.CLEAR) report(erase())
        } catch (error: Exception) {
            if (action == CacheReleasePolicy.Action.CLEAR) report(CleanupResult(CleanupStatus.FAILED))
            throw error
        }
    }
}
