package com.akira.island.service

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Adb
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.ui.graphics.vector.ImageVector
import com.akira.island.core.Notice
import com.akira.island.core.Tints
import com.akira.island.core.tr
import kotlinx.coroutines.flow.MutableStateFlow

/** null means that Android has not supplied a reading, never an invented Off state. */
data class SystemEventReadings(
    val observing: Boolean = false,
    val vpn: Boolean? = null,
    val hotspot: Boolean? = null,
    val usbDebug: Boolean? = null,
    val wirelessDebug: Boolean? = null,
)

object SystemEventObservation {
    val state = MutableStateFlow(SystemEventReadings())
}

/** Initial snapshots and repeated OEM broadcasts are not changes. */
internal fun eventChanged(before: Boolean?, after: Boolean?, initial: Boolean = false): Boolean =
    !initial && before != null && after != null && before != after

enum class ConnectionEvent {
    VPN, HOTSPOT, USB_DEBUG, WIRELESS_DEBUG;

    val title: String get() = when (this) {
        VPN -> "VPN"
        HOTSPOT -> tr("Точка доступа", "Hotspot")
        USB_DEBUG -> tr("Отладка USB", "USB debugging")
        WIRELESS_DEBUG -> tr("Отладка Wi-Fi", "Wireless debugging")
    }
    val icon: ImageVector get() = when (this) {
        VPN -> Icons.Rounded.VpnKey
        HOTSPOT -> Icons.Rounded.WifiTethering
        USB_DEBUG -> Icons.Rounded.Usb
        WIRELESS_DEBUG -> Icons.Rounded.Adb
    }
    fun notice(on: Boolean) = Notice(title, if (on) tr("Вкл", "On") else tr("Выкл", "Off"), icon,
        if (!on) Tints.Gray else if (this == VPN || this == HOTSPOT) Tints.Blue else Tints.Teal)
}

val BtKind.icon: ImageVector get() = when (this) {
    BtKind.HEADPHONES -> Icons.Rounded.Headphones
    BtKind.SPEAKER -> Icons.Rounded.Speaker
    BtKind.CAR -> Icons.Rounded.DirectionsCar
    BtKind.WATCH -> Icons.Rounded.Watch
    BtKind.PHONE -> Icons.Rounded.Smartphone
    BtKind.COMPUTER -> Icons.Rounded.Computer
    BtKind.KEYBOARD -> Icons.Rounded.Keyboard
    BtKind.MOUSE -> Icons.Rounded.Mouse
    BtKind.OTHER -> Icons.Rounded.BluetoothConnected
}

/** Shown as the title when Android does not give the device name. */
val BtKind.label: String get() = when (this) {
    BtKind.HEADPHONES -> tr("Наушники", "Headphones")
    BtKind.SPEAKER -> tr("Колонка", "Speaker")
    BtKind.CAR -> tr("Автомобиль", "Car")
    BtKind.WATCH -> tr("Часы", "Watch")
    BtKind.PHONE -> tr("Телефон", "Phone")
    BtKind.COMPUTER -> tr("Компьютер", "Computer")
    BtKind.KEYBOARD -> tr("Клавиатура", "Keyboard")
    BtKind.MOUSE -> tr("Мышь", "Mouse")
    BtKind.OTHER -> tr("Устройство Bluetooth", "Bluetooth device")
}

/** Same look as headphones before: icon and name on the left, charge ring or "Connected" on the right. */
fun btDeviceNotice(kind: BtKind, name: String?, connected: Boolean, ring: Float? = null): Notice = Notice(
    title = name?.takeIf { it.isNotBlank() } ?: kind.label,
    icon = kind.icon,
    tint = if (connected) Tints.Green else Tints.Gray,
    ring = if (connected) ring else null,
    caption = if (connected) tr("Подключено", "Connected") else tr("Отключено", "Disconnected"),
)

fun bluetoothPowerNotice(on: Boolean): Notice = Notice("Bluetooth", if (on) tr("Вкл", "On") else tr("Выкл", "Off"),
    if (on) Icons.Rounded.Bluetooth else Icons.Rounded.BluetoothDisabled, if (on) Tints.Blue else Tints.Gray)
