package com.akira.island.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.akira.island.data.SettingsRepo

/**
 * After a reboot the accessibility service would stay off if the phone was restarted while the guard had it
 * switched off — nothing else could start the island again. Restores it and restarts the watcher.
 */
class BankGuardBoot : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext
        SettingsRepo.init(app)
        if (!BankGuard.pendingRestore(app) && !BankGuard.isOn(SettingsRepo.current)) return
        val result = goAsync()
        Thread {
            try {
                // A restart means no chosen app is open: the island can come back at once
                if (BankGuard.pendingRestore(app)) BankGuard.enableService(app)
                BankGuard.sync(app)
            } finally {
                result.finish()
            }
        }.start()
    }
}
