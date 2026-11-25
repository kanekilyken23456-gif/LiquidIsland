package com.akira.island.face

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.akira.island.core.IslandBus
import com.akira.island.data.SettingsRepo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class FaceAvailability { OFF, SERVICE_OFF, ROOT_UNAVAILABLE, PERMISSION_NEEDED, PAUSED, CONNECTING, WAITING_FOR_EVENTS, EVENTS_SEEN, SOURCE_UNAVAILABLE }

/** Result of the last `su` check. UNKNOWN until the check has run (it may show the root manager prompt). */
enum class RootState { UNKNOWN, CHECKING, GRANTED, DENIED }

object FaceUnlockState {
    val phase = MutableStateFlow(FacePhase.HIDDEN)
    val availability = MutableStateFlow(FaceAvailability.OFF)
    /** Last few semantic steps, shown in the settings so a failing stage is visible. */
    val debug = MutableStateFlow<List<String>>(emptyList())
    internal fun note(text: String) {
        val t = System.currentTimeMillis()
        val stamp = "%02d:%02d.%03d".format((t / 60_000) % 60, (t / 1_000) % 60, t % 1_000)
        debug.update { (it + "$stamp $text").takeLast(12) }
    }
    internal val revision = MutableStateFlow(0L)
    internal val reconnectRequest = MutableStateFlow(0L)
    @Volatile internal var root = RootState.UNKNOWN
    /** Asks for root again (e.g. after the user changed the grant in the root manager) and reconnects. */
    fun reconnect() { root = RootState.UNKNOWN; reconnectRequest.update { it + 1 }; refresh() }
    fun refresh() { revision.update { it + 1 } }
    fun access(): FaceAvailability = when (root) {
        RootState.UNKNOWN, RootState.CHECKING -> FaceAvailability.PERMISSION_NEEDED
        RootState.DENIED -> FaceAvailability.ROOT_UNAVAILABLE
        RootState.GRANTED -> FaceAvailability.CONNECTING
    }
}

