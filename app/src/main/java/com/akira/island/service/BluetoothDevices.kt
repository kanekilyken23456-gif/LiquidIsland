package com.akira.island.service

/** What a connected Bluetooth device is, decided from its Bluetooth class only (never from the name). */
enum class BtKind {
    HEADPHONES, SPEAKER, CAR, WATCH, PHONE, COMPUTER, KEYBOARD, MOUSE, OTHER;

    /** Audio devices follow the "Headphones and speakers" toggles, the rest follow "Other devices". */
    val isAudio: Boolean get() = this == HEADPHONES || this == SPEAKER || this == CAR

    companion object {
        private const val MAJOR_MASK = 0x1F00
        private const val MAJOR_COMPUTER = 0x0100
        private const val MAJOR_PHONE = 0x0200
        private const val MAJOR_AUDIO = 0x0400
        private const val MAJOR_PERIPHERAL = 0x0500
        private const val MAJOR_WEARABLE = 0x0700

        // BluetoothClass.Device.AUDIO_VIDEO_*
        private const val LOUDSPEAKER = 0x0414
        private const val PORTABLE_AUDIO = 0x041C
        private const val CAR_AUDIO = 0x0420
        private const val HIFI_AUDIO = 0x0428

        /** [deviceClass] is BluetoothClass.getDeviceClass(); null when the class is unknown (e.g. BLE-only). */
        fun of(deviceClass: Int?): BtKind {
            if (deviceClass == null) return OTHER
            return when (deviceClass and MAJOR_MASK) {
                MAJOR_AUDIO -> when (deviceClass) {
                    LOUDSPEAKER, PORTABLE_AUDIO, HIFI_AUDIO -> SPEAKER
                    CAR_AUDIO -> CAR
                    else -> HEADPHONES
                }
                MAJOR_WEARABLE -> WATCH
                MAJOR_PHONE -> PHONE
                MAJOR_COMPUTER -> COMPUTER
                MAJOR_PERIPHERAL -> when (deviceClass and 0x00C0) {
                    0x40, 0xC0 -> KEYBOARD
                    0x80 -> MOUSE
                    else -> OTHER
                }
                else -> OTHER
            }
        }
    }
}

/**
 * Debounces ACL broadcasts: repeated "connected" for the same device (BLE reconnects) is not a new event,
 * and a "disconnected" is only an event for a device we saw connect. Addresses stay in memory only.
 */
internal class BtLinkTracker {
    private val connected = LinkedHashSet<String>()

    /** True when [address] just went from disconnected to connected. */
    fun connect(address: String): Boolean {
        val added = connected.add(address)
        if (connected.size > 24) connected.remove(connected.first())
        return added
    }

    /** True when [address] was connected and just disconnected. */
    fun disconnect(address: String): Boolean = connected.remove(address)

    fun clear() = connected.clear()
}
