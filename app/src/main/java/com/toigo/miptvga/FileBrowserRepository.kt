package com.toigo.miptvga

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

internal class FileBrowserRepository {
    suspend fun listRoots(context: Context): List<FileBrowserEntry> = withContext(Dispatchers.IO) {
        buildRootCandidates(context)
            .mapNotNull { (file, label) ->
                val canonical = canonicalReadableDirectory(file) ?: return@mapNotNull null
                FileBrowserEntry(
                    path = canonical.absolutePath,
                    name = label,
                    subtitle = canonical.absolutePath,
                    isDirectory = true,
                    isRoot = true
                )
            }
            .distinctBy { it.path }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    suspend fun listDirectory(path: String): List<FileBrowserEntry> = withContext(Dispatchers.IO) {
        val directory = File(path)
        val canonicalDirectory = canonicalReadableDirectory(directory)
            ?: error("No se puede abrir la carpeta seleccionada")

        val children = canonicalDirectory.listFiles()
            ?.asSequence()
            ?.filter { it.exists() && it.canRead() && !it.isHidden }
            ?.filter { it.isDirectory || isPlaylistFile(it) }
            ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
            ?.map { file ->
                FileBrowserEntry(
                    path = file.absolutePath,
                    name = file.name.ifBlank { file.absolutePath },
                    subtitle = if (file.isDirectory) "Carpeta" else "Lista M3U",
                    isDirectory = file.isDirectory,
                    isRoot = false
                )
            }
            ?.toList()
            .orEmpty()

        children
    }

    fun isPlaylistPath(path: String): Boolean = isPlaylistFile(File(path))

    private fun buildRootCandidates(context: Context): List<Pair<File, String>> {
        val candidates = linkedMapOf<String, Pair<File, String>>()

        fun add(file: File?, label: String) {
            val canonical = canonicalReadableDirectory(file) ?: return
            candidates.putIfAbsent(canonical.absolutePath, canonical to label)
        }

        add(Environment.getExternalStorageDirectory(), "Almacenamiento interno")
        add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Descargas")
        add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Vídeos")
        add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Documentos")

        context.getExternalFilesDirs(null)
            .orEmpty()
            .forEachIndexed { index, appDir ->
                val storageRoot = appDir
                    ?.parentFile
                    ?.parentFile
                    ?.parentFile
                    ?.parentFile
                val defaultName = if (index == 0) "Almacenamiento principal" else "Unidad externa $index"
                add(storageRoot, defaultName)
            }

        return candidates.values.toList()
    }

    private fun canonicalReadableDirectory(file: File?): File? {
        val canonical = runCatching { file?.canonicalFile }.getOrNull() ?: return null
        if (!canonical.exists() || !canonical.isDirectory || !canonical.canRead()) return null
        return canonical
    }

    private fun isPlaylistFile(file: File): Boolean {
        if (!file.isFile) return false
        return file.extension.lowercase(Locale.ROOT) in setOf("m3u", "m3u8")
    }
}

