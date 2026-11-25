package com.akira.island.xposed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build

/** Helpers shared by the hooks. Runs inside SystemUI: framework APIs only. */
internal object SystemUiHookUtil {

    /** The SystemUI Application, or null while the process is still starting. */
    fun currentContext(): Context? = try {
        Class.forName("android.app.AndroidAppHelper")
            .getMethod("currentApplication")
            .invoke(null) as? Context
    } catch (_: Throwable) {
        null
    }

    /** The app lives in another uid, so the receiver must be exported. */
    fun register(ctx: Context, receiver: BroadcastReceiver, action: String) {
        val filter = IntentFilter(action)
        if (Build.VERSION.SDK_INT >= 33) {
            ctx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            ctx.registerReceiver(receiver, filter)
        }
    }

    /**
     * Call only from onReceive. Android 14+ can tell who sent the broadcast: reject it when the
     * sender is known and is not the island app. Older Android (or a ROM that returns null)
     * cannot, so the payload stays harmless by design: booleans and two ints.
     */
    fun senderIsApp(receiver: BroadcastReceiver): Boolean {
        if (Build.VERSION.SDK_INT >= 34) {
            val from = try { receiver.sentFromPackage } catch (_: Throwable) { null }
            if (from != null && from != HookContract.APP_PACKAGE) return false
        }
        return true
    }
}
