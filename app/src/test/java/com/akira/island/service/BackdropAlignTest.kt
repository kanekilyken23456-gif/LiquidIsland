package com.akira.island.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropAlignTest {
    @Test fun spring_tail_jitter_of_a_pixel_or_two_keeps_the_blur() {
        assertTrue(backdropWithinTolerance(0, 0, 0, 0))
        assertTrue(backdropWithinTolerance(1, -1, 0, 1))
        assertTrue(backdropWithinTolerance(0, 2, -2, 0))
    }

    @Test fun a_visible_misplacement_still_switches_the_blur_off() {
        assertFalse(backdropWithinTolerance(0, 3, 0, 0))
        assertFalse(backdropWithinTolerance(-9, 0, 0, 0))
        assertFalse(backdropWithinTolerance(0, 0, 0, 40))
    }
}
