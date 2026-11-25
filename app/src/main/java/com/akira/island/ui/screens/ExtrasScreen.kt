package com.akira.island.ui.screens

import android.content.Intent
import android.content.pm.ResolveInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.akira.island.core.Controls
import com.akira.island.core.IslandBus
import com.akira.island.core.tr
import com.akira.island.data.BriefClockStyle
import com.akira.island.data.ContactShortcut
import com.akira.island.data.ExtrasRepo
import com.akira.island.data.SettingsRepo
import com.akira.island.island.Act
import com.akira.island.service.WeatherCity
import com.akira.island.service.WeatherRepo
import com.akira.island.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ExtrasScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val s by ExtrasRepo.state.collectAsState()
    val island by SettingsRepo.state.collectAsState()
    val serviceRunning by IslandBus.serviceRunning.collectAsState()
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<ResolveInfo>?>(null) }
    var query by remember { mutableStateOf("") }
    var cities by remember { mutableStateOf<List<WeatherCity>>(emptyList()) }
    var cityError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var stepError by remember { mutableStateOf<String?>(null) }
    var clockStyle by remember { mutableStateOf(false) }
    val stepPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        ExtrasRepo.update { copy(steps = granted) }
        if (!granted) stepError = tr("Разрешите доступ к физической активности для подсчёта шагов.",
            "Allow physical activity access to count steps.")
    }
    val contact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null && uri.scheme == "content" && uri.authority == "com.android.contacts") {
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf("display_name"), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull() ?: tr("Контакт", "Contact")
            ExtrasRepo.update { copy(contacts = (contacts.filter { it.uri != uri.toString() } + ContactShortcut(name, uri.toString())).takeLast(6)) }
        }
    }
    Page(tr("Дополнительная панель", "Extra controls"), onBack) {
        if (serviceRunning && island.enabled) {
            GroupItem(Pos.Single, Icons.AutoMirrored.Rounded.OpenInNew, tr("Открыть на острове", "Open on the island"), null,
                color = Tile.Purple, onClick = { Controls.expand(Act.Idle.id) })
        } else {
            SettingsHint(tr("Включите остров на Главной, чтобы открыть панель.", "Enable the island on Home to open the panel."))
        }
        GroupToggle(Pos.Single, Icons.Rounded.Dashboard, tr("Сводка", "Brief"),
            tr("Часы, шаги и погода в раскрытом пустом острове", "Clock, steps and weather in the expanded idle island"),
            s.briefEnabled, color = Tile.Purple) { ExtrasRepo.update { copy(briefEnabled = it) } }
        if (s.briefEnabled) {
            GroupItem(Pos.Single, Icons.Rounded.Schedule, tr("Стиль часов", "Clock style"), s.briefClockStyle.title,
                color = Tile.Purple, onClick = { clockStyle = true })
            GroupToggle(Pos.Single, Icons.AutoMirrored.Rounded.DirectionsWalk, tr("Страница шагомера", "Step counter page"),
                tr("Показывать страницу шагомера в сводке", "Show the step counter page in Brief"),
                s.briefShowSteps, color = Tile.Green) { ExtrasRepo.update { copy(briefShowSteps = it) } }
            SettingsHint(tr("Сводка использует включённый шагомер и погоду выбранного города. Ярлыки и управление остаются ниже. Шаги, погода и ярлыки скрыты до разблокировки.",
                "Brief uses your enabled step counter and selected city's weather. Shortcuts and controls stay below. Steps, weather and shortcuts are hidden until unlocked."))
        }
        GroupToggle(Pos.Single, Icons.Rounded.Tune, tr("Элементы управления", "Controls"),
            tr("Громкость, яркость и журнал звонков в раскрытом пустом острове", "Volume, brightness and call history in the expanded idle island"), s.controls, color = Tile.Indigo) {
            ExtrasRepo.update { copy(controls = it) }
        }
        if (!s.briefEnabled) GroupToggle(Pos.Single, Icons.Rounded.Schedule, tr("Аналоговые часы", "Analog clock"),
            tr("Циферблат в раскрытом пустом острове", "Clock face in the expanded idle island"), s.analogClock, color = Tile.Purple) { ExtrasRepo.update { copy(analogClock = it) } }
        GroupToggle(Pos.Single, Icons.AutoMirrored.Rounded.DirectionsWalk, tr("Шагомер", "Step counter"),
            tr("Шаги за день с момента включения; нужен датчик шагов", "Daily steps since enabling; requires a step sensor"), s.steps, color = Tile.Green) { enabled ->
            stepError = null
            val sensor = context.getSystemService(android.hardware.SensorManager::class.java)?.getDefaultSensor(android.hardware.Sensor.TYPE_STEP_COUNTER)
            if (enabled && sensor == null) stepError = tr("На устройстве нет датчика шагов", "This device has no step sensor")
            else if (enabled && android.os.Build.VERSION.SDK_INT >= 29) stepPermission.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
            else ExtrasRepo.update { copy(steps = enabled) }
        }
        stepError?.let { SettingsHint(it) }
        SectionHeader(tr("Быстрый доступ", "Shortcuts"))
        Group {
            GroupItem(Pos.First, Icons.Rounded.Apps, tr("Добавить приложение", "Add app"), tr("До шести приложений", "Up to six apps"), color = Tile.Purple, onClick = {
                scope.launch {
                    apps = withContext(Dispatchers.IO) {
                        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                            .distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(context.packageManager).toString() }
                    }
                }
            })
            s.apps.forEach { pkg ->
                val visual = com.akira.island.island.rememberAppShortcut(pkg)
                SelectedShortcutRow(visual.label, visual.icon, null) { ExtrasRepo.update { copy(apps = this.apps - pkg) } }
            }
            GroupItem(Pos.Middle, Icons.Rounded.PersonAdd, tr("Добавить контакт", "Add contact"), tr("Открывает карточку контакта; до шести", "Opens the contact card; up to six"), color = Tile.Green, onClick = { runCatching { contact.launch(null) }.onFailure { error = tr("Не удалось открыть контакты", "Could not open contacts") } })
            s.contacts.forEach { c ->
                SelectedShortcutRow(c.name, null, c.name) { ExtrasRepo.update { copy(contacts = contacts - c) } }
            }
            GroupItem(Pos.Last, Icons.Rounded.Info, tr("Ваши ярлыки", "Your shortcuts"),
                tr("Приложения и контакты доступны после раскрытия пустого острова. Контакты скрыты на экране блокировки.", "Apps and contacts appear in the expanded idle island. Contacts are hidden on the lock screen."))
        }
        SectionHeader(tr("Погода", "Weather"))
        val forecast by WeatherRepo.state.collectAsState()
        val forecastLoading by WeatherRepo.loading.collectAsState()
        val forecastError by WeatherRepo.error.collectAsState()
        LaunchedEffect(s.weatherEnabled, s.weatherCity, s.latitude, s.longitude) {
            if (s.weatherEnabled && s.weatherCity.isNotBlank()) WeatherRepo.refresh(force = false)
        }
        GroupToggle(Pos.Single, Icons.Rounded.Cloud, if (s.briefEnabled) tr("Погода в сводке", "Weather in Brief") else tr("Погода", "Weather"),
            s.weatherCity.ifBlank { tr("Сначала выберите город", "Choose a city first") }, s.weatherEnabled, color = Tile.Teal) {
            ExtrasRepo.update { copy(weatherEnabled = it && weatherCity.isNotBlank()) }
        }
        if (s.weatherEnabled && s.weatherCity.isNotBlank()) {
            forecast?.takeIf { it.city == s.weatherCity && System.currentTimeMillis() - it.updatedAt in 0..3_600_000L }?.let { weather ->
                Text(String.format(java.util.Locale.ROOT, "%.0f°C", weather.temperature) + " · " + weather.summary(), color = Ui.Text)
            }
            if (forecastLoading) Text(tr("Загрузка погоды…", "Loading weather…"), color = Ui.Sub)
            else forecastError?.let { Text(it, color = Ui.Sub) }
            GroupItem(Pos.Single, Icons.Rounded.Refresh, tr("Обновить погоду", "Refresh weather"), null,
                color = Tile.Teal, onClick = { if (!forecastLoading) scope.launch { WeatherRepo.refresh() } })
        }
        OutlinedTextField(query, {
            query = it.take(100)
            cities = emptyList()
            cityError = null
        }, label = { Text(tr("Город", "City")) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        TextButton(enabled = !busy && query.trim().length >= 2, onClick = {
            val requested = query.trim()
            busy = true
            cityError = null
            cities = emptyList()
            scope.launch {
                try {
                    val results = WeatherRepo.cities(requested)
                    if (query.trim() == requested) {
                        cities = results
                        if (results.isEmpty()) cityError = tr("Город не найден", "City not found")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    if (query.trim() == requested)
                        cityError = tr("Не удалось найти город. Проверьте подключение.", "City search failed. Check your connection.")
                } finally {
                    busy = false
                }
            }
        }) { Text(if (busy) tr("Поиск…", "Searching…") else tr("Найти город", "Find city")) }
        error?.let { Text(it, color = Ui.Sub) }
        cityError?.let { Text(it, color = Ui.Sub) }
        cities.forEach { city ->
            TextButton(onClick = {
                ExtrasRepo.update { copy(weatherCity = city.name, latitude = city.latitude, longitude = city.longitude, weatherEnabled = true) }
                cities = emptyList()
            }) { Text(city.name) }
        }
        Text(tr("Прогноз Open-Meteo (CC BY 4.0). Выбранный город и координаты отправляются Open-Meteo. Обновление раз в 15 минут при включённом экране; геолокация не запрашивается.",
            "Forecast by Open-Meteo (CC BY 4.0). Your selected city and coordinates are sent to Open-Meteo. Refreshes every 15 minutes while the screen is on; no location permission is requested."), color = Ui.Sub, modifier = Modifier.padding(vertical = 12.dp))
    }
    if (clockStyle) ChoiceDialog(tr("Стиль часов", "Clock style"), BriefClockStyle.entries.toList(), s.briefClockStyle,
        { it.title }, { style -> ExtrasRepo.update { copy(briefClockStyle = style) } }, { clockStyle = false })
    apps?.let { available ->
        val labels = remember(available) {
            available.associate { it.activityInfo.packageName to it.loadLabel(context.packageManager).toString() }
        }
        var appSearch by remember { mutableStateOf("") }
        var selected by remember { mutableStateOf(s.apps.toSet()) }
        AlertDialog(
            onDismissRequest = { apps = null },
            title = { Text(tr("Выберите до шести приложений", "Choose up to six apps")) },
            text = {
                Column {
                    OutlinedTextField(appSearch, { appSearch = it }, label = { Text(tr("Поиск приложений", "Search apps")) },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    val matches = labels.filter { (pkg, label) ->
                        label.contains(appSearch.trim(), ignoreCase = true) || pkg.contains(appSearch.trim(), ignoreCase = true)
                    }
                    if (matches.isEmpty()) Text(tr("Приложения не найдены", "No matching apps"))
                    Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                        matches.forEach { (pkg, label) ->
                            val checked = pkg in selected
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Checkbox(checked, onCheckedChange = { on -> selected = if (on) selected + pkg else selected - pkg },
                                    enabled = checked || selected.size < 6)
                                Text(label)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    ExtrasRepo.update { copy(apps = selected.toList()) }
                    apps = null
                }) { Text(tr("Готово", "Done") + " (${selected.size}/6)") }
            },
            dismissButton = { TextButton(onClick = { apps = null }) { Text(tr("Отмена", "Cancel")) } },
        )
    }
}
