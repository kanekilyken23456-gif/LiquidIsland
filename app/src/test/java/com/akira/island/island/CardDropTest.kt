package com.akira.island.island

import com.akira.island.data.IslandSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class CardDropTest {
    @Test
    fun disabledSettingNeverMovesTheCard() {
        assertEquals(0f, cardDropDp(IslandSettings(expandBelowStatusBar = false, expandBelowGap = 40), 24f), 0f)
    }

    @Test
    fun cardTopLandsBelowTheStatusBarByTheGap() {
        val s = IslandSettings(expandBelowStatusBar = true, expandBelowGap = 8, offsetY = 11)
        // The island already starts 11 dp from the top, so only the difference is added.
        assertEquals(24f + 8f - 11f, cardDropDp(s, 24f), 0f)
        assertEquals(32f, 11f + cardDropDp(s, 24f), 0f)
    }

    @Test
    fun notchPlacementStartsAtTheEdgeOfTheScreen() {
        val s = IslandSettings(expandBelowStatusBar = true, expandBelowGap = 0, notch = true, notchOffsetY = 0)
        assertEquals(24f, cardDropDp(s, 24f), 0f)
    }

    @Test
    fun islandAlreadyBelowTheBarDoesNotMove() {
        val s = IslandSettings(expandBelowStatusBar = true, expandBelowGap = 4, offsetY = 100)
        assertEquals(0f, cardDropDp(s, 24f), 0f)
    }

    @Test
    fun gapIsClampedToTheSliderRange() {
        val s = IslandSettings(expandBelowStatusBar = true, expandBelowGap = 500, offsetY = 0)
        assertEquals(24f + EXPAND_BELOW_GAP_MAX_DP, cardDropDp(s, 24f), 0f)
        assertEquals(24f, cardDropDp(s.copy(expandBelowGap = -9), 24f), 0f)
    }

    @Test
    fun dropAlwaysFitsInTheReservedWindowBand() {
        val s = IslandSettings(expandBelowStatusBar = true, expandBelowGap = EXPAND_BELOW_GAP_MAX_DP, notch = true, notchOffsetY = -10)
        // Even a tall 64 dp status bar must stay within the extra band the service adds to the window.
        assert(cardDropDp(s, 64f) <= EXPAND_BELOW_BAND_DP)
    }
}
