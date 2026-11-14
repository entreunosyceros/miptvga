package com.toigo.miptvga

/** Main-thread owner. Closing an old session cannot invalidate its replacement. */
internal class PlaybackSessionGate {
    class Session internal constructor() {
        internal var closed = false
    }
    private var current: Session? = null

    fun activate(session: Session) {
        check(!session.closed)
        current = session
    }

    fun isCurrent(session: Session): Boolean = current === session && !session.closed

    /** Invalidate callbacks before stopping, including callbacks emitted synchronously by stop. */
    fun retire(session: Session, stop: () -> Unit): Boolean {
        val owned = close(session)
        if (owned) stop()
        return owned
    }

    fun close(session: Session): Boolean {
        val wasCurrent = isCurrent(session)
        session.closed = true
        if (wasCurrent) current = null
        return wasCurrent
    }
}

/** Counts failures across short READY/Playing bursts, not across healthy sessions. */
internal class PlaybackRetryBudget(private val stablePlaybackMillis: Long) {
    var attempts: Int = 0
        private set
    private var playingSince: Long? = null
    private var healthyInterval = false

    fun playing(now: Long) {
        if (playingSince == null) playingSince = now
    }

    fun paused(now: Long) {
        if (playingSince?.let { now - it >= stablePlaybackMillis } == true) healthyInterval = true
        playingSince = null
    }

    fun acquire(now: Long, maximum: Int): Boolean {
        if (healthyInterval || playingSince?.let { now - it >= stablePlaybackMillis } == true) attempts = 0
        healthyInterval = false
        playingSince = null
        if (attempts >= maximum) return false
        attempts++
        return true
    }
}

internal fun isPermanentPlaybackHttpStatus(code: Int): Boolean = code in setOf(400, 401, 403, 404, 410)

internal data class PlaybackFailure(
    val detail: String?,
    val failures: Int = 1,
    val allowBackendFallback: Boolean = true
)

/** One alternative engine per explicit request prevents automatic engine ping-pong. */
internal class PlaybackFallbackPolicy {
    private var used = false
    fun resetRequest() { used = false }
    fun acquire(failures: Int, threshold: Int, allowed: Boolean): Boolean {
        if (used || !allowed || failures < threshold) return false
        used = true
        return true
    }
}

/** Used under PlaybackCache's lock. Clear/release waits for its last player reader. */
internal class CacheReleasePolicy {
    enum class Action { NONE, RELEASE, CLEAR }
    private var readers = 0
    private var pending = Action.NONE
    fun acquire() { readers++ }
    fun request(clear: Boolean): Action {
        if (clear) pending = Action.CLEAR
        else if (pending == Action.NONE) pending = Action.RELEASE
        return drain()
    }
    fun releaseReader(): Action {
        check(readers > 0)
        readers--
        return drain()
    }
    private fun drain(): Action {
        if (readers != 0) return Action.NONE
        return pending.also { pending = Action.NONE }
    }
}
