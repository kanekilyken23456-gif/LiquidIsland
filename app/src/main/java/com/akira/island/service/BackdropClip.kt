package com.akira.island.service

import kotlin.math.ceil

/** Public native blur cannot clip unequal corner radii. Oversized asymmetric corners need
 * a central rectangle: a normalized native round-rect could otherwise protrude at the large end. */
internal fun backdropHorizontalInset(width: Int, height: Int, bottom: Float, top: Float): Int {
    val largest = maxOf(bottom, top).coerceAtLeast(0f)
    if (bottom == top || largest <= minOf(width, height)/2f) return 0
    return ceil(largest.coerceAtMost(width.coerceAtLeast(0)/2f)).toInt()
}

/** Расхождение окна размытия с карточкой, которое глазом не видно (px): не повод выключать размытие. */
internal const val BACKDROP_ALIGN_TOLERANCE_PX = 2

/** Сколько миллисекунд размытие остаётся выключенным после настоящего расхождения, чтобы не мигать. */
internal const val BACKDROP_BLUR_HOLD_MS = 120L

internal fun backdropWithinTolerance(dx: Int, dy: Int, dw: Int, dh: Int, tolerance: Int = BACKDROP_ALIGN_TOLERANCE_PX): Boolean =
    kotlin.math.abs(dx) <= tolerance && kotlin.math.abs(dy) <= tolerance &&
        kotlin.math.abs(dw) <= tolerance && kotlin.math.abs(dh) <= tolerance
