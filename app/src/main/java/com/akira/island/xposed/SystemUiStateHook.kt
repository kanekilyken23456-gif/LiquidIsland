package com.akira.island.xposed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Reports two facts from inside SystemUI to the island app:
 *  - fullscreen: an immersive app hid the status bar;
 *  - shade: the notification shade / quick settings panel is open (or being dragged open).
 *
 * Both are sent together in one [HookContract.ACTION_STATE] broadcast, only when one of them changes,
 * plus on request and whenever the status bar (re)attaches. The accessibility service cannot read either
 * reliably (the shade needs view ids that many ROMs hide), SystemUI knows both exactly.
 *
 * Fullscreen: StatusBarManager window constants (stable across AOSP)
 *   WINDOW_STATUS_BAR = 1;  WINDOW_STATE_SHOWING = 0, HIDING = 1, HIDDEN = 2
 */
object SystemUiStateHook {

    private const val WINDOW_STATUS_BAR = 1
    private const val WINDOW_STATE_SHOWING = 0

    private val main = Handler(Looper.getMainLooper())

    @Volatile private var fullscreen = false
    @Volatile private var shade = false
    @Volatile private var hotspot = HookContract.HOTSPOT_UNKNOWN
    @Volatile private var hotspotClients = -1
    private var windowStateHooked = false
    private var context: Context? = null
    private var requestReceiverRegistered = false
    private var readyAttempts = 0

    fun install(lpparam: XC_LoadPackage.LoadPackageParam) {
        hookWindowState(lpparam)
        if (!windowStateHooked) hookStatusBarVisibilityFallback(lpparam)
        hookShade(lpparam)
        // StatusIconsHook hands us a context when the status bar attaches; if that hook is missing on
        // this ROM, pick the SystemUI application up ourselves a few seconds later.
        main.postDelayed({ ensureReady() }, 4_000L)
    }

    /** Called by [StatusIconsHook] when the status bar view attaches. */
    fun announce(ctx: Context) {
        context = ctx.applicationContext ?: ctx
        registerRequestReceiver()
        broadcast()
    }

    // ---- fullscreen -------------------------------------------------------------------------

