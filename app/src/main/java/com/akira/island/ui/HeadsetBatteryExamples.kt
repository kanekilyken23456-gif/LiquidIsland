package com.akira.island.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.runtime.Composable
import com.akira.island.core.Notice
import com.akira.island.core.tr
import com.akira.island.service.BtKind
import com.akira.island.service.ConnectionEvent
import com.akira.island.service.HeadsetBatteryTracker
import com.akira.island.service.SystemEventReadings
import com.akira.island.service.bluetoothPowerNotice
import com.akira.island.service.btDeviceNotice

/**
 * Previews of the event banners, all in one card group: Bluetooth adapter on/off, a connected device with or without
 * a known charge, and the connection events (VPN, hotspot, debugging) with what the phone reports right now.
 */
@Composable
fun EventExamples(readings: SystemEventReadings, onExample: (Notice) -> Unit) {
    fun device(kind: BtKind, name: String, level: Int?) =
        onExample(btDeviceNotice(kind, name, true, HeadsetBatteryTracker.ring(level)))

    fun status(value: Boolean?): String = when {
        !readings.observing -> tr("остров выключен", "island is off")
        value == true -> tr("сейчас включено", "currently on")
        value == false -> tr("сейчас выключено", "currently off")
        else -> tr("нет данных от телефона", "no reading from phone")
    }.replaceFirstChar { it.uppercase() }

    Group {
        ExampleRow(
            Pos.First, Icons.Rounded.Bluetooth, Tile.Blue, "Bluetooth", null,
            listOf(
                tr("Вкл", "On") to { onExample(bluetoothPowerNotice(true)) },
                tr("Выкл", "Off") to { onExample(bluetoothPowerNotice(false)) },
            ),
        )
        ExampleRow(
            Pos.Middle, Icons.Rounded.Headphones, Tile.Indigo, tr("Устройство", "Device"), null,
            listOf(
                tr("Наушники", "Headphones") to { device(BtKind.HEADPHONES, "AirPods Pro", 75) },
                tr("Колонка", "Speaker") to { device(BtKind.SPEAKER, "JBL Flip 6", 60) },
                tr("Нет данных", "No data") to { device(BtKind.CAR, "", null) },
            ),
        )
        val kinds = ConnectionEvent.entries
        kinds.forEachIndexed { i, kind ->
            val value = when (kind) {
                ConnectionEvent.VPN -> readings.vpn
                ConnectionEvent.HOTSPOT -> readings.hotspot
                ConnectionEvent.USB_DEBUG -> readings.usbDebug
                ConnectionEvent.WIRELESS_DEBUG -> readings.wirelessDebug
            }
            ExampleRow(
                if (i == kinds.lastIndex) Pos.Last else Pos.Middle, kind.icon,
                if (kind == ConnectionEvent.VPN) Tile.Blue else Tile.Teal,
                kind.title, status(value),
                listOf(tr("Пример", "Example") to { onExample(kind.notice(value != true)) }),
            )
        }
    }
}
