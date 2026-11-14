package com.toigo.miptvga

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PlaylistLoaderTest {
    @Test(timeout = 10000)
    fun cancellationClosesSocketWhileReadingBody(): Unit = runBlocking {
        ServerSocket(0).use { server ->
            val executor = Executors.newSingleThreadExecutor()
            val bodyStarted = CompletableDeferred<Unit>()
            try {
                val remoteClosed = executor.submit<Boolean> {
                    server.accept().use { socket ->
                        socket.soTimeout = 5000
                        val input = socket.getInputStream().bufferedReader()
                        while (!input.readLine().isNullOrEmpty()) { /* consume request headers */ }
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Length: 1000\r\n\r\na".toByteArray())
                            flush()
                        }
                        input.read() == -1
                    }
                }
                val job = launch(Dispatchers.Default) {
                    PlaylistLoader().withUrlReader("http://127.0.0.1:${server.localPort}/synthetic") { reader ->
                        bodyStarted.complete(Unit)
                        reader.readText() // block until cancellation closes the socket
                    }
                }
                withTimeout(5000) { bodyStarted.await() }
                job.cancelAndJoin()
                assertTrue(remoteClosed.get(5, TimeUnit.SECONDS))
            } finally { executor.shutdownNow() }
        }
    }

    @Test(timeout = 10000)
    fun streamedReaderClosesNormallyAndPreservesContent(): Unit = runBlocking {
        ServerSocket(0).use { server ->
            val executor = Executors.newSingleThreadExecutor()
            try {
                val sent = executor.submit {
                    server.accept().use { socket ->
                        val input = socket.getInputStream().bufferedReader()
                        while (!input.readLine().isNullOrEmpty()) { }
                        val text = "#EXTM3U\n#EXTINF:-1,Noticias, España\nhttps://example.invalid/live\n"
                        val bytes = text.toByteArray(Charsets.UTF_8)
                        socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n\r\n").toByteArray() + bytes)
                    }
                }
                val channels = PlaylistLoader().withUrlReader("http://127.0.0.1:${server.localPort}/synthetic") {
                    M3uParser().parse(it)
                }
                assertEquals("Noticias, España", channels.single().name)
                sent.get(5, TimeUnit.SECONDS)
            } finally { executor.shutdownNow() }
        }
    }
}