/** Owns the optional root reader and session; existing music/events/WindowManager are independent. */
class FaceUnlockController(private val context: Context, parent: CoroutineScope) {
    private val scope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val wake = FaceWakeSession()
    private val session get() = wake.session
    private var source: FaceRootSource? = null
    private var rootCheck: Job? = null
    private var timer: Job? = null
    private var bindingTimeout: Job? = null
    private var presenceWatch: Job? = null
    private var generation = 0L
    private var started = false
    private var retries = 0
    private var lastReconnect = FaceUnlockState.reconnectRequest.value
    private val screen = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    wake.screenOff(SettingsRepo.current.faceUnlockOnly && SettingsRepo.current.enabled,
                        keyguard?.isDeviceSecure == true, SystemClock.elapsedRealtime())
                    // The reader stays up: starting `su` + logcat after wake can miss a fast face result.
                    FaceUnlockState.note("screen off (secure=${keyguard?.isDeviceSecure == true}) phase=${session.phase}")
                    publish(); FaceUnlockState.refresh()
                }
                Intent.ACTION_SCREEN_ON -> { beginLock(); FaceUnlockState.note("screen on"); FaceUnlockState.refresh() }
                Intent.ACTION_USER_PRESENT -> {
                    beginLock()
                    val unlocked = power?.isInteractive == true &&
                        keyguard?.isKeyguardLocked == false && keyguard.isDeviceLocked == false
                    session.userPresent(SystemClock.elapsedRealtime(), unlocked)
                    publish()
                    FaceUnlockState.note("user present (unlocked=$unlocked) phase=${session.phase}")
                }
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        ContextCompat.registerReceiver(context, screen, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        beginLock()
        // ACTION_USER_PRESENT is unreliable (late or never delivered on some ROMs), so the verified
        // "keyguard is gone" state from the island service is accepted as the same presence evidence.
        scope.launch {
            combine(IslandBus.keyguard, IslandBus.locked) { keyguardShown, locked -> !keyguardShown && !locked }
                .distinctUntilChanged()
                .collect { gone -> if (gone) markPresent("keyguard gone") }
        }
        scope.launch {
            combine(SettingsRepo.state.map { it.faceUnlockOnly && it.enabled }.distinctUntilChanged(),
                IslandBus.screenOn, FaceUnlockState.revision, FaceUnlockState.reconnectRequest) { enabled, _, _, retry -> enabled to retry }
                .collect { (enabled, retry) ->
                    if (retry != lastReconnect) {
                        lastReconnect = retry
                        disconnect()
                    }
                    beginLock()
                    val status = when {
                        !enabled -> FaceAvailability.OFF
                        else -> FaceUnlockState.access()
                    }
                    if (status == FaceAvailability.PERMISSION_NEEDED && enabled) {
                        checkRoot()
                    } else if (status == FaceAvailability.CONNECTING) {
                        if (source == null) connect()
                    } else {
                        disconnect()
                        if (status != FaceAvailability.PAUSED) wake.clear()
                        publish(); FaceUnlockState.availability.value = status
                    }
                }
        }
    }

    private fun beginLock() {
        wake.refresh(SettingsRepo.current.faceUnlockOnly && SettingsRepo.current.enabled,
            keyguard?.isKeyguardLocked == true && keyguard.isDeviceLocked, SystemClock.elapsedRealtime())
        publish()
    }

    /** The first `su` call can show the root manager prompt, so it runs off the main thread. */
    private fun checkRoot() {
        if (FaceUnlockState.root == RootState.CHECKING) return
        FaceUnlockState.root = RootState.CHECKING
        FaceUnlockState.availability.value = FaceAvailability.CONNECTING
        rootCheck?.cancel()
        rootCheck = scope.launch(Dispatchers.IO) {
            val granted = RootShell.hasRoot()
            FaceUnlockState.root = if (granted) RootState.GRANTED else RootState.DENIED
            FaceUnlockState.note("root: " + if (granted) "granted" else "denied")
            FaceUnlockState.refresh()
        }
    }

    private fun connect() {
        val token = ++generation
        FaceUnlockState.availability.value = FaceAvailability.CONNECTING
        val events = object : FaceRootSource.Events {
            override fun onSignal(kind: Int, elapsedMillis: Long) {
                scope.launch {
                    if (token != generation || !SettingsRepo.current.faceUnlockOnly || power?.isInteractive != true) return@launch
                    val signal = FaceSignal.entries.getOrNull(kind) ?: return@launch
                    retries = 0
                    if (signal != FaceSignal.OTHER_UNLOCK && signal != FaceSignal.NON_CREDENTIAL_ENTRY)
                        FaceUnlockState.availability.value = FaceAvailability.EVENTS_SEEN
                    session.signal(signal, elapsedMillis, SystemClock.elapsedRealtime()); publish()
                    FaceUnlockState.note("signal $signal age=${SystemClock.elapsedRealtime() - elapsedMillis}ms phase=${session.phase}")
                    // Result evidence arrived: watch the keyguard briefly so presence never depends on a broadcast.
                    if (signal == FaceSignal.ACCEPTED || signal == FaceSignal.NON_CREDENTIAL_ENTRY) watchPresence()
                }
            }
            override fun onDebug(text: String) { scope.launch { if (token == generation) FaceUnlockState.note(text) } }
            override fun onSourceState(state: Int) {
                scope.launch {
                    if (token != generation) return@launch
                    if (state == 1) {
                        bindingTimeout?.cancel()
                        if (FaceUnlockState.availability.value != FaceAvailability.EVENTS_SEEN)
                            FaceUnlockState.availability.value = FaceAvailability.WAITING_FOR_EVENTS
                    } else if (state == 3 && retries < 3) {
                        // The stream ended (e.g. a clock change): quietly reconnect a few times.
                        retries++
                        FaceUnlockState.note("stream ended, reconnecting ($retries)")
                        disconnect()
                        delay(700)
                        FaceUnlockState.refresh()
                    } else failSource()
                }
            }
        }
        // Package visibility can hide this optional source. The independently trusted
        // system-server source still requires explicit face authentication and entry.
        val uiUid = runCatching {
            context.packageManager.getApplicationInfo("com.android.systemui", 0)
                .takeIf { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 }?.uid ?: -1
        }.getOrDefault(-1)
        val remote = FaceRootSource(android.os.Process.myUid() / 100_000, uiUid, events)
        source = remote
        remote.start()
        bindingTimeout = scope.launch {
            delay(10_000)
            if (token == generation && FaceUnlockState.availability.value == FaceAvailability.CONNECTING) failSource()
        }
    }

    private fun unlockedNow(): Boolean = power?.isInteractive == true &&
        keyguard?.isKeyguardLocked == false && keyguard.isDeviceLocked == false

    /** Device really is unlocked and interactive: counts as presence, exactly like USER_PRESENT does. */
    private fun markPresent(origin: String) {
        if (!unlockedNow()) return
        session.userPresent(SystemClock.elapsedRealtime(), true)
        publish()
        FaceUnlockState.note("unlock seen ($origin) phase=${session.phase}")
    }

    /** Polls the keyguard for a short window after face evidence; ends at the first confirmed unlock. */
    private fun watchPresence() {
        if (presenceWatch?.isActive == true) return
        presenceWatch = scope.launch {
            val end = SystemClock.elapsedRealtime() + FaceSession.PAIR_MS + FaceSession.SETTLE_MS + 800
            while (isActive && SystemClock.elapsedRealtime() < end) {
                if (unlockedNow()) { markPresent("poll"); return@launch }
                delay(40)
            }
        }
    }

    private fun publish() {
        FaceUnlockState.phase.value = session.phase
        timer?.cancel()
        val deadline = session.deadline ?: return
        timer = scope.launch {
            delay((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1))
            session.tick(SystemClock.elapsedRealtime()); publish()
        }
    }
    private fun failSource() {
        disconnect(); wake.clear(); publish()
        FaceUnlockState.availability.value = FaceAvailability.SOURCE_UNAVAILABLE
    }
    private fun disconnect() {
        ++generation
        bindingTimeout?.cancel(); bindingTimeout = null
        val remote = source; source = null
        // Reader cleanup cannot block the UI thread.
        if (remote != null) Thread({ runCatching { remote.stop() } }, "island-face-stop").apply {
            isDaemon = true
            start()
        }
    }
    fun stop() {
        if (!started) return
        started = false
        runCatching { context.unregisterReceiver(screen) }
        rootCheck?.cancel(); presenceWatch?.cancel(); disconnect(); wake.clear(); FaceUnlockState.phase.value = FacePhase.HIDDEN
        FaceUnlockState.availability.value = FaceAvailability.SERVICE_OFF
        scope.cancel()
    }
}
