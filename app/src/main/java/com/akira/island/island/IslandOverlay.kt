package com.akira.island.island

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.updateTransition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.akira.island.core.BatteryInfo
import com.akira.island.core.ChronoInfo
import com.akira.island.core.Controls
import com.akira.island.core.IslandBus
import com.akira.island.core.Lyrics
import com.akira.island.core.NoticeEffect
import com.akira.island.core.NoticeStyle
import com.akira.island.core.Tints
import com.akira.island.core.tr
import com.akira.island.core.trf
import com.akira.island.data.usesCardSurface
import com.akira.island.data.BubbleSide
import com.akira.island.data.Elasticity
import com.akira.island.data.MessageStyle
import com.akira.island.data.ExpandStyle
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import com.akira.island.data.shouldTimeOutIdle
import com.akira.island.data.TapAction
import com.akira.island.service.CallMonitor
import com.akira.island.service.NavScreen
import com.akira.island.service.Inbox
import com.akira.island.service.PayMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

/** Пример активности для подбора ширины: стрелка направо и «300 м». */
private val WidthDemo = com.akira.island.core.LiveInfo(
    key = "width-demo", pkg = "", kind = com.akira.island.core.LiveKind.NAV, title = "", text = "", sub = "",
    icon = null, art = null, postTime = 0L, open = null, distance = tr("300 м", "300 m"), turn = com.akira.island.core.Turn.RIGHT,
)

/** Цвет острова на экране «Размер и положение»: его нужно спрятать под вырезом камеры. */
val CalibrationRed = Color(0xFFFF6B6B)

/** Запас вокруг острова в окне: под тень, «желе» и пузырь. */
const val ISLAND_PAD_DP = 12

/** Максимальный отступ карточки от строки состояния, dp (ползунок «Отступ от строки состояния»). */
const val EXPAND_BELOW_GAP_MAX_DP = 60

/** Запас высоты окна острова под опущенную карточку: строка состояния плюс наибольший отступ. */
const val EXPAND_BELOW_BAND_DP = 140

/** Высота строки состояния в dp; если система не отдаёт её, берётся типичная 24 dp. */
fun statusBarHeightDp(context: Context): Float {
    val res = context.resources
    val id = res.getIdentifier("status_bar_height", "dimen", "android")
    val px = if (id > 0) res.getDimensionPixelSize(id) else 0
    return if (px > 0) px / res.displayMetrics.density else 24f
}

/**
 * На сколько dp опустить раскрытую карточку, чтобы её верх оказался ниже строки состояния на «Отступ».
 * Верх острова и так стоит на [IslandSettings.topOffset] от верха экрана, поэтому опускаем лишь на разницу.
 */
fun cardDropDp(s: IslandSettings, statusBarDp: Float): Float =
    if (!s.expandBelowStatusBar) 0f
    else (statusBarDp + s.expandBelowGap.coerceIn(0, EXPAND_BELOW_GAP_MAX_DP) - s.topOffset).coerceAtLeast(0f)

/** Карточка опущена под строку состояния: прямой верх «выреза» ей ни к чему, а камера не мешает содержимому. */
internal val LocalCardBelowBar = staticCompositionLocalOf { false }

private data class Frame(val activityId: String, val expanded: Boolean, val noticeStyle: NoticeStyle?)

