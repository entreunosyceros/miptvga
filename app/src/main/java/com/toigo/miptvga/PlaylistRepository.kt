package com.toigo.miptvga

import android.content.Context
import android.net.Uri
import java.io.File

internal class PlaylistRepository(
    private val parser: M3uParser,
    private val loader: PlaylistLoader
) {
    suspend fun loadFromUrl(url: String): List<Channel> {
        return loader.withUrlReader(url) { reader ->
            parser.parse(reader)
        }
    }

    suspend fun loadFromUri(context: Context, uri: Uri): List<Channel> {
        return loader.withUriReader(context, uri) { reader ->
            parser.parse(reader)
        }
    }

    suspend fun loadFromFile(file: File): List<Channel> {
        return loader.withFileReader(file) { reader ->
            parser.parse(reader)
        }
    }

    suspend fun sendXtreamKeepAlive(request: XtreamKeepAliveRequest): Boolean {
        return loader.sendXtreamKeepAlive(request)
    }
}

