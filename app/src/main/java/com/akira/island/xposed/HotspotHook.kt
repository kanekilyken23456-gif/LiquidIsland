package com.akira.island.xposed

import android.os.Handler
import android.os.Looper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.ref.WeakReference

/**
 * Reads the hotspot state from SystemUI's own HotspotController, the same object that drives the
 * quick-settings tile. Third-party apps cannot see this on many ROMs (no broadcast, no notification),
 * SystemUI always knows it.
 *
 * Hook points differ per ROM, so candidates are looked up by name; whichever exists is hooked. After any
 * of them runs, the state is re-read through the public controller getters (isHotspotEnabled and
 * getNumConnectedDevices), never guessed from arguments.
 */
object HotspotHook {
    private val CLASS_NAMES = listOf(
        "com.android.systemui.statusbar.policy.HotspotControllerImpl",
        "com.android.systemui.statusbar.policy.MiuiHotspotControllerImpl",
        "com.android.systemui.statusbar.policy.OplusHotspotControllerImpl",
    )
    /** Methods that mean "the state may have changed". */
    private val CHANGE_METHODS = setOf(
        "fireHotspotChangedCallback", "fireCallback", "onStateChanged", "onNumClientsChanged",
        "onConnectedClientsChanged", "onTetheredInterfacesChanged", "updateHotspotState", "setHotspotEnabled",
    )
    /** Methods SystemUI calls early and often: they only give us the controller instance. */
    private val CAPTURE_METHODS = setOf("addCallback", "isHotspotEnabled", "getNumConnectedDevices", "isHotspotSupported")
    private val main = Handler(Looper.getMainLooper())
    private var controller: WeakReference<Any>? = null
    private var pending = false

    fun install(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (name in CLASS_NAMES) {
            val cls = XposedHelpers.findClassIfExists(name, lpparam.classLoader) ?: continue
            val capture = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    param.thisObject?.let { controller = WeakReference(it) }
                }
            }
            val change = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    param.thisObject?.let { controller = WeakReference(it) }
                    scheduleRead()
                }
            }
            val declared = cls.methods.map { it.name }.toSet() + cls.declaredMethods.map { it.name }
            for (method in declared) {
                val hook = when (method) {
                    in CHANGE_METHODS -> change
                    in CAPTURE_METHODS -> capture
                    else -> continue
                }
                runCatching { XposedBridge.hookAllMethods(cls, method, hook) }
                    .onFailure { XposedBridge.log("DynamicIsland: hotspot hook $method failed") }
            }
            XposedBridge.log("DynamicIsland: hooked hotspot controller $name")
            return
        }
        XposedBridge.log("DynamicIsland: no HotspotController class found; hotspot status stays unavailable")
    }

    /** Callbacks can arrive in bursts (state + client count): read once, a moment later. */
    private fun scheduleRead() {
        if (pending) return
        pending = true
        main.postDelayed({
            pending = false
            read()
        }, 150L)
    }

    /** Also used when the app asks for the current state. */
    fun read() {
        val c = controller?.get() ?: return
        try {
            val enabled = call(c, "isHotspotEnabled") as? Boolean ?: return
            val clients = (call(c, "getNumConnectedDevices") as? Int) ?: -1
            SystemUiStateHook.publishHotspot(enabled, clients)
        } catch (t: Throwable) {
            XposedBridge.log("DynamicIsland: reading hotspot state failed")
            XposedBridge.log(t)
        }
    }

    /** Plain reflection: the controller's getters are public and take no arguments. */
    private fun call(target: Any, name: String): Any? = runCatching {
        val m = target.javaClass.methods.firstOrNull { it.name == name && it.parameterTypes.isEmpty() }
            ?: target.javaClass.declaredMethods.firstOrNull { it.name == name && it.parameterTypes.isEmpty() }
                ?.also { it.isAccessible = true }
        m?.invoke(target)
    }.getOrNull()
}
