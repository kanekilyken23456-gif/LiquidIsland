package com.akira.island.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import com.akira.island.core.tr
import com.akira.island.core.trf
import com.akira.island.data.SettingsRepo
import com.akira.island.face.RootShell
import com.akira.island.island.rememberAppShortcut
import com.akira.island.service.BankGuard
import com.akira.island.ui.Group
import com.akira.island.ui.GroupRow
import com.akira.island.ui.GroupToggle
import com.akira.island.ui.IslandSwitch
import com.akira.island.ui.ItemIcon
import com.akira.island.ui.Page
import com.akira.island.ui.Pos
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.Tile
import com.akira.island.ui.Ui
import com.akira.island.ui.posOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * «Hide in selected apps»: the island is hidden while a chosen app (typically a banking app) is open and returns
 * when it is closed. The optional root mode also switches the accessibility service itself off, for apps that
 * look for it. Changes apply at once, like every other settings page.
 */
@Composable
fun HideInAppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by SettingsRepo.state.collectAsState()
    var apps by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var checkingRoot by remember { mutableStateOf(false) }
    var rootDenied by remember { mutableStateOf(false) }
    // Apps chosen before this page opened stay on top; they do not jump around while switches are toggled
    val initial = remember { SettingsRepo.current.hideInApps }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .distinctBy { it.activityInfo.packageName }
                .filter { it.activityInfo.packageName != context.packageName }
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
        }
    }

    fun choose(pkg: String, on: Boolean) {
        SettingsRepo.update { copy(hideInApps = if (on) hideInApps + pkg else hideInApps - pkg) }
        BankGuard.sync(context)
    }

    fun setRootMode(on: Boolean) {
        rootDenied = false
        if (!on) {
            SettingsRepo.update { copy(hideInAppsDisableService = false) }
            BankGuard.sync(context)
            return
        }
        scope.launch {
            checkingRoot = true
            val granted = withContext(Dispatchers.IO) { RootShell.hasRoot() }
            checkingRoot = false
            if (granted) {
                SettingsRepo.update { copy(hideInAppsDisableService = true) }
                BankGuard.sync(context)
            } else {
                rootDenied = true
            }
        }
    }

    Page(tr("Скрывать в выбранных приложениях", "Hide in selected apps"), onBack) {
        InfoText(
            tr("Пока выбранное приложение открыто, остров скрыт; после закрытия он вернётся сам.",
                "While a selected app is open the island is hidden; it comes back by itself once the app is closed."),
        )

        Group {
            GroupToggle(
                Pos.Single, Icons.Rounded.Security, tr("Полностью выключать спец. возможности", "Fully switch accessibility off"),
                if (checkingRoot) tr("Проверка root…", "Checking root…")
                else tr("Для приложений, которые видят саму службу спец. возможностей. Через root она выключается, пока приложение открыто, и включается обратно после него.",
                    "For apps that detect the accessibility service itself. It is switched off through root while the app is open and back on afterwards."),
                s.hideInAppsDisableService, color = Tile.Red, badge = "root",
            ) { setRootMode(it) }
        }
        if (rootDenied) {
            Text(
                tr("Нет root-доступа: разрешите его в менеджере root и повторите", "No root access: allow it in your root manager and try again"),
                color = Tile.Red, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp),
            )
        } else if (s.hideInAppsDisableService) {
            InfoText(tr("Пока режим включён, небольшое уведомление показывает, что слежение работает.",
                "While this mode is on, a small notification shows that the watcher is running."))
        }

        SectionHeader(tr("Приложения", "Apps")) {
            if (s.hideInApps.isNotEmpty()) Text(trf("Выбрано: {0}", "Selected: {0}", s.hideInApps.size), color = Ui.Sub, fontSize = 14.sp)
        }
        val all = apps
        if (all == null) {
            Text(tr("Загрузка…", "Loading…"), color = Ui.Sub, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
        } else {
            OutlinedTextField(query, { query = it.take(60) }, label = { Text(tr("Поиск приложений", "Search apps")) },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            val q = query.trim()
            val list = all.filter { (pkg, label) -> q.isEmpty() || label.contains(q, ignoreCase = true) || pkg.contains(q, ignoreCase = true) }
                .sortedWith(compareByDescending<Pair<String, String>> { it.first in initial }.thenBy { it.second.lowercase() })
            if (list.isEmpty()) {
                Text(tr("Приложения не найдены", "No matching apps"), color = Ui.Sub, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
            }
            Group {
                list.forEachIndexed { i, (pkg, label) ->
                    key(pkg) {
                        val on = pkg in s.hideInApps
                        val visual = rememberAppShortcut(pkg)
                        GroupRow(posOf(i, list.size), label, null, onClick = { choose(pkg, !on) },
                            trailing = { IslandSwitch(on, onChange = { choose(pkg, it) }) }) {
                            val icon = visual.icon
                            if (icon != null) Image(icon, null, Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)))
                            else Box { ItemIcon(Icons.Rounded.Apps, Tile.Gray, 40.dp) }
                        }
                    }
                }
            }
        }
    }
}
