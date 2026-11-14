package com.toigo.miptvga

import android.content.Context
import android.net.Uri
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class PlaylistRepository(
    private val parser: M3uParser,
    private val loader: PlaylistLoader
) {
    suspend fun loadFromUrl(url: String): List<Channel> {
        val context = currentCoroutineContext()
        return loader.withUrlReader(url) { reader ->
            parser.parse(reader) { context.ensureActive() }
        }
    }

    suspend fun loadFromUri(context: Context, uri: Uri): List<Channel> {
        val jobContext = currentCoroutineContext()
        return loader.withUriReader(context, uri) { reader ->
            parser.parse(reader) { jobContext.ensureActive() }
        }
    }

    suspend fun loadFromFile(file: File): List<Channel> {
        val context = currentCoroutineContext()
        return loader.withFileReader(file) { reader ->
            parser.parse(reader) { context.ensureActive() }
        }
    }

    suspend fun sendXtreamKeepAlive(request: XtreamKeepAliveRequest): Boolean {
        return loader.sendXtreamKeepAlive(request)
    }
}

