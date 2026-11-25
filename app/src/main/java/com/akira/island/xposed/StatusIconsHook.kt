package com.akira.island.xposed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Hides the status-bar icons that sit under the island.
 *
 * Differences from a plain "set GONE and hope" approach:
 *  - icons are made INVISIBLE, not GONE. They keep their size and position, so hiding one icon
 *    never shifts its neighbours into the island's zone (which would cascade into hiding everything)
 *    and a hidden icon can still be measured when the island shrinks;
 *  - only views this hook hid are restored, and to the visibility they had before. Slots the system
 *    keeps hidden on purpose are never forced visible;
 *  - while the selective mode is on, every frame of the status bar is checked just before it is drawn
 *    (see [preDrawListener]). An icon that appears, is recreated, moves, or is made visible again by
 *    SystemUI is hidden the moment it touches the island, with no state change needed; an icon that
 *    does not touch the island is left alone (and restored if it moved away).
 *
 * Clock and BatteryMeterView are never touched.
 */
object StatusIconsHook {

    /** System icon containers (wifi, signal, …). Names differ per ROM; the first one found wins. */
    private val ICON_CONTAINER_IDS = listOf("statusIcons", "status_bar_wifi_group", "system_icon_area")
    private val NOTIFICATION_ICON_IDS = listOf("notificationIcons", "notification_icon_area", "notification_icons")
    private val NETWORK_SPEED_IDS = listOf("network_speed_indicator", "traffic_text_view", "internet_speed")
    private val REC_CHIP_ROW_IDS = listOf("status_bar_start_side_except_heads_up")
    private val NEVER_HIDE_SUFFIXES = listOf("Clock", "BatteryMeterView")
    private val SPEED_NAME = Regex("(?i)(net(work)?_?speed|traffic|internet_?speed|speed_?(view|text|indicator|meter))")
    private val SPEED_TEXT = Regex("(?i)^\\s*\\d+([.,]\\d+)?\\s*[KMG]?i?B?(/s|ps)\\s*$")
    private const val SPEED_RESCAN_MS = 1_000L
    private const val NOTIFICATION_ICON_CONTAINER_CLASS = "NotificationIconContainer"

    /** Extra px so a partially overlapping icon still counts as colliding. */
    private const val COLLISION_PAD_PX = 4
    private const val REAPPLY_THROTTLE_MS = 250L
    private const val SECOND_PASS_MS = 120L

    /**
     * Safety net. The app re-sends the selective mode every ~30 s while it is hiding icons; if it
     * stops (crash, force-stop, killed) the icons come back after this lease instead of staying hidden.
     */
    private const val LEASE_MS = 75_000L

    private val main = Handler(Looper.getMainLooper())

    private var statusIcons: ViewGroup? = null
    private var notificationIcons: ViewGroup? = null
    /** Network-speed meters. ROMs name and place them differently, so they are found by id, class or text. */
    private var speedViews: List<WeakReference<View>> = emptyList()
    private var lastSpeedScan = 0L

    /** view → visibility it had before we hid it */
    private val hiddenByUs = WeakHashMap<View, Int>()

    private var receiverRegistered = false
    private var lastMode = HookContract.MODE_SHOW_ALL
    private var lastLeft = 0
    private var lastRight = 0
    private var lastHideRecChip = false

    private var layoutRoot: WeakReference<View>? = null
    private var reapplyPending = false

    private val leaseExpiry = Runnable {
        lastMode = HookContract.MODE_SHOW_ALL
        safeApply()
    }

