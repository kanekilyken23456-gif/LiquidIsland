package com.akira.island.data

import android.content.Context
import android.net.Uri
import com.akira.island.island.IslandType
import com.akira.island.security.readUtf8Bounded
import com.akira.island.service.BankGuard
import com.akira.island.service.KeepAliveService
import com.akira.island.service.NotifApps
import java.io.File

/**
 * Cadangkan dan pulihkan seluruh konfigurasi ke satu berkas JSON yang dipilih pengguna (Storage Access Framework),
 * supaya setelah instal ulang tidak perlu mengatur semuanya lagi. Panggil dari thread latar (IO).
 */
object ConfigBackup {
    /** @property fontSkipped font kustom dipakai tetapi tidak disertakan (hilang atau terlalu besar). */
    data class Exported(val fontSkipped: Boolean)

    /** @property fontLost cadangan memakai font kustom yang tidak bisa dipulihkan; island memakai Inter. */
    data class Restored(val fontLost: Boolean)

    fun export(context: Context, uri: Uri): Exported {
        val app = context.applicationContext
        SettingsRepo.init(app)
        ExtrasRepo.init(app)
        NotifApps.load(app)
        val settings = SettingsRepo.current

        // Font disertakan selama berkasnya ada, juga bila sekarang sedang beralih ke Inter: pilihan "Font sendiri" tetap utuh.
        var font: BackupFont? = null
        var fontSkipped = false
        if (settings.customFontFile.isNotEmpty()) {
            val file = File(IslandType.fontsDir(app), settings.customFontFile)
            if (file.isFile && file.length() in 1..BackupCodec.MAX_FONT_BYTES.toLong()) {
                font = runCatching { BackupCodec.fontToBackup(settings.customFontName, file.readBytes()) }.getOrNull()
            }
            fontSkipped = font == null && settings.islandFont == IslandFont.CUSTOM
        }

        val backup = SettingsBackup(
            appVersion = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull().orEmpty(),
            createdAt = System.currentTimeMillis(),
            settings = settings,
            extras = ExtrasRepo.state.value,
            apps = NotifApps.apps.value,
            font = font,
        )
        val bytes = BackupCodec.encode(backup).toByteArray(Charsets.UTF_8)
        // "wt": potong berkas lama bila pengguna menimpa cadangan sebelumnya.
        val out = app.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open the backup file for writing")
        out.use { it.write(bytes) }
        return Exported(fontSkipped)
    }

    /** Melempar [BackupException] bila berkas bukan cadangan yang sah; pengaturan saat ini tidak disentuh dalam kasus itu. */
    fun restore(context: Context, uri: Uri): Restored {
        val app = context.applicationContext
        SettingsRepo.init(app)
        ExtrasRepo.init(app)
        NotifApps.load(app)

        val raw = try {
            val input = app.contentResolver.openInputStream(uri) ?: error("Cannot open the backup file")
            input.use { it.readUtf8Bounded(BackupCodec.MAX_FILE_BYTES) }
        } catch (e: IllegalArgumentException) {
            throw BackupException(BackupException.Reason.TOO_LARGE)
        }
        val backup = BackupCodec.decode(raw)

        // Font dulu: pengaturan baru boleh merujuk ke berkas font hanya bila berkas itu sudah ada dan terbaca.
        val dir = IslandType.fontsDir(app)
        var newFile = ""
        var newName = ""
        val bytes = BackupCodec.fontBytes(backup)
        if (bytes != null) {
            dir.mkdirs()
            val file = File(dir, "font_${System.currentTimeMillis()}")
            val ok = runCatching { file.writeBytes(bytes); IslandType.isFont(file) }.getOrDefault(false)
            if (ok) {
                newFile = file.name
                newName = backup.font?.name.orEmpty().take(40)
            } else {
                file.delete()
            }
        }
        val fontLost = newFile.isEmpty() && backup.settings.islandFont == IslandFont.CUSTOM

        // Nama berkas dari JSON tidak pernah dipakai langsung (path traversal): selalu diganti hasil tulis kita sendiri.
        val restored = backup.settings.copy(
            customFontFile = newFile,
            customFontName = newName,
            islandFont = if (backup.settings.islandFont == IslandFont.CUSTOM && newFile.isEmpty()) IslandFont.INTER else backup.settings.islandFont,
        )
        val oldFont = SettingsRepo.current.customFontFile

        SettingsRepo.restore(restored)
        ExtrasRepo.restore(app, backup.extras)
        NotifApps.merge(app, backup.apps)

        if (oldFont.isNotEmpty() && oldFont != newFile) runCatching { File(dir, oldFont).delete() }

        // Layanan latar mengikuti pengaturan baru (tetap hidup, penjaga aplikasi bank).
        runCatching { KeepAliveService.sync(app) }
        runCatching { BankGuard.sync(app) }
        return Restored(fontLost)
    }
}