@Composable
fun IslandOverlay(
    onBodyBounds: (Rect) -> Unit = {},
    onBubbleBounds: (Rect) -> Unit = {},
    onBackdropShape: ((BackdropGeometry) -> Unit)? = null,
    onBackdropSampler: (((() -> Unit)?) -> Unit)? = null,
) {
    val saved by SettingsRepo.state.collectAsState()
    val powerSave by IslandBus.powerSave.collectAsState()
    // В энергосбережении Android с «Экономией заряда» остров обходится без живых эффектов
    val s = remember(saved, powerSave) { saved.forPowerSave(powerSave) }
    val media by IslandBus.media.collectAsState()
    val lyrics by IslandBus.lyrics.collectAsState()
    val progress by IslandBus.progress.collectAsState()
    val chrono by IslandBus.chrono.collectAsState()
    val calls by IslandBus.calls.collectAsState()
    val live by IslandBus.live.collectAsState()
    val torch by IslandBus.torch.collectAsState()
    val privacy by IslandBus.privacy.collectAsState()
    val timer by IslandBus.timer.collectAsState()
    val stopwatch by IslandBus.stopwatch.collectAsState()
    val notice by IslandBus.notice.collectAsState()
    val battery by IslandBus.battery.collectAsState()
    val foreground by IslandBus.foregroundPackage.collectAsState()
    val expanded by IslandBus.expanded.collectAsState()
    val focus by IslandBus.focus.collectAsState()
    val calibrating by IslandBus.calibrating.collectAsState()
    val systemAlarm by IslandBus.nextAlarm.collectAsState()
    val nextAlarm = systemAlarm?.withDisplayOffset(s.alarmDisplayOffsetMinutes)
    val egg by IslandBus.egg.collectAsState()
    val payReady by IslandBus.payReady.collectAsState()
    val inbox by IslandBus.inbox.collectAsState()
    val hotspot by com.akira.island.service.HotspotMonitor.state.collectAsState()
    val shadeOpen by IslandBus.shadeOpen.collectAsState()
    // Будильник появляется в острове за 12 часов и уходит, когда прозвенел: просыпаемся ровно в эти два момента,
    // а не каждые полминуты. Пока телефон спит, ожидание стоит — поэтому со включением экрана пересчитываем
    val screenOn by IslandBus.screenOn.collectAsState()
    var alarmNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(nextAlarm, screenOn) {
        val at = nextAlarm?.triggerAt ?: return@LaunchedEffect
        while (true) {
            val now = System.currentTimeMillis()
            alarmNow = now
            val next = when {
                now < at - ALARM_LEAD_MS -> at - ALARM_LEAD_MS
                now < at -> at
                else -> break
            }
            delay(next - now + 50)
        }
    }

    // Обратный отсчёт из чужого уведомления, дошедший до нуля, уходит через 3 секунды, а не висит «00:00».
    // Просыпаемся ровно к этому моменту; пока экран погашен, ожидание стоит — со включением пересчитываем
    var chronoNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(chrono, screenOn) {
        while (true) {
            val now = System.currentTimeMillis()
            chronoNow = now
            val next = chrono.values.filter { it.countDown && it.pausedMs == null }
                .map { it.base + ChronoInfo.DONE_MS }.filter { it >= now }.minOrNull() ?: break
            delay(next - now + 50)
        }
    }
    val chronoActive = chrono.values.filterNot { it.finished(chronoNow) }

    // «История уведомлений»: уведомление уходит из острова ровно к своему сроку; пока экран погашен, ожидание
    // стоит — со включением экрана пересчитываем. Выключили историю — список очищается
    LaunchedEffect(inbox, screenOn, s.historyMinutes) {
        while (true) {
            val now = System.currentTimeMillis()
            Inbox.prune(now)
            val next = Inbox.nextExpiry(now) ?: break
            delay(next + 50)
        }
    }

    // Пробная «живая активность» исчезает по времени, даже если таймер удаления не сработал (сон телефона)
    val hasExpiring = live.values.any { it.expiresAt > 0 }
    var wallNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(hasExpiring) {
        while (hasExpiring) {
            wallNow = System.currentTimeMillis()
            delay(1000)
        }
    }
    // Навигатор, у которого в уведомлении пусто, дополняется тем, что видно на его экране
    val screenNav by NavScreen.data.collectAsState()
    val liveActive = live.values.filter { it.expiresAt == 0L || wallNow < it.expiresAt }
        .map { NavScreen.merge(it, screenNav[it.pkg]) }
        .let { list -> list + screenNav.values.filter { sn -> sn.distance != null && list.none { it.pkg == sn.pkg } }.map(NavScreen::synthetic) }

    val call = if (!s.cardCall) null else {
        val tel = calls[CallMonitor.KEY]
        val fromApp = calls.filterKeys { it != CallMonitor.KEY }.values.maxByOrNull { it.postTime }
        when {
            fromApp == null -> tel
            tel == null -> fromApp
            // У звонилки богаче карточка (имя, фото, кнопки), а состояние надёжнее у телефона
            else -> fromApp.copy(
                incoming = tel.incoming,
                base = if (!tel.incoming && fromApp.incoming) tel.base else fromApp.base,
                avatar = fromApp.avatar ?: tel.avatar,
            )
        }
    }
    // Смахнутый входящий не показываем, пока звонит (сбрасывает служба, когда звонить перестали)
    val callHidden by IslandBus.callHidden.collectAsState()
    val acts = buildList {
        // Входящий звонок важнее всего, как на iPhone
        if (call != null && call.incoming && !callHidden) add(Act.CallA(call))
        notice?.let { n ->
            // Hide only the presentation while its source is in front. Do not cancel the Android
            // notification or remove history; returning before expiry can show the banner again.
            val source = n.message?.pkg ?: n.live?.pkg ?: n.openPkg ?: n.alarm?.pkg
            if (!s.hideWhenSourceOpen || source == null || source != foreground) add(Act.NoticeA(n))
        }

        val persistent = mutableListOf<Act>()
        val t = timer
        if (t != null && t.ringing) persistent += Act.TimerA(t)
        // Оплата телефоном — пока ждём терминал, она главнее остального
        payReady?.let { persistent += Act.PayA(it) }
        if (call != null && !call.incoming) persistent += Act.CallA(call)
        if (torch.on && s.cardTorch) persistent += Act.TorchA(torch)
        if (t != null && !t.ringing) persistent += Act.TimerA(t)
        if (s.cardLive) liveActive.maxByOrNull { it.postTime }?.let { persistent += Act.LiveA(it) }
        stopwatch?.let { persistent += Act.StopwatchA(it) }
        chronoActive.filter { if (it.recording) s.cardRecording else s.cardTimer }
            .maxByOrNull { it.postTime }?.let { persistent += Act.ChronoA(it) }
        val m = media
        if (s.cardMedia && m != null && !(s.hideWhenPlayerOpen && foreground == m.pkg)) {
            val at = if (s.preferMusic && m.playing) persistent.indexOfFirst {
                it !is Act.PayA && it !is Act.CallA && !(it is Act.TimerA && it.t.ringing)
            }.let { if (it < 0) persistent.size else it } else persistent.size
            persistent.add(at, Act.MediaA(m))
        }
        if (s.cardProgress) groupedProgress(progress.values.toList())?.let { persistent += Act.ProgressA(it) }
        if (s.evHotspot && hotspot.enabled == true) persistent += Act.HotspotA(hotspot)
        // Privacy indicators (camera / mic / location) — compact, high visibility like iPhone
        if (privacy.isNotEmpty()) persistent += Act.PrivacyA(privacy)
        // «Только при подключении» — зарядка не занимает остров всё время, пока телефон заряжается
        if (s.cardCharging && battery.charging && !s.chargingBrief) persistent += Act.ChargingA(battery)
        if (s.historyMinutes != 0 && inbox.isNotEmpty()) persistent += Act.InboxA(inbox)
        // Ожидающий будильник остаётся доступен в пузырьке, не занимая основную капсулу.
        val alarm = nextAlarm
        if (s.cardAlarm && alarm != null && alarm.triggerAt - alarmNow in 1..ALARM_LEAD_MS) persistent += Act.AlarmA(alarm)

        if (s.hideWhenSourceOpen) persistent.removeAll { it.pkg != null && it.pkg == foreground }

        // Выбранная активность выходит вперёд, но звонящий таймер всегда главный
        val ringing = (persistent.firstOrNull() as? Act.TimerA)?.t?.ringing == true || call?.incoming == true
        val i = persistent.indexOfFirst { it.id == focus }
        if (i > 0 && !ringing) persistent.add(0, persistent.removeAt(i))
        addAll(persistent)
    }

    // Пока двигают ползунок «Ширина с активностью», на острове пример: стрелка и метры
    val previewUntil by IslandBus.widthPreviewUntil.collectAsState()
    var previewNow by remember { mutableLongStateOf(0L) }
    LaunchedEffect(previewUntil) {
        while (true) {
            previewNow = android.os.SystemClock.elapsedRealtime()
            if (previewNow >= previewUntil) break
            delay(250)
        }
    }
    val previewing = previewNow < previewUntil

    // «Положение и размер»: пока двигают отступ карточки, на месте капсулы показывается красная раскрытая карточка
    val cardPreviewUntil by IslandBus.cardPreviewUntil.collectAsState()
    var cardPreviewNow by remember { mutableLongStateOf(0L) }
    LaunchedEffect(cardPreviewUntil) {
        while (true) {
            cardPreviewNow = android.os.SystemClock.elapsedRealtime()
            if (cardPreviewNow >= cardPreviewUntil) break
            delay(250)
        }
    }
    val cardPreviewing = calibrating && s.expandBelowStatusBar && cardPreviewNow < cardPreviewUntil

    // Во время подгонки размера показываем только пустую красную капсулу
    val primary = when {
        calibrating || s.collapseOnShade && shadeOpen -> Act.Idle
        previewing -> Act.LiveA(WidthDemo)
        else -> primaryWithPendingAlarm(acts, expanded, focus)
    }
    val incoming = (primary as? Act.CallA)?.c?.incoming == true
    // Вторая активность — кружком рядом, если его не выключили в «Поведении»
    val secondary = if (!s.bubble || primary.transient || incoming || calibrating || s.collapseOnShade && shadeOpen) null
    else secondaryWithPendingAlarm(acts, primary)
    val showExpanded = cardPreviewing || expanded && !primary.transient && !incoming && !calibrating && !(s.collapseOnShade && shadeOpen)
    val h = s.height.dp

    // Пузырь уходит с анимацией, поэтому помним последнюю активность в нём
    val lastSecondary = remember { arrayOfNulls<Act>(1) }
    val bubbleVisible = secondary != null && !showExpanded
    if (bubbleVisible) SideEffect { lastSecondary[0] = secondary }
    // Место под пузырь читается при измерении: кадр анимации не пересобирает остров
    val slot = animateDpAsState(if (bubbleVisible) activeHeight(s) + 6.dp else 0.dp, islandSpec(s), label = "slot")

    // Раскрытая активность пропала (музыку закрыли, таймер отменили) — сворачиваемся, а не показываем часы
    val focusGone = expanded && focus != null && focus != Act.Idle.id && acts.none { it.id == focus }
    LaunchedEffect(focusGone) { if (focusGone) Controls.collapse() }

    // «Сворачивать раскрытый остров»: если его столько секунд не трогают, он сворачивается сам
    LaunchedEffect(showExpanded, s.autoCollapseSec) {
        val limit = s.autoCollapseSec * 1000L
        if (!showExpanded || limit <= 0) return@LaunchedEffect
        val opened = SystemClock.elapsedRealtime()
        while (true) {
            val idle = SystemClock.elapsedRealtime() - maxOf(opened, IslandBus.lastTouch)
            if (idle >= limit) break
            delay(limit - idle)
        }
        Controls.collapse()
    }

    val keyguard by IslandBus.keyguard.collectAsState()
    val autoHideEligible = shouldTimeOutIdle(s, primary is Act.Idle && secondary == null, showExpanded, calibrating, screenOn, keyguard, shadeOpen)
    var idleTimedOut by remember { mutableStateOf(false) }
    var idleWakeRevision by remember { mutableLongStateOf(0L) }
    LaunchedEffect(autoHideEligible, s.idleHideSec, idleWakeRevision) {
        idleTimedOut = false
        if (!autoHideEligible) return@LaunchedEffect
        val started = SystemClock.elapsedRealtime()
        val timeout = s.idleHideSec.coerceIn(1, 300) * 1000L
        // Sleep to the deadline; touches extend it without a polling loop.
        while (true) {
            val remaining = timeout - (SystemClock.elapsedRealtime() - maxOf(started, IslandBus.lastTouch))
            if (remaining <= 0) break
            delay(remaining)
        }
        idleTimedOut = true
    }
    val timedHidden = autoHideEligible && idleTimedOut
    // Explicitly hiding the empty capsule retains its previous non-interactive behavior.
    val hidden = timedHidden || s.hideIdle && primary is Act.Idle && secondary == null && !showExpanded && !calibrating && !(s.evLock && keyguard)
    val wakeBounds = remember { mutableStateOf(Rect.Zero) }

    // Спрятанный остров не ловит касания, даже пока уходит
    if (hidden) {
        SideEffect {
            onBodyBounds(if (timedHidden) wakeBounds.value else Rect.Zero)
            onBubbleBounds(Rect.Zero)
        }
    }
    val hiddenNow = rememberUpdatedState(hidden)
    val timedHiddenNow = rememberUpdatedState(timedHidden)

    // Где остров в окне — вокруг него рисуются эффекты пасхалки
    val body = remember { mutableStateOf(Rect.Zero) }
    val onBody = remember(onBodyBounds) {
        { r: Rect ->
            body.value = r
            onBodyBounds(if (hiddenNow.value) { if (timedHiddenNow.value) wakeBounds.value else Rect.Zero } else r)
        }
    }
    val onBubble = remember(onBubbleBounds) { { r: Rect -> onBubbleBounds(if (hiddenNow.value) Rect.Zero else r) } }
    val eggOn = egg && !calibrating

    // Остров меряется по своему размеру, а не по окну, и стоит сверху по центру окна
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        // Оплата: волны NFC и вспышка успеха вокруг острова, как в ролике
        if (!calibrating) PayEffects(primary, { body.value }, s)
        // Пасхалка: свечение за островом, вспышки и звёзды поверх
        if (eggOn) EggGlow { body.value }
        // «Скрывать пустой остров»: он не пропадает рывком, а уходит в камеру и выходит из неё
        val top = TransformOrigin(0.5f, 0f)
        AnimatedVisibility(
            !hidden,
            enter = if (s.reduceEffects) fadeIn() else scaleIn(islandSpec(s), initialScale = 0.3f, transformOrigin = top) + fadeIn(tween(120)),
            exit = if (s.reduceEffects) fadeOut() else scaleOut(islandSpec(s), targetScale = 0.3f, transformOrigin = top) + fadeOut(tween(160)),
        ) {
            CompositionLocalProvider(LocalActivityIds provides acts.filterNot { it.transient }.map { it.id }) {
                IslandRow(slot, s, primary, showExpanded, lyrics, battery, bubbleVisible, if (bubbleVisible) secondary else lastSecondary[0], calibrating, onBody, onBubble, onBackdropShape, onBackdropSampler)
            }
        }
        if (timedHidden) CameraWakeTarget(s, onBounds = { r ->
            wakeBounds.value = r
            onBodyBounds(r)
        }) {
            IslandBus.lastTouch = SystemClock.elapsedRealtime()
            idleTimedOut = false
            idleWakeRevision++
        }
        if (eggOn) {
            EggSparks { body.value }
            EggDriver({ body.value }, s)
        }
        com.akira.island.face.FaceUnlockLayer(s, allowed = !calibrating && !shadeOpen &&
            !showExpanded && !eggOn, bodyBounds = { body.value })
    }
}

