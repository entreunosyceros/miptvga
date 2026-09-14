package com.toigo.miptvga

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.File
import java.util.concurrent.TimeUnit

private const val HttpConnectTimeoutSeconds = 15L
private const val HttpReadTimeoutSeconds = 20L
private const val HttpCallTimeoutSeconds = 120L
private const val KeepAliveConnectTimeoutSeconds = 8L
private const val KeepAliveReadTimeoutSeconds = 10L
private const val KeepAliveCallTimeoutSeconds = 15L
private const val KeepAliveMaxAttempts = 3

internal class PlaylistLoader {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(HttpConnectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(HttpReadTimeoutSeconds, TimeUnit.SECONDS)
            .callTimeout(HttpCallTimeoutSeconds, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val keepAliveClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(KeepAliveConnectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(KeepAliveReadTimeoutSeconds, TimeUnit.SECONDS)
            .callTimeout(KeepAliveCallTimeoutSeconds, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    suspend fun <T> withUrlReader(urlString: String, block: (BufferedReader) -> T): T = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(urlString.trim())
            .get()
            .header("User-Agent", IptvDefaultUserAgent)
            .build()

        val response = client.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                val message = resp.message.takeIf { it.isNotBlank() } ?: "HTTP ${resp.code}"
                error("Error al descargar la lista: $message")
            }
            val body = resp.body ?: error("Respuesta vacía del servidor")
            body.charStream().buffered().use(block)
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
        val httpRequest = Request.Builder()
            .url(request.pingUrl)
            .get()
            .header("Connection", "keep-alive")
            .header("Cache-Control", "no-cache")
            .apply {
                request.requestHeaders.forEach { (name, value) ->
                    if (value.isNotBlank()) {
                        header(name, value)
                    }
                }
            }
            .build()

        repeat(KeepAliveMaxAttempts) { attempt ->
            try {
                val response = keepAliveClient.newCall(httpRequest).execute()
                val ok = response.use { resp -> resp.code in 200..399 }
                if (ok) return@withContext true
            } catch (_: Throwable) {
                // retry below
            }
            if (attempt < KeepAliveMaxAttempts - 1) {
                try {
                    Thread.sleep(400L * (attempt + 1))
                } catch (_: InterruptedException) {
                    return@withContext false
                }
            }
        }
        false
    }

    suspend fun validateStreamUrl(url: String, headers: Map<String, String> = emptyMap()): StreamValidation = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) return@withContext StreamValidation(reachable = false, error = "URL vacía")

        val normalized = trimmedUrl.lowercase()
        if (normalized.startsWith("rtsp://") || normalized.startsWith("rtmp://") || normalized.startsWith("udp://")) {
            return@withContext StreamValidation(reachable = true)
        }

        try {
            val effectiveHeaders = ensureIptvHeaders(headers)
            val requestBuilder = Request.Builder()
                .url(trimmedUrl)
                .head()

            effectiveHeaders.forEach { (name, value) ->
                if (value.isNotBlank()) requestBuilder.header(name, value)
            }

            val validationClient = client.newBuilder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .callTimeout(12, TimeUnit.SECONDS)
                .build()

            val response = validationClient.newCall(requestBuilder.build()).execute()
            response.use { resp ->
                if (resp.code in 200..399) {
                    StreamValidation(reachable = true, contentType = resp.header("Content-Type"))
                } else {
                    StreamValidation(reachable = false, error = "HTTP ${resp.code}", httpCode = resp.code)
                }
            }
        } catch (e: Throwable) {
            StreamValidation(reachable = false, error = e.message ?: "Error de conexión")
        }
    }
}

internal data class StreamValidation(
    val reachable: Boolean,
    val error: String? = null,
    val contentType: String? = null,
    val httpCode: Int? = null
)
