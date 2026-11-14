package com.toigo.miptvga

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.StrictMode
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import android.view.FrameMetrics
import android.view.KeyEvent
import android.view.Window
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Explicit debug-only measurements. Never records channel names, URLs or typed text. */
internal object NavigationDiagnostics {
    private const val Tag = "MiptvgaNav"
    @Volatile private var enabled = false
    private var pendingFocusAt = 0L // Main only
    private var activeEntries: Any? = null
    private var allChannels = false
    @Volatile private var segment = 0
    private var ordinal = 0
    private var eventId = 0L
    private data class FocusSample(val id: Long, val segment: Int, val ordinal: Int, val start: Long, val focus: Long)
    private val pendingFrame = AtomicReference<FocusSample?>(null)
    private var thread: HandlerThread? = null
    private var listener: Window.OnFrameMetricsAvailableListener? = null
    private var previousPolicy: StrictMode.ThreadPolicy? = null
    private val rowCompositions = AtomicInteger()
    private val panelCompositions = AtomicInteger()

    fun attach(activity: Activity, requested: Boolean) {
        if (!requested || activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        enabled = true
        rowCompositions.set(0)
        panelCompositions.set(0)
        segment = 0
        ordinal = 0
        previousPolicy = StrictMode.getThreadPolicy()
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder(previousPolicy)
            .detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build())
        val worker = HandlerThread("miptvga-nav-metrics").also { it.start() }
        thread = worker
        var frames = 0
        var missed = 0
        var totalNs = 0L
        var maxNs = 0L
        var lastSample = SystemClock.elapsedRealtime()
        // Match the display's refresh period instead of assuming 60 Hz.
        @Suppress("DEPRECATION")
        val budgetNs = (1_000_000_000.0 / activity.windowManager.defaultDisplay.refreshRate.coerceAtLeast(1f)).toLong()
        val callback = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
            val sample = pendingFrame.get()
            // This is a post-focus frame proxy, not proof of pixel presentation.
            if (sample != null && metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP) >= sample.focus &&
                pendingFrame.compareAndSet(sample, null)) {
                Log.d(Tag, "postFocusFrame event=${sample.id} segment=${sample.segment} ordinal=${sample.ordinal} dispatchToFrameCallbackNs=${System.nanoTime() - sample.start} frameDurationNs=${metrics.getMetric(FrameMetrics.TOTAL_DURATION)}")
            }
            val duration = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            frames++
            missed += if (duration > budgetNs) 1 else 0
            totalNs += duration
            maxNs = maxOf(maxNs, duration)
            val now = SystemClock.elapsedRealtime()
            if (now - lastSample >= 10_000L) {
                val memory = Debug.MemoryInfo().also(Debug::getMemoryInfo)
                val heap = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
                Log.d(Tag, "frames=$frames overBudget=$missed avgNs=${totalNs / frames} maxNs=$maxNs callbackDrops=$dropped " +
                    "heapBytes=$heap pssKiB=${memory.totalPss} rowCompositions=${rowCompositions.getAndSet(0)} panelCompositions=${panelCompositions.getAndSet(0)} " +
                    "gcCount=${Debug.getRuntimeStat("art.gc.gc-count")} gcTimeMs=${Debug.getRuntimeStat("art.gc.gc-time")} allocatedBytes=${Debug.getRuntimeStat("art.gc.bytes-allocated")}")
                frames = 0; missed = 0; totalNs = 0; maxNs = 0; lastSample = now
            }
        }
        listener = callback
        activity.window.addOnFrameMetricsAvailableListener(callback, Handler(worker.looper))
    }

    fun detach(window: Window) {
        listener?.let(window::removeOnFrameMetricsAvailableListener)
        listener = null
        thread?.quitSafely()
        thread = null
        previousPolicy?.let(StrictMode::setThreadPolicy)
        previousPolicy = null
        enabled = false
        pendingFocusAt = 0L
        activeEntries = null
        pendingFrame.set(null)
    }

    fun catalogueShown(all: Boolean, entries: List<ChannelListEntry>) {
        if (!enabled || (activeEntries === entries && allChannels == all)) return
        activeEntries = entries
        allChannels = all
        segment++
        ordinal = 0
        pendingFocusAt = 0L
        pendingFrame.set(null)
        Log.d(Tag, "catalogue segment=$segment all=$all count=${entries.size}")
    }

    fun catalogueHidden() {
        if (!enabled) return
        activeEntries = null
        pendingFocusAt = 0L
        pendingFrame.set(null)
    }

    fun <T> measureWork(label: String, block: () -> T): T {
        if (!enabled) return block()
        val start = System.nanoTime()
        Trace.beginSection("miptvga.$label")
        try {
            return block()
        } finally {
            Trace.endSection()
            Log.d(Tag, "work=$label durationNs=${System.nanoTime() - start} main=${Looper.myLooper() == Looper.getMainLooper()}")
        }
    }

    fun <T> traceKey(event: KeyEvent, block: () -> T): T {
        if (!enabled || activeEntries == null || event.action != KeyEvent.ACTION_DOWN ||
            (event.keyCode != KeyEvent.KEYCODE_DPAD_UP && event.keyCode != KeyEvent.KEYCODE_DPAD_DOWN)) return block()
        val start = System.nanoTime()
        val cpu = Debug.threadCpuTimeNanos()
        ordinal++
        eventId++
        pendingFocusAt = start
        Trace.beginSection("miptvga.dpad.dispatch")
        try {
            return block()
        } finally {
            Trace.endSection()
            Log.d(Tag, "event=$eventId segment=$segment ordinal=$ordinal dispatchNs=${System.nanoTime() - start} mainCpuNs=${Debug.threadCpuTimeNanos() - cpu} keyEventAgeMs=${SystemClock.uptimeMillis() - event.eventTime} repeat=${event.repeatCount}")
        }
    }

    fun focusChanged() {
        if (!enabled || pendingFocusAt == 0L) return
        Trace.beginSection("miptvga.channel.focus")
        try {
            val focusAt = System.nanoTime()
            Log.d(Tag, "focus event=$eventId segment=$segment ordinal=$ordinal dispatchToFocusCallbackNs=${focusAt - pendingFocusAt}")
            pendingFrame.set(FocusSample(eventId, segment, ordinal, pendingFocusAt, focusAt))
            pendingFocusAt = 0L
        } finally {
            Trace.endSection()
        }
    }

    fun composedRow() { if (enabled) rowCompositions.incrementAndGet() }
    fun composedPanel() { if (enabled) panelCompositions.incrementAndGet() }
}
