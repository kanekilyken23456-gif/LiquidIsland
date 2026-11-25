package com.akira.island.island

/** A scheduled alarm may occupy the main card only after an explicit selection. */
internal fun primaryWithPendingAlarm(acts: List<Act>, expanded: Boolean, focus: String?): Act {
    val primary = acts.firstOrNull { it !is Act.AlarmA || expanded && focus == it.id } ?: return Act.Idle
    if (!expanded || focus != Act.Idle.id) return primary
    // Explicitly opening the idle controls should still let urgent events take precedence.
    return when {
        primary.transient -> primary
        primary is Act.CallA && primary.c.incoming -> primary
        primary is Act.TimerA && primary.t.ringing -> primary
        else -> Act.Idle
    }
}

internal fun secondaryWithPendingAlarm(acts: List<Act>, primary: Act): Act? =
    acts.firstOrNull { !it.transient && it.id != primary.id }
