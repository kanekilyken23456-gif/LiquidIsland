package com.akira.island.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.akira.island.core.IslandBus
import com.akira.island.core.Tints
import com.akira.island.core.tr
import com.akira.island.core.trf
import com.akira.island.data.SettingsRepo
import com.akira.island.service.NotificationParser
import com.akira.island.service.SystemBanners
import com.akira.island.ui.Group
import com.akira.island.ui.GroupItem
import com.akira.island.ui.Page
import com.akira.island.ui.Perms
import com.akira.island.ui.PillAction
import com.akira.island.ui.Pos
import com.akira.island.ui.posOf
import com.akira.island.ui.Screen
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.Tile
import com.akira.island.ui.ToggleCard
import com.akira.island.ui.Ui
import kotlinx.coroutines.launch

/** Какие разрешения есть. Проверяются заново каждый раз, когда возвращаемся из системных настроек. */
private class PermState(
    val accessibility: Boolean, val notifications: Boolean, val calls: Boolean, val bluetooth: Boolean,
    val media: Boolean, val mediaNeeded: Boolean,
) {
    /** Чего не хватает — для подписи на главной */
    val missing: List<String>
        get() = listOfNotNull(
            tr("спец. возможности", "accessibility").takeIf { !accessibility },
            tr("уведомления", "notifications").takeIf { !notifications },
            tr("звонки", "calls").takeIf { !calls },
            "Bluetooth".takeIf { !bluetooth },
            tr("медиа", "media").takeIf { mediaNeeded && !media },
        )
}

/**
 * Значение, которое читается заново каждый раз, когда возвращаемся на экран (например, из настроек системы),
 * и функция «прочитать ещё раз».
 */
@Composable
internal fun <T> rememberOnResume(read: () -> T): Pair<T, () -> Unit> {
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    return remember(tick) { read() } to { tick++ }
}

/** Разрешения и функция «проверить ещё раз» — после ответа на системный запрос. */
@Composable
private fun rememberPermState(): Pair<PermState, () -> Unit> {
    val context = LocalContext.current
    return rememberOnResume {
        PermState(
            Perms.accessibility(context), Perms.notifications(context), Perms.calls(context), Perms.bluetooth(context),
            Perms.media(context),
            // Медиа нужно, только пока включена плашка «Снимок экрана»
            mediaNeeded = SettingsRepo.state.value.evScreenshot,
        )
    }
}

/**
 * «Главная»: живой пример, включён ли остров, разрешения и проверка. Всё, что настраивается, —
 * во вкладках «Функции» и «Оформление», версия и помощь — в «О приложении».
 */
@Composable
fun HomeScreen(open: (Screen) -> Unit) {
    val context = LocalContext.current
    val s by SettingsRepo.state.collectAsState()
    val running by IslandBus.serviceRunning.collectAsState()
    val (perms, _) = rememberPermState()
    val missing = perms.missing

    Page(tr("Динамический остров", "Dynamic Island")) {
        IslandPreview()

        // Без службы спец. возможностей острова нет вовсе — это первый шаг
        if (!perms.accessibility) {
            StartCard(onOpen = { Perms.openAccessibility(context) }, onHelp = { open(Screen.INSTRUCTION) })
        }

        // Сообщение пришло и в остров, и своей плашкой мессенджера — подсказываем один раз, как убрать вторую
        val popUp by SystemBanners.popUp.collectAsState()
        val doubled = popUp.filterValues { it }.keys.firstOrNull()
        if (doubled != null && !s.bannerHintHidden) {
            DoubleBannerCard(
                NotificationParser.appLabel(context, doubled),
                onFix = { open(Screen.BANNERS) },
                onHide = { SettingsRepo.update { copy(bannerHintHidden = true) } },
            )
        }

        ToggleCard(
            Icons.Rounded.PowerSettingsNew,
            tr("Остров включён", "Island on"),
            when {
                !perms.accessibility -> tr("Сначала включите службу в спец. возможностях", "First turn on the service in Accessibility")
                running -> tr("Капсула показывается над камерой", "The capsule shows over the camera")
                else -> tr("Служба запускается… если долго — выключите и снова включите её в спец. возможностях",
                    "The service is starting… if it takes long, turn it off and on again in Accessibility")
            },
            s.enabled, color = Tile.Green,
        ) { v -> SettingsRepo.update { copy(enabled = v) } }

        Group {
            GroupItem(
                Pos.First, Icons.Rounded.VerifiedUser, tr("Разрешения", "Permissions"),
                if (missing.isEmpty()) tr("Всё включено", "All granted") else tr("Нужно включить: ", "Needed: ") + missing.joinToString(", "),
                color = if (missing.isEmpty()) Tile.Green else Tile.Orange,
                onClick = { open(Screen.PERMISSIONS) },
            ) { if (missing.isEmpty()) Arrow() else CountBadge(missing.size) }
            // На виду: на Xiaomi, Oppo, Samsung остров иначе пропадает, и люди не знают почему
            KeepAliveItem(Pos.Middle, open)
            GroupItem(
                Pos.Last, Icons.Rounded.Science, tr("Проверка", "Try it"),
                tr("Событие, сообщение, звонок, оплата — прямо в острове", "An event, message, call, payment — right in the island"),
                color = Tile.Purple, onClick = { open(Screen.TESTS) },
            ) { Arrow() }
        }
    }
}

