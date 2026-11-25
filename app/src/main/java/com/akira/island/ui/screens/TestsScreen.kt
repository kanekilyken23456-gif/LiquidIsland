package com.akira.island.ui.screens

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Adb
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.R
import com.akira.island.core.AlarmInfo
import com.akira.island.core.AppMessage
import com.akira.island.core.BatteryInfo
import com.akira.island.core.ChronoInfo
import com.akira.island.core.Controls
import com.akira.island.core.InboxItem
import com.akira.island.core.IslandBus
import com.akira.island.core.LiveKind
import com.akira.island.core.LyricLine
import com.akira.island.core.Lyrics
import com.akira.island.core.LyricsStatus
import com.akira.island.core.MediaInfo
import com.akira.island.core.MsgAction
import com.akira.island.core.Notice
import com.akira.island.core.NoticeEffect
import com.akira.island.core.NoticeStyle
import com.akira.island.core.PayReady
import com.akira.island.core.PrivacySensor
import com.akira.island.core.PrivacyUsage
import com.akira.island.core.ProgressInfo
import com.akira.island.core.RecordingKind
import com.akira.island.core.Tints
import com.akira.island.core.TorchState
import com.akira.island.core.TransferDetails
import com.akira.island.core.TransferDirection
import com.akira.island.core.tr
import com.akira.island.data.SettingsRepo
import com.akira.island.island.Ios
import com.akira.island.island.rememberHaptic
import com.akira.island.service.BtKind
import com.akira.island.service.ConnectionEvent
import com.akira.island.service.HeadsetBatteryTracker
import com.akira.island.service.HotspotMonitor
import com.akira.island.service.NotificationParser
import com.akira.island.service.bluetoothPowerNotice
import com.akira.island.service.btDeviceNotice
import com.akira.island.ui.ItemIcon
import com.akira.island.ui.Page
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.Tile
import com.akira.island.ui.Ui
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Пример для «Проверки»: значок, название, что увидите, и что делает нажатие. */
private class Demo(val icon: ImageVector, val title: String, val caption: String, val color: Color, val run: () -> Unit)

/** Раздел «Проверки»: заголовок и его примеры. */
private class DemoSection(val title: String, val demos: List<Demo>)