/** Only the area at the camera remains tappable after a timed hide, not the old wide capsule. */
@Composable
internal fun CameraWakeTarget(s: IslandSettings, onBounds: (Rect) -> Unit, onWake: () -> Unit) {
    val label = tr("Показать остров", "Show the island")
    Box(Modifier.layout { measurable, constraints ->
        val p = measurable.measure(constraints)
        val room = ((constraints.maxWidth - s.width.dp.roundToPx() - 2 * ISLAND_PAD_DP.dp.roundToPx()) / 2).coerceAtLeast(0)
        layout(p.width, p.height) { p.place(s.offsetX.dp.roundToPx().coerceIn(-room, room), 0) }
    }.padding(ISLAND_PAD_DP.dp)) {
        Box(Modifier.size(s.width.coerceIn(24, 48).dp, s.height.coerceIn(24, 48).dp)
            .onGloballyPositioned { onBounds(it.boundsInWindow()) }
            .testTag("idle-island-wake")
            .semantics { contentDescription = label }
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClickLabel = label, onClick = onWake))
    }
}

@Composable
internal fun IslandRow(
    slot: State<Dp>,
    s: IslandSettings,
    primary: Act,
    showExpanded: Boolean,
    lyrics: Lyrics?,
    battery: BatteryInfo,
    bubbleVisible: Boolean,
    bubbleAct: Act?,
    calibrating: Boolean,
    onBodyBounds: (Rect) -> Unit,
    onBubbleBounds: (Rect) -> Unit,
    onBackdropShape: ((BackdropGeometry) -> Unit)? = null,
    onBackdropSampler: (((() -> Unit)?) -> Unit)? = null,
) {
    val compactWidth = remember { mutableIntStateOf(0) }
    val left = s.bubbleSide == BubbleSide.LEFT
    // Карточка уходит под строку состояния той же пружиной, что и форма острова, — сдвиг читается при
    // размещении, поэтому кадр анимации не пересобирает остров
    val preview = LocalIslandPreview.current
    val context = LocalContext.current
    val detail by IslandBus.messageDetail.collectAsState()
    val card = s.usesCardSurface(showExpanded, (primary as? Act.NoticeA)?.n?.style, detail && !preview)
    val statusBar = remember(context) { statusBarHeightDp(context) }
    // Во время подгонки раскрытой бывает только красная пробная карточка, поэтому calibrating сюда не мешает
    val dropTarget = if (card && !preview) cardDropDp(s, statusBar) else 0f
    val drop = animateDpAsState(dropTarget.dp, islandSpec(s), label = "cardDrop")
    // Полный сдвиг помним и после сворачивания: форма возвращается к «вырезу» плавно, а не щелчком
    val fullDrop = remember { floatArrayOf(0f) }
    if (dropTarget > 0f || !s.expandBelowStatusBar) fullDrop[0] = dropTarget
    val detach = remember(drop, fullDrop) { { if (fullDrop[0] > 0f) (drop.value.value / fullDrop[0]).coerceIn(0f, 1f) else 0f } }
    val room = if (s.expandBelowStatusBar && !preview) EXPAND_BELOW_BAND_DP.dp else 0.dp
    // Служба решает, прятать ли значки строки состояния, по цели, а не по кадру анимации: иначе на границе
    // строки значки то прячутся, то появляются с задержкой синхронизации
    SideEffect { if (!preview) IslandBus.cardBelowBar.value = dropTarget > 0f }
    DisposableEffect(preview) { onDispose { if (!preview) IslandBus.cardBelowBar.value = false } }
    Layout(
        modifier = Modifier.padding(vertical = ISLAND_PAD_DP.dp),
        content = {
            BubbleSlot(bubbleVisible, bubbleAct, s, onBubbleBounds, left)
            IslandBody(primary, showExpanded, s, lyrics, battery, calibrating, onBodyBounds,
                onBackdropShape, onBackdropSampler, onCompactWidth = { compactWidth.intValue = it },
                detach = detach)
        },
    ) { children, constraints ->
        val reserve = room.roundToPx()
        val loose = Constraints(maxHeight = (constraints.maxHeight - reserve).coerceAtLeast(0))
        val body = children[1].measure(loose)
        val bubble = children[0].measure(loose)
        val pad = ISLAND_PAD_DP.dp.roundToPx()
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else body.width + 2 * (bubble.width + pad)
        layout(width, (maxOf(body.height + reserve, bubble.height)).coerceIn(constraints.minHeight, constraints.maxHeight)) {
            // The bubble emerges from the compact capsule. The expanding card covers it in place.
            val placement = islandPlacement(width, body.width, compactWidth.intValue, bubble.width,
                slot.value.roundToPx(), pad, s.offsetX.dp.roundToPx(), left)
            bubble.place(placement.bubbleX, 0)
            body.place(placement.bodyX, drop.value.roundToPx().coerceAtLeast(0))
        }
    }
}

