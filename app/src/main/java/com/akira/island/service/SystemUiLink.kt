package com.akira.island.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import com.akira.island.core.IslandBus
import com.akira.island.xposed.HookContract

/**
 * The island app's side of the optional LSPosed module (see the `xposed` package).
 *
 *  SystemUI → app : fullscreen / shade / hotspot state → [IslandBus.fullscreen], [IslandBus.shadeHook], [HotspotMonitor]
 *  app → SystemUI : which status-bar icons to hide, and "send your state now"
 *
 * Without the module nothing arrives and nothing breaks: [IslandBus.hookConnected] just stays false.
 * Every outgoing broadcast is pinned to the SystemUI package, so no other app receives it.
 */
internal object SystemUiLink {
    private var registered = false
    private var receiver: BroadcastReceiver? = null
    /** Called on the main thread whenever SystemUI has sent its state (also right after it restarts). */
    private var onState: (() -> Unit)? = null

    fun start(context: Context, onState: () -> Unit) {
        this.onState = onState
        if (registered) {
            requestState(context)
            return
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != HookContract.ACTION_STATE) return
                // Android 14+ tells us the sender: accept only SystemUI. Older versions cannot; the
                // payload is two booleans, so the worst a spoofer can do is hide the island.
                if (Build.VERSION.SDK_INT >= 34) {
                    val from = runCatching { sentFromPackage }.getOrNull()
                    if (from != null && from != HookContract.SYSTEMUI_PACKAGE) return
                }
                IslandBus.fullscreen.value = intent.getBooleanExtra(HookContract.EXTRA_FULLSCREEN, false)
                IslandBus.shadeHook.value = intent.getBooleanExtra(HookContract.EXTRA_SHADE, false)
                IslandBus.hookConnected.value = true
                when (intent.getIntExtra(HookContract.EXTRA_HOTSPOT, HookContract.HOTSPOT_UNKNOWN)) {
                    HookContract.HOTSPOT_ON -> SystemEventsRegistry.onHotspotHook(true,
                        intent.getIntExtra(HookContract.EXTRA_HOTSPOT_CLIENTS, -1).takeIf { it >= 0 })
                    HookContract.HOTSPOT_OFF -> SystemEventsRegistry.onHotspotHook(false, null)
                }
                this@SystemUiLink.onState?.invoke()
            }
        }
        // SystemUI is another uid, so the receiver has to be exported.
        ContextCompat.registerReceiver(
            context.applicationContext, r, IntentFilter(HookContract.ACTION_STATE), ContextCompat.RECEIVER_EXPORTED
        )
        receiver = r
        registered = true
        requestState(context)
    }

    fun stop(context: Context) {
        receiver?.let { runCatching { context.applicationContext.unregisterReceiver(it) } }
        receiver = null
        registered = false
        onState = null
        IslandBus.fullscreen.value = false
        IslandBus.shadeHook.value = false
        IslandBus.hookConnected.value = false
    }

    /** Hooks broadcast only on change: ask for the current state, e.g. when the service starts mid-video. */
    fun requestState(context: Context) {
        runCatching {
            context.applicationContext.sendBroadcast(
                Intent(HookContract.ACTION_REQUEST_STATE).setPackage(HookContract.SYSTEMUI_PACKAGE)
            )
        }
    }

    fun showAllIcons(context: Context) = sendIcons(context, HookContract.MODE_SHOW_ALL, 0, 0)

    /** Hide the status-bar icons whose screen bounds overlap [leftPx]..[rightPx]. */
    fun hideIconsBetween(context: Context, leftPx: Int, rightPx: Int, hideRecChip: Boolean = false) =
        sendIcons(context, HookContract.MODE_SELECTIVE, leftPx, rightPx, hideRecChip)

    private fun sendIcons(context: Context, mode: String, left: Int, right: Int, hideRecChip: Boolean = false) {
        runCatching {
            context.applicationContext.sendBroadcast(
                Intent(HookContract.ACTION_ICONS)
                    .setPackage(HookContract.SYSTEMUI_PACKAGE)
                    .putExtra(HookContract.EXTRA_MODE, mode)
                    .putExtra(HookContract.EXTRA_LEFT, left)
                    .putExtra(HookContract.EXTRA_RIGHT, right)
                    .putExtra(HookContract.EXTRA_HIDE_REC_CHIP, hideRecChip)
            )
        }
    }
}
