package com.akira.island.service

/**
 * The ROM draws its own chip (red screen recording, green call) next to the clock. It is redundant, and it
 * collides with the island, whenever the island already shows the same activity itself.
 */
internal fun hidesRomChip(screenRecording: Boolean, callShown: Boolean): Boolean = screenRecording || callShown
