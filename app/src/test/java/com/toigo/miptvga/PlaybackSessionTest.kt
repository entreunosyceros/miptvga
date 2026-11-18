package com.toigo.miptvga

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PlaybackSessionTest {
    @Test fun fiveRapidChangesRejectAllEarlierCallbacks() {
        val gate = PlaybackSessionGate()
        val sessions = List(5) { PlaybackSessionGate.Session().also(gate::activate) }
        assertEquals(listOf(false, false, false, false, true), sessions.map(gate::isCurrent))
        sessions.dropLast(1).forEach(gate::close)
        assertTrue(gate.isCurrent(sessions.last()))
    }

    @Test fun closingCurrentSessionRejectsEventsAndCannotReopenIt() {
        val gate = PlaybackSessionGate()
        val session = PlaybackSessionGate.Session().also(gate::activate)
        assertTrue(gate.close(session))
        assertFalse(gate.close(session))
        assertFalse(gate.isCurrent(session))
        try { gate.activate(session); fail("A released session cannot be reused") } catch (_: IllegalStateException) { }
    }

    @Test fun lateReconnectJobCannotModifyReplacement(): Unit = runBlocking {
        val gate = PlaybackSessionGate()
        val old = PlaybackSessionGate.Session().also(gate::activate)
        val ready = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var opened = false
        val job = launch {
            ready.complete(Unit)
            withContext(NonCancellable) {
                release.await()
                // Simulate an external callback that ignores coroutine cancellation.
                if (gate.isCurrent(old)) opened = true
            }
        }
        ready.await()
        gate.close(old)
        gate.activate(PlaybackSessionGate.Session())
        job.cancel()
        release.complete(Unit)
        job.join()
        assertFalse(opened)
    }

    @Test fun retirementStopsBeforeReplacementAndRejectsSynchronousStopCallback() {
        val gate = PlaybackSessionGate()
        var oldPlaying = true
        val old = PlaybackSessionGate.Session().also(gate::activate)
        assertTrue(gate.retire(old) {
            assertFalse(gate.isCurrent(old))
            oldPlaying = false
        })
        val replacement = PlaybackSessionGate.Session().also(gate::activate)
        assertFalse(oldPlaying)
        assertFalse(gate.retire(old) { fail("Late disposal must not stop the replacement") })
        assertTrue(gate.isCurrent(replacement))
    }

    @Test fun hundredSuccessiveSelectionsRetireEveryOwnerExactlyOnce() {
        val gate = PlaybackSessionGate()
        var active = 0
        var stops = 0
        var current: PlaybackSessionGate.Session? = null
        repeat(100) {
            current?.let { old -> gate.retire(old) { active--; stops++ } }
            current = PlaybackSessionGate.Session().also(gate::activate)
            active++
            assertEquals(1, active)
        }
        gate.retire(current!!) { active--; stops++ }
        assertEquals(0, active)
        assertEquals(100, stops)
    }

    @Test fun selectionDuringPendingPreparationCancelsJobAndCannotPrepareOldSession(): Unit = runBlocking {
        val gate = PlaybackSessionGate()
        val old = PlaybackSessionGate.Session().also(gate::activate)
        val waiting = CompletableDeferred<Unit>()
        var prepared = false
        val ready = CompletableDeferred<Unit>()
        val job = launch {
            waiting.complete(Unit)
            ready.await() // surface, lifecycle, or connection pending
            if (gate.isCurrent(old)) prepared = true
        }
        waiting.await()
        gate.retire(old) { job.cancel() }
        ready.complete(Unit)
        gate.activate(PlaybackSessionGate.Session())
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(prepared)
    }

    @Test fun obsoleteRetryCannotConsumeOrReactivateReplacementAfterFailure(): Unit = runBlocking {
        val gate = PlaybackSessionGate()
        val old = PlaybackSessionGate.Session().also(gate::activate)
        val retry = CompletableDeferred<Unit>()
        var reopened = false
        val budget = PlaybackRetryBudget(8000)
        val job = launch {
            retry.await()
            if (gate.isCurrent(old) && budget.acquire(0, 3)) reopened = true
        }
        yield()
        gate.retire(old) { }
        val recovered = PlaybackSessionGate.Session().also(gate::activate)
        retry.complete(Unit)
        job.join()
        assertFalse(reopened)
        assertEquals(0, budget.attempts)
        assertTrue(gate.isCurrent(recovered))
    }

    @Test fun shortPlayingBurstsDoNotResetRetryBudget() {
        val budget = PlaybackRetryBudget(8000)
        repeat(3) { index -> budget.playing(index * 1000L); assertTrue(budget.acquire(index * 1000L + 100, 3)) }
        budget.playing(4000)
        assertFalse(budget.acquire(4100, 3))
    }

    @Test fun healthyPlaybackStartsNewRecoveryEpisode() {
        val budget = PlaybackRetryBudget(8000)
        repeat(3) { assertTrue(budget.acquire(it.toLong(), 3)) }
        assertFalse(budget.acquire(10, 3))
        budget.playing(100)
        budget.playing(1000) // repeated READY must not postpone stability forever
        assertTrue(budget.acquire(8100, 3))
        assertEquals(1, budget.attempts)
    }

    @Test fun pausedTimeDoesNotReplenishBudget() {
        val budget = PlaybackRetryBudget(8000)
        assertTrue(budget.acquire(0, 1))
        budget.playing(100)
        budget.paused(200)
        assertFalse(budget.acquire(100000, 1))
    }

    @Test fun bufferingAfterHealthyPlaybackKeepsTheResetCredit() {
        val budget = PlaybackRetryBudget(8000)
        assertTrue(budget.acquire(0, 1))
        budget.playing(100)
        budget.paused(8100) // Exo reports isPlaying=false before the error/end event
        assertTrue(budget.acquire(8101, 1))
        assertEquals(1, budget.attempts)
    }

    @Test fun permanentHttpFailuresAreSeparateFromTemporaryOnes() {
        listOf(400, 401, 403, 404, 410).forEach { assertTrue(isPermanentPlaybackHttpStatus(it)) }
        listOf(408, 429, 500, 502, 503, 504).forEach { assertFalse(isPermanentPlaybackHttpStatus(it)) }
    }

    @Test fun cacheReleaseWaitsForAllReadersWithoutRequestingDeletion() {
        val policy = CacheReleasePolicy()
        policy.acquire(); policy.acquire()
        assertEquals(CacheReleasePolicy.Action.NONE, policy.request(clear = false))
        assertEquals(CacheReleasePolicy.Action.NONE, policy.releaseReader())
        assertEquals(CacheReleasePolicy.Action.RELEASE, policy.releaseReader())
    }

    @Test fun explicitCacheClearIsDeferredAndSurvivesNormalReleaseRequest() {
        val policy = CacheReleasePolicy()
        policy.acquire()
        assertEquals(CacheReleasePolicy.Action.NONE, policy.request(clear = true))
        assertEquals(CacheReleasePolicy.Action.NONE, policy.request(clear = false))
        assertEquals(CacheReleasePolicy.Action.CLEAR, policy.releaseReader())
    }

    @Test fun cacheClearDoesNotReleaseANewOverlappingReader() {
        val policy = CacheReleasePolicy()
        policy.acquire()
        policy.request(clear = true)
        policy.acquire()
        assertEquals(CacheReleasePolicy.Action.NONE, policy.releaseReader())
        assertEquals(CacheReleasePolicy.Action.CLEAR, policy.releaseReader())
        assertEquals(CacheReleasePolicy.Action.RELEASE, policy.request(clear = false))
    }

    @Test fun exhaustedRetriesAllowOneAlternativeButNotEnginePingPong() {
        val fallback = PlaybackFallbackPolicy()
        assertTrue(fallback.acquire(4, 3, true))
        assertFalse(fallback.acquire(13, 3, true))
        fallback.resetRequest()
        assertTrue(fallback.acquire(13, 3, true))
    }

    @Test fun authenticationFailureDoesNotSwitchEngineOrSpendItsFallback() {
        val fallback = PlaybackFallbackPolicy()
        assertFalse(fallback.acquire(20, 3, false))
        assertFalse(fallback.acquire(1, 3, true))
        assertTrue(fallback.acquire(3, 3, true))
    }

    @Test fun queuedCacheMaintenanceRechecksReadersBeforeReleasing() {
        val policy = CacheReleasePolicy()
        assertEquals(CacheReleasePolicy.Action.CLEAR, policy.request(clear = true))
        policy.acquire() // a new Activity opens before the queued maintenance task
        assertEquals(CacheReleasePolicy.Action.NONE, policy.request(clear = true))
        assertEquals(CacheReleasePolicy.Action.CLEAR, policy.releaseReader())
    }
}
