package com.akira.island.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.akira.island.core.IslandBus
import com.akira.island.core.ServiceCommand
import com.akira.island.ui.Perms

/**
 * Кнопка «Восстановить остров» в уведомлении Keep Alive.
 * Если служба спец. возможностей жива — принудительно пересобираем окна.
 * Если нет — открываем системные настройки спец. возможностей.
 */
class RestoreIslandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (Perms.accessibility(context) && IslandBus.serviceRunning.value) {
            IslandBus.commands.tryEmit(ServiceCommand.RESTORE)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