internal data class IslandPlacement(val bodyX: Int, val bubbleX: Int)

internal fun islandPlacement(width: Int, body: Int, compact: Int, bubble: Int, slot: Int, pad: Int,
    offset: Int, left: Boolean): IslandPlacement {
    val anchor = if (compact > 0) minOf(body, compact) else body
    val extent = slot.coerceAtLeast(0)
    val half = maxOf((body + 1) / 2, (anchor + 1) / 2 + (extent + 1) / 2)
    val room = (width / 2 - half - pad).coerceAtLeast(0)
    // Kapsul + lingkaran dianggap satu grup yang dipusatkan: kapsul bergeser setengah lebar slot
    // menjauhi sisi lingkaran. `extent` ikut dianimasikan (slot), jadi pergeseran ikut mulus.
    val shift = if (left) extent / 2 else -(extent / 2)
    val center = width / 2 + offset.coerceIn(-room, room) + shift
    val bubbleX = if (left) center - anchor / 2 - extent else center + anchor / 2 + extent - bubble
    return IslandPlacement(center - body / 2, bubbleX)
}

@Composable
private fun BubbleSlot(visible: Boolean, act: Act?, s: IslandSettings, onBounds: (Rect) -> Unit, left: Boolean) {
    LaunchedEffect(visible) { if (!visible) onBounds(Rect.Zero) }
    Box {
        val drop = TransformOrigin(if (left) 1f else 0f, 0.5f)
        AnimatedVisibility(
            visible && act != null,
            enter = if (s.reduceEffects) fadeIn() else scaleIn(islandSpec(s), initialScale = 0.4f, transformOrigin = drop) + fadeIn(tween(90)),
            exit = if (s.reduceEffects) fadeOut() else scaleOut(islandSpec(s), targetScale = 0.4f, transformOrigin = drop) + fadeOut(tween(120, delayMillis = 60)),
        ) {
            if (act != null) Box(Modifier.onGloballyPositioned {
                onBounds(if (visible) it.boundsInWindow() else Rect.Zero)
            }) { Bubble(act, s) }
        }
    }
}

