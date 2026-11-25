package com.akira.island.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.akira.island.R
import com.akira.island.core.tr
import com.akira.island.data.LauncherVisibility
import com.akira.island.data.SettingsRepo
import com.akira.island.face.RootShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Foreground watcher for the root mode of «Hide in selected apps». It reads the system's activity events
 * (`logcat -b events`, event-driven, no polling): when a chosen app starts, the island's accessibility service is
 * switched off through root — before the app has had time to look — and it is switched back on a moment after the
 * user moves on to another app. It lives apart from the accessibility service, so it keeps running while that is off.
 */
class BankGuardService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    @Volatile private var process: Process? = null
    private var started = false
    private var disabled = false
    private var leaveJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        SettingsRepo.init(this)
        // A service started with startForegroundService must go foreground even if it stops right away
        val shown = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(ID, notification())
            }
        }.isSuccess
        if (!shown || !BankGuard.isOn(SettingsRepo.current)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            scope.launch { watch() }
        }
        return START_STICKY
    }

    private suspend fun watch() {
        if (!RootShell.hasRoot()) {
            stopSelf()
            return
        }
        // A restart in the middle of a session: the service may still be switched off from before
        synchronized(lock) { disabled = BankGuard.pendingRestore(this) }
        while (scope.isActive && BankGuard.isOn(SettingsRepo.current)) {
            val p = runCatching { RootShell.stream("logcat -b events -v brief -T 1") }.getOrNull()
            if (p == null) {
                delay(10_000)
                continue
            }
            process = p
            try {
                val reader = p.inputStream.bufferedReader()
                while (true) onLine(reader.readLine() ?: break)
            } catch (_: IOException) {
                // the stream ended (service stopped or logcat died): reconnect below
            } finally {
                p.destroy()
                process = null
            }
            delay(5_000)
        }
    }

    private fun onLine(line: String) {
        val resumed = line.contains("wm_set_resumed_activity")
        if (!resumed && !line.contains("wm_create_activity")) return
        val pkg = COMPONENT.find(line.substringAfter("): ", ""))?.groupValues?.get(1) ?: return
        val chosen = SettingsRepo.current.hideInApps
        synchronized(lock) {
            if (pkg in chosen) {
                leaveJob?.cancel()
                leaveJob = null
                if (!disabled) {
                    // Remember first: if the process dies right after the switch, the service is still restored later
                    BankGuard.setPending(this, true)
                    if (BankGuard.disableService(this)) disabled = true else BankGuard.setPending(this, false)
                }
            } else if (resumed && disabled && pkg !in IGNORED) {
                leaveJob?.cancel()
                leaveJob = scope.launch {
                    delay(LEAVE_DELAY_MS) // short hops (permission dialog, app switcher) must not flip it back and forth
                    synchronized(lock) {
                        if (BankGuard.enableService(this@BankGuardService)) disabled = false
                        leaveJob = null
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        process?.destroy()
        super.onDestroy()
    }

    private fun notification(): Notification {
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, tr("Защита острова", "Island guard"), NotificationManager.IMPORTANCE_MIN).apply {
                description = tr("Выключает остров на время выбранных приложений", "Switches the island off while selected apps are open")
                setShowBadge(false)
            },
        )
        val open = PendingIntent.getActivity(this, 2, LauncherVisibility.settingsIntent(this), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_tab_island)
            .setContentTitle(tr("Остров: скрытие в приложениях", "Island: hide in selected apps"))
            .setContentText(tr("Слежение за открытыми приложениями (root)", "Watching which app is open (root)"))
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private companion object {
        const val ID = 8
        const val CHANNEL = "bank_guard"
        const val LEAVE_DELAY_MS = 1_500L
        val COMPONENT = Regex("([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+)/")
        // Overlays of the system that appear on top of a running app are not «leaving» it
        val IGNORED = setOf(
            "com.android.systemui",
            "com.google.android.permissioncontroller", "com.android.permissioncontroller",
            "com.google.android.packageinstaller", "com.android.packageinstaller",
        )
    }
}
