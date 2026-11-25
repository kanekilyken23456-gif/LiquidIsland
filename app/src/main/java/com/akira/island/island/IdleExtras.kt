package com.akira.island.island

import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.CallLog
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.Controls
import com.akira.island.core.IslandBus
import com.akira.island.core.tr
import com.akira.island.data.ExtrasRepo
import com.akira.island.data.IslandSettings

@Composable
fun IdleExtras(s: IslandSettings) {
    val context = LocalContext.current
    val extras by ExtrasRepo.state.collectAsState()
    val locked by IslandBus.locked.collectAsState()
    val steps by com.akira.island.service.StepRepo.count.collectAsState()
    val standaloneInfo = !extras.briefEnabled && (extras.steps || extras.analogClock)
    if (!extras.controls && !standaloneInfo) return
    fun open(intent: Intent): Boolean =
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onSuccess { Controls.collapse() }.isSuccess
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        if (extras.analogClock && !extras.briefEnabled) AnalogClock(rememberWallNow(1000), Modifier.fillMaxWidth().height(110.dp))
        if (extras.steps && !locked && !extras.briefEnabled) Text(tr("Шаги сегодня", "Steps today") + ": " + (steps?.toString() ?: "…"), color = Color.White, fontSize = 14.sp)
        if (extras.controls && !locked) {
            val audio = remember { context.getSystemService(AudioManager::class.java) }
            val maximum = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 1
            var volume by remember { mutableFloatStateOf((audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0).toFloat()) }
            var changingVolume by remember { mutableStateOf(false) }
            LaunchedEffect(audio) {
                while (true) {
                    if (!changingVolume) volume = runCatching { audio?.getStreamVolume(AudioManager.STREAM_MUSIC)?.toFloat() ?: volume }.getOrDefault(volume)
                    kotlinx.coroutines.delay(1000)
                }
            }
            Text(tr("Громкость мультимедиа", "Media volume"), color = Color.White, fontSize = 13.sp)
            Slider(volume.coerceIn(0f, maximum.toFloat()), onValueChange = { v ->
                changingVolume = true
                if (audio != null && runCatching {
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, v.toInt(), 0)
                }.isSuccess) volume = v
            }, onValueChangeFinished = { changingVolume = false }, valueRange = 0f..maximum.toFloat())
            var brightness by remember { mutableFloatStateOf(runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrDefault(128).toFloat()) }
            if (Settings.System.canWrite(context)) {
                Text(tr("Яркость экрана", "Screen brightness"), color = Color.White, fontSize = 13.sp)
                Slider(brightness.coerceIn(1f, 255f), onValueChange = { v ->
                    val ok = runCatching {
                        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, v.toInt())
                    }.getOrDefault(false)
                    if (ok) brightness = v
                }, valueRange = 1f..255f)
            } else TextButton(onClick = { open(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))) }) {
                Text(tr("Разрешить управление яркостью", "Allow brightness controls"))
            }
            Row {
                TextButton(onClick = {
                    if (!open(Intent(Intent.ACTION_VIEW).setType(CallLog.Calls.CONTENT_TYPE)) &&
                        !open(Intent(Intent.ACTION_VIEW, CallLog.Calls.CONTENT_URI))) {
                        Toast.makeText(context, tr("Не удалось открыть журнал звонков", "Could not open call history"),
                            Toast.LENGTH_SHORT).show()
                    }
                }) { Text(tr("Журнал звонков", "Call history")) }
                TextButton(onClick = { open(Intent(context, com.akira.island.ui.MainActivity::class.java)) }) { Text(tr("Настройки", "Settings")) }
            }
        }
    }
}

@Composable
internal fun AnalogClock(now: Long, modifier: Modifier) {
    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = now }
    androidx.compose.foundation.Canvas(modifier) {
        val radius = size.minDimension / 2 - 6.dp.toPx()
        val middle = center
        drawCircle(Color.White.copy(alpha = 0.15f), radius)
        fun hand(degrees: Float, length: Float, width: Float) {
            val angle = Math.toRadians((degrees - 90).toDouble())
            drawLine(Color.White, middle, androidx.compose.ui.geometry.Offset(middle.x + kotlin.math.cos(angle).toFloat() * radius * length, middle.y + kotlin.math.sin(angle).toFloat() * radius * length), width.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
        for (i in 0 until 12) {
            val a = Math.toRadians((i * 30 - 90).toDouble())
            drawCircle(Color.White, 1.5.dp.toPx(), androidx.compose.ui.geometry.Offset(middle.x + kotlin.math.cos(a).toFloat() * radius * 0.85f, middle.y + kotlin.math.sin(a).toFloat() * radius * 0.85f))
        }
        val minute = calendar.get(java.util.Calendar.MINUTE)
        hand(calendar.get(java.util.Calendar.HOUR) * 30f + minute / 2f, 0.5f, 3f)
        hand(minute * 6f, 0.75f, 2f)
        drawCircle(Color.White, 3.dp.toPx())
    }
}