@Composable
internal fun IslandBody(
    primary: Act,
    expanded: Boolean,
    s: IslandSettings,
    lyrics: Lyrics?,
    battery: BatteryInfo,
    calibrating: Boolean,
    onBounds: (Rect) -> Unit,
    onBackdropShape: ((BackdropGeometry) -> Unit)? = null,
    onBackdropSampler: (((() -> Unit)?) -> Unit)? = null,
    onCompactWidth: ((Int) -> Unit)? = null,
    /** 0 — карточка у камеры, 1 — опущена под строку состояния; читается при рисовании */
    detach: () -> Float = { 0f },
) {
    val preview = LocalIslandPreview.current
    val context = LocalContext.current
    // Карточка стоит под строкой состояния: камера ей не мешает, а верх у неё круглый, как у обычной карточки
    val belowBar = !preview && cardDropDp(s, remember(context) { statusBarHeightDp(context) }) > 0f
    val haptic = rememberHaptic()
    val activities by rememberUpdatedState(LocalActivityIds.current)
    val scope = rememberCoroutineScope()
    val conf = LocalConfiguration.current
    // Раскрытый остров iPhone — 371 pt при ширине экрана 393 pt, то есть по 11 с каждой стороны;
    // «Ширина раскрытого острова» в «Оформлении» делает его уже
    val expandedWidth = expandedWidth(conf.screenWidthDp.dp, s)
    val h = s.height.dp

    // Скругления — как в ролике: плеер и фонарик 46, навигатор 42, обновление 36, уведомление 32
    val cornerDefault = when {
        expanded && (primary is Act.MediaA || primary is Act.TorchA) -> 46.dp
        expanded && primary is Act.LiveA -> 42.dp
        expanded -> 44.dp
        primary is Act.NoticeA && primary.n.style == NoticeStyle.MESSAGE && s.messageStyle == MessageStyle.PILL -> 40.dp
        primary is Act.NoticeA && (primary.n.style == NoticeStyle.MESSAGE || primary.n.style == NoticeStyle.EGG) -> 32.dp
        // Системные события (зарядка…замок, оплата, будильник) — капсула высоты активности, как у фонарика и музыки
        primary is Act.NoticeA && primary.n.style == NoticeStyle.SENT -> noticeHeight(s) / 2
        primary is Act.CallA && primary.c.incoming -> callHeight(s) / 2
        primary is Act.Idle -> h / 2
        else -> activeHeight(s) / 2
    }
    val realDetail by IslandBus.messageDetail.collectAsState()
    val detail = realDetail && !preview
    val large = s.usesCardSurface(expanded, (primary as? Act.NoticeA)?.n?.style, detail)
    val radius = if (large) s.expandedCornerRadius else s.cornerRadius
    val cornerTarget = if (radius >= 0) radius.dp else cornerDefault
    // Скругление читается при рисовании: кадр раскрытия перерисовывает рамку и стекло, а не пересобирает остров
    val corner = animateDpAsState(cornerTarget, islandSpec(s), label = "corner")
    val shape = remember(corner, s.notch, s.notchTopRadius, detach) { AnimatedIslandShape(corner, s.notch, s.notchTopRadius, detach) }
    val accent = primary.surfaceAccent()

    // Свечение цвета обложки, пока играет музыка («Эффекты»): с треком цвет плавно меняется, с паузой гаснет
    val playingArt = (primary as? Act.MediaA)?.m?.takeIf { it.playing }?.accent
    val lastArt = remember { arrayOf(Color.White) }
    if (playingArt != null) lastArt[0] = playingArt
    val glowOn = s.fxGlow && !s.reduceEffects && !calibrating && playingArt != null
    val glowAlpha by animateFloatAsState(if (glowOn) 1f else 0f, tween(500), label = "glowAlpha")
    val glowTint by animateColorAsState(lastArt[0], tween(500), label = "glowTint")

    // «Как фрукт»: при раскрытии остров сплющивается и отпружинивает
    val jellyX = remember { Animatable(1f) }
    val jellyY = remember { Animatable(1f) }
    var firstRun by remember { mutableStateOf(true) }
    LaunchedEffect(expanded) {
        if (firstRun) {
            firstRun = false
            return@LaunchedEffect
        }
        if (s.expandStyle != ExpandStyle.FRUIT || s.reduceEffects || s.elasticity == Elasticity.NONE) return@LaunchedEffect
        val amp = 0.07f * (1.25f - s.elasticity.damping)
        // Отскок затухает по «Упругости»: сильная — как раньше, слабая — почти без колебаний
        val rebound = spring<Float>(dampingRatio = (s.elasticity.damping * 0.62f).coerceAtLeast(0.3f), stiffness = 260f)
        val dir = if (expanded) 1f else -1f
        launch {
            jellyX.animateTo(1f - amp * dir, tween(110))
            jellyX.animateTo(1f, rebound)
        }
        launch {
            jellyY.animateTo(1f + amp * dir, tween(110))
            jellyY.animateTo(1f, rebound)
        }
    }
    // Оттягивание вниз пальцем
    val pull = remember { Animatable(0f) }

    // Liquid Glass: при смене содержимого по стеклу пробегает блик, при наклоне телефона блики сдвигаются
    val tilt = IslandBus.glassTilt.collectAsState()
    val previewTilt = LocalGlassTilt.current
    val sheen = remember { Animatable(-1f) }
    LaunchedEffect(primary.id, expanded, s.liquidGlass, s.reduceEffects) {
        sheen.snapTo(-1f)
        if (!s.liquidGlass || s.reduceEffects) return@LaunchedEffect
        sheen.snapTo(0f)
        sheen.animateTo(1f, tween(850, easing = FastOutSlowInEasing))
        sheen.snapTo(-1f)
    }

    // Короткая пилюля (режим звука) вздрагивает вбок, как остров на iPhone при переключении «Звонок/Бесшумно»
    val shake = remember { Animatable(0f) }
    val banner = (primary as? Act.NoticeA)?.n
    val pillId = banner?.takeIf { it.style == NoticeStyle.PILL && it.effect != NoticeEffect.RINGER }?.id
    LaunchedEffect(pillId, s.reduceEffects, s.fxShake) {
        shake.snapTo(0f)
        if (pillId == null || s.reduceEffects || !s.fxShake) return@LaunchedEffect
        for (x in floatArrayOf(-8f, 7f, -5f, 3.5f, -2f, 0f)) shake.animateTo(x, tween(60))
    }

    val current by rememberUpdatedState(primary)
    val isExpanded by rememberUpdatedState(expanded)
    val settings by rememberUpdatedState(s)
    val realEgg by IslandBus.egg.collectAsState()
    val egg = realEgg && !preview
    // The hit area and native material both follow the drawn contour.
    val reportedBackdrop = remember { arrayOfNulls<BackdropGeometry>(1) }
    val backdropCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val backdropDensity = LocalDensity.current
    fun reportBackdrop() {
        if (s.liquidGlass && !preview && onBackdropShape != null) {
            val c = backdropCoordinates[0]?.takeIf { it.isAttached && it.size.width > 0 && it.size.height > 0 } ?: return
            val origin = c.localToWindow(Offset.Zero)
            val x = c.localToWindow(Offset(c.size.width.toFloat(), 0f))
            val y = c.localToWindow(Offset(0f, c.size.height.toFloat()))
            val scale = maxOf((x-origin).getDistance()/c.size.width, (y-origin).getDistance()/c.size.height)
            val geometry = BackdropGeometry(c.boundsInWindow(),
                with(backdropDensity) { corner.value.toPx() }*scale,
                with(backdropDensity) { (if (s.notch) lerp(s.notchTopRadius.dp, corner.value, detach().coerceIn(0f, 1f)) else corner.value).toPx() }*scale,
                !calibrating && !egg && scale.isFinite(), large)
            if (reportedBackdrop[0] != geometry) {
                reportedBackdrop[0] = geometry
                onBackdropShape(geometry)
            }
        }
    }
    DisposableEffect(onBackdropSampler, s.liquidGlass, s.notch, s.notchTopRadius, large, calibrating, egg, preview, backdropDensity) {
        onBackdropSampler?.invoke { reportBackdrop() }
        onDispose { onBackdropSampler?.invoke(null) }
    }
    Box(
        Modifier
            .graphicsLayer {
                // Пасхалка: остров подпрыгивает в такт песни; оплата прошла — на миг подрастает
                val bump = if (preview) 1f else Egg.bump() * PayFx.pop.floatValue
                scaleX = jellyX.value * (1f + 0.03f * pull.value) * bump
                scaleY = jellyY.value * (1f + 0.08f * pull.value) * bump
                translationX = shake.value.dp.toPx()
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
            .onGloballyPositioned {
                onBounds(it.boundsInWindow())
                backdropCoordinates[0] = it
                reportBackdrop()
            }
            .then(if (s.liquidGlass && !preview && onBackdropShape != null) Modifier.drawWithContent {
                reportBackdrop()
                drawContent()
            } else Modifier)
            .drawBehind { if (glowAlpha > 0f) islandGlow(glowTint, glowAlpha, corner.value.toPx(), s.notch && detach() < 0.5f) }
            .then(
                if (calibrating) Modifier.clip(shape).background(CalibrationRed).border(2.dp, Color.White, shape)
                else Modifier.islandSurface(s, large, shape, accent, tilt = { previewTilt ?: tilt.value }, sheen = { sheen.value }),
            )
            .then(if (!calibrating && banner?.effect == NoticeEffect.CHARGING)
                chargingWave(s, banner.id) else Modifier)
            .then(
                if (egg && !calibrating) {
                    Modifier.drawWithContent {
                        drawContent()
                        eggRim(corner.value.toPx(), s.notch)
                    }
                } else {
                    Modifier
                },
            )
            .then(if (preview) Modifier else Modifier
            // Любое касание — раскрытый остров не свернётся сам из-под пальца
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        IslandBus.lastTouch = SystemClock.elapsedRealtime()
                    }
                }
            }
            .pointerInput(s.doubleTapAction != TapAction.NOTHING, s.longPressAction != TapAction.NOTHING) {
                val doubleOn = settings.doubleTapAction != TapAction.NOTHING
                val longOn = settings.longPressAction != TapAction.NOTHING
                val onDouble: ((Offset) -> Unit)? = if (!doubleOn) null else {
                    { _: Offset ->
                        haptic()
                        perform(context, settings.doubleTapAction, current)
                    }
                }
                val onLong: ((Offset) -> Unit)? = if (!longOn) null else {
                    { _: Offset ->
                        haptic()
                        perform(context, settings.longPressAction, current)
                    }
                }
                detectTapGestures(
                    onPress = { haptic() },
                    onTap = { onTap(context, current, isExpanded, settings) },
                    onDoubleTap = onDouble,
                    onLongPress = onLong,
                )
            }
            .pointerInput(Unit) {
                val threshold = 36.dp.toPx()
                val side = 56.dp.toPx()
                var dx = 0f
                var dy = 0f
                detectDragGestures(
                    onDragStart = {
                        dx = 0f
                        dy = 0f
                    },
                    onDragEnd = {
                        scope.launch { pull.animateTo(0f, spring(dampingRatio = 0.45f)) }
                        val vertical = abs(dy) > abs(dx)
                        val act = current
                        when {
                            !isExpanded && vertical && dy > threshold && settings.swipeExpand && !act.transient &&
                                (act !is Act.Idle || settings.idleExpand) -> {
                                haptic()
                                Controls.expand(act.id)
                            }
                            isExpanded && vertical && dy < -threshold -> {
                                haptic()
                                Controls.collapse()
                            }
                            // Смахнуть уведомление вверх, как баннер на iPhone. Порог полный, чтобы палец,
                            // чуть съехавший при нажатии «Ответить», не убирал плашку.
                            // Сообщение приложения при этом отменяется и в самой шторке уведомлений.
                            !isExpanded && vertical && dy < -threshold && act is Act.NoticeA -> {
                                val key = act.n.message?.key
                                if (key != null && com.akira.island.service.NotifListener.canDismiss(key)) {
                                    haptic()
                                    com.akira.island.service.NotifListener.dismiss(key)
                                } else {
                                    IslandBus.notice.value = null
                                }
                            }
                            // Подсказку оплаты тоже можно смахнуть
                            !isExpanded && vertical && dy < -threshold && act is Act.PayA -> PayMonitor.clearReady()
                            // И входящий звонок, если не хочется отвечать: звонок идёт дальше, но остров свободен
                            !isExpanded && vertical && dy < -threshold && act is Act.CallA && act.c.incoming -> {
                                haptic()
                                Controls.hideIncomingCall()
                            }
                            !isExpanded && !vertical && abs(dx) > side && settings.swipeActivities && activities.size > 1 -> {
                                val i = activities.indexOf(act.id).coerceAtLeast(0)
                                val next = Math.floorMod(i + if (dx < 0) 1 else -1, activities.size)
                                IslandBus.focus.value = activities[next]
                            }
                            !isExpanded && !vertical && abs(dx) > side && act is Act.MediaA && settings.swipeTracks -> {
                                haptic()
                                // Как в ПК-версии: толкнуть влево — следующий трек
                                if (dx < 0) Controls.next(act.m.pkg) else Controls.prev(act.m.pkg)
                            }
                        }
                    },
                    onDragCancel = { scope.launch { pull.animateTo(0f) } },
                    onDrag = { change, amount ->
                        change.consume()
                        dx += amount.x
                        dy += amount.y
                        if (!isExpanded && settings.swipeExpand) {
                            scope.launch { pull.snapTo((dy / (threshold * 3)).coerceIn(0f, 1f)) }
                        }
                    },
                )
            }),
    ) {
        // Смена содержимого: с «Жидким раскрытием» новое вырастает из прежнего вместе с формой острова,
        // без него — прежнее перетекание. Форма в обоих случаях меняется пружиной из «Раскрытия острова»
        val widths = remember { HashMap<Frame, Int>() }
        // Only a change of activity or presentation starts a morph. Telemetry stays live in its slot.
        val scene = Frame(primary.id, expanded, banner?.style)
        val frames = updateTransition(scene, label = "island")
        frames.AnimatedContent(
            contentAlignment = Alignment.TopCenter,
            transitionSpec = {
                val change = if (s.liquidExpand) EnterTransition.None togetherWith ExitTransition.None else classicChange(s)
                change.using(SizeTransform(clip = true) { _, _ -> islandSpec(s) })
            },
        ) { f ->
            val last = remember(f) { arrayOf(primary) }
            val act = if (f == scene) primary else last[0]
            if (f == scene) SideEffect { last[0] = primary }
            val reportWidth = if (f == scene && !f.expanded && onCompactWidth != null)
                Modifier.onSizeChanged { onCompactWidth(it.width) } else Modifier
            Box(reportWidth.then(if (s.liquidExpand) liquidFlow(f, frames, widths, s) else classicFlow(s))) {
                if (f.expanded) {
                    CompositionLocalProvider(LocalCardBelowBar provides belowBar) {
                        ExpandedContent(act, s, lyrics, battery, expandedWidth)
                    }
                } else {
                    CompactContent(act, s, expandedWidth)
                }
            }
        }
    }
}

