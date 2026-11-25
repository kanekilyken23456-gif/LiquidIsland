package com.akira.island.island

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.akira.island.core.IslandBus
import com.akira.island.core.Tints
import com.akira.island.data.ExpandStyle
import com.akira.island.data.IslandSettings
import kotlin.math.PI
import kotlin.math.sin

/** Two short green crests inside the charging surface, behind its battery reading. */
@Composable
internal fun chargingWave(s: IslandSettings, eventId: Long): Modifier {
    val screenOn by IslandBus.screenOn.collectAsState()
    if (!screenOn || s.reduceEffects || s.expandStyle == ExpandStyle.INSTANT) return Modifier
    val phase = remember(eventId) { Animatable(0f) }
    LaunchedEffect(phase) { phase.animateTo(1f, tween(1050, easing = LinearEasing)) }
    return Modifier.drawWithCache {
        val w = size.width
        val h = size.height
        val depth = minOf(h * 0.23f, 12.dp.toPx())
        val crest = Path().apply {
            moveTo(-w, h - depth)
            for (i in -1..1) {
                val x = i * w
                cubicTo(x + w * 0.25f, h - 2 * depth, x + w * 0.25f, h, x + w * 0.5f, h - depth)
                cubicTo(x + w * 0.75f, h - 2 * depth, x + w * 0.75f, h, x + w, h - depth)
            }
            lineTo(2 * w, h + depth)
            lineTo(-w, h + depth)
            close()
        }
        onDrawBehind {
            val t = phase.value
            if (t > 0f && t < 1f) {
                val envelope = sin(PI * t).toFloat()
                translate(left = w * t * 0.65f) { drawPath(crest, Tints.Green, alpha = 0.25f * envelope) }
                translate(left = w * (0.3f - t * 0.4f), top = depth * 0.36f) {
                    drawPath(crest, Tints.Green, alpha = 0.22f * envelope)
                }
            }
        }
    }
}
