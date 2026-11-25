package com.akira.island.core

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import com.akira.island.data.SettingsRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RingerNoticeTest {
    @Test fun rapidChangesCoalesceBehindAnotherNoticeAndRefreshTheVisiblePill() {
        val dispatcher = StandardTestDispatcher()
        val clock = dispatcher.scheduler
        Dispatchers.setMain(dispatcher)
        try {
            fun notice(text: String, ringer: Boolean = false) = Notice(text, icon = Icons.Rounded.Notifications,
                tint = Tints.Red, effect = if (ringer) NoticeEffect.RINGER else NoticeEffect.NONE)

            val duration = SettingsRepo.current.noticeDurationMs.toLong()
            val ordinary = notice("First")
            IslandBus.post(ordinary)
            clock.runCurrent()
            assertEquals(ordinary, IslandBus.notice.value)

            val vibrate = notice("Vibrate", true)
            IslandBus.post(notice("Ring", true))
            IslandBus.post(vibrate)
            clock.runCurrent()
            assertEquals(ordinary, IslandBus.notice.value)
            clock.advanceTimeBy(duration + 111)
            assertEquals(vibrate, IslandBus.notice.value)

            val last = notice("Last")
            IslandBus.post(last)
            clock.advanceTimeBy(duration - 100)
            val silent = notice("Silent", true)
            IslandBus.post(silent)
            assertEquals(silent, IslandBus.notice.value)
            clock.advanceTimeBy(duration - 1)
            assertEquals(silent, IslandBus.notice.value)
            clock.advanceTimeBy(112)
            assertEquals(last, IslandBus.notice.value)
            clock.advanceTimeBy(duration + 111)
            assertNull(IslandBus.notice.value)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