/**
 * Прежнее перетекание, как в ролике: старое тает за 1/6 с, уменьшаясь, новое проявляется чуть позже
 * из 90% размера. Скорость — от «Длительности» (по умолчанию как в ролике).
 */
private fun classicChange(s: IslandSettings): ContentTransform {
    val m = motionScale(s)
    val origin = TransformOrigin(0.5f, 0.3f)
    val inSpec = tween<Float>((300 * m).toInt(), delayMillis = (100 * m).toInt(), easing = ExpoOut)
    val outSpec = tween<Float>((167 * m).toInt(), easing = LinearEasing)
    val enter = if (s.reduceEffects) {
        fadeIn(tween(150))
    } else {
        fadeIn(inSpec) + scaleIn(inSpec, initialScale = 0.9f, transformOrigin = origin)
    }
    val exit = if (s.reduceEffects) {
        fadeOut(tween(90))
    } else {
        fadeOut(outSpec) + scaleOut(outSpec, targetScale = 0.95f, transformOrigin = origin)
    }
    return enter togetherWith exit
}

/** Размытие к прежнему перетеканию (Android 12+): новое проявляется из 7 dp, уходящее размывается до 5 dp. */
@Composable
private fun AnimatedVisibilityScope.classicFlow(s: IslandSettings): Modifier {
    val m = motionScale(s)
    val blurCache = remember { BlurEffectCache() }
    val blur = transition.animateFloat(
        transitionSpec = {
            if (targetState == EnterExitState.Visible) tween((300 * m).toInt(), delayMillis = (100 * m).toInt(), easing = ExpoOut)
            else tween((167 * m).toInt())
        },
        label = "blur",
    ) { state ->
        when {
            s.reduceEffects || !s.fxBlur -> 0f
            state == EnterExitState.PreEnter -> 7f
            state == EnterExitState.PostExit -> 5f
            else -> 0f
        }
    }
    return Modifier.graphicsLayer { blurLayer(blur.value, blurCache) }
}

/**
 * «Жидкое раскрытие», как в образце: новое содержимое вырастает из прежнего вместе с формой острова и проявляется
 * из размытия, прежнее растягивается или сжимается к размеру нового и тает. Ход — та же пружина, что у формы,
 * поэтому содержимое не обгоняет форму и не отстаёт. [widths] — ширина каждого содержимого, запомненная при измерении.
 */
