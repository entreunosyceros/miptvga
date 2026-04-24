package com.toigo.miptvga

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val HttpConnectTimeoutMillis = 12_000
private const val HttpReadTimeoutMillis = 15_000

internal class PlaylistLoader {
    suspend fun <T> withUrlReader(urlString: String, block: (BufferedReader) -> T): T = withContext(Dispatchers.IO) {
        val url = URL(urlString)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = HttpConnectTimeoutMillis
            readTimeout = HttpReadTimeoutMillis
            instanceFollowRedirects = true
            useCaches = false
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val message = connection.responseMessage?.takeIf { it.isNotBlank() } ?: "HTTP $responseCode"
                error("Error al descargar la lista: $message")
            }
            connection.inputStream.bufferedReader().use(block)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun <T> withUriReader(context: Context, uri: Uri, block: (BufferedReader) -> T): T = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use(block)
            ?: error("No se pudo abrir el archivo")
    }

    suspend fun <T> withFileReader(file: File, block: (BufferedReader) -> T): T = withContext(Dispatchers.IO) {
        file.bufferedReader().use(block)
    }

    suspend fun sendXtreamKeepAlive(request: XtreamKeepAliveRequest): Boolean = withContext(Dispatchers.IO) {
        val url = URL(request.pingUrl)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = HttpConnectTimeoutMillis
            readTimeout = HttpReadTimeoutMillis
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Connection", "close")
            request.requestHeaders.forEach { (name, value) ->
                if (value.isNotBlank()) {
                    setRequestProperty(name, value)
                }
            }
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..399) {
                return@withContext false
            }

            runCatching { connection.inputStream?.close() }
            true
        } catch (_: Throwable) {
            false
        } finally {
            connection.disconnect()
        }
    }
}

