package com.akira.island.face

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

/**
 * Reads the system biometric log through root (`su`) instead of a Shizuku service.
 * Fixed read-only command; only semantic signals leave this class, never log text or identifiers.
 */
internal class FaceRootSource(private val userId: Int, private val systemUiUid: Int, private val events: Events) {
    interface Events {
        fun onSignal(kind: Int, elapsedMillis: Long)
        /** 1 = stream open, 2 = unsupported, 3 = stream ended. */
        fun onSourceState(state: Int)
        /** Semantic diagnostics only (booleans, signal names): never log text or identifiers. */
        fun onDebug(text: String) {}
    }

    private val serial = AtomicLong()
    @Volatile private var reader: Process? = null
    @Volatile private var worker: Thread? = null

    @Synchronized fun start() {
        stopInternal()
        val token = serial.get()
        worker = Thread({
            try {
                val sources = readSources()
                events.onDebug("pids: system_server=${sources.serverPid > 0} keystore2=${sources.keyPid > 0} systemui=${sources.uiPid > 0}")
                // A PID only identifies a candidate source. Success additionally needs a positive
                // parsed non-credential entry from it, so missing/unknown formats fail closed.
                if (sources.serverPid <= 0 || sources.keyPid <= 0) {
                    events.onSourceState(2); return@Thread
                }
                if (serial.get() != token) return@Thread
                val wallNow = System.currentTimeMillis()
                val wallFloor = wallNow - FaceSession.PAIR_MS
                val offset = wallNow - SystemClock.elapsedRealtime()
                val parser = FaceLogParser(sources)
                // Bounded catch-up covers a fast face result during startup. The controller also
                // enforces its fresh lock-session epoch; history can never authenticate a later unlock.
                val process = RootShell.stream(LOGCAT)
                synchronized(this) {
                    if (serial.get() != token) { process.destroy(); return@Thread }
                    reader = process
                }
                events.onDebug("logcat stream opened")
                events.onSourceState(1) // Stream opened, NOT proof of device compatibility.
                process.outputStream.close()
                process.inputStream.buffered().use { input ->
                    while (serial.get() == token && !Thread.currentThread().isInterrupted) {
                        val record = FaceLogReader.read(input) ?: break
                        val now = SystemClock.elapsedRealtime()
                        // Time changes and old buffered entries must never authenticate a new session.
                        if (kotlin.math.abs((System.currentTimeMillis() - now) - offset) > 250) break
                        val at = record.wallMs - offset
                        if (at > now + 100) continue
                        val signal = parser.parse(record) ?: continue
                        // A delayed competing method must still withdraw a result from this lock.
                        if (signal != FaceSignal.OTHER_UNLOCK &&
                            (record.wallMs < wallFloor || now - at > FaceSession.PAIR_MS)) continue
                        if (serial.get() == token) events.onSignal(signal.ordinal, at)
                    }
                }
            } catch (e: Exception) {
                events.onDebug("reader error: ${e.javaClass.simpleName}")
                // Deliberately do not log exception messages, journal text or captured identifiers.
            } finally {
                synchronized(this) { if (serial.get() == token) { reader?.destroy(); reader = null } }
                if (serial.get() == token) runCatching { events.onSourceState(3) }
            }
        }, "island-face-events").apply { isDaemon = true; start() }
    }

    private fun readSources(): FaceLogSources {
        // One su call for all three lookups. "name=<pids>"; anything but exactly one PID is unusable.
        val out = RootShell.output(
            "echo s=$(pidof system_server); echo u=$(pidof com.android.systemui); echo k=$(pidof keystore2)"
        ).orEmpty()
        fun pid(key: String): Int = out.lineSequence().firstOrNull { it.startsWith("$key=") }
            ?.substringAfter('=')?.trim()?.split(Regex("\\s+"))?.singleOrNull()?.toIntOrNull() ?: -1
        return FaceLogSources(pid("s"), pid("u"), systemUiUid, pid("k"), userId)
    }

    @Synchronized private fun stopInternal() {
        serial.incrementAndGet()
        reader?.destroy(); reader = null
        worker?.interrupt(); worker = null
    }

    fun stop() = stopInternal()

    private companion object {
        const val LOGCAT = "/system/bin/logcat -B -b main -b system -T 128 " +
            "Biometrics/AuthenticationClient:V BiometricScheduler:D BiometricStatusRepositoryImpl:D keystore2:I '*:S'"
    }
}