/** Первый шаг на главной: пока служба спец. возможностей выключена. */
@Composable
private fun StartCard(onOpen: () -> Unit, onHelp: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Ui.Primary.copy(alpha = 0.14f))
            .border(1.dp, Ui.Primary.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
            .padding(20.dp),
    ) {
        Text(tr("Включите остров", "Turn on the island"), color = Ui.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            tr(
                "Капсулу рядом с камерой может рисовать только служба спец. возможностей. " +
                    "Откройте «Спец. возможности» → «Динамический остров» и включите её.",
                "Only an accessibility service can draw the capsule next to the camera. Open Accessibility → Dynamic " +
                    "Island and turn it on.",
            ),
            color = Ui.Sub, fontSize = 14.sp, lineHeight = 20.sp,
        )
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.fillMaxWidth().clip(CircleShape).background(Ui.Primary).clickable(onClick = onOpen).padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text(tr("Открыть спец. возможности", "Open Accessibility"), color = Ui.OnPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        // Android 13+ не даёт включить службу приложению не из Play Маркета, пока не разрешишь «ограниченные настройки»
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Spacer(Modifier.height(16.dp))
            Text(restrictedHelp(), color = Ui.Sub, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().clip(CircleShape).border(1.dp, Ui.Primary, CircleShape)
                    .clickable { Perms.openAppDetails(context) }.padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(tr("Открыть «О приложении»", "Open App info"), color = Ui.Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Box(
            Modifier.fillMaxWidth().padding(top = 4.dp).clip(CircleShape).clickable(onClick = onHelp).padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(tr("Переключатель серый? Инструкция", "Switch grayed out? Instructions"), color = Ui.Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** Android 13+ не даёт включить службу приложению не из Play Маркета, пока не разрешишь «ограниченные настройки». */
internal fun restrictedHelp(): String = tr(
    "Android пишет «Ограниченная настройка» или «Доступ запрещён»? Так он защищает приложения не из Play Маркета:\n" +
        "1. Попробуйте включить службу один раз — появится это окно, закройте его.\n" +
        "2. Откройте «О приложении» → «⋮» справа вверху → «Разрешить ограниченные настройки».\n" +
        "3. Вернитесь в «Спец. возможности» и включите остров.",
    "Android says “Restricted setting” or “App was denied access”? That's how it protects apps not from Google Play:\n" +
        "1. Try to turn the service on once — that window shows up, close it.\n" +
        "2. Open App info → “⋮” in the top right → “Allow restricted settings”.\n" +
        "3. Go back to Accessibility and turn the island on.",
)

/** «Сообщения приходят дважды?» — когда у мессенджера ещё всплывает своя плашка поверх острова. */
@Composable
private fun DoubleBannerCard(app: String, onFix: () -> Unit, onHide: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Ui.Card).padding(20.dp),
    ) {
        Text(tr("Сообщения приходят дважды?", "Messages show up twice?"), color = Ui.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            trf(
                "Сообщение показывает и остров, и сам {0} своей плашкой. Выключите у него «Всплывающие уведомления» — останется только остров, а в шторке уведомление сохранится.",
                "Both the island and {0} itself show the message. Turn off its “Pop on screen” — only the island will remain, and the notification stays in the shade.", app,
            ),
            color = Ui.Sub, fontSize = 14.sp, lineHeight = 20.sp,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillAction(tr("Настроить", "Set up"), onClick = onFix)
            PillAction(tr("Скрыть", "Hide"), onClick = onHide)
        }
    }
}

/** «Разрешения»: что острову нужно от системы и зачем. */
@Composable
fun PermissionsScreen(onBack: () -> Unit, open: (Screen) -> Unit) {
    val context = LocalContext.current
    val (perms, refresh) = rememberPermState()
    // Читаются заново при возврате из системных настроек (refresh меняет perms, а с ним и этот блок)
    val (activityOk, writeOk, dndOk, alarmsOk) = remember(perms) {
        listOf(Perms.activityRecognition(context), Perms.writeSettings(context), Perms.dnd(context), Perms.exactAlarms(context))
    }
    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    val activityLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh()
        if (!granted && !Perms.activityRecognition(context)) Perms.openAppDetails(context)
    }
    val mediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh()
        // Если система больше не показывает запрос — открываем настройки приложения
        if (!granted && !Perms.media(context)) Perms.openAppDetails(context)
    }
    val callLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        refresh()
        // Если система больше не показывает запрос — открываем настройки приложения
        if (result.values.none { it } && !Perms.calls(context)) Perms.openAppDetails(context)
    }

    Page(tr("Разрешения", "Permissions"), onBack,
        subtitle = tr("Остров не хранит и не отправляет ваши уведомления, звонки и экраны: разрешения нужны, только чтобы показывать события",
            "The island doesn't store or send your notifications, calls or screens: permissions are only used to show events")) {
        Group {
            GroupItem(
                Pos.First, Icons.Rounded.AccessibilityNew, tr("Спец. возможности", "Accessibility"),
                if (perms.accessibility) tr("Включено — остров рисуется поверх строки состояния", "On — the island draws over the status bar")
                else tr("Нужно включить: без этого острова не будет", "Needed: without it there's no island"),
                onClick = { Perms.openAccessibility(context) }, color = Tile.Blue,
            ) { Status(perms.accessibility) }
            GroupItem(
                Pos.Middle, Icons.Rounded.Notifications, tr("Доступ к уведомлениям", "Notification access"),
                if (perms.notifications) tr("Включено — музыка, сообщения, прогресс и таймеры", "On — music, messages, progress and timers")
                else tr("Нужен для музыки, сообщений, прогресса и таймеров", "Needed for music, messages, progress and timers"),
                onClick = { Perms.openNotifications(context) }, color = Tile.Red,
            ) { Status(perms.notifications) }
            GroupItem(
                Pos.Middle, Icons.Rounded.Call, tr("Звонки", "Calls"),
                if (perms.calls) tr("Входящий вызов, имя из контактов, ответ кнопкой", "Incoming calls, names from contacts, answer button")
                else tr("Чтобы остров показывал входящие звонки", "So the island shows incoming calls"),
                onClick = { if (perms.calls) Perms.openAppDetails(context) else callLauncher.launch(Perms.CALL_PERMISSIONS) },
                color = Tile.Green,
            ) { Status(perms.calls) }
            GroupItem(
                Pos.Middle, Icons.Rounded.Bluetooth, "Bluetooth",
                if (perms.bluetooth) tr("Названия и заряд устройств при подключении", "Device names and battery when they connect")
                else tr("Чтобы видеть подключение устройств Bluetooth", "To see Bluetooth devices connect"),
                onClick = {
                    if (!perms.bluetooth && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) btLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    else Perms.openAppDetails(context)
                },
                color = Tile.Indigo,
            ) { Status(perms.bluetooth) }
            GroupItem(
                Pos.Last, Icons.Rounded.Screenshot, tr("Медиа", "Media"),
                if (perms.media) tr("Плашка после снимка экрана — остров видит новые изображения", "Screenshot banner — the island sees new images")
                else tr("Чтобы остров показывал плашку после снимка экрана", "So the island shows a banner after a screenshot"),
                onClick = { if (perms.media) Perms.openAppDetails(context) else mediaLauncher.launch(Perms.MEDIA_PERMISSION) },
                color = Tile.Purple,
            ) { Status(perms.media) }
        }

        // Остальные разрешения: их можно выдать здесь, а что выдаётся само — показано ниже
        SectionHeader(tr("Другие разрешения", "Other permissions"))
        val special = buildList {
            add(SpecialPerm(
                Icons.AutoMirrored.Rounded.DirectionsWalk, tr("Физическая активность", "Physical activity"),
                if (activityOk) tr("Шагомер: шаги за день в острове", "Step counter: daily steps in the island")
                else tr("Чтобы остров считал ваши шаги", "So the island can count your steps"),
                Tile.Green, activityOk,
            ) { if (activityOk) Perms.openAppDetails(context) else activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) })
            add(SpecialPerm(
                Icons.Rounded.BrightnessMedium, tr("Изменение настроек системы", "Modify system settings"),
                if (writeOk) tr("Ползунок яркости в раскрытом острове", "Brightness slider in the expanded island")
                else tr("Нужно для ползунка яркости", "Needed for the brightness slider"),
                Tile.Orange, writeOk,
            ) { Perms.openWriteSettings(context) })
            add(SpecialPerm(
                Icons.Rounded.DoNotDisturbOn, tr("Доступ к «Не беспокоить»", "Do Not Disturb access"),
                if (dndOk) tr("Кнопки «Режим звука» и «Не беспокоить»", "Ring mode and Do Not Disturb buttons")
                else tr("Нужен для кнопок «Режим звука» и «Не беспокоить»", "Needed for the Ring mode and Do Not Disturb buttons"),
                Tile.Red, dndOk,
            ) { Perms.openDnd(context) })
            if (Perms.needsExactAlarmGrant()) add(SpecialPerm(
                Icons.Rounded.Alarm, tr("Будильники и напоминания", "Alarms and reminders"),
                if (alarmsOk) tr("Таймер будит телефон к своему окончанию", "The timer wakes the phone when it ends")
                else tr("Нужно, чтобы таймер сработал вовремя", "Needed so the timer rings on time"),
                Tile.Yellow, alarmsOk,
            ) { Perms.openExactAlarms(context) })
        }
        Group {
            special.forEachIndexed { i, p ->
                GroupItem(posOf(i, special.size), p.icon, p.title, p.subtitle, color = p.color, onClick = p.onClick) { Status(p.granted) }
            }
        }

        SectionHeader(tr("Выдаются автоматически", "Granted automatically"))
        val automatic = listOf(
            Triple(Icons.Rounded.Language, tr("Интернет", "Internet"), tr("Погода и тексты песен", "Weather and lyrics")),
            Triple(Icons.Rounded.NetworkCheck, tr("Состояние сети", "Network state"), tr("Скорость сети и события подключения", "Network speed and connection events")),
            Triple(Icons.Rounded.Vibration, tr("Вибрация", "Vibration"), tr("Отклик на нажатия и сигналы", "Feedback for taps and alerts")),
            Triple(Icons.AutoMirrored.Rounded.VolumeUp, tr("Настройки звука", "Audio settings"), tr("Громкость и режим звука", "Volume and ring mode controls")),
            Triple(Icons.Rounded.Timer, tr("Будильники и таймеры", "Set alarms and timers"), tr("Создание таймеров и будильников в часах", "Create timers and alarms in the clock app")),
            Triple(Icons.Rounded.LightMode, tr("Включение экрана", "Wake the screen"), tr("Включает экран при звонке и таймере", "Turns the screen on for calls and timers")),
            Triple(Icons.Rounded.Layers, tr("Работа в фоне", "Background service"), tr("Не даёт системе выгрузить остров", "Keeps the island from being unloaded")),
            Triple(Icons.Rounded.RestartAlt, tr("Запуск после перезагрузки", "Start after reboot"), tr("Возвращает остров после перезагрузки телефона", "Restores the island after the phone restarts")),
        )
        Group {
            automatic.forEachIndexed { i, (icon, title, caption) ->
                GroupItem(posOf(i, automatic.size), icon, title, caption, color = Tile.Gray) { Status(true) }
            }
        }

        SectionHeader(tr("Если не включается", "If it doesn't turn on"))
        Group {
            GroupItem(Pos.First, Icons.Rounded.Lightbulb, tr("Инструкция", "Instructions"),
                tr("Серый переключатель на Android 13+, батарея и автозапуск", "Grayed-out switch on Android 13+, battery and autostart"),
                onClick = { open(Screen.INSTRUCTION) }, color = Tile.Yellow) { Arrow() }
            KeepAliveItem(Pos.Middle, open)
            AppDetailsItem(Pos.Last)
        }
    }
}

/** Разрешение, которое выдают отдельно: значок, подпись, выдано ли и что делает нажатие. */
private class SpecialPerm(
    val icon: ImageVector, val title: String, val subtitle: String, val color: Color,
    val granted: Boolean, val onClick: () -> Unit,
)

@Composable
internal fun Status(ok: Boolean) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (ok) Tints.Green.copy(alpha = 0.18f) else Tints.Orange.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(if (ok) tr("Да", "Yes") else tr("Нет", "No"), color = if (ok) Tints.Green else Tints.Orange, fontSize = 13.sp)
    }
}

/** Сколько разрешений не хватает — оранжевый кружок с числом. */
@Composable
private fun CountBadge(n: Int) {
    Box(Modifier.size(26.dp).clip(CircleShape).background(Tile.Orange), contentAlignment = Alignment.Center) {
        Text("$n", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Arrow() {
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Ui.Sub)
}

/** Живой пример капсулы с текущими настройками: толщина, боковые части, цвет и контур. */
@Composable
fun IslandPreview(expanded: Boolean = false) {
    SettingsPreview(card = expanded)
}