/** «Проверка»: все примеры в одном месте — нажатие показывает событие или активность в настоящем острове. */
@Composable
fun TestsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = rememberHaptic()
    val sections = remember { demoSections(context) }
    Page(tr("Проверка", "Try it"), onBack,
        subtitle = tr("Нажмите — пример появится в острове наверху экрана", "Tap — the example shows up in the island at the top of the screen")) {
        sections.forEach { section ->
            SectionHeader(section.title)
            section.demos.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { d ->
                        DemoTile(d, Modifier.weight(1f).fillMaxHeight()) {
                            haptic()
                            d.run()
                        }
                    }
                    // Нечётный ряд: пустое место справа, чтобы последняя плитка не растягивалась на всю ширину
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DemoTile(d: Demo, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(Ui.Card).clickable(onClick = onClick).padding(16.dp)) {
        ItemIcon(d.icon, d.color, 40.dp)
        Spacer(Modifier.height(14.dp))
        Text(d.title, color = Ui.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
        Spacer(Modifier.height(2.dp))
        Text(d.caption, color = Ui.Sub, fontSize = 13.sp, lineHeight = 17.sp)
    }
}

// ───────────────────────── Вспомогательное ─────────────────────────

/** Несуществующее приложение-источник: пример не прячется правилами «скрывать, когда открыто приложение». */
private const val DEMO_PKG = "com.akira.island.demo"

/** Прежние значения того, что пример подменил в [IslandBus], и «поколение» подмены — чтобы поздний таймер не затирал новый пример. */
private val demoOriginal = HashMap<Any, Any?>()
private val demoGeneration = HashMap<Any, Int>()

/**
 * Показать в общем состоянии острова [value] на [ms] мс, потом вернуть то, что было до примера. Если за это время
 * состояние сменилось само (например, заиграла настоящая музыка), его не трогаем.
 */
private fun <T> MutableStateFlow<T>.showFor(value: T, ms: Long) {
    val flow = this
    val generation = (demoGeneration[flow] ?: 0) + 1
    demoGeneration[flow] = generation
    if (!demoOriginal.containsKey(flow)) demoOriginal[flow] = flow.value
    flow.value = value
    IslandBus.scope.launch {
        delay(ms)
        if (demoGeneration[flow] != generation) return@launch
        demoGeneration.remove(flow)
        @Suppress("UNCHECKED_CAST")
        val before = demoOriginal.remove(flow) as T
        if (flow.value == value) flow.value = before
    }
}

/** Два события подряд: остров покажет первое, потом второе — как при настоящем включении и выключении. */
private fun postBoth(first: Notice, second: Notice) {
    IslandBus.post(first)
    IslandBus.post(second)
}

/** Номер текущей пробной загрузки: новая отменяет прежнюю. */
private var transferRun = 0

private fun showTransfer(upload: Boolean) {
    val run = ++transferRun
    val key = if (upload) "demo-upload" else "demo-download"
    val title = if (upload) "video.mp4" else "backup.zip"
    val direction = if (upload) TransferDirection.UP else TransferDirection.DOWN
    val steps = 24
    val postTime = System.currentTimeMillis()
    IslandBus.scope.launch {
        for (i in 0..steps) {
            if (run != transferRun) return@launch
            IslandBus.progress.value = IslandBus.progress.value + (key to ProgressInfo(
                key = key, pkg = DEMO_PKG, title = title, icon = null,
                progress = i * 100 / steps, max = 100, indeterminate = false, postTime = postTime,
                transferDetails = TransferDetails(bytesPerSecond = 3_200_000.0, direction = direction, etaSeconds = (steps - i) / 2L),
            ))
            delay(500)
        }
        if (run == transferRun) IslandBus.progress.value = IslandBus.progress.value - key
    }
}

private fun showRecording(kind: RecordingKind) {
    val key = "demo-recording-${kind.name}"
    val now = System.currentTimeMillis()
    IslandBus.chrono.value = IslandBus.chrono.value + (key to ChronoInfo(
        key = key, pkg = DEMO_PKG, title = kind.title, icon = null,
        base = now, countDown = false, postTime = now, recordingKind = kind,
    ))
    IslandBus.scope.launch {
        delay(15_000)
        IslandBus.chrono.value = IslandBus.chrono.value - key
    }
}

private fun showMusic() {
    val key = "demo-music"
    val info = MediaInfo(
        key = key, pkg = DEMO_PKG,
        title = tr("Пример трека", "Sample track"), artist = tr("Исполнитель", "Artist"), album = null,
        art = null, accent = Tints.Purple,
        durationMs = 215_000L, positionMs = 0L, positionTime = SystemClock.elapsedRealtime(),
        speed = 1f, playing = true, canSeek = false,
    )
    val lyrics = Lyrics(key, listOf(
        LyricLine(0L, tr("Текст песни идёт прямо в острове", "Live lyrics play right in the island")),
        LyricLine(4_000L, tr("Строка за строкой, в такт музыке", "Line by line, in time with the music")),
        LyricLine(8_000L, tr("Открывать плеер не нужно", "No need to open the player")),
        LyricLine(12_000L, tr("Так выглядит живой текст", "This is how live lyrics look")),
    ))
    IslandBus.media.showFor(info, 16_000L)
    IslandBus.lyrics.showFor(lyrics, 16_000L)
    IslandBus.lyricsStatus.showFor(key to LyricsStatus.FOUND, 16_000L)
}

private fun showInbox(context: Context) {
    val icon = NotificationParser.icon(context, context.packageName)
    val now = System.currentTimeMillis()
    fun item(n: Int, title: String, text: String) = InboxItem(
        key = "demo-inbox-$n", pkg = DEMO_PKG, appName = tr("Остров", "Island"), title = title, text = text,
        avatar = null, appIcon = icon, intent = null, time = now + n, sensitive = false,
    )
    IslandBus.inbox.value = IslandBus.inbox.value.filterNot { it.key.startsWith("demo-inbox-") } + listOf(
        item(1, tr("Анна", "Anna"), tr("Как дела?", "How are you?")),
        item(2, tr("Анна", "Anna"), tr("Есть минутка?", "Got a minute?")),
        item(3, tr("Остров", "Island"), tr("Привет! Так выглядит уведомление в острове", "Hi! This is how a notification looks in the island")),
    )
    IslandBus.scope.launch {
        delay(20_000)
        IslandBus.inbox.value = IslandBus.inbox.value.filterNot { it.key.startsWith("demo-inbox-") }
    }
}

private fun showHotspotCard() {
    val before = HotspotMonitor.state.value
    // Настоящая точка доступа уже включена — карточка и так на экране, её состояние не подменяем
    if (before.enabled == true) return
    HotspotMonitor.onHook(true, 2)
    IslandBus.scope.launch {
        delay(12_000)
        val now = HotspotMonitor.state.value
        if (now.enabled == true && now.clients == 2) {
            if (before.enabled == false) HotspotMonitor.onHook(false, null) else HotspotMonitor.broadcastStarted()
        }
    }
}

// ───────────────────────── Сами примеры ─────────────────────────

private fun demoSections(context: Context): List<DemoSection> {
    fun ic(@DrawableRes id: Int) = Ios.load(context, id)
    fun showPrivacy(vararg sensors: PrivacySensor) {
        val label = tr("Остров", "Island")
        IslandBus.privacy.showFor(sensors.map { PrivacyUsage(it, DEMO_PKG, label) }, 8_000L)
    }

    val messages = DemoSection(tr("Сообщения и звонки", "Messages and calls"), listOf(
        Demo(Icons.Rounded.ChatBubble, tr("Сообщение", "Message"),
            tr("С кнопками «Ответить» и «Пометить прочитанным»", "With Reply and Mark as read buttons"), Tile.Blue) {
            // Пробные кнопки: «Ответить» с полем ввода, как у Telegram и WhatsApp
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val test = PendingIntent.getBroadcast(context, 0, Intent("com.dynamic.island.TEST").setPackage(context.packageName), flags)
            val input = RemoteInput.Builder("reply").setLabel(tr("Ответить", "Reply")).build()
            IslandBus.post(
                Notice(
                    tr("Анна", "Anna"), icon = ic(R.drawable.ios_chatbubble_ellipses), tint = Tints.Blue,
                    style = NoticeStyle.MESSAGE,
                    extraMs = 4_500,
                    message = AppMessage("test", context.packageName, tr("Остров", "Island"),
                        tr("Привет! Так выглядит уведомление в острове", "Hi! This is how a notification looks in the island"),
                        null, NotificationParser.icon(context, context.packageName), null,
                        actions = listOf(
                            MsgAction(tr("Ответить", "Reply"), test, listOf(input)),
                            MsgAction(tr("Пометить прочитанным", "Mark as read"), test),
                        ),
                        history = listOf(tr("Как дела?", "How are you?"), tr("Есть минутка?", "Got a minute?"),
                            tr("Привет! Так выглядит уведомление в острове", "Hi! This is how a notification looks in the island")),
                    ),
                ),
            )
        },
        Demo(Icons.Rounded.CheckCircle, tr("Ответ отправлен", "Reply sent"),
            tr("«Отправлено» с галочкой и именем", "“Sent” with a check mark and the name"), Tile.Blue) {
            IslandBus.post(
                Notice(
                    title = tr("Отправлено", "Sent"), state = tr("Анна", "Anna"),
                    icon = ic(R.drawable.ios_chatbubble_ellipses), tint = Tints.Blue,
                    style = NoticeStyle.SENT,
                    extraMs = -SettingsRepo.current.noticeDurationMs / 2L,
                ),
            )
        },
        Demo(Icons.Rounded.Call, tr("Входящий звонок", "Incoming call"), tr("Можно ответить и сбросить", "You can answer or decline"), Tile.Green) {
            Controls.showTestCall()
        },
        Demo(Icons.Rounded.PhoneInTalk, tr("Идёт разговор", "Call in progress"),
            tr("Время разговора и кнопка «Положить трубку»", "The call time and a hang-up button"), Tile.Green) {
            Controls.showTestCall(incoming = false)
        },
        Demo(Icons.Rounded.Inbox, tr("История уведомлений", "Notification history"),
            tr("Сообщения ждут в острове, пока их не прочитают", "Messages wait in the island until you read them"), Tile.Teal) {
            showInbox(context)
        },
    ))

    val time = DemoSection(tr("Время", "Time"), listOf(
        Demo(Icons.Rounded.HourglassBottom, tr("Таймер", "Timer"), tr("Обратный отсчёт одной минуты", "A one-minute countdown"), Tile.Orange) {
            Controls.startTimer(1)
        },
        Demo(Icons.Rounded.NotificationsActive, tr("Таймер звонит", "Timer rings"),
            tr("Таймер на 5 секунд, затем звонок", "A 5-second timer, then it rings"), Tile.Orange) {
            Controls.startTimerSeconds(5)
        },
        Demo(Icons.Rounded.Timer, tr("Секундомер", "Stopwatch"),
            tr("Идёт отсчёт, можно поставить на паузу", "Counting up, you can pause it"), Tile.Orange) {
            Controls.startStopwatch()
            val started = IslandBus.stopwatch.value
            IslandBus.scope.launch {
                delay(25_000)
                if (IslandBus.stopwatch.value === started) Controls.stopStopwatch()
            }
        },
        Demo(Icons.Rounded.Alarm, tr("Будильник", "Alarm"), tr("Время подъёма и сколько осталось спать", "Wake-up time and how long you can sleep"), Tile.Indigo) {
            IslandBus.post(
                Notice(
                    title = tr("Будильник", "Alarm"),
                    icon = ic(R.drawable.ios_alarm),
                    tint = Tints.Orange,
                    style = NoticeStyle.ALARM,
                    alarm = AlarmInfo(System.currentTimeMillis() + (8 * 60 + 15) * 60_000L, null, null),
                    extraMs = 2_000,
                ),
            )
        },
        Demo(Icons.Rounded.AlarmOn, tr("Ближайший будильник", "Upcoming alarm"),
            tr("Время подъёма, пока будильник близко", "The wake-up time while the alarm is near"), Tile.Indigo) {
            IslandBus.nextAlarm.showFor(AlarmInfo(System.currentTimeMillis() + (6 * 60 + 30) * 60_000L, null, null), 15_000L)
        },
    ))

    val media = DemoSection(tr("Музыка, запись и файлы", "Music, recording and files"), listOf(
        Demo(Icons.Rounded.MusicNote, tr("Музыка", "Music"),
            tr("Название, исполнитель и живой текст", "Title, artist and live lyrics"), Tile.Pink) {
            showMusic()
        },
        Demo(Icons.Rounded.FlashlightOn, tr("Фонарик", "Flashlight"),
            tr("Значок фонарика и уровень яркости", "The flashlight icon and its brightness level"), Tile.Yellow) {
            IslandBus.torch.showFor(TorchState(on = true, cameraId = null, level = 3, maxLevel = 5), 10_000L)
        },
        Demo(Icons.Rounded.FiberManualRecord, RecordingKind.SCREEN.title,
            tr("Идёт запись, время растёт", "Recording, the time keeps counting"), Tile.Red) {
            showRecording(RecordingKind.SCREEN)
        },
        Demo(Icons.Rounded.Mic, RecordingKind.VOICE.title,
            tr("Идёт запись, время растёт", "Recording, the time keeps counting"), Tile.Red) {
            showRecording(RecordingKind.VOICE)
        },
        Demo(Icons.Rounded.Videocam, RecordingKind.VIDEO.title,
            tr("Идёт запись, время растёт", "Recording, the time keeps counting"), Tile.Red) {
            showRecording(RecordingKind.VIDEO)
        },
        Demo(Icons.Rounded.CloudDownload, tr("Загрузка", "Download"),
            tr("Полоса прогресса, скорость и время до конца", "A progress bar, speed and time left"), Tile.Blue) {
            showTransfer(upload = false)
        },
        Demo(Icons.Rounded.CloudUpload, tr("Отправка файла", "Upload"),
            tr("Файл уходит, прогресс идёт до конца", "A file is sent, progress runs to the end"), Tile.Blue) {
            showTransfer(upload = true)
        },
    ))

    val live = DemoSection(tr("Живые активности и оплата", "Live activities and payments"), listOf(
        Demo(Icons.Rounded.Navigation, tr("Навигатор", "Navigation"), tr("Стрелка поворота и метры до неё", "The turn arrow and meters to it"), Tile.Purple) {
            Controls.showTestLive(context)
        },
        Demo(Icons.Rounded.LocalTaxi, tr("Такси", "Taxi"),
            tr("Сколько минут до машины", "How many minutes until the car arrives"), Tile.Yellow) {
            Controls.showTestLive(context, LiveKind.RIDE)
        },
        Demo(Icons.Rounded.ShoppingBag, tr("Доставка", "Delivery"),
            tr("Сколько минут до курьера", "How many minutes until the courier arrives"), Tile.Orange) {
            Controls.showTestLive(context, LiveKind.DELIVERY)
        },
        Demo(Icons.Rounded.Nfc, tr("Оплата", "Payment"), tr("«К терминалу», затем «Оплачено» с суммой", "Hold near reader, then Paid with the amount"), Tile.Teal) {
            // Как настоящая оплата: подсказка держится, пока ждём терминал, потом — «Оплачено»
            val pkg = context.packageName
            IslandBus.payReady.value = PayReady(pkg, NotificationParser.icon(context, pkg))
            IslandBus.scope.launch {
                delay(3000)
                if (IslandBus.payReady.value?.pkg == pkg) IslandBus.payReady.value = null
                IslandBus.post(
                    Notice(
                        tr("Оплачено", "Paid"), tr("1 250 ₽", "$12.50"), ic(R.drawable.ios_checkmark_circle), Tints.Green,
                        caption = tr("Остров", "Island"), style = NoticeStyle.PAY_DONE, extraMs = 900,
                    ),
                )
            }
        },
    ))

    val battery = DemoSection(tr("Батарея", "Battery"), listOf(
        Demo(Icons.Rounded.Bolt, tr("Зарядка", "Charging"), tr("Зарядка 75% во всю ширину", "Charging 75% at full width"), Tile.Green) {
            IslandBus.post(Notice(tr("Зарядка", "Charging"), "75%", ic(R.drawable.ios_flash), Tints.Green, battery = 75, effect = NoticeEffect.CHARGING))
        },
        Demo(Icons.Rounded.BatteryChargingFull, tr("Карточка зарядки", "Charging card"),
            tr("Мощность и время до полного заряда", "Power and time until fully charged"), Tile.Green) {
            IslandBus.battery.showFor(
                BatteryInfo(level = 75, charging = true, currentMa = 1850f, voltageV = 4.3f, powerWatts = 7.9f, remainingMs = 48 * 60_000L),
                12_000L,
            )
        },
        Demo(Icons.Rounded.BatteryAlert, tr("Отключено", "Unplugged"), tr("Зарядку отключили, заряд остался", "Unplugged, the charge level stays"), Tile.Orange) {
            IslandBus.post(Notice(tr("Отключено", "Unplugged"), "75%", ic(R.drawable.ios_battery_full), Tints.Orange, battery = 75))
        },
        Demo(Icons.Rounded.BatteryAlert, tr("Низкий заряд", "Low Battery"), tr("Когда заряд падает до 5%", "When the battery drops to 5%"), Tile.Red) {
            IslandBus.post(Notice(tr("Низкий заряд", "Low Battery"), "5%", ic(R.drawable.ios_battery_dead), Tints.Red, battery = 5))
        },
    ))

    val connections = DemoSection(tr("Подключения", "Connections"), listOf(
        Demo(Icons.Rounded.Wifi, "Wi-Fi", tr("Подключён, затем отключён", "Connected, then disconnected"), Tile.Blue) {
            postBoth(
                Notice("Wi-Fi", tr("Подключён", "Connected"), ic(R.drawable.ios_wifi), Tints.Blue),
                Notice("Wi-Fi", tr("Отключён", "Disconnected"), ic(R.drawable.ios_wifi), Tints.Gray),
            )
        },
        Demo(Icons.Rounded.SignalCellularAlt, tr("Мобильные данные", "Mobile data"), tr("Включены, затем выключены", "On, then off"), Tile.Green) {
            postBoth(
                Notice(tr("Мобильные данные", "Mobile data"), tr("Вкл", "On"), ic(R.drawable.ios_cellular), Tints.Green),
                Notice(tr("Мобильные данные", "Mobile data"), tr("Выкл", "Off"), ic(R.drawable.ios_cellular), Tints.Gray),
            )
        },
        Demo(Icons.Rounded.Bluetooth, "Bluetooth", tr("Включён, затем выключен", "On, then off"), Tile.Blue) {
            postBoth(bluetoothPowerNotice(true), bluetoothPowerNotice(false))
        },
        Demo(Icons.Rounded.Headphones, tr("Наушники", "Headphones"),
            tr("Название и кольцо заряда, затем отключение", "Name and battery ring, then disconnect"), Tile.Indigo) {
            postBoth(
                btDeviceNotice(BtKind.HEADPHONES, "AirPods Pro", true, HeadsetBatteryTracker.ring(75)),
                btDeviceNotice(BtKind.HEADPHONES, "AirPods Pro", false),
            )
        },
        Demo(Icons.Rounded.Speaker, tr("Колонка", "Speaker"), tr("Название колонки и её заряд", "The speaker name and its charge"), Tile.Indigo) {
            IslandBus.post(btDeviceNotice(BtKind.SPEAKER, "JBL Flip 6", true, HeadsetBatteryTracker.ring(60)))
        },
        Demo(Icons.Rounded.Watch, tr("Часы", "Watch"), tr("Часы подключились с зарядом", "A watch connects, with its charge"), Tile.Indigo) {
            IslandBus.post(btDeviceNotice(BtKind.WATCH, "Galaxy Watch", true, HeadsetBatteryTracker.ring(82)))
        },
        Demo(Icons.Rounded.DirectionsCar, tr("Автомобиль", "Car"), tr("Устройство без данных о заряде", "A device with no battery data"), Tile.Indigo) {
            IslandBus.post(btDeviceNotice(BtKind.CAR, null, true))
        },
        Demo(Icons.Rounded.Usb, "USB", tr("Подключено, затем отключено", "Connected, then disconnected"), Tile.Teal) {
            postBoth(
                Notice("USB", tr("Подключено", "Connected"), ic(R.drawable.ios_hardware_chip), Tints.Teal),
                Notice("USB", tr("Отключено", "Disconnected"), ic(R.drawable.ios_hardware_chip), Tints.Gray),
            )
        },
        Demo(Icons.Rounded.VpnKey, "VPN", tr("Включён, затем выключен", "On, then off"), Tile.Blue) {
            postBoth(ConnectionEvent.VPN.notice(true), ConnectionEvent.VPN.notice(false))
        },
        Demo(Icons.Rounded.WifiTethering, tr("Точка доступа", "Hotspot"), tr("Включена, затем выключена", "On, then off"), Tile.Teal) {
            postBoth(ConnectionEvent.HOTSPOT.notice(true), ConnectionEvent.HOTSPOT.notice(false))
        },
        Demo(Icons.Rounded.WifiTethering, tr("Карточка точки доступа", "Hotspot card"),
            tr("Остаётся в капсуле, пока точка доступа включена", "Stays in the capsule while the hotspot is on"), Tile.Teal) {
            showHotspotCard()
        },
        Demo(Icons.Rounded.Adb, tr("Отладка USB", "USB debugging"), tr("Режим отладки включён", "Debugging mode turned on"), Tile.Gray) {
            IslandBus.post(ConnectionEvent.USB_DEBUG.notice(true))
        },
        Demo(Icons.Rounded.Adb, tr("Отладка Wi-Fi", "Wireless debugging"), tr("Режим отладки включён", "Debugging mode turned on"), Tile.Gray) {
            IslandBus.post(ConnectionEvent.WIRELESS_DEBUG.notice(true))
        },
    ))

    val modes = DemoSection(tr("Режимы", "Modes"), listOf(
        Demo(Icons.Rounded.AirplanemodeActive, tr("Режим полёта", "Airplane Mode"), tr("Включён, затем выключен", "On, then off"), Tile.Orange) {
            postBoth(
                Notice(tr("Режим полёта", "Airplane Mode"), tr("Вкл", "On"), ic(R.drawable.ios_airplane), Tints.Orange),
                Notice(tr("Режим полёта", "Airplane Mode"), tr("Выкл", "Off"), ic(R.drawable.ios_airplane), Tints.Gray),
            )
        },
        Demo(Icons.Rounded.DoNotDisturbOn, tr("Не беспокоить", "Do Not Disturb"), tr("Луна и состояние при переключении", "A moon and the state when it changes"), Tile.Indigo) {
            postBoth(
                Notice(tr("Не беспокоить", "Do Not Disturb"), tr("Вкл", "On"), ic(R.drawable.ios_moon), Tints.Purple),
                Notice(tr("Не беспокоить", "Do Not Disturb"), tr("Выкл", "Off"), ic(R.drawable.ios_moon), Tints.Gray),
            )
        },
        Demo(Icons.Rounded.NotificationsOff, tr("Режим звука", "Ring mode"),
            tr("Капсула вздрагивает, колокольчик качается", "The capsule shakes, the bell swings"), Tile.Red) {
            // Как при переключателе: звонок → вибрация → без звука, плашка меняется на месте
            IslandBus.post(Notice("", tr("Звонок", "Ring"), ic(R.drawable.ios_notifications), Color.White, style = NoticeStyle.PILL, effect = NoticeEffect.RINGER))
            IslandBus.scope.launch {
                delay(1_600)
                IslandBus.post(Notice("", tr("Вибрация", "Vibrate"), ic(R.drawable.ios_phone_portrait), Tints.Purple, style = NoticeStyle.PILL, effect = NoticeEffect.RINGER))
                delay(1_600)
                IslandBus.post(Notice("", tr("Без звука", "Silent"), ic(R.drawable.ios_notifications_off), Tints.Red, style = NoticeStyle.PILL, effect = NoticeEffect.RINGER))
            }
        },
        Demo(Icons.Rounded.BrightnessAuto, tr("Яркость", "Brightness"), tr("Авто, затем вручную", "Auto, then manual"), Tile.Yellow) {
            postBoth(
                Notice(tr("Яркость", "Brightness"), tr("Авто", "Auto"), ic(R.drawable.ios_sunny), Tints.Yellow),
                Notice(tr("Яркость", "Brightness"), tr("Вручную", "Manual"), ic(R.drawable.ios_contrast), Tints.Yellow),
            )
        },
        Demo(Icons.Rounded.Screenshot, tr("Снимок экрана", "Screenshot"), tr("Краткая плашка после снимка", "A brief banner after a screenshot"), Tile.Purple) {
            IslandBus.post(Notice(tr("Снимок экрана", "Screenshot"), tr("Сохранён", "Saved"), Icons.Rounded.Screenshot, Tints.Purple))
        },
    ))

    val privacySection = DemoSection(tr("Приватность и замок", "Privacy and lock"), listOf(
        Demo(Icons.Rounded.Videocam, tr("Камера", "Camera"), tr("Зелёный индикатор и название приложения", "A green indicator and the app name"), Tile.Green) {
            showPrivacy(PrivacySensor.CAMERA)
        },
        Demo(Icons.Rounded.Mic, tr("Микрофон", "Microphone"), tr("Оранжевый индикатор и название приложения", "An orange indicator and the app name"), Tile.Orange) {
            showPrivacy(PrivacySensor.MICROPHONE)
        },
        Demo(Icons.Rounded.LocationOn, tr("Геолокация", "Location"), tr("Синий индикатор и название приложения", "A blue indicator and the app name"), Tile.Blue) {
            showPrivacy(PrivacySensor.LOCATION)
        },
        Demo(Icons.Rounded.Videocam, tr("Камера и микрофон", "Camera and microphone"),
            tr("Несколько индикаторов сразу, как в видеозвонке", "Several indicators at once, like in a video call"), Tile.Green) {
            showPrivacy(PrivacySensor.CAMERA, PrivacySensor.MICROPHONE)
        },
        Demo(Icons.Rounded.Lock, tr("Замок", "Lock"),
            tr("Замок в капсуле, как на экране блокировки", "A lock in the capsule, as on the lock screen"), Tile.Indigo) {
            IslandBus.keyguard.showFor(true, 4_000L)
        },
        Demo(Icons.Rounded.LockOpen, tr("Разблокировка", "Unlock"), tr("Замок открывается", "The lock opens"), Tile.Purple) {
            IslandBus.post(Notice("", null, ic(R.drawable.ios_lock_open), Color.White, style = NoticeStyle.PILL))
        },
    ))

    return listOf(messages, time, media, live, battery, connections, modes, privacySection)
}
