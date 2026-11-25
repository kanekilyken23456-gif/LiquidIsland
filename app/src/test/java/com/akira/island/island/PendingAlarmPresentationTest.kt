package com.akira.island.island

import com.akira.island.core.AlarmInfo
import org.junit.Assert.*
import org.junit.Test

class PendingAlarmPresentationTest {
    private val alarm = Act.AlarmA(AlarmInfo(123456L, null, null))
    private val other = Act.InboxA(emptyList())

    @Test fun alarmAloneLeavesTheCapsuleIdleAndUsesTheBubble() {
        assertEquals(Act.Idle, primaryWithPendingAlarm(listOf(alarm), false, null))
        assertEquals(alarm, secondaryWithPendingAlarm(listOf(alarm), Act.Idle))
    }
    @Test fun explicitAlarmSelectionOpensItsCardUntilCollapse() {
        assertEquals(alarm, primaryWithPendingAlarm(listOf(alarm), true, "alarm"))
        assertEquals(Act.Idle, primaryWithPendingAlarm(listOf(alarm), false, "alarm"))
        assertEquals(Act.Idle, primaryWithPendingAlarm(listOf(alarm), true, "idle"))
    }
    @Test fun otherActivitiesKeepTheirPriorityAndAreNotDuplicated() {
        assertEquals(other, primaryWithPendingAlarm(listOf(alarm, other), false, null))
        assertEquals(alarm, secondaryWithPendingAlarm(listOf(alarm, other), other))
        assertNull(secondaryWithPendingAlarm(listOf(other), other))
    }
    @Test fun explicitControlsSelectionOverridesAnOrdinaryActivityUntilCollapse() {
        assertEquals(Act.Idle, primaryWithPendingAlarm(listOf(other, alarm), true, Act.Idle.id))
        assertEquals(other, primaryWithPendingAlarm(listOf(other, alarm), false, null))
    }

    @Test fun aRingingTimerCanInterruptExplicitControls() {
        val ringing = Act.TimerA(com.akira.island.core.TimerState(60_000L, 123_456L, ringing = true))
        assertEquals(ringing, primaryWithPendingAlarm(listOf(ringing, other), true, Act.Idle.id))
    }

    @Test fun noAlarmPreservesTheExistingFallback() {
        assertEquals(Act.Idle, primaryWithPendingAlarm(emptyList(), false, null))
        assertEquals(other, primaryWithPendingAlarm(listOf(other), false, null))
        assertEquals(other, secondaryWithPendingAlarm(listOf(alarm, other), alarm))
    }
}
