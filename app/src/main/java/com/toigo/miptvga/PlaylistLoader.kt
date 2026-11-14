package com.toigo.miptvga

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

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

        client.newCall(request).readResponse { resp ->
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
                currentCoroutineContext().ensureActive()
                val ok = keepAliveClient.newCall(httpRequest).readResponse { resp -> resp.code in 200..399 }
                if (ok) return@withContext true
            } catch (_: IOException) {
                // retry below
            }
            if (attempt < KeepAliveMaxAttempts - 1) {
                delay(400L * (attempt + 1))
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

            validationClient.newCall(requestBuilder.build()).readResponse { resp ->
                if (resp.code in 200..399) {
                    StreamValidation(reachable = true, contentType = resp.header("Content-Type"))
                } else {
                    StreamValidation(reachable = false, error = "HTTP ${resp.code}", httpCode = resp.code)
                }
            }
        } catch (e: IOException) {
            StreamValidation(reachable = false, error = e.message ?: "Error de conexión")
        } catch (_: IllegalArgumentException) {
            StreamValidation(reachable = false, error = "URL no válida")
        }
    }
}

/** Keep cancellation connected to the socket until the streamed body is consumed. */
private suspend fun <T> Call.readResponse(block: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (!continuation.isActive) {
                response.close()
                return
            }
            try {
                val value = response.use(block)
                continuation.resumeWith(Result.success(value))
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    })
}

internal data class StreamValidation(
    val reachable: Boolean,
    val error: String? = null,
    val contentType: String? = null,
    val httpCode: Int? = null
)
