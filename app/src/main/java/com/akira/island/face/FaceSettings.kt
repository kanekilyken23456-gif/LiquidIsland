package com.akira.island.face

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.akira.island.core.IslandBus
import com.akira.island.core.tr
import com.akira.island.data.SettingsRepo
import com.akira.island.ui.*
import com.akira.island.ui.screens.SettingsHint
import kotlinx.coroutines.delay

/** Permission requests are explicit user actions, never a side-effect of opening settings. */
@Composable
fun FaceSettings(enabled: Boolean, onEnabled: (Boolean) -> Unit) {
    val runtime by FaceUnlockState.availability.collectAsState()
    val service by IslandBus.serviceRunning.collectAsState()
    val revision by FaceUnlockState.revision.collectAsState()
    val settings by SettingsRepo.state.collectAsState()
    val powerSave by IslandBus.powerSave.collectAsState()
    var example by remember { mutableIntStateOf(0) }
    var demo by remember { mutableStateOf(FacePhase.HIDDEN) }
    val access=remember(revision,runtime,service,enabled) { if(enabled) FaceUnlockState.access() else FaceAvailability.OFF }
    val status=when {
        !enabled -> tr("Выключено", "Off")
        !service -> tr("Включите остров на Главной", "Turn on the island on Home")
        access == FaceAvailability.ROOT_UNAVAILABLE -> tr("Root-доступ не выдан. Разрешите его в менеджере root и нажмите «Проверить»", "Root access was not granted. Allow it in your root manager, then tap Check")
        access == FaceAvailability.PERMISSION_NEEDED -> tr("Ожидаем ответ менеджера root. Разрешите доступ для Dynamic Island", "Waiting for your root manager. Allow access for Dynamic Island")
        runtime == FaceAvailability.PAUSED -> tr("Приостановлено: экран выключен", "Paused while the screen is off")
        runtime == FaceAvailability.EVENTS_SEEN -> tr("События лица обнаружены. Проверьте вход лицом, PIN и отпечатком", "Face events detected. Test face, PIN and fingerprint unlock")
        runtime == FaceAvailability.SOURCE_UNAVAILABLE -> tr(
            "Источник событий не ответил. Нажмите «Проверить» для переподключения.",
            "The event source did not respond. Tap Check to reconnect.",
        )
        runtime == FaceAvailability.CONNECTING -> tr("Подключаемся к источнику событий", "Connecting to the event source")
        else -> tr(
            "Ожидаем события лица. Заблокируйте телефон и разблокируйте его лицом. Если статус не изменится, прошивка может не передавать нужные события.",
            "Waiting for face events. Lock the phone and unlock with your face. If the status stays the same, the firmware may not expose the required events.",
        )
    }
    SectionHeader(tr("Распознавание лица", "Face recognition"))
    GroupToggle(Pos.Single,Icons.Rounded.Face,tr("Face ID · только лицо", "Face ID · face only"),
        tr("Анимация при подтверждённой разблокировке лицом. Требуется root", "Animation for verified face unlock. Requires root"), enabled,color=Tile.Indigo) { onEnabled(it) }
    SettingsHint(status)
    if (enabled) {
        SettingsHint(tr("Работает через root (Magisk, KernelSU, APatch), Shizuku не нужен. События лица читаются из системного журнала только для чтения. При неизвестном способе входа эффект не показывается. Общая плашка «Разблокировка устройства» при этом режиме не используется.",
            "Works through root (Magisk, KernelSU, APatch); Shizuku is not needed. Face events are read from the system log, read-only. Unknown unlock methods show no effect. The general Device unlock banner is suppressed in this mode."))
        SettingsHint(tr(
            "Для обычной анимации включите «Разблокировка устройства» и выключите режим «только лицо».",
            "For the general animation, enable Device unlock and switch off face-only mode.",
        ))
        TextButton(onClick={ FaceUnlockState.reconnect() }) {
            Text(if(access == FaceAvailability.ROOT_UNAVAILABLE || access == FaceAvailability.PERMISSION_NEEDED) tr("Запросить root", "Request root") else tr("Проверить", "Check"))
        }
    }
    TextButton(onClick={example++},modifier=Modifier.testTag("face-preview")) { Text(tr("Пример анимации", "Preview animation")) }
    LaunchedEffect(example) {
        if(example==0) return@LaunchedEffect
        demo=FacePhase.SCANNING; delay(1_400); demo=FacePhase.SUCCESS; delay(1_200); demo=FacePhase.HIDDEN
    }
    fun closeExample() { example=0; demo=FacePhase.HIDDEN }
    if(demo!=FacePhase.HIDDEN) AlertDialog(
        onDismissRequest=::closeExample,
        title={ Text(tr("Пример анимации", "Preview animation")) },
        text={ Row(Modifier.fillMaxWidth().testTag("face-demo"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            FaceUnlockCard(demo,settings.forPowerSave(powerSave).reduceEffects)
            Text(tr("Демонстрация, не реальная проверка лица", "Preview, not an actual face scan"),color=Ui.Sub,modifier=Modifier.weight(1f))
        } },
        confirmButton={ TextButton(onClick=::closeExample) { Text(tr("Закрыть", "Close")) } },
    )
}
