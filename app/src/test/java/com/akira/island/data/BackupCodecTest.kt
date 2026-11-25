package com.akira.island.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCodecTest {
    private fun reasonOf(raw: String): BackupException.Reason {
        try {
            BackupCodec.decode(raw)
        } catch (e: BackupException) {
            return e.reason
        }
        fail("Expected BackupException for: $raw")
        throw AssertionError()
    }

    @Test fun everySettingSurvivesBackupAndRestore() {
        val settings = IslandSettings(
            enabled = false, language = AppLanguage.ID, width = 120, height = 33, offsetY = 14,
            color = 0xFF112233, liquidGlass = true, glassOpacity = 40, expandStyle = ExpandStyle.SPRING,
            tapAction = TapAction.PLAY_PAUSE, quickActions = listOf(QuickAction.DND, QuickAction.CAMERA),
            msgAppRules = mapOf("com.whatsapp" to AppRule.ALWAYS, "com.example.spam" to AppRule.NEVER),
            msgChannels = mapOf("org.telegram.messenger" to "messages"),
            hideInApps = setOf("com.bank.app"), hideInAppsDisableService = true,
            messageIconPosition = MessageIconPosition.CENTER, islandFont = IslandFont.CUSTOM,
        )
        val extras = ExtraSettings(
            controls = true, apps = listOf("com.android.chrome"), weatherEnabled = true, weatherCity = "Jakarta",
            latitude = -6.2, longitude = 106.8, briefEnabled = true, briefClockStyle = BriefClockStyle.ANALOG,
            contacts = listOf(ContactShortcut("Ibu", "content://com.android.contacts/contacts/lookup/abc/1")),
        )
        val backup = SettingsBackup(
            appVersion = "1.0.0", createdAt = 1234L, settings = settings, extras = extras,
            apps = mapOf("com.whatsapp" to "WhatsApp"),
        )

        val restored = BackupCodec.decode(BackupCodec.encode(backup))

        assertEquals(settings, restored.settings)
        assertEquals(extras, restored.extras)
        assertEquals(mapOf("com.whatsapp" to "WhatsApp"), restored.apps)
        assertEquals("1.0.0", restored.appVersion)
    }

    @Test fun customFontTravelsInsideTheFile() {
        val bytes = ByteArray(5000) { (it * 31).toByte() }
        val backup = SettingsBackup(font = BackupCodec.fontToBackup("Roboto Slab", bytes))
        val restored = BackupCodec.decode(BackupCodec.encode(backup))
        assertEquals("Roboto Slab", restored.font?.name)
        assertArrayEquals(bytes, BackupCodec.fontBytes(restored))
    }

    @Test fun damagedFontDataIsReportedAsMissingNotAsCrash() {
        assertNull(BackupCodec.fontBytes(SettingsBackup(font = BackupFont("x", "***not base64***"))))
        assertNull(BackupCodec.fontBytes(SettingsBackup(font = BackupFont("x", ""))))
        assertNull(BackupCodec.fontBytes(SettingsBackup()))
    }

    @Test fun otherFilesAreRejectedBeforeTouchingSettings() {
        assertEquals(BackupException.Reason.NOT_BACKUP, reasonOf("not json at all"))
        assertEquals(BackupException.Reason.NOT_BACKUP, reasonOf("[1,2,3]"))
        assertEquals(BackupException.Reason.NOT_BACKUP, reasonOf("""{"width":120,"height":30}"""))
        assertEquals(BackupException.Reason.NOT_BACKUP, reasonOf("""{"format":"something-else","version":1,"settings":{}}"""))
    }

    @Test fun backupFromANewerAppIsRefused() {
        assertEquals(BackupException.Reason.TOO_NEW,
            reasonOf("""{"format":"${BackupCodec.FORMAT}","version":${BackupCodec.VERSION + 1},"settings":{}}"""))
    }

    @Test fun truncatedOrIncompleteBackupIsCorrupt() {
        val good = BackupCodec.encode(SettingsBackup())
        assertEquals(BackupException.Reason.CORRUPT, reasonOf(good.take(good.length / 2)))
        // Tanpa blok pengaturan, nilai bawaan akan diam-diam menimpa semua pengaturan pengguna
        assertEquals(BackupException.Reason.CORRUPT, reasonOf("""{"format":"${BackupCodec.FORMAT}","version":1}"""))
        assertEquals(BackupException.Reason.CORRUPT, reasonOf("""{"format":"${BackupCodec.FORMAT}","version":"x","settings":{}}"""))
        assertEquals(BackupException.Reason.CORRUPT, reasonOf("""{"format":"${BackupCodec.FORMAT}","version":1,"settings":"oops"}"""))
    }

    @Test fun unknownFieldsAndUnknownChoicesFallBackToDefaults() {
        val raw = """{"format":"${BackupCodec.FORMAT}","version":1,"future":true,
            "settings":{"width":111,"tapAction":"SOMETHING_NEW","somethingNew":5}}"""
        val restored = BackupCodec.decode(raw)
        assertEquals(111, restored.settings.width)
        assertEquals(IslandSettings().tapAction, restored.settings.tapAction)
    }

    @Test fun olderSettingsKeepTheirLayoutVersionSoMigrationsStillRun() {
        val raw = """{"format":"${BackupCodec.FORMAT}","version":1,"settings":{"width":126,"height":37,"layoutVersion":3}}"""
        assertEquals(3, BackupCodec.decode(raw).settings.layoutVersion)
    }

    @Test fun handEditedListsAreBounded() {
        val contacts = (1..20).map { ContactShortcut("c$it", "content://com.android.contacts/contacts/lookup/k$it/$it") } +
            ContactShortcut("evil", "https://example.com/") + ContactShortcut("evil2", "content://other.provider/x")
        val apps = (1..500).map { "pkg.app$it" } + listOf("", "pkg.app1")
        val labels = (1..500).associate { "pkg.l$it" to "x".repeat(300) }
        val restored = BackupCodec.decode(BackupCodec.encode(
            SettingsBackup(extras = ExtraSettings(apps = apps, contacts = contacts), apps = labels),
        ))
        assertEquals(6, restored.extras.contacts.size)
        assertTrue(restored.extras.contacts.all { it.uri.startsWith("content://com.android.contacts/") })
        assertEquals(200, restored.extras.apps.size)
        assertFalse(restored.extras.apps.contains(""))
        assertEquals(200, restored.apps.size)
        assertTrue(restored.apps.values.all { it.length <= 80 })
    }
}