@Composable
private fun AnimatedVisibilityScope.liquidFlow(key: Frame, frames: Transition<Frame>, widths: HashMap<Frame, Int>, s: IslandSettings): Modifier {
    val blurCache = remember { BlurEffectCache() }
    // «Другое» содержимое: для нового — прежнее, для уходящего — новое. Если на полпути направление
    // сменилось (раскрыли и тут же свернули), «другое» остаётся прежним — масштаб идёт дальше без скачка
    val target = frames.targetState
    val other = if (key == target) frames.currentState else target
    val from = remember { arrayOfNulls<Frame>(1) }
    if (other != key) from[0] = other
    val lastOtherWidth = remember { IntArray(1) }
    // 0 — содержимого ещё (или уже) нет, 1 — оно на месте; читается только при рисовании
    val presence = transition.animateFloat({ islandSpec(s) }, label = "presence") { if (it == EnterExitState.Visible) 1f else 0f }
    // Проявление и таяние — своя пружина, быстрее и без отскока: новое видно уже в начале хода
    val fade = transition.animateFloat({ fadeSpec(s) }, label = "fade") { if (it == EnterExitState.Visible) 1f else 0f }
    DisposableEffect(key) { onDispose { widths.remove(key) } }
    val zoom = !s.reduceEffects
    val blurDp = if (s.reduceEffects || !s.fxBlur) 0f else 6f
    return Modifier.layout { measurable, constraints ->
        val content = measurable.measure(constraints)
        widths[key] = content.width
        layout(content.width, content.height) {
            content.placeWithLayer(0, 0) {
                val p = presence.value
                alpha = fade.value.coerceIn(0f, 1f)
                from[0]?.let { widths[it] }?.let { lastOtherWidth[0] = it }
                val ow = lastOtherWidth[0]
                if (zoom && ow > 0 && content.width > 0) {
                    val k = (ow.toFloat() / content.width).coerceIn(0.25f, 3f)
                    scaleX = k + (1f - k) * p
                    scaleY = scaleX
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
                // Размытие сильное в начале и сходит к концу хода, а не тянется до последнего кадра
                val rest = (1f - p).coerceAtLeast(0f)
                blurLayer(blurDp * rest * sqrt(rest), blurCache)
            }
        }
    }
}

/** Размытие слоя на [dp] — на Android 12+, где оно есть, и только когда заметно. */
private class BlurEffectCache {
    private val effects = object : LinkedHashMap<Int, BlurEffect>(48, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, BlurEffect>?) = size > 48
    }
    fun effect(px: Float): BlurEffect {
        val key = (px * 2f).toInt().coerceAtLeast(1)
        return effects.getOrPut(key) { BlurEffect(key / 2f, key / 2f, TileMode.Decal) }
    }
}

private fun GraphicsLayerScope.blurLayer(dp: Float, cache: BlurEffectCache) {
    // Graphics layers retain their properties: explicitly clear a previous effect on the final frame.
    renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dp > 0.3f) cache.effect(dp.dp.toPx()) else null
}

private fun onTap(context: Context, act: Act, expanded: Boolean, s: IslandSettings) {
    if (expanded) {
        Controls.collapse()
        return
    }
    if (s.tapAction == TapAction.SCROLL_TOP) {
        perform(context, s.tapAction, act)
        return
    }
    when (act) {
        is Act.NoticeA -> {
            // Пока вводят ответ, нажатие по карточке не должно открывать приложение
            if (IslandBus.reply.value != null) return
            if (act.n.message != null) {
                when (s.tapAction) {
                    TapAction.OPEN_APP, TapAction.DISMISS_NOTIFICATION -> perform(context, s.tapAction, act)
                    else -> IslandBus.messageDetail.value = true
                }
                return
            }
            act.n.openPkg?.let { Controls.openApp(context, it) }
            act.n.alarm?.let { openAlarm(context, it) }
            IslandBus.notice.value = null
        }
        // Пустой остров раскрывается, если это не выключили в «Поведении»
        is Act.Idle -> if (s.idleExpand) Controls.expand(act.id)
        // Оплата: нажатие открывает приложение оплаты (окно Google открывать нечего)
        is Act.PayA -> if (act.pkg != PayMonitor.GMS) Controls.openApp(context, act.pkg)
        is Act.TimerA -> if (act.t.ringing) Controls.cancelTimer() else perform(context, s.tapAction, act)
        is Act.CallA -> if (act.c.incoming) {
            Controls.openCall(context, act.c)
        } else {
            perform(context, s.tapAction, act)
        }
        else -> perform(context, s.tapAction, act)
    }
}

private fun perform(context: Context, action: TapAction, act: Act) {
    if (action == TapAction.SCROLL_TOP) {
        Controls.scrollToTop()
        return
    }
    if (act is Act.NoticeA && act.n.message != null) {
        if (IslandBus.locked.value) return
        val message = act.n.message
        when (action) {
            TapAction.OPEN_APP -> {
                if (!Controls.send(context, message.intent)) Controls.openApp(context, message.pkg)
                if (IslandBus.notice.value?.id == act.n.id) {
                    IslandBus.messageDetail.value = false
                    IslandBus.noticeHold.value = false
                    IslandBus.notice.value = null
                }
            }
            TapAction.DISMISS_NOTIFICATION -> {
                if (!com.akira.island.service.NotifListener.dismiss(message.key)) android.widget.Toast.makeText(context,
                    tr("Не удалось удалить уведомление", "Couldn't dismiss notification"), android.widget.Toast.LENGTH_SHORT).show()
            }
            else -> Unit
        }
        return
    }
    if (act.transient) return
    val hasMedia = IslandBus.media.value != null
    when (action) {
        TapAction.EXPAND -> Controls.expand(act.id)
        TapAction.OPEN_APP -> act.pkg?.let { Controls.openApp(context, it) } ?: Controls.expand(act.id)
        TapAction.PLAY_PAUSE -> if (hasMedia) Controls.playPause() else Controls.expand(act.id)
        TapAction.NEXT_TRACK -> if (hasMedia) Controls.next() else Controls.expand(act.id)
        TapAction.SCREENSHOT -> Controls.screenshot()
        TapAction.SCROLL_TOP -> Unit // Handled above, including the idle island and transient cards.
        TapAction.NOTHING -> Unit
        TapAction.DISMISS_NOTIFICATION -> Unit
    }
}

/** Вторая активность — круглый пузырь справа от острова. */
private fun Act.surfaceAccent(): Color? = when (this) {
    is Act.MediaA -> m.accent
    is Act.NoticeA -> n.message?.accent ?: n.tint
    is Act.ChargingA, is Act.CallA -> Tints.Green
    is Act.TimerA, is Act.StopwatchA, is Act.ChronoA -> Tints.Orange
    is Act.TorchA -> Tints.Yellow
    is Act.ProgressA, is Act.HotspotA -> Tints.Blue
    is Act.LiveA -> liveTint(l.kind)
    else -> null
}

@Composable
private fun Bubble(act: Act, s: IslandSettings) {
    IslandTextTheme(s) { BubbleContent(act, s) }
}

