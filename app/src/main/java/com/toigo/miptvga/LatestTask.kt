package com.toigo.miptvga

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/** Owned and called on Main. A cancelled request can never publish a late result. */
internal class LatestTask(private val scope: CoroutineScope) {
    private var generation = 0L
    private var job: Job? = null

    inner class Request internal constructor(private val id: Long) {
        suspend fun ensureCurrent() {
            coroutineContext.ensureActive()
            if (id != generation) throw CancellationException("Superseded request")
        }
    }

    fun launch(block: suspend (Request) -> Unit) {
        cancel()
        val request = Request(generation)
        job = scope.launch { block(request) }
    }

    fun cancel() {
        generation++
        job?.cancel()
        job = null
    }
}

internal inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}