    private fun hookWindowState(lpparam: XC_LoadPackage.LoadPackageParam) {
        val cls = XposedHelpers.findClassIfExists("com.android.systemui.statusbar.CommandQueue", lpparam.classLoader)
        if (cls == null) {
            XposedBridge.log("DynamicIsland: CommandQueue not found")
            return
        }
        val i = Int::class.javaPrimitiveType
        // Newer AOSP: setWindowState(displayId, window, state)
        try {
            XposedHelpers.findAndHookMethod(cls, "setWindowState", i, i, i, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    onWindowState(param.args[1] as Int, param.args[2] as Int)
                }
            })
            windowStateHooked = true
            XposedBridge.log("DynamicIsland: hooked CommandQueue.setWindowState(display, window, state)")
            return
        } catch (_: Throwable) { /* try the older signature */ }
        // Older AOSP: setWindowState(window, state)
        try {
            XposedHelpers.findAndHookMethod(cls, "setWindowState", i, i, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    onWindowState(param.args[0] as Int, param.args[1] as Int)
                }
            })
            windowStateHooked = true
            XposedBridge.log("DynamicIsland: hooked CommandQueue.setWindowState(window, state)")
        } catch (_: Throwable) {
            XposedBridge.log("DynamicIsland: CommandQueue.setWindowState not found, using visibility fallback")
        }
    }

    private fun onWindowState(window: Int, state: Int) {
        if (window == WINDOW_STATUS_BAR) publishFullscreen(state != WINDOW_STATE_SHOWING)
    }

    /** Only used when CommandQueue could not be hooked. */
    private fun hookStatusBarVisibilityFallback(lpparam: XC_LoadPackage.LoadPackageParam) {
        val cls = XposedHelpers.findClassIfExists(
            "com.android.systemui.statusbar.phone.PhoneStatusBarView", lpparam.classLoader
        ) ?: return
        try {
            XposedHelpers.findAndHookMethod(cls, "setVisibility", Int::class.javaPrimitiveType, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    publishFullscreen((param.args[0] as Int) == View.GONE)
                }
            })
            XposedBridge.log("DynamicIsland: hooked PhoneStatusBarView.setVisibility (fallback)")
        } catch (t: Throwable) {
            XposedBridge.log("DynamicIsland: visibility fallback failed")
            XposedBridge.log(t)
        }
    }

    // ---- shade / quick settings ----------------------------------------------------------

    /**
     * Hook points differ per Android version and ROM, so every candidate is looked up by name and
     * the argument is read by type, not by position. The first candidate that exists wins; a second
     * one would only race the first.
     */
    private fun hookShade(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Android 13+: onPanelExpansionChanged(float fraction, boolean expanded, boolean tracking[, float dragDown])
        val manager = XposedHelpers.findClassIfExists(
            "com.android.systemui.shade.ShadeExpansionStateManager", lpparam.classLoader
        )
        if (manager != null) {
            try {
                XposedBridge.hookAllMethods(manager, "onPanelExpansionChanged", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val fraction = param.args.firstOrNull { it is Float } as? Float
                        val expanded = param.args.firstOrNull { it is Boolean } as? Boolean
                        publishShade(expanded ?: ((fraction ?: 0f) > 0f))
                    }
                })
                XposedBridge.log("DynamicIsland: hooked ShadeExpansionStateManager.onPanelExpansionChanged")
                return
            } catch (t: Throwable) {
                XposedBridge.log("DynamicIsland: ShadeExpansionStateManager hook failed")
                XposedBridge.log(t)
            }
        }
        // Android 10-14 fallback: StatusBarStateControllerImpl.setPanelExpanded(boolean)
        val controller = XposedHelpers.findClassIfExists(
            "com.android.systemui.statusbar.StatusBarStateControllerImpl", lpparam.classLoader
        )
        if (controller != null) {
            try {
                XposedBridge.hookAllMethods(controller, "setPanelExpanded", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        (param.args.firstOrNull { it is Boolean } as? Boolean)?.let { publishShade(it) }
                    }
                })
                XposedBridge.log("DynamicIsland: hooked StatusBarStateControllerImpl.setPanelExpanded")
                return
            } catch (t: Throwable) {
                XposedBridge.log("DynamicIsland: StatusBarStateControllerImpl hook failed")
                XposedBridge.log(t)
            }
        }
        XposedBridge.log("DynamicIsland: no shade hook point found; the app falls back to accessibility detection")
    }

    // ---- publishing ---------------------------------------------------------------------------

    private fun publishFullscreen(value: Boolean) {
        if (fullscreen == value) return
        fullscreen = value
        broadcast()
    }

    /** Called by [HotspotHook] from SystemUI's own HotspotController: no polling, no private API in the app. */
    fun publishHotspot(enabled: Boolean, clients: Int) {
        val state = if (enabled) HookContract.HOTSPOT_ON else HookContract.HOTSPOT_OFF
        val count = if (enabled && clients in 0..999) clients else -1
        if (hotspot == state && hotspotClients == count) return
        hotspot = state
        hotspotClients = count
        broadcast()
    }

    private fun publishShade(value: Boolean) {
        if (shade == value) return
        shade = value
        broadcast()
    }

    private fun broadcast() {
        val ctx = context ?: SystemUiHookUtil.currentContext()?.also { context = it } ?: return
        try {
            ctx.sendBroadcast(
                Intent(HookContract.ACTION_STATE)
                    .setPackage(HookContract.APP_PACKAGE)
                    .putExtra(HookContract.EXTRA_FULLSCREEN, fullscreen)
                    .putExtra(HookContract.EXTRA_SHADE, shade)
                    .putExtra(HookContract.EXTRA_HOTSPOT, hotspot)
                    .putExtra(HookContract.EXTRA_HOTSPOT_CLIENTS, hotspotClients)
            )
        } catch (t: Throwable) {
            XposedBridge.log("DynamicIsland: state broadcast failed")
            XposedBridge.log(t)
        }
    }

    private fun registerRequestReceiver() {
        if (requestReceiverRegistered) return
        val ctx = context ?: return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action != HookContract.ACTION_REQUEST_STATE) return
                if (!SystemUiHookUtil.senderIsApp(this)) return
                // Re-send even if nothing changed: the requester has no state yet.
                HotspotHook.read()
                broadcast()
            }
        }
        try {
            SystemUiHookUtil.register(ctx, receiver, HookContract.ACTION_REQUEST_STATE)
            requestReceiverRegistered = true
        } catch (t: Throwable) {
            XposedBridge.log("DynamicIsland: request receiver registration failed")
            XposedBridge.log(t)
        }
    }

    private fun ensureReady() {
        if (context == null) context = SystemUiHookUtil.currentContext()
        if (context != null) {
            registerRequestReceiver()
            broadcast()
        } else if (++readyAttempts < 5) {
            main.postDelayed({ ensureReady() }, 4_000L)
        }
    }
}
