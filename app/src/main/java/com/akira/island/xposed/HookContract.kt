package com.akira.island.xposed

/**
 * Broadcast contract between the island app process and the SystemUI process
 * (where the optional LSPosed module runs).
 *
 * This object is loaded in BOTH processes, so it must stay constants-only:
 * no Compose, no app singletons, nothing that needs the app's own runtime.
 *
 * Direction is always pinned with setPackage(), so a broadcast reaches only the intended side:
 *  - app → SystemUI : [ACTION_ICONS], [ACTION_REQUEST_STATE]  (package = SystemUI)
 *  - SystemUI → app : [ACTION_STATE]                           (package = the island app)
 */
object HookContract {
    const val APP_PACKAGE = "com.dynamic.island"
    const val SYSTEMUI_PACKAGE = "com.android.systemui"

    /** app → SystemUI: which status-bar icons to hide. */
    const val ACTION_ICONS = "com.dynamic.island.hook.ICONS"
    const val EXTRA_MODE = "mode"
    const val MODE_SHOW_ALL = "show_all"
    /** Hide only icons whose screen bounds overlap [EXTRA_LEFT]..[EXTRA_RIGHT]. */
    const val MODE_SELECTIVE = "selective"
    const val EXTRA_LEFT = "left"
    const val EXTRA_RIGHT = "right"
    /**
     * Also hide the ROM's own screen-recording chip (the red pill next to the clock) while the island
     * shows the recording itself. Only meaningful together with [MODE_SELECTIVE]; absent = false.
     */
    const val EXTRA_HIDE_REC_CHIP = "hide_rec_chip"

    /** SystemUI → app: current fullscreen and shade state (always both fields). */
    const val ACTION_STATE = "com.dynamic.island.hook.STATE"
    const val EXTRA_FULLSCREEN = "fullscreen"
    const val EXTRA_SHADE = "shade"
    /** Hotspot as seen by SystemUI's HotspotController: [HOTSPOT_UNKNOWN], [HOTSPOT_OFF] or [HOTSPOT_ON]. */
    const val EXTRA_HOTSPOT = "hotspot"
    /** Connected device count, or -1 when SystemUI does not know it. Only meaningful when the hotspot is on. */
    const val EXTRA_HOTSPOT_CLIENTS = "hotspot_clients"
    const val HOTSPOT_UNKNOWN = -1
    const val HOTSPOT_OFF = 0
    const val HOTSPOT_ON = 1

    /** app → SystemUI: "send your current state now" (hooks only broadcast on change). */
    const val ACTION_REQUEST_STATE = "com.dynamic.island.hook.REQUEST_STATE"
}
