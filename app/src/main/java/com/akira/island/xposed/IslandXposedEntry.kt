package com.akira.island.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Optional LSPosed entry. The island itself is still drawn by the accessibility service
 * (IslandService); this module only runs inside SystemUI and:
 *  - hides status-bar icons that collide with the capsule  → [StatusIconsHook]
 *  - reports fullscreen/immersive and shade/QS state       → [SystemUiStateHook]
 *  - reports hotspot on/off and client count               → [HotspotHook]
 *
 * Every install is isolated in its own try/catch: a ROM that renamed one class must never
 * take SystemUI down, it just loses that one feature (see the "DynamicIsland:" lines in the LSPosed log).
 */
class IslandXposedEntry : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != HookContract.SYSTEMUI_PACKAGE) return
        XposedBridge.log("DynamicIsland: SystemUI loaded, installing hooks")
        try {
            StatusIconsHook.install(lpparam)
        } catch (t: Throwable) {
            XposedBridge.log("DynamicIsland: StatusIconsHook install failed")
            XposedBridge.log(t)
        }
        try {
            HotspotHook.install(lpparam)
        } catch (t: Throwable) {
            XposedBridge.log("DynamicIsland: HotspotHook install failed")
            XposedBridge.log(t)
        }
        try {
            SystemUiStateHook.install(lpparam)
        } catch (t: Throwable) {
            XposedBridge.log("DynamicIsland: SystemUiStateHook install failed")
            XposedBridge.log(t)
        }
    }
}
