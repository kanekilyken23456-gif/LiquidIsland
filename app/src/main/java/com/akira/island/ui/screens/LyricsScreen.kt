package com.akira.island.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.tr
import com.akira.island.data.SettingsRepo
import com.akira.island.ui.Group
import com.akira.island.ui.GroupStepSlider
import com.akira.island.ui.GroupToggle
import com.akira.island.ui.Page
import com.akira.island.ui.Pos
import com.akira.island.ui.Tile
import com.akira.island.ui.Ui

@Composable
fun LyricsScreen(onBack: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    Page(tr("Текст песен", "Lyrics"), onBack) {
        Group {
            GroupToggle(
                Pos.First,
                Icons.Rounded.LibraryMusic,
                tr("Текст песен", "Lyrics"),
                tr(
                    "Строка песни в раскрытом плеере заливается белым по мере пения",
                    "The current line in the expanded player fills with white as it's sung",
                ),
                s.lyricsEnabled,
                color = Tile.Pink,
            ) { v -> SettingsRepo.update { copy(lyricsEnabled = v) } }

            // Matches SizeScreen offset controls: GroupStepSlider + Reset action + ms unit.
            GroupStepSlider(
                Pos.Last,
                Icons.Rounded.Timer,
                tr("Смещение синхронизации", "Sync offset"),
                s.lyricsOffsetMs,
                -2000..2000,
                unit = "ms",
                action = tr("Сброс", "Reset") to {
                    SettingsRepo.update { copy(lyricsOffsetMs = 0) }
                },
                color = Tile.Blue,
            ) { v -> SettingsRepo.update { copy(lyricsOffsetMs = v) } }
        }
        Text(
            tr(
                "Текст ищется на LRCLIB, затем по очереди на NetEase, SyncLRC, LrcShare и LRC.cx. Для поиска отправляются название, исполнитель и длительность трека (альбом — только на LRCLIB). Караоке использует только настоящие метки времени; обычный текст можно читать и прокручивать без автоматического перелистывания. Во время инструментального вступления строка не подсвечивается. Нужен плеер с системной медиасессией и доступ к уведомлениям.\n\nПоложительное смещение задерживает строки (если они опережают музыку), отрицательное — сдвигает их вперёд (если они запаздывают).",
                "Lyrics are searched on LRCLIB, then NetEase, SyncLRC, LrcShare and LRC.cx in turn. The title, artist and track length are sent for the search (the album only to LRCLIB). Karaoke uses real timestamps only; plain lyrics can be read and scrolled without automatic progression. No line is highlighted during an instrumental intro. A player with a system media session and notification access is required.\n\nA positive offset delays the lines (when they appear ahead of the music); a negative offset advances them (when they lag behind).",
            ),
            color = Ui.Sub,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Ui.Card)
                .padding(20.dp),
        )
    }
}
