package com.akira.island.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import com.akira.island.face.RootShell

/**
 * Root mode of «Hide in selected apps». Some apps (banking) refuse to run while an accessibility service is
 * enabled; hiding the overlay is not enough for them. Through root this switches the island's accessibility
 * service off while such an app is open and restores it afterwards. All commands are fixed; the only
 * interpolated text is the system's own service list, which is validated first.
 */
object BankGuard {
    private const val PREFS = "bank_guard"
    private const val KEY_DISABLED = "disabled"
    private const val SETTING = "enabled_accessibility_services"
    private val SAFE = Regex("[A-Za-z0-9_.\\$/:-]*")

    fun isOn(s: IslandSettings): Boolean = s.hideInAppsDisableService && s.hideInApps.isNotEmpty()

    /** True while the service was switched off by the guard and still has to be restored. */
    fun pendingRestore(context: Context): Boolean = prefs(context).getBoolean(KEY_DISABLED, false)

    internal fun setPending(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_DISABLED, value).apply()
    }

    /** Start or stop the watcher to match the settings; restores the service if the guard was switched off meanwhile. */
    fun sync(context: Context) {
        val app = context.applicationContext
        SettingsRepo.init(app)
        val intent = Intent(app, BankGuardService::class.java)
        if (isOn(SettingsRepo.current)) {
            runCatching { app.startForegroundService(intent) }
        } else {
            app.stopService(intent)
            if (pendingRestore(app)) Thread { enableService(app) }.start()
        }
    }

    /** Removes the island from the enabled accessibility services. Blocking: call off the main thread. */
    internal fun disableService(context: Context): Boolean {
        val entries = readEntries() ?: return false
        val rest = entries.filterNot { it.startsWith(context.packageName + "/") }
        if (rest.size == entries.size) return true
        return write(rest)
    }

    /** Adds the island back to the enabled accessibility services. Blocking: call off the main thread. */
    internal fun enableService(context: Context): Boolean {
        val entries = readEntries() ?: return false
        val ok = entries.any { it.startsWith(context.packageName + "/") } ||
            write(entries + ComponentName(context, IslandService::class.java).flattenToString())
        if (ok) setPending(context, false)
        return ok
    }

    private fun readEntries(): List<String>? {
        val raw = RootShell.output("settings get secure $SETTING")?.trim() ?: return null
        val value = if (raw == "null") "" else raw
        if (!SAFE.matches(value)) return null // never put unexpected text into a root command
        return value.split(':').filter { it.isNotBlank() }
    }

    private fun write(list: List<String>): Boolean {
        val command = if (list.isEmpty()) {
            "settings delete secure $SETTING || settings put secure $SETTING ''; settings put secure accessibility_enabled 0"
        } else {
            "settings put secure $SETTING '${list.joinToString(":")}'; settings put secure accessibility_enabled 1"
        }
        return RootShell.output(command, 6_000L) != null
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
