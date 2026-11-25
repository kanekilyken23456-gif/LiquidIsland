package com.akira.island.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothDevicesTest {
    @Test fun classifiesAudioDevices() {
        assertEquals(BtKind.HEADPHONES, BtKind.of(0x0418)) // headphones
        assertEquals(BtKind.HEADPHONES, BtKind.of(0x0404)) // wearable headset
        assertEquals(BtKind.HEADPHONES, BtKind.of(0x0408)) // handsfree
        assertEquals(BtKind.SPEAKER, BtKind.of(0x0414))    // loudspeaker
        assertEquals(BtKind.SPEAKER, BtKind.of(0x041C))    // portable audio
        assertEquals(BtKind.SPEAKER, BtKind.of(0x0428))    // hi-fi
        assertEquals(BtKind.CAR, BtKind.of(0x0420))        // car audio
        assertEquals(BtKind.HEADPHONES, BtKind.of(0x0400)) // unspecified audio/video keeps the old headphone look
    }

    @Test fun classifiesOtherDevices() {
        assertEquals(BtKind.WATCH, BtKind.of(0x0704))
        assertEquals(BtKind.PHONE, BtKind.of(0x0204))
        assertEquals(BtKind.COMPUTER, BtKind.of(0x010C))
        assertEquals(BtKind.KEYBOARD, BtKind.of(0x0540))
        assertEquals(BtKind.KEYBOARD, BtKind.of(0x05C0))
        assertEquals(BtKind.MOUSE, BtKind.of(0x0580))
        assertEquals(BtKind.OTHER, BtKind.of(0x0500))
        assertEquals(BtKind.OTHER, BtKind.of(null))
    }

    @Test fun onlyAudioKindsUseHeadsetToggles() {
        assertTrue(BtKind.SPEAKER.isAudio && BtKind.CAR.isAudio && BtKind.HEADPHONES.isAudio)
        assertFalse(BtKind.WATCH.isAudio || BtKind.KEYBOARD.isAudio || BtKind.OTHER.isAudio)
    }

    @Test fun repeatedLinkEventsAreNotNewEvents() {
        val t = BtLinkTracker()
        assertTrue(t.connect("a")); assertFalse(t.connect("a"))
        assertTrue(t.disconnect("a")); assertFalse(t.disconnect("a"))
        t.connect("b"); t.clear(); assertFalse(t.disconnect("b"))
    }
}
