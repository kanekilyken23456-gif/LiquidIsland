package com.akira.island.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.akira.island.service.IslandService
import com.akira.island.service.NotifListener

object Perms {
    fun accessibility(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(context, IslandService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun notifications(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun bluetooth(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /**
     * Медиа: плашка «Снимок экрана» узнаёт о новом снимке через галерею (MediaStore).
     * Android 13+ — доступ к изображениям, раньше — общий доступ к памяти.
     * Частичный доступ («Выбранные фото», Android 14+) не годится: нового снимка в нём не видно.
     */
    val MEDIA_PERMISSION: String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun media(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, MEDIA_PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Физическая активность: шагомер. До Android 10 отдельного разрешения нет. */
    fun activityRecognition(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    /** Особый доступ «Изменение системных настроек» — ползунок яркости. */
    fun writeSettings(context: Context): Boolean = Settings.System.canWrite(context)

    fun openWriteSettings(context: Context) {
        if (!start(context, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))) openAppDetails(context)
    }

    /** Доступ к «Не беспокоить»: кнопки режима звука и «Не беспокоить». */
    fun dnd(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true

    fun openDnd(context: Context) {
        if (!start(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))) openAppDetails(context)
    }

    /** «Будильники и напоминания» просят только на Android 12–12L: с 13 точный будильник выдаётся сам. */
    fun needsExactAlarmGrant(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

    fun exactAlarms(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    fun openExactAlarms(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
        } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        if (!start(context, intent)) openAppDetails(context)
    }

    /** Звонки: состояние вызова обязательно, остальное — номер, имя и ответ кнопкой. */
    val CALL_PERMISSIONS = buildList {
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.ANSWER_PHONE_CALLS)
    }.toTypedArray()

    fun calls(context: Context): Boolean = CALL_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun openAccessibility(context: Context) = start(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openNotifications(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(context, NotifListener::class.java).flattenToString(),
                )
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        if (!start(context, intent)) start(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    /** Здесь в меню «⋮» прячется «Разрешить ограниченные настройки» (Android 13+). */
    fun openAppDetails(context: Context) =
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))

    /** Батарея «Без ограничений»: система не усыпляет и не выгружает остров. */
    fun batteryUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    /** Список «Оптимизация батареи»; если его нет — «О приложении», там «Батарея». */
    fun openBattery(context: Context) {
        if (!start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) openAppDetails(context)
    }

    /**
     * Чей телефон: у каждой прошивки свой автозапуск и своё закрепление в недавних.
     * [marks] — как прошивка подписывает производителя, [autostart] — её экраны «Автозапуск», от новых к старым.
     */
    enum class Vendor(val marks: List<String>, val autostart: List<String> = emptyList()) {
        XIAOMI(listOf("xiaomi", "redmi", "poco"), listOf(
            "com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity",
        )),
        OPPO(listOf("oppo", "realme", "oneplus"), listOf(
            "com.oplus.safecenter/com.oplus.safecenter.startupapp.view.StartupAppListActivity",
            "com.coloros.safecenter/com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.coloros.safecenter/com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.oppo.safe/com.oppo.safe.permission.startup.StartupAppListActivity",
        )),
        VIVO(listOf("vivo", "iqoo"), listOf(
            "com.vivo.permissionmanager/com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure/com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        )),
        HUAWEI(listOf("huawei", "honor"), listOf(
            "com.huawei.systemmanager/com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager/com.huawei.systemmanager.optimize.process.ProtectActivity",
        )),
        SAMSUNG(listOf("samsung")),
        TRANSSION(listOf("infinix", "tecno", "itel", "transsion"), listOf(
            "com.transsion.phonemaster/com.cyin.himgr.autostart.AutoStartActivity",
        )),
        ASUS(listOf("asus"), listOf(
            "com.asus.mobilemanager/com.asus.mobilemanager.autostart.AutoStartActivity",
        )),
        OTHER(emptyList()),
    }

    /** Производитель не меняется — определяем один раз. */
    private val vendor: Vendor by lazy {
        val m = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
        Vendor.entries.firstOrNull { v -> v.marks.any { it in m } } ?: Vendor.OTHER
    }

    fun vendor(): Vendor = vendor

    /** Есть ли у этой прошивки свой «Автозапуск». */
    fun hasAutostart(): Boolean = vendor.autostart.isNotEmpty()

    /** Открыть «Автозапуск» прошивки; не вышло — «О приложении». */
    fun openAutostart(context: Context) {
        val opened = vendor.autostart.any { name ->
            ComponentName.unflattenFromString(name)?.let { start(context, Intent().setComponent(it)) } == true
        }
        if (!opened) openAppDetails(context)
    }

    private fun start(context: Context, intent: Intent): Boolean = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}
