package com.akira.island.island

import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.IslandBus
import com.akira.island.core.Lang
import com.akira.island.core.tr
import com.akira.island.data.ExpandStyle
import com.akira.island.data.ExtraSettings
import com.akira.island.data.IslandSettings
import com.akira.island.service.WeatherInfo
import com.akira.island.service.WeatherRepo
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun PanelWeather(s: IslandSettings, extras: ExtraSettings, now: Long) {
    val weather by WeatherRepo.state.collectAsState()
    val loading by WeatherRepo.loading.collectAsState()
    val error by WeatherRepo.error.collectAsState()
    val locked by IslandBus.locked.collectAsState()
    val scope = rememberCoroutineScope()
    WeatherStrip(s, briefDetails(extras, locked, null, weather, now, loading, error)) {
        scope.launch { WeatherRepo.refresh() }
    }
}

@Composable
private fun WeatherStrip(s: IslandSettings, details: BriefDetails, refresh: () -> Unit) {
    val city = details.weatherCity ?: return
    val weather = details.weather
    val preview = LocalIslandPreview.current
    val status = when {
        details.weatherLoading -> tr("Загрузка погоды…", "Loading weather…")
        details.weatherError != null -> details.weatherError
        weather != null -> weather.summary()
        else -> tr("Погода пока недоступна", "Weather is not available yet")
    }
    Column(Modifier.fillMaxWidth().padding(top = 5.dp).clip(RoundedCornerShape(13.dp))
        .background(Color.White.copy(alpha = 0.075f))
        .clickable(enabled = !preview && !details.weatherLoading, role = Role.Button,
            onClickLabel = tr("Обновить погоду", "Refresh weather"), onClick = refresh)
        .heightIn(min = 48.dp).padding(horizontal = 9.dp, vertical = 5.dp).testTag("panel-weather")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            if (weather != null) WeatherSymbol(weather, s)
            else Icon(Icons.Rounded.CloudQueue, null, tint = Color.LightGray, modifier = Modifier.size(30.dp))
            Column(Modifier.weight(1f)) {
                Text(city, color = Color.White, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold.island,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(status, color = Color.LightGray, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (weather != null) Text(String.format(Lang.locale, "%.0f°", weather.temperature), color = Color.White,
                fontSize = 22.sp, style = Digits, maxLines = 1)
        }
        if (weather != null) {
            val context = LocalContext.current
            val time = DateFormat.getTimeFormat(context).format(Date(weather.updatedAt))
            Text("Open-Meteo · CC BY 4.0 · $time", color = Color.LightGray, fontSize = 10.sp, lineHeight = 12.sp)
        }
    }
}

@Composable
internal fun WeatherSymbol(weather: WeatherInfo, s: IslandSettings, diameter: Dp = 36.dp) {
    val clear = weather.code in 0..2
    val night = weather.isDay == false
    val sun = clear && !night
    val screenOn by IslandBus.screenOn.collectAsState()
    val progress = remember(weather.code, night) { Animatable(1f) }
    LaunchedEffect(progress, screenOn, s.reduceEffects, s.expandStyle) {
        if (screenOn && !s.reduceEffects && s.expandStyle != ExpandStyle.INSTANT) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(1250))
        } else progress.snapTo(1f)
    }
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        if (clear) {
            val halo = if (sun) Color(0xFFFFC351) else Color(0xFFAEBFFF)
            val clouds = weather.code != 0
            Box(Modifier.align(if (clouds) Alignment.TopStart else Alignment.Center)
                .fillMaxSize(if (clouds) 0.78f else 1f).drawWithCache {
                    val brush = Brush.radialGradient(listOf(halo.copy(alpha = 0.65f), Color.Transparent),
                        radius = size.minDimension / 2)
                    onDrawBehind {
                        val glow = sin(PI * progress.value).toFloat()
                        drawCircle(brush, alpha = 0.55f + 0.38f * glow)
                    }
                }, contentAlignment = Alignment.Center) {
                Icon(if (sun) Icons.Rounded.WbSunny else Icons.Rounded.DarkMode, null, tint = halo,
                    modifier = Modifier.fillMaxSize(0.66f).graphicsLayer { rotationZ = (progress.value - 1f) * 22f })
            }
            if (clouds) Icon(Icons.Rounded.Cloud, null, tint = Color(0xFFD7E1EB),
                modifier = Modifier.align(Alignment.BottomEnd).fillMaxSize(0.64f).graphicsLayer { translationX = (1f - progress.value) * 3.dp.toPx() })
        } else {
            val icon = when (weather.code) {
                45, 48 -> Icons.Rounded.Air
                in 51..67, in 80..82 -> Icons.Rounded.WaterDrop
                in 71..77, 85, 86 -> Icons.Rounded.AcUnit
                in 95..99 -> Icons.Rounded.Thunderstorm
                else -> Icons.Rounded.Cloud
            }
            Icon(icon, null, tint = Color(0xFFB8D6EF), modifier = Modifier.fillMaxSize(0.7f)
                .graphicsLayer { translationY = (1f - progress.value) * -3.dp.toPx() })
        }
    }
}
