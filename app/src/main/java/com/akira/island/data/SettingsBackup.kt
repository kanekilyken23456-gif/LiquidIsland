package com.akira.island.data

import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Berkas font kustom yang ikut dalam cadangan, sebagai Base64, supaya tampilan sama setelah instal ulang. */
@Serializable
data class BackupFont(val name: String, val data: String)

/**
 * Isi berkas cadangan (JSON). Hanya pengaturan pengguna: tidak ada izin, status layanan, langkah harian,
 * log diagnostik maupun isi notifikasi — itu tidak bisa dipulihkan di perangkat baru.
 */
@Serializable
data class SettingsBackup(
    val format: String = BackupCodec.FORMAT,
    val version: Int = BackupCodec.VERSION,
    val appVersion: String = "",
    val createdAt: Long = 0L,
    val settings: IslandSettings = IslandSettings(),
    val extras: ExtraSettings = ExtraSettings(),
    /** Paket → nama aplikasi, hanya untuk menampilkan nama di daftar aturan notifikasi. */
    val apps: Map<String, String> = emptyMap(),
    val font: BackupFont? = null,
)

class BackupException(val reason: Reason) : Exception(reason.name) {
    enum class Reason {
        /** Berkas bukan cadangan Dynamic Island. */
        NOT_BACKUP,

        /** Dibuat oleh versi aplikasi yang lebih baru dari yang dipahami build ini. */
        TOO_NEW,

        /** Rusak atau terpotong. */
        CORRUPT,

        /** Terlalu besar untuk sebuah cadangan pengaturan. */
        TOO_LARGE,
    }
}

/** Kodek murni (tanpa Android) untuk berkas cadangan, supaya bisa diuji di JVM. */
object BackupCodec {
    const val FORMAT = "dynamic-island-backup"
    const val VERSION = 1

    /** Batas ukuran berkas yang dibaca saat memulihkan. */
    const val MAX_FILE_BYTES = 16 * 1024 * 1024

    /** Font lebih besar dari ini tidak disertakan (pengaturan lain tetap tersimpan). */
    const val MAX_FONT_BYTES = 8 * 1024 * 1024

    private const val MAX_CONTACTS = 6
    private const val MAX_PACKAGES = 200
    private const val CONTACTS_PREFIX = "content://com.android.contacts/"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        prettyPrint = true
    }

    fun encode(backup: SettingsBackup): String = json.encodeToString(backup)

    fun fontToBackup(name: String, bytes: ByteArray): BackupFont =
        BackupFont(name, Base64.getEncoder().encodeToString(bytes))

    /** Byte font dari cadangan, atau null jika tidak ada atau Base64-nya rusak. */
    fun fontBytes(backup: SettingsBackup): ByteArray? {
        val data = backup.font?.data ?: return null
        return runCatching { Base64.getDecoder().decode(data) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** Mengurai dan memvalidasi; melempar [BackupException] bila berkas tidak layak dipulihkan. */
    fun decode(raw: String): SettingsBackup {
        val element = runCatching { json.parseToJsonElement(raw) }.getOrNull()
        if (element == null) {
            // Berkas cadangan yang terpotong (unduhan putus, penyimpanan penuh) tetap dikenali sebagai cadangan yang rusak.
            val looksLikeBackup = raw.contains("\"format\"") && raw.contains(FORMAT)
            throw BackupException(if (looksLikeBackup) BackupException.Reason.CORRUPT else BackupException.Reason.NOT_BACKUP)
        }
        val root = element as? JsonObject ?: throw BackupException(BackupException.Reason.NOT_BACKUP)

        val format = runCatching { root["format"]?.jsonPrimitive?.contentOrNull }.getOrNull()
        if (format != FORMAT) throw BackupException(BackupException.Reason.NOT_BACKUP)
        val version = runCatching { root["version"]?.jsonPrimitive?.int }.getOrNull()
            ?: throw BackupException(BackupException.Reason.CORRUPT)
        if (version > VERSION) throw BackupException(BackupException.Reason.TOO_NEW)
        // Tanpa blok pengaturan, nilai bawaan diam-diam menimpa semua pengaturan pengguna.
        if (root["settings"] !is JsonObject) throw BackupException(BackupException.Reason.CORRUPT)

        val parsed = try {
            json.decodeFromJsonElement(SettingsBackup.serializer(), root)
        } catch (e: Exception) {
            throw BackupException(BackupException.Reason.CORRUPT)
        }
        return sanitize(parsed)
    }

    /** Berkas bisa disunting tangan atau datang dari orang lain: batasi isinya sama seperti saat memilihnya di aplikasi. */
    internal fun sanitize(b: SettingsBackup): SettingsBackup = b.copy(
        extras = b.extras.copy(
            apps = b.extras.apps.filter { it.isNotBlank() }.distinct().take(MAX_PACKAGES),
            contacts = b.extras.contacts.filter { it.uri.startsWith(CONTACTS_PREFIX) }.takeLast(MAX_CONTACTS),
        ),
        apps = b.apps.entries.take(MAX_PACKAGES).associate { it.key to it.value.take(80) },
    )
}
