package com.akira.island.ui.screens

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VerticalAlignCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.IslandBus
import com.akira.island.core.tr
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import com.akira.island.island.EXPAND_BELOW_GAP_MAX_DP
import com.akira.island.island.rememberHaptic
import com.akira.island.ui.Group
import com.akira.island.ui.GroupStepSlider
import com.akira.island.ui.GroupToggle
import com.akira.island.ui.Page
import com.akira.island.ui.Pos
import com.akira.island.ui.Tile
import com.akira.island.ui.Ui

/**
 * Положение и размер острова: сдвиг по горизонтали, отступ сверху, ширина и высота пустой капсулы.
 * Пока экран открыт, остров на телефоне красный с белым ободком — так видно, где он стоит
 * относительно камеры. «Сохранить» оставляет новые значения, «назад» возвращает прежние.
 * «Карточка под строкой состояния» опускает любую раскрытую карточку под строку состояния; пока двигают
 * отступ, на острове показывается красная пробная карточка.
 */
@Composable
fun SizeScreen(onBack: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    val original = remember { SettingsRepo.current }
    val haptic = rememberHaptic()

    DisposableEffect(Unit) {
        IslandBus.calibrating.value = true
        onDispose {
            IslandBus.calibrating.value = false
            IslandBus.cardPreviewUntil.value = 0L
        }
    }
    // Красная пробная карточка живёт пару секунд после каждого изменения — видно, где она встанет
    fun previewCard() { IslandBus.cardPreviewUntil.value = SystemClock.elapsedRealtime() + CARD_PREVIEW_MS }

    val cancel = {
        SettingsRepo.update {
            withPlacement(original)
        }
        onBack()
    }
    BackHandler(onBack = cancel)

    fun set(block: IslandSettings.() -> IslandSettings) = SettingsRepo.update(block)

    Box(Modifier.fillMaxSize()) {
        Page(
            tr("Положение и размер", "Position and size"),
            onBack = cancel,
            action = {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Ui.Card).clickable {
                        set { withPlacement(IslandSettings()) }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Restore, tr("Сбросить", "Reset"), tint = Ui.Text)
                }
            },
        ) {
            Group {
                // Камера слева или справа — сдвиньте капсулу к ней; широкие плашки всё равно остаются на экране
                GroupStepSlider(
                    Pos.First, Icons.Rounded.SwapHoriz, tr("По горизонтали", "Horizontal"), s.offsetX, -200..200,
                    action = tr("Центр", "Center") to { set { copy(offsetX = 0) } }, color = Tile.Blue,
                ) { v -> set { copy(offsetX = v) } }
                GroupStepSlider(Pos.Middle, Icons.Rounded.SwapVert, tr("Отступ сверху", "Top offset"),
                    if (s.notch) s.notchOffsetY else s.offsetY, if (s.notch) -10..30 else -10..120, color = Tile.Indigo) { v ->
                    set { if (notch) copy(notchOffsetY = v) else copy(offsetY = v) }
                }
                GroupToggle(
                    Pos.Last, Icons.Rounded.WaterDrop, tr("Как вырез", "Like a notch"),
                    tr("Для камеры-капли: остров прилегает к верху экрана и закрывает вырез",
                        "For a waterdrop camera: the island sits against the top of the screen and covers the notch"),
                    s.notch, color = Tile.Teal,
                ) { v -> set { copy(notch = v) } }
            }
            Spacer(Modifier.height(16.dp))
            Group {
                GroupStepSlider(Pos.First, Icons.Rounded.Straighten, tr("Ширина", "Width"), s.width, 50..400, color = Tile.Purple) { v ->
                    set { copy(width = v) }
                }
                GroupStepSlider(Pos.Last, Icons.Rounded.Height, tr("Высота", "Height"), s.height, 18..90, color = Tile.Pink) { v ->
                    set { copy(height = v) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Group {
                // Капсула остаётся у камеры, а раскрытая карточка любой активности или события опускается под строку состояния
                GroupToggle(
                    if (s.expandBelowStatusBar) Pos.First else Pos.Single, Icons.Rounded.VerticalAlignBottom,
                    tr("Карточка под строкой состояния", "Card below the status bar"),
                    tr("Раскрытые карточки опускаются ниже строки состояния и не закрывают часы, сеть и батарею",
                        "Expanded cards drop below the status bar so they don't cover the clock, network and battery"),
                    s.expandBelowStatusBar, color = Tile.Orange,
                ) { v ->
                    set { copy(expandBelowStatusBar = v) }
                    if (v) {
                        previewCard()
                    } else {
                        IslandBus.cardPreviewUntil.value = 0L
                    }
                }
                if (s.expandBelowStatusBar) {
                    GroupStepSlider(
                        Pos.Last, Icons.Rounded.VerticalAlignCenter, tr("Отступ от строки состояния", "Distance from the status bar"),
                        s.expandBelowGap, 0..EXPAND_BELOW_GAP_MAX_DP,
                        action = tr("Сброс", "Reset") to { set { copy(expandBelowGap = IslandSettings().expandBelowGap) }; previewCard() },
                        color = Tile.Orange,
                    ) { v ->
                        set { copy(expandBelowGap = v) }
                        previewCard()
                    }
                }
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(Ui.Primary)
                .clickable { haptic(); onBack() }
                .padding(horizontal = 30.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Check, null, tint = Ui.OnPrimary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(tr("Сохранить", "Save"), color = Ui.OnPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Всё, что настраивается на этом экране, — из [from]: для «назад» и «Сбросить». */
private fun IslandSettings.withPlacement(from: IslandSettings) =
    copy(offsetX = from.offsetX, offsetY = from.offsetY, notchOffsetY = from.notchOffsetY,
        width = from.width, height = from.height, notch = from.notch,
        expandBelowStatusBar = from.expandBelowStatusBar, expandBelowGap = from.expandBelowGap)

/** Сколько миллисекунд красная пробная карточка остаётся после последнего изменения */
private const val CARD_PREVIEW_MS = 2_500L
