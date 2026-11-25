package com.akira.island.service

import org.junit.Assert.*
import org.junit.Test

class ShadeDetectionTest {
    @Test fun acceptsKnownVisiblePanelIdentifiers() {
        assertTrue(ShadeDetection.isPanel("com.android.systemui:id/notification_panel", "android.widget.FrameLayout"))
        assertTrue(ShadeDetection.isPanel("", "com.android.systemui.shade.NotificationShadeWindowView"))
    }
    @Test fun acceptsAndroid16ShadeIdentifiers() {
        for (id in listOf("quick_settings_panel", "expanded_qs_scroll_view", "quick_settings_container", "split_shade_status_bar"))
            assertTrue(ShadeDetection.isPanel("com.android.systemui:id/$id", "android.view.View"))
    }
    @Test fun unrelatedSystemWindowsAreNotTheShade() {
        assertFalse(ShadeDetection.isPanel("com.android.systemui:id/navigation_bar_view", "android.widget.FrameLayout"))
        assertFalse(ShadeDetection.isPanel("com.android.systemui:id/keyguard_status_view", "android.widget.FrameLayout"))
        assertFalse(ShadeDetection.isPanel("", "android.inputmethodservice.SoftInputWindow"))
    }
}
