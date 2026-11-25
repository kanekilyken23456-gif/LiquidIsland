package com.akira.island.ui.screens

import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.akira.island.core.tr
import com.akira.island.data.BackupException
import com.akira.island.data.ConfigBackup
import com.akira.island.ui.GroupItem
import com.akira.island.ui.Pos
import com.akira.island.ui.Tile
import com.akira.island.ui.Ui
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dua baris «Cadangkan» dan «Pulihkan» untuk grup «Umum». Keduanya aksi, bukan layar baru,
 * jadi tanpa panah — sama seperti «Atur ulang semua pengaturan».
 */
@Composable
fun ColumnScope.BackupGroupItems() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var restoreUri by remember { mutableStateOf<Uri?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ConfigBackup.export(context, uri) }.onFailure {
                    // Berkas kosong setengah jadi lebih buruk daripada tidak ada berkas
                    runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                }
            }
            val message = result.fold(
                onSuccess = {
                    if (it.fontSkipped) tr("Настройки сохранены, но свой шрифт в файл не вошёл", "Settings backed up, but your custom font wasn't included")
                    else tr("Настройки сохранены", "Settings backed up")
                },
                onFailure = { tr("Не удалось сохранить файл", "Couldn't save the file") },
            )
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri }

    GroupItem(Pos.Middle, Icons.Rounded.Backup, tr("Сохранить настройки в файл", "Back up settings"),
        tr("Все настройки и свой шрифт — в один файл, чтобы не настраивать заново после переустановки",
            "All settings and your custom font in one file, so you don't have to set up again after reinstalling"),
        color = Tile.Green,
        onClick = {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date())
            exporter.launch("dynamic-island-backup-$stamp.json")
        })
    GroupItem(Pos.Middle, Icons.Rounded.Download, tr("Восстановить из файла", "Restore from backup"),
        tr("Выберите файл, сохранённый ранее", "Pick a file saved earlier"),
        color = Tile.Teal, onClick = { importer.launch(arrayOf("*/*")) })

    restoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            containerColor = Ui.Card,
            title = { Text(tr("Восстановить настройки?", "Restore settings?")) },
            text = {
                Text(tr("Текущие настройки будут заменены настройками из файла. Разрешения Android и доступ к уведомлениям останутся как есть.",
                    "Your current settings will be replaced with the ones from the file. Android permissions and notification access stay as they are."))
            },
            confirmButton = {
                TextButton(onClick = {
                    restoreUri = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { ConfigBackup.restore(context, uri) } }
                        val message = result.fold(
                            onSuccess = {
                                if (it.fontLost) tr("Настройки восстановлены, но свой шрифт восстановить не удалось — включён Inter",
                                    "Settings restored, but the custom font couldn't be restored — Inter is used")
                                else tr("Настройки восстановлены", "Settings restored")
                            },
                            onFailure = { backupErrorText(it) },
                        )
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }) { Text(tr("Восстановить", "Restore"), color = Ui.Primary) }
            },
            dismissButton = { TextButton(onClick = { restoreUri = null }) { Text(tr("Отмена", "Cancel"), color = Ui.Sub) } },
        )
    }
}

private fun backupErrorText(e: Throwable): String = when ((e as? BackupException)?.reason) {
    BackupException.Reason.NOT_BACKUP -> tr("Это не резервная копия Динамического острова", "This isn't a Dynamic Island backup")
    BackupException.Reason.TOO_NEW -> tr("Копия сделана более новой версией приложения — сначала обновите приложение",
        "This backup was made by a newer version of the app — update the app first")
    BackupException.Reason.CORRUPT -> tr("Файл повреждён", "The backup file is damaged")
    BackupException.Reason.TOO_LARGE -> tr("Файл слишком большой для резервной копии", "The file is too large to be a backup")
    null -> tr("Не удалось прочитать файл", "Couldn't read the file")
}
