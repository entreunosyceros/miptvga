package com.toigo.miptvga

import android.os.SystemClock
import android.util.Log
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.util.concurrent.atomic.AtomicLong

/** Only closed vocabulary and numeric IDs: never accepts a URL, header or exception message. */
internal enum class PlaybackEvent {
    SELECTED, FALLBACK, SESSION_ACTIVE, SESSION_RETIRED, STOP_BEGIN, STOP_RETURNED,
    STOP_FAILED, PREPARE, PLAY_REQUEST, OPENING, READY, PLAYING, FIRST_FRAME,
    RETRY_WAIT, START_ERROR, ENGINE_ERROR, TERMINAL_ERROR, PREPARATION_TASK_FINISHED, PREPARATION_TASK_CANCELLED,
    TRANSFER_INITIALIZING, TRANSFER_STARTED, TRANSFER_ENDED
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class PlaybackTrace(private val request: Long, private val engine: String) {
    private val id = nextId.incrementAndGet()
    private val start = SystemClock.elapsedRealtime()
    fun event(event: PlaybackEvent, number: Int = 0, delayMs: Long = 0) {
        if (!enabled) return
        Log.d("MiptvgaZap", "request=$request session=$id engine=$engine event=$event " +
            "atMs=${SystemClock.elapsedRealtime()} elapsedMs=${SystemClock.elapsedRealtime() - start} " +
            "number=$number delayMs=$delayMs")
    }
    fun stop(block: () -> Unit): Boolean {
        event(PlaybackEvent.STOP_BEGIN)
        return try { block(); event(PlaybackEvent.STOP_RETURNED); true }
        catch (_: Exception) { event(PlaybackEvent.STOP_FAILED); false }
    }
    /** Transfer end measures DataSource activity, never claims TCP or provider-session closure. */
    val transferListener = object : TransferListener {
        override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
            if (isNetwork) event(PlaybackEvent.TRANSFER_INITIALIZING)
        }
        override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
            if (isNetwork) event(PlaybackEvent.TRANSFER_STARTED)
        }
        override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) = Unit
        override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
            if (isNetwork) event(PlaybackEvent.TRANSFER_ENDED)
        }
    }
    companion object {
        private val nextId = AtomicLong()
        @Volatile var enabled = false
    }
}
