package com.akira.island.island

import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test

class SquareCornerTest {
    @Test fun springOvershootStillProducesASquareOutline() {
        val overshoot = object : State<Dp> {
            override val value = (-2).dp
        }
        for (notch in listOf(false, true)) {
            val shape = AnimatedIslandShape(overshoot, notch, 0)
            val outline = shape.createOutline(Size(200f, 80f), LayoutDirection.Ltr, Density(1f))
            assertTrue(outline is Outline.Rectangle)
        }
    }
}
