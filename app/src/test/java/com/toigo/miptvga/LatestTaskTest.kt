package com.toigo.miptvga

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class LatestTaskTest {
    @Test fun lateNonCooperativeResultCannotReplaceNewerResult() = runBlocking {
        val task = LatestTask(this)
        val started = CompletableDeferred<Unit>()
        val finishOld = CompletableDeferred<Unit>()
        val oldFinished = CompletableDeferred<Unit>()
        val newFinished = CompletableDeferred<Unit>()
        val publications = mutableListOf<String>()
        task.launch { request ->
            started.complete(Unit)
            try {
                withContext(NonCancellable) { finishOld.await() }
                request.ensureCurrent()
                publications += "old"
            } finally { oldFinished.complete(Unit) }
        }
        started.await()
        task.launch { request -> request.ensureCurrent(); publications += "new"; newFinished.complete(Unit) }
        newFinished.await()
        finishOld.complete(Unit)
        oldFinished.await()
        assertEquals(listOf("new"), publications)
    }

    @Test fun resetInvalidatesEvenNonCancellablePublication() = runBlocking {
        val task = LatestTask(this)
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var published = false
        task.launch { request ->
            withContext(NonCancellable) {
                started.complete(Unit)
                finish.await()
                request.ensureCurrent()
                published = true
            }
        }
        started.await()
        task.cancel()
        finish.complete(Unit)
        yield()
        assertFalse(published)
    }

    @Test fun cancellationIsNeverConvertedToFailureResult() {
        try {
            runCatchingCancellable { throw CancellationException() }
            fail("Must propagate cancellation")
        } catch (_: CancellationException) { /* expected */ }
    }

    @Test fun filterUsesSelectionAtPublicationTime() {
        val results = listOf(1, 7, 99).map { ChannelListEntry(it, Channel("Synthetic", "https://example.invalid/stream")) }
        assertEquals(2, visibleIndexForSelection(99, results))
        assertEquals(0, visibleIndexForSelection(200, results))
        assertEquals(-1, visibleIndexForSelection(200, emptyList()))
    }
}
