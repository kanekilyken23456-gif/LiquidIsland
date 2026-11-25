package com.akira.island.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.tr
import com.akira.island.data.AppRule
import com.akira.island.data.SettingsRepo
import com.akira.island.service.NotifApps
import com.akira.island.service.NotificationParser
import com.akira.island.ui.ChoiceDialog
import com.akira.island.ui.Group
import com.akira.island.ui.GroupRow
import com.akira.island.ui.ItemIcon
import com.akira.island.ui.Page
import com.akira.island.ui.Tile
import com.akira.island.ui.Ui
import com.akira.island.ui.posOf

/**
 * «Приложения» в уведомлениях: для каждого приложения, от которого приходили уведомления, — как в системе,
 * всегда (и тихие) или никогда. Список приложений телефона Android без особого разрешения не даёт,
 * поэтому здесь те, кто уже писал.
 */
@Composable
fun NotifAppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { NotifApps.load(context) }
    val apps by NotifApps.apps.collectAsState()
    val s by SettingsRepo.state.collectAsState()
    var pick by remember { mutableStateOf<Pair<String, String>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var bulkRule by remember { mutableStateOf(false) }

    Page(tr("Приложения", "Apps"), onBack) {
        Text(
            tr("Здесь появляются приложения, от которых приходили уведомления. «Всегда» — показывать и тихие, «Никогда» — не показывать в острове.",
                "Apps that have sent notifications show up here. Always — also show silent ones, Never — don't show them in the island."),
            color = Ui.Sub, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp),
        )
        if (apps.isNotEmpty()) {
            OutlinedTextField(query, { query = it }, label = { Text(tr("Поиск приложений", "Search apps")) },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { selecting = !selecting; selected = emptySet() }) {
                Text(if (selecting) tr("Отменить выбор", "Cancel selection") else tr("Выбрать несколько", "Select multiple"))
            }
            if (selecting) TextButton(enabled = selected.isNotEmpty(), onClick = { bulkRule = true }) {
                Text(tr("Правило для выбранных", "Set rule for selected") + " (${selected.size})")
            }
        }
        val list = apps.entries.filter { (pkg, label) ->
            label.contains(query.trim(), ignoreCase = true) || pkg.contains(query.trim(), ignoreCase = true)
        }.sortedBy { it.value.lowercase() }
        if (list.isEmpty()) {
            Text(if (apps.isEmpty()) tr("Пока пусто: уведомлений приложений ещё не приходило.", "No app notifications have arrived yet.")
                else tr("Приложения не найдены", "No matching apps"),
                color = Ui.Sub, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
        }
        Group {
            list.forEachIndexed { i, (pkg, label) ->
                GroupRow(posOf(i, list.size), label, ruleLabel(s.msgAppRules[pkg]), onClick = {
                    if (selecting) selected = if (pkg in selected) selected - pkg else selected + pkg
                    else pick = pkg to label
                }) {
                    val icon = remember(pkg) { NotificationParser.icon(context, pkg) }
                    if (selecting) {
                        Checkbox(pkg in selected, onCheckedChange = { checked ->
                            selected = if (checked) selected + pkg else selected - pkg
                        })
                    } else if (icon != null) {
                        Image(icon, null, Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)))
                    } else {
                        ItemIcon(Icons.Rounded.Apps, Tile.Gray)
                    }
                }
            }
        }
    }

    if (bulkRule) {
        ChoiceDialog(tr("Правило для выбранных", "Set rule for selected"), listOf<AppRule?>(null, AppRule.ALWAYS, AppRule.NEVER),
            selected.map { s.msgAppRules[it] }.distinct().singleOrNull(), ::ruleLabel, { rule ->
                SettingsRepo.update { copy(msgAppRules = if (rule == null) msgAppRules - selected
                    else msgAppRules + selected.associateWith { rule }) }
                selected = emptySet()
                selecting = false
            }) { bulkRule = false }
    }

    pick?.let { (pkg, label) ->
        ChoiceDialog(label, listOf<AppRule?>(null, AppRule.ALWAYS, AppRule.NEVER), s.msgAppRules[pkg], ::ruleLabel, { v ->
            SettingsRepo.update { copy(msgAppRules = if (v == null) msgAppRules - pkg else msgAppRules + (pkg to v)) }
        }) { pick = null }
    }
}

private fun ruleLabel(rule: AppRule?) = when (rule) {
    null -> tr("Как в системе — то, что всплывает", "Like the system — what pops up")
    AppRule.ALWAYS -> tr("Всегда, и тихие", "Always, silent ones too")
    AppRule.NEVER -> tr("Никогда", "Never")
}
