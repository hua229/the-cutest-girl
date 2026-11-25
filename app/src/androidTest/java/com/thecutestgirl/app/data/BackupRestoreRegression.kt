package com.thecutestgirl.app.data

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal suspend fun backupRestoreRoundTrip(context: Context) {
    check(context.packageName.endsWith(".verification"))
    val db = AppDatabase.getInstance(context)
    val name = "img_restore_evidence.jpg"
    val photo = PhotoStore.file(context, name)
    val draft = PhotoStore.file(context, "draft_restore_evidence.jpg")
    val bytes = "backup photo fixture".encodeToByteArray()
    val original = OutfitRecord(
        id = "restore-outfit",
        date = "2026-11-18",
        photoPath = name,
        note = "待上传的备注",
        reply = "同步来的回复",
        origin = SettingsStore.deviceId(context),
        dirty = true
    )
    val identity = SettingsStore.backupIdentity(context)
    val tombstone = Tombstone("restore-deleted-record")
    val archive = File(context.cacheDir, "backup-restore-${UUID.randomUUID()}.zip")
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", archive)
    val invalid = File(context.cacheDir, "invalid-restore-${UUID.randomUUID()}.zip")
    try {
        photo.writeBytes(bytes)
        draft.writeText("unsaved picture")
        db.outfitDao().insert(original)
        db.tombstoneDao().insert(tombstone)
        BackupExporter.export(context, uri).getOrThrow()
        SettingsStore.saveDavConfig(context, SettingsStore.DEFAULT_DAV_URL, "", "")
        check(BackupImporter.prepare(context, uri).isFailure) { "Restore accepted a nonempty database" }

        db.clearAllTables()
        photo.delete()
        val initialIdentity = BackupIdentity("empty", "新设备", "")
        check(SettingsStore.restoreBackupIdentity(context, initialIdentity))
        ZipOutputStream(invalid.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("photos/../unknown"))
            zip.write(bytes)
        }
        val invalidUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", invalid)
        check(BackupImporter.prepare(context, invalidUri).isFailure) { "Restore accepted an unknown path" }

        val conflicting = BackupImporter.prepare(context, uri).getOrThrow()
        try {
            photo.writeText("different local photo")
            check(BackupImporter.restore(context, conflicting).isFailure)
            check(db.outfitDao().getById(original.id) == null) { "Failed restore inserted records" }
            check(db.tombstoneDao().getAll().isEmpty()) { "Failed restore changed deletion history" }
            check(SettingsStore.backupIdentity(context) == initialIdentity)
            check(photo.readText() == "different local photo") { "Failed restore overwrote local photo" }
        } finally {
            photo.delete()
            conflicting.discard()
        }

        val preview = BackupImporter.prepare(context, uri).getOrThrow()
        try {
            check(preview.outfits == 1L && preview.meals == 0L && preview.events == 0L)
            check(preview.photos >= 1 && preview.missingPhotos == 0)
            BackupImporter.restore(context, preview).getOrThrow()
            check(db.outfitDao().getById(original.id) == original) { "Record fields changed during restore" }
            check(db.tombstoneDao().getAll().contains(tombstone)) { "Deletion history was dropped" }
            check(SettingsStore.backupIdentity(context) == identity) { "Original device identity was lost" }
            check(photo.readBytes().contentEquals(bytes))
            check(!SettingsStore.isSyncConfigured(context))
        } finally {
            preview.discard()
        }
    } finally {
        archive.delete()
        invalid.delete()
        photo.delete()
        draft.delete()
    }
}
