package com.akira.island.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallRulesTest {
    @Test fun callHidesRomChipLikeScreenRecording() {
        assertTrue(hidesRomChip(screenRecording = false, callShown = true))
        assertTrue(hidesRomChip(screenRecording = true, callShown = false))
        assertFalse(hidesRomChip(screenRecording = false, callShown = false))
    }
}