@Composable
private fun BubbleContent(act: Act, s: IslandSettings) {
    val h = activeHeight(s)
    val haptic = rememberHaptic()
    val tilt = IslandBus.glassTilt.collectAsState()
    val previewTilt = LocalGlassTilt.current
    val now = rememberNow(500, active = act is Act.TimerA)
    Box(
        Modifier
            .size(h)
            .islandSurface(s, false, CircleShape, act.surfaceAccent(), tilt = { previewTilt ?: tilt.value })
            // Privacy indicators are informational only: as the second activity they are not tappable
            .then(if (act is Act.PrivacyA) Modifier else Modifier.clickable {
                haptic()
                Controls.expand(act.id)
            }),
        contentAlignment = Alignment.Center,
    ) {
        val inner: Dp = h * 0.62f
        when (act) {
            is Act.MediaA -> Art(act.m.art, h * 0.72f, h / 2)
            is Act.TimerA -> {
                ProgressRing(
                    progress = if (act.t.totalMs > 0) act.t.left(now).toFloat() / act.t.totalMs else 0f,
                    color = Tints.Orange,
                    modifier = Modifier.size(inner),
                    stroke = 2.5.dp,
                )
                Icon(Ios.timer, null, tint = Tints.Orange, modifier = Modifier.size(inner * 0.5f))
            }
            is Act.StopwatchA -> Icon(Ios.stopwatch, null, tint = Tints.Orange, modifier = Modifier.size(inner * 0.8f))
            is Act.AlarmA -> Icon(Ios.alarm, tr("Будильник", "Alarm"), tint = AlarmTint, modifier = Modifier.size(inner * 0.8f))
            is Act.TorchA -> Icon(Ios.flashlight, null, tint = Tints.Yellow, modifier = Modifier.size(inner * 0.75f))
            is Act.ChronoA -> AppIcon(act.c.icon, inner, act.c.recordingKind?.icon ?: Ios.timer,
                if (act.c.recording) Tints.Red else Tints.Orange)
            is Act.ProgressA -> {
                ProgressRing(act.p.fraction, Tints.Blue, Modifier.size(inner), stroke = 2.5.dp)
                Icon(Ios.download, null, tint = Tints.Blue, modifier = Modifier.size(inner * 0.5f))
            }
            is Act.CallA -> Icon(Ios.call, null, tint = Tints.Green, modifier = Modifier.size(inner * 0.7f))
            is Act.LiveA -> LiveGlyph(act.l, inner, liveTint(act.l.kind))
            is Act.ChargingA -> Icon(Ios.flash, tr("Зарядка", "Charging"), tint = Tints.Green, modifier = Modifier.size(inner * 0.7f))
            is Act.HotspotA -> Icon(androidx.compose.material.icons.Icons.Rounded.WifiTethering,
                tr("Точка доступа", "Hotspot"), tint = Tints.Blue, modifier = Modifier.size(inner))
            is Act.InboxA -> AppIcon(act.items.last().appIcon, inner, Ios.chatbubble, Tints.Blue)
            is Act.PrivacyA -> {
                val u = act.usages.first()
                val glyph = when (u.sensor) {
                    com.akira.island.core.PrivacySensor.CAMERA -> androidx.compose.material.icons.Icons.Rounded.Videocam
                    com.akira.island.core.PrivacySensor.MICROPHONE -> androidx.compose.material.icons.Icons.Rounded.Mic
                    com.akira.island.core.PrivacySensor.LOCATION -> androidx.compose.material.icons.Icons.Rounded.LocationOn
                }
                val tint = when (u.sensor) {
                    com.akira.island.core.PrivacySensor.CAMERA -> Tints.Green
                    com.akira.island.core.PrivacySensor.MICROPHONE -> Tints.Orange
                    com.akira.island.core.PrivacySensor.LOCATION -> Tints.Blue
                }
                Icon(glyph, null, tint = tint, modifier = Modifier.size(inner * 0.8f))
            }
            else -> Unit
        }
    }
}

/**
 * Свечение вокруг острова: десять колец, всё прозрачнее наружу, — мягкий ореол без размытия, в запасе окна.
 * У «Как вырез» верх прямой, как у самого острова.
 */
fun DrawScope.islandGlow(color: Color, alpha: Float, corner: Float, notch: Boolean = false) {
    val spread = 10.dp.toPx()
    for (i in 1..10) {
        val d = spread * i / 10f
        drawIslandRect(color, Offset(-d, -d), Size(size.width + 2 * d, size.height + 2 * d), corner + d, notch, alpha = 0.06f * alpha)
    }
}

/** Контур формы острова со скруглением [corner] в [topLeft] — для свечения, волн и кромки вокруг него. */
private fun DrawScope.islandOutline(topLeft: Offset, size: Size, corner: Float, notch: Boolean, draw: DrawScope.(Outline) -> Unit) {
    val outline = islandShape(corner.coerceAtLeast(0f).toDp(), notch).createOutline(size, layoutDirection, this)
    translate(topLeft.x, topLeft.y) { draw(outline) }
}

/** Прямоугольник той же формы, что остров: у «Как вырез» верхние углы прямые. */
fun DrawScope.drawIslandRect(
    color: Color,
    topLeft: Offset,
    size: Size,
    corner: Float,
    notch: Boolean,
    style: DrawStyle = Fill,
    alpha: Float = 1f,
) = islandOutline(topLeft, size, corner, notch) { drawOutline(it, color, alpha, style) }

fun DrawScope.drawIslandRect(
    brush: Brush,
    topLeft: Offset,
    size: Size,
    corner: Float,
    notch: Boolean,
    style: DrawStyle = Fill,
    alpha: Float = 1f,
) = islandOutline(topLeft, size, corner, notch) { drawOutline(it, brush, alpha, style) }

/** Форма острова: капсула со скруглениями или, у «Как вырез», прямой верх у края экрана. */
fun islandShape(corner: Dp, s: IslandSettings): RoundedCornerShape =
    if (s.notch) RoundedCornerShape(s.notchTopRadius.dp, s.notchTopRadius.dp, corner, corner) else RoundedCornerShape(corner)

fun islandShape(corner: Dp, notch: Boolean): RoundedCornerShape =
    if (notch) RoundedCornerShape(0.dp, 0.dp, corner, corner) else RoundedCornerShape(corner)

/** Форма острова со скруглением из анимации: рамка и стекло читают его при рисовании и обновляются без пересборки. */
internal class AnimatedIslandShape(
    private val corner: State<Dp>,
    private val notch: Boolean,
    private val topRadius: Int,
    /** 0 — верх «выреза» прямой у края экрана, 1 — карточка опущена и верхние углы такие же, как нижние */
    private val detach: () -> Float = { 0f },
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        // A spring can overshoot past zero when the user selects square corners.
        val radius = corner.value.coerceAtLeast(0.dp)
        val top = lerp(topRadius.coerceAtLeast(0).dp, radius, detach().coerceIn(0f, 1f))
        val shape = if (notch) RoundedCornerShape(top, top, radius, radius) else RoundedCornerShape(radius)
        return shape.createOutline(size, layoutDirection, density)
    }
}

/** Заливка острова: цвет из «Цвета капсулы» с непрозрачностью из «Оформления». */
fun islandFill(s: IslandSettings): Color = Color(s.color).let { it.copy(alpha = it.alpha * s.islandOpacity.coerceIn(10, 100) / 100f) }

/**
 * Ширина раскрытого острова и широких плашек: почти во всю ширину экрана, уже — по «Ширине раскрытого острова»,
 * но не уже капсулы и не шире экрана.
 */
fun expandedWidth(screen: Dp, s: IslandSettings): Dp {
    val full = (screen - 22.dp).coerceAtMost(460.dp)
    return (full * (s.expandedWidthPercent.coerceIn(60, 100) / 100f)).coerceAtLeast((s.width.dp + 60.dp).coerceAtMost(full))
}


private val LocalActivityIds = staticCompositionLocalOf<List<String>> { emptyList() }

/** Stable aggregate: notification update times must not switch between downloads every second. */
internal fun groupedProgress(items: List<com.akira.island.core.ProgressInfo>): com.akira.island.core.ProgressInfo? {
    if (items.isEmpty()) return null
    val first = items.minWithOrNull(compareBy<com.akira.island.core.ProgressInfo> { it.postTime }.thenBy { it.key })!!
    if (items.size == 1) return first
    val unknown = items.any { it.indeterminate || it.max <= 0 }
    return first.copy(key = "downloads", title = trf("Загрузок: {0}", "Downloads: {0}", items.size),
        progress = (items.sumOf { it.fraction.toDouble() } / items.size * 1000).toInt(), max = 1000,
        indeterminate = unknown, transferDetails = null)
}
