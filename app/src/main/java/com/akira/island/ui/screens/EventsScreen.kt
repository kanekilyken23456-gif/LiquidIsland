package com.akira.island.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.akira.island.core.Notice
import com.akira.island.core.NoticeEffect
import com.akira.island.core.NoticeStyle
import com.akira.island.core.Tints
import com.akira.island.service.ConnectionEvent
import com.akira.island.service.SystemEventObservation
import com.akira.island.ui.*


import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.AirplanemodeInactive
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.PowerOff
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.UsbOff
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.akira.island.core.tr
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import com.akira.island.ui.Group
import com.akira.island.ui.GroupToggle
import com.akira.island.ui.Page
import com.akira.island.ui.Pos
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.Tile

/** «События»: короткие плашки о том, что произошло, — по разделам. */
@Composable
fun EventsScreen(onBack: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    val readings by SystemEventObservation.state.collectAsState()
    val hotspot by com.akira.island.service.HotspotMonitor.state.collectAsState()
    var example by remember { mutableStateOf(ConnectionEvent.VPN.notice(true)) }
    fun set(block: IslandSettings.() -> IslandSettings) {
        val before = SettingsRepo.current
        SettingsRepo.update(block)
        example = eventExample(before, SettingsRepo.current) ?: example
    }

    Page(tr("События", "Events"), onBack, preview = { EventExamplePreview(example) }) {
        if (!readings.observing) SettingsHint(tr("Чтобы получать реальные события, включите остров на Главной. Здесь можно посмотреть примеры без изменения настроек телефона.",
            "Turn on the island on Home to receive real events. You can view examples here without changing your phone settings."))
        SectionHeader(tr("Показ событий", "Event display"))
        Group {
            GroupSlider(Pos.First, Icons.Rounded.Timer, tr("Время на экране", "Time on screen"), s.noticeDurationMs / 1000f, 1.5f..10f,
                tr("%.1f с", "%.1f s").format(s.noticeDurationMs / 1000f), color = Tile.Orange) { v -> SettingsRepo.update { copy(noticeDurationMs = (v * 2).toInt() * 500) } }
            GroupSlider(Pos.Last, Icons.Rounded.HourglassTop, tr("Задержка перед показом", "Delay before showing"), s.noticeDelayMs / 1000f, 0f..3f,
                tr("%.1f с", "%.1f s").format(s.noticeDelayMs / 1000f), color = Tile.Gray) { v -> SettingsRepo.update { copy(noticeDelayMs = (v * 10).toInt() * 100) } }
        }
        SettingsHint(tr("Для коротких системных событий. Время сообщений настраивается отдельно в «Уведомлениях».", "For short system events. Message duration is set separately in Notifications."))
        SectionHeader(tr("Батарея", "Battery"))
        Group {
            GroupToggle(Pos.First, Icons.Rounded.Bolt, tr("Зарядка началась", "Charging started"), null,
                s.evChargeStart, color = Tile.Green) { set { copy(evChargeStart = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.PowerOff, tr("Зарядка остановлена", "Charging stopped"), null,
                s.evChargeStop, color = Tile.Gray) { set { copy(evChargeStop = it) } }
            GroupToggle(Pos.Last, Icons.Rounded.BatteryAlert, tr("Низкий заряд", "Low battery"),
                tr("Когда заряд батареи падает до 5%", "When the battery drops to 5%"),
                s.evLowBattery, color = Tile.Red) { set { copy(evLowBattery = it) } }
        }

        SectionHeader(tr("Подключения", "Connections"))
        Group {
            GroupToggle(Pos.First, Icons.Rounded.Wifi, tr("Wi-Fi подключён", "Wi-Fi connected"), null,
                s.evWifiOn, color = Tile.Blue) { set { copy(evWifiOn = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.WifiOff, tr("Wi-Fi отключён", "Wi-Fi disconnected"), null,
                s.evWifiOff, color = Tile.Gray) { set { copy(evWifiOff = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.SignalCellularAlt, tr("Мобильные данные включены", "Mobile data on"), null,
                s.evCellularOn, color = Tile.Green) { set { copy(evCellularOn = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.SignalCellularOff, tr("Мобильные данные выключены", "Mobile data off"), null,
                s.evCellularOff, color = Tile.Gray) { set { copy(evCellularOff = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.Bluetooth, tr("Bluetooth включён", "Bluetooth on"), null,
                s.evBluetoothOn, color = Tile.Blue) { set { copy(evBluetoothOn = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.BluetoothDisabled, tr("Bluetooth выключен", "Bluetooth off"), null,
                s.evBluetoothOff, color = Tile.Gray) { set { copy(evBluetoothOff = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.Headphones, tr("Наушники и колонки подключены", "Headphones and speakers connected"),
                tr("Название, значок устройства и заряд, если он известен. Заряд обновляется, пока карточка видна", "Name, device icon and battery when known. Battery updates while the card is visible"),
                s.evHeadsetOn, color = Tile.Indigo) { set { copy(evHeadsetOn = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.BluetoothDisabled, tr("Наушники и колонки отключены", "Headphones and speakers disconnected"), null,
                s.evHeadsetOff, color = Tile.Gray) { set { copy(evHeadsetOff = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.Watch, tr("Другие устройства Bluetooth", "Other Bluetooth devices"),
                tr("Часы, автомобиль, клавиатура и другие — при подключении и отключении. Только сопряжённые", "Watch, car, keyboard and more — when connecting or disconnecting. Paired devices only"),
                s.evBtOther, color = Tile.Indigo) { set { copy(evBtOther = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.Usb, tr("USB-устройство подключено", "USB device connected"), null,
                s.evUsbOn, color = Tile.Teal) { set { copy(evUsbOn = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.UsbOff, tr("USB-устройство отключено", "USB device disconnected"), null,
                s.evUsbOff, color = Tile.Gray) { set { copy(evUsbOff = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.VpnKey, "VPN", tr("Плашка при подключении и отключении", "Banner when connecting or disconnecting"),
                s.evVpn, color = Tile.Blue) { on -> set { copy(evVpn = on) } }
            GroupToggle(Pos.Last, Icons.Rounded.WifiTethering, tr("Точка доступа", "Hotspot"), tr("Карточка видна, пока точка доступа включена", "Card stays visible while the hotspot is on"),
                s.evHotspot, color = Tile.Teal) { on -> set { copy(evHotspot = on) } }
        }
        SectionHeader(tr("Примеры", "Examples"))
        EventExamples(readings) { example = it }
        SettingsHint(tr("Нажмите кнопку — плашка появится в примере сверху", "Tap a button to see the banner in the preview at the top"))
        if (s.evHotspot && hotspot.enabled == true) SettingsHint(hotspot.clients?.let {
            com.akira.island.core.trf("Подключено устройств: {0}", "Connected devices: {0}", it)
        } ?: tr("Число подключённых устройств недоступно", "Connected device count is unavailable"))
        val hooked by com.akira.island.core.IslandBus.hookConnected.collectAsState()
        if (readings.observing && readings.hotspot == null && !hooked) SettingsHint(tr("Эта прошивка пока не передала состояние точки доступа. Если оно недоступно Android-приложениям, реальные плашки для неё не появятся.",
            "This phone has not reported its hotspot state. If the firmware does not expose it to apps, real hotspot banners are unavailable."))

        SectionHeader(tr("Режимы", "Modes"))
        Group {
            GroupToggle(Pos.First, Icons.Rounded.AirplanemodeActive, tr("Режим полёта включён", "Airplane mode on"), null,
                s.evAirplaneOn, color = Tile.Orange) { set { copy(evAirplaneOn = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.AirplanemodeInactive, tr("Режим полёта выключен", "Airplane mode off"), null,
                s.evAirplaneOff, color = Tile.Gray) { set { copy(evAirplaneOff = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.DoNotDisturbOn, tr("Не беспокоить", "Do Not Disturb"),
                tr("Луна и состояние при переключении", "A moon and the state when it changes"),
                s.evDnd, color = Tile.Indigo) { set { copy(evDnd = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.Vibration, tr("Режим громкости", "Ring mode"),
                tr("Звонок, вибрация или без звука", "Ring, vibrate or silent"),
                s.evRinger, color = Tile.Pink) { set { copy(evRinger = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.BrightnessAuto, tr("Режим яркости", "Brightness mode"),
                tr("Когда включают или выключают адаптивную яркость", "When adaptive brightness is turned on or off"),
                s.evBrightness, color = Tile.Yellow) { set { copy(evBrightness = it) } }
            GroupToggle(Pos.Last, Icons.Rounded.Screenshot, tr("Снимок экрана", "Screenshot"),
                tr("Краткая плашка после создания снимка экрана", "Brief banner after a screenshot is taken"),
                s.evScreenshot, color = Tile.Purple) { set { copy(evScreenshot = it) } }
        }

        SectionHeader(tr("Индикаторы приватности", "Privacy indicators"))
        SettingsHint(tr("Как на iPhone и Android 12+: остров показывает, когда камера, микрофон или геолокация используются приложением.",
            "Like on iPhone and Android 12+: the island shows when the camera, microphone or location is used by an app."))
        Group {
            GroupToggle(Pos.First, Icons.Rounded.Videocam, tr("Камера", "Camera"),
                tr("Зелёный индикатор, когда приложение использует камеру", "Green indicator when an app uses the camera"),
                s.privacyCamera, color = Tile.Green) { set { copy(privacyCamera = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.Mic, tr("Микрофон", "Microphone"),
                tr("Оранжевый индикатор, когда приложение использует микрофон", "Orange indicator when an app uses the microphone"),
                s.privacyMic, color = Tile.Orange) { set { copy(privacyMic = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.LocationOn, tr("Геолокация", "Location"),
                tr("Синий индикатор, когда приложение использует местоположение", "Blue indicator when an app uses location"),
                s.privacyLocation, color = Tile.Blue) { set { copy(privacyLocation = it) } }
            GroupToggle(Pos.Last, Icons.Rounded.Apps, tr("Показывать приложение", "Show the app"),
                tr("Отображать название приложения, которое использует датчик", "Display the name of the app using the sensor"),
                s.privacyShowApp, color = Tile.Indigo) { set { copy(privacyShowApp = it) } }
        }

        SectionHeader(tr("Для разработчиков", "Developer options"))
        GroupToggle(Pos.Single, Icons.Rounded.DeveloperMode, tr("Отладка USB и Wi-Fi", "USB and wireless debugging"),
            tr("Сообщать об изменении режима отладки, а не о подключении кабеля", "Report debugging mode changes, not cable connections"),
            s.evDebugging, color = Tile.Gray) { on -> set { copy(evDebugging = on) } }

        SectionHeader(tr("Оплата", "Payments"))
        Group {
            GroupToggle(Pos.First, Icons.Rounded.Nfc, tr("Приложите к терминалу", "Hold near reader"),
                tr("Когда на экране оплата телефоном — Mir Pay, T-Pay, SberPay, Яндекс Пэй и другие. Держится, пока не заплатите или не выйдете из приложения",
                    "When a phone payment is on screen — Mir Pay, T-Pay, SberPay, Yandex Pay and others. Stays until you pay or leave the app"),
                s.evPayReady, color = Tile.Blue) { set { copy(evPayReady = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.AccountBalance, tr("Подсказка при открытии банка", "Hint when opening a bank"),
                tr("«К терминалу» сразу, как открыли банк или кошелёк, а не только на экране оплаты",
                    "Hold near reader as soon as you open a bank or wallet app, not only on the payment screen"),
                s.payHintOnOpen, color = Tile.Gray) { set { copy(payHintOnOpen = it) } }
            GroupToggle(Pos.Last, Icons.Rounded.CheckCircle, tr("Оплачено", "Paid"),
                tr("Галочка и сумма — с экрана кошелька, из уведомления или СМС банка",
                    "A check mark and the amount — from the wallet screen, a notification or a bank SMS"),
                s.evPaySuccess, color = Tile.Green) { set { copy(evPaySuccess = it) } }
        }

        com.akira.island.face.FaceSettings(s.faceUnlockOnly) { set { copy(faceUnlockOnly = it) } }

        SectionHeader(tr("Другое", "Other"))
        Group {
            GroupToggle(Pos.First, Icons.Rounded.Alarm, tr("Будильник поставлен", "Alarm set"),
                tr("Время подъёма и сколько осталось спать", "Wake-up time and how long you can sleep"),
                s.evAlarmSet, color = Tile.Orange) { set { copy(evAlarmSet = it) } }
            var offsetPicker by remember { mutableStateOf(false) }
            GroupItem(Pos.Middle, Icons.Rounded.Alarm, tr("Поправка времени будильника", "Alarm time adjustment"),
                alarmOffsetLabel(s.alarmDisplayOffsetMinutes), color = Tile.Orange, onClick = { offsetPicker = true })
            if (offsetPicker) com.akira.island.ui.ChoiceDialog(
                tr("Поправка времени будильника", "Alarm time adjustment"),
                (-120..120 step 15).toList(), s.alarmDisplayOffsetMinutes, ::alarmOffsetLabel,
                { minutes -> set { copy(alarmDisplayOffsetMinutes = minutes) } },
                { offsetPicker = false },
            )
            GroupToggle(Pos.Middle, Icons.Rounded.Lock, tr("Замок на экране блокировки", "Lock on the lock screen"),
                tr("Пока телефон заблокирован, в капсуле замок", "While the phone is locked, the capsule shows a lock"),
                s.evLock, color = Tile.Indigo) { set { copy(evLock = it) } }
            GroupToggle(Pos.Last, Icons.Rounded.LockOpen, tr("Разблокировка устройства", "Device unlock"),
                tr("Замок открывается, когда вы разблокируете телефон", "A lock opens when you unlock the phone"),
                s.evUnlock, color = Tile.Purple) { set { copy(evUnlock = it) } }
        }
        SettingsHint(tr(
            "Если время в острове расходится с приложением «Часы», укажите разницу. Поправка меняет отображение и отсчёт в острове; будильник Android звонит по своему расписанию.",
            "If the island and Clock show different times, set the difference. This adjusts the island display and countdown; Android keeps the alarm schedule.",
        ))
    }
}

private fun alarmOffsetLabel(minutes: Int): String =
    if (minutes == 0) tr("Как в системе", "System time")
    else "${if (minutes > 0) "+" else "−"}${kotlin.math.abs(minutes)} ${tr("мин", "min")}"

@Composable
private fun EventExamplePreview(example: Notice) {
    val s by SettingsRepo.state.collectAsState()
    var replay by remember { mutableIntStateOf(0) }
    var shown by remember { mutableStateOf(false) }
    var waiting by remember { mutableStateOf(false) }
    LaunchedEffect(example.id, s.noticeDurationMs, s.noticeDelayMs, replay) {
        shown = false; waiting = s.noticeDelayMs > 0
        kotlinx.coroutines.delay(s.noticeDelayMs.toLong())
        waiting = false; shown = true
        kotlinx.coroutines.delay(s.noticeDurationMs.toLong())
        shown = false
    }
    Column {
        SettingsPreview(event = example, eventShown = shown)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(if (waiting) tr("Ожидание перед показом…", "Waiting before showing…") else tr("Это пример, состояние телефона не меняется", "Preview only; phone state is unchanged"),
                color = Ui.Sub, fontSize = 12.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { replay++ }) { Text(tr("Повторить", "Replay")) }
        }
    }
}

private fun eventExample(old: IslandSettings, s: IslandSettings): Notice? {
    if (old.evVpn != s.evVpn) return ConnectionEvent.VPN.notice(s.evVpn)
    if (old.evHotspot != s.evHotspot) return ConnectionEvent.HOTSPOT.notice(s.evHotspot)
    if (old.evDebugging != s.evDebugging) return ConnectionEvent.USB_DEBUG.notice(s.evDebugging)
    if (old.evChargeStart != s.evChargeStart) return Notice(tr("Зарядка", "Charging"), "75%",
        Icons.Rounded.Bolt, Tints.Green, battery = 75, effect = NoticeEffect.CHARGING)
    if (old.evRinger != s.evRinger) return Notice("", tr("Вибрация", "Vibrate"), Icons.Rounded.Vibration,
        Tints.Purple, style = NoticeStyle.PILL, effect = NoticeEffect.RINGER)
    val change = listOf(
        Triple(old.evChargeStop != s.evChargeStop, tr("Зарядка остановлена", "Charging stopped"), Icons.Rounded.PowerOff),
        Triple(old.evLowBattery != s.evLowBattery, tr("Низкий заряд", "Low battery"), Icons.Rounded.BatteryAlert),
        Triple(old.evWifiOn != s.evWifiOn, "Wi-Fi", Icons.Rounded.Wifi),
        Triple(old.evWifiOff != s.evWifiOff, "Wi-Fi", Icons.Rounded.WifiOff),
        Triple(old.evCellularOn != s.evCellularOn, tr("Мобильные данные", "Mobile data"), Icons.Rounded.SignalCellularAlt),
        Triple(old.evCellularOff != s.evCellularOff, tr("Мобильные данные", "Mobile data"), Icons.Rounded.SignalCellularOff),
        Triple(old.evBluetoothOn != s.evBluetoothOn, "Bluetooth", Icons.Rounded.Bluetooth),
        Triple(old.evBluetoothOff != s.evBluetoothOff, "Bluetooth", Icons.Rounded.BluetoothDisabled),
        Triple(old.evHeadsetOn != s.evHeadsetOn, tr("Наушники", "Headphones"), Icons.Rounded.Headphones),
        Triple(old.evHeadsetOff != s.evHeadsetOff, tr("Наушники", "Headphones"), Icons.Rounded.BluetoothDisabled),
        Triple(old.evBtOther != s.evBtOther, tr("Часы", "Watch"), Icons.Rounded.Watch),
        Triple(old.evUsbOn != s.evUsbOn || old.evUsbOff != s.evUsbOff, "USB", Icons.Rounded.Usb),
        Triple(old.evAirplaneOn != s.evAirplaneOn || old.evAirplaneOff != s.evAirplaneOff, tr("Режим полёта", "Airplane mode"), Icons.Rounded.AirplanemodeActive),
        Triple(old.evDnd != s.evDnd, tr("Не беспокоить", "Do Not Disturb"), Icons.Rounded.DoNotDisturbOn),
        Triple(old.evBrightness != s.evBrightness, tr("Яркость", "Brightness"), Icons.Rounded.BrightnessAuto),
        Triple(old.evPayReady != s.evPayReady || old.payHintOnOpen != s.payHintOnOpen, tr("К терминалу", "Hold near reader"), Icons.Rounded.Nfc),
        Triple(old.evPaySuccess != s.evPaySuccess, tr("Оплачено", "Paid"), Icons.Rounded.CheckCircle),
        Triple(old.evAlarmSet != s.evAlarmSet, tr("Будильник", "Alarm"), Icons.Rounded.Alarm),
        Triple(old.evLock != s.evLock || old.evUnlock != s.evUnlock, tr("Блокировка", "Lock"), Icons.Rounded.Lock),
    ).firstOrNull { it.first } ?: return null
    return Notice(change.second, tr("Пример", "Example"), change.third, Tints.Blue)
}