    /**
     * Runs before each draw of the status bar, so a colliding icon is never shown even for one frame.
     * It only fires when something in the status bar was invalidated (icon added, visibility or
     * translation changed, animation frame) and the work is idempotent: a second pass changes nothing,
     * so it does not keep re-invalidating the view.
     */
    private val preDrawListener = android.view.ViewTreeObserver.OnPreDrawListener {
        if (lastMode == HookContract.MODE_SELECTIVE) {
            try {
                // The meter may be inflated or recreated after attach: look again about once a second.
                val root = layoutRoot?.get()
                if (root != null && android.os.SystemClock.uptimeMillis() - lastSpeedScan > SPEED_RESCAN_MS) {
                    scanSpeedViews(root, null)
                }
                applySelective(lastLeft - COLLISION_PAD_PX, lastRight + COLLISION_PAD_PX)
            } catch (t: Throwable) {
                XposedBridge.log("DynamicIsland: per-frame icon check failed")
                XposedBridge.log(t)
            }
        }
        true
    }
    private var preDrawObserver: WeakReference<android.view.ViewTreeObserver>? = null

    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        if (lastMode == HookContract.MODE_SELECTIVE && !reapplyPending) {
            reapplyPending = true
            main.postDelayed({
                reapplyPending = false
                safeApply()
            }, REAPPLY_THROTTLE_MS)
        }
    }

    fun install(lpparam: XC_LoadPackage.LoadPackageParam) {
        val cls = XposedHelpers.findClass(
            "com.android.systemui.statusbar.phone.PhoneStatusBarView",
            lpparam.classLoader
        )
        XposedHelpers.findAndHookMethod(cls, "onAttachedToWindow", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val root = param.thisObject as? View ?: return
                try {
                    onStatusBarAttached(root)
                } catch (t: Throwable) {
                    XposedBridge.log("DynamicIsland: onStatusBarAttached failed")
                    XposedBridge.log(t)
                }
            }
        })
        XposedBridge.log("DynamicIsland: hooked PhoneStatusBarView.onAttachedToWindow")
    }

    private fun onStatusBarAttached(root: View) {
        resolveViews(root)
        registerReceiver(root.context)
        watchLayout(root)
        watchFrames(root)
        // The status bar may have been recreated (config change): put the last mode back.
        safeApply()
        XposedBridge.log(
            "DynamicIsland: status bar attached. statusIcons=${statusIcons != null} " +
                "notificationIcons=${notificationIcons != null} networkSpeed=${speedViews.size}"
        )
        // After a SystemUI restart the app has forgotten nothing but we have: tell it we are
        // here so it re-sends the icon mode and learns the current fullscreen/shade state.
        SystemUiStateHook.announce(root.context)
    }

    private fun resolveViews(root: View) {
        val res = root.resources
        val pkg = root.context.packageName

        fun byIds(ids: List<String>): View? = ids.asSequence()
            .map { res.getIdentifier(it, "id", pkg) }
            .filter { it != 0 }
            .mapNotNull { root.findViewById<View>(it) }
            .firstOrNull()

        statusIcons = byIds(ICON_CONTAINER_IDS) as? ViewGroup
        // Prefer the id; fall back to the class so ROMs that rename the id but keep the class still work.
        notificationIcons = (byIds(NOTIFICATION_ICON_IDS) as? ViewGroup)
            ?: findGroupByClassSuffix(root, NOTIFICATION_ICON_CONTAINER_CLASS)
        scanSpeedViews(root, byIds(NETWORK_SPEED_IDS))
    }

    /** Walks the status bar once and remembers every view that looks like a network-speed meter. */
    private fun scanSpeedViews(root: View, known: View?) {
        lastSpeedScan = android.os.SystemClock.uptimeMillis()
        val found = LinkedHashSet<View>()
        known?.let { found += it }
        val res = root.resources
        fun walk(v: View) {
            if (v !== root && !isProtected(v)) {
                val idName = if (v.id != View.NO_ID) runCatching { res.getResourceEntryName(v.id) }.getOrNull() else null
                val byId = idName != null && SPEED_NAME.containsMatchIn(idName)
                val byClass = v.javaClass.simpleName.let { it.contains("Speed", true) || it.contains("Traffic", true) }
                val byText = v is TextView && SPEED_TEXT.matches(v.text ?: "")
                // A matching container is judged as one block; its children need no separate look.
                if (byId || byClass || byText) { found += v; return }
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(root)
        speedViews = found.map { WeakReference(it) }
    }

    private fun findGroupByClassSuffix(root: View, suffix: String): ViewGroup? {
        if (root is ViewGroup) {
            if (root.javaClass.simpleName.endsWith(suffix)) return root
            for (i in 0 until root.childCount) {
                findGroupByClassSuffix(root.getChildAt(i), suffix)?.let { return it }
            }
        }
        return null
    }

    private fun watchLayout(root: View) {
        if (layoutRoot?.get() === root) return
        layoutRoot?.get()?.removeOnLayoutChangeListener(layoutListener)
        root.addOnLayoutChangeListener(layoutListener)
        layoutRoot = WeakReference(root)
    }

    /** The observer belongs to the attached window, so it is registered again on every attach. */
    private fun watchFrames(root: View) {
        preDrawObserver?.get()?.let { if (it.isAlive) it.removeOnPreDrawListener(preDrawListener) }
        val observer = root.viewTreeObserver
        if (!observer.isAlive) return
        observer.addOnPreDrawListener(preDrawListener)
        preDrawObserver = WeakReference(observer)
    }

    private fun registerReceiver(ctx: Context) {
        if (receiverRegistered) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != HookContract.ACTION_ICONS) return
                if (!SystemUiHookUtil.senderIsApp(this)) return
                lastMode = intent.getStringExtra(HookContract.EXTRA_MODE) ?: HookContract.MODE_SHOW_ALL
                lastLeft = intent.getIntExtra(HookContract.EXTRA_LEFT, 0)
                lastRight = intent.getIntExtra(HookContract.EXTRA_RIGHT, 0)
                lastHideRecChip = intent.getBooleanExtra(HookContract.EXTRA_HIDE_REC_CHIP, false)
                main.removeCallbacks(leaseExpiry)
                safeApply()
                if (lastMode == HookContract.MODE_SELECTIVE) {
                    main.postDelayed(leaseExpiry, LEASE_MS)
                    // Right after attach / rotation the icons are not laid out yet: look once more.
                    main.postDelayed({ if (lastMode == HookContract.MODE_SELECTIVE) safeApply() }, SECOND_PASS_MS)
                }
            }
        }
        try {
            SystemUiHookUtil.register(ctx, receiver, HookContract.ACTION_ICONS)
            receiverRegistered = true
        } catch (t: Throwable) {
            XposedBridge.log("DynamicIsland: icon receiver registration failed")
            XposedBridge.log(t)
        }
    }

    private fun safeApply() {
        try {
            if (lastMode == HookContract.MODE_SELECTIVE) {
                applySelective(lastLeft - COLLISION_PAD_PX, lastRight + COLLISION_PAD_PX)
            } else {
                restoreAll()
            }
        } catch (t: Throwable) {
            XposedBridge.log("DynamicIsland: applying icon visibility failed")
            XposedBridge.log(t)
        }
    }

    private fun applySelective(left: Int, right: Int) {
        selectiveGroup(statusIcons, left, right, protectClockAndBattery = true)
        selectiveGroup(notificationIcons, left, right, protectClockAndBattery = false)
        speedViews.forEach { ref -> ref.get()?.let { selectiveView(it, left, right) } }
        applyRecordingChip()
    }

    /**
     * The ROM's ongoing-activity chips (screen recording, call, …) are drawn by Compose: the chip itself
     * is not a View, there is nothing to find by id. What exists is the ComposeView that hosts them, a
     * direct child of the start-side row next to the clock (id "status_bar_start_side_except_heads_up").
     * Hiding that host hides the chip, so it is done only while the app says the island shows the recording.
     */
    private fun applyRecordingChip() {
        val host = recordingChipHost() ?: return
        if (lastHideRecChip) hide(host) else restore(host)
    }

    private fun recordingChipHost(): View? {
        val root = layoutRoot?.get() ?: return null
        val row = REC_CHIP_ROW_IDS.asSequence()
            .map { root.resources.getIdentifier(it, "id", root.context.packageName) }
            .filter { it != 0 }
            .mapNotNull { root.findViewById<View>(it) as? ViewGroup }
            .firstOrNull()
            // Fallback for ROMs that renamed the row: the clock's own parent holds the chip host as well.
            ?: root.resources.getIdentifier("clock", "id", root.context.packageName)
                .takeIf { it != 0 }?.let { root.findViewById<View>(it)?.parent as? ViewGroup }
            ?: return null
        for (i in 0 until row.childCount) {
            val child = row.getChildAt(i)
            if (child.id == View.NO_ID && child.javaClass.simpleName.endsWith("ComposeView")) return child
        }
        return null
    }

    private fun selectiveGroup(group: ViewGroup?, left: Int, right: Int, protectClockAndBattery: Boolean) {
        if (group == null) return
        if (group.childCount == 0) {
            // This ROM draws the icons inside the container itself: all we can test is the container.
            selectiveView(group, left, right)
            return
        }
        restore(group) // the container itself must not stay hidden once it has children to judge
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (protectClockAndBattery && isProtected(child)) {
                restore(child)
                continue
            }
            selectiveView(child, left, right)
        }
    }

    private fun selectiveView(view: View, left: Int, right: Int) {
        // Not laid out (or hidden by the system with no size): nothing to judge, leave it alone.
        if (view.width <= 0 || view.height <= 0) return
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        val vLeft = loc[0]
        val vRight = vLeft + view.width
        if (vLeft < right && vRight > left) hide(view) else restore(view)
    }

    private fun hide(view: View) {
        if (!hiddenByUs.containsKey(view)) {
            // The system already hides it: not ours to touch, and not ours to restore later.
            if (view.visibility != View.VISIBLE) return
            hiddenByUs[view] = view.visibility
        }
        if (view.visibility != View.INVISIBLE) view.visibility = View.INVISIBLE
    }

    private fun restore(view: View) {
        val previous = hiddenByUs.remove(view) ?: return
        if (view.visibility == View.INVISIBLE) view.visibility = previous
    }

    private fun restoreAll() {
        for (view in ArrayList(hiddenByUs.keys)) restore(view)
    }

    private fun isProtected(view: View): Boolean {
        val name = view.javaClass.simpleName
        return NEVER_HIDE_SUFFIXES.any { name.endsWith(it) }
    }
}
