package com.thecutestgirl.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.thecutestgirl.app.sync.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipInputStream

internal object BackupImporter {
    private const val DATABASE = "db/the_cutest_girl.db"
    private const val MANIFEST = "manifest.json"
    private const val IDENTITY = "identity.json"
    private const val PHOTOS = "photos/"
    private const val JSON_LIMIT = 64 * 1024L
    private val recordTables = listOf("outfits", "meals", "events")
    private val tables = recordTables + "tombstones"

    class Preview internal constructor(
        internal val directory: File,
        internal val identity: BackupIdentity,
        internal val photoFiles: List<File>,
        val outfits: Long,
        val meals: Long,
        val events: Long,
        val missingPhotos: Int
    ) {
        val photos get() = photoFiles.size
        fun discard() {
            directory.deleteRecursively()
        }
    }

    suspend fun prepare(context: Context, uri: Uri): Result<Preview> {
        var staging: File? = null
        try {
            val result = withContext(Dispatchers.IO) {
                runCatchingCancellable {
                    val local = AppDatabase.getInstance(context).openHelper.readableDatabase
                    checkCanRestore(context, local)
                    val directory = Files.createTempDirectory(
                        context.cacheDir.toPath(),
                        "backup-restore-"
                    ).toFile().also { staging = it }
                    val files = unpack(context, uri, directory)
                    val manifest = readJson(files[MANIFEST], "备份清单")
                    require(manifest.optString("app") == "the cutest girl") { "请选择本应用导出的备份" }
                    val identity = readJson(files[IDENTITY], "设备身份")
                    val deviceId = identity.getString("deviceId")
                    require(deviceId.isNotBlank()) { "备份缺少原设备身份" }
                    val restoredIdentity = BackupIdentity(
                        deviceId,
                        identity.getString("myName"),
                        identity.optString("propagatedName")
                    )
                    val photoEntries = files.filterKeys { it.startsWith(PHOTOS) }.values
                    require(manifest.getLong("photos") == photoEntries.size.toLong()) {
                        "备份中的照片数量与清单不符"
                    }
                    val photos = photoEntries.filter { it.length() > 0L }
                    val snapshot = files[DATABASE] ?: error("备份缺少数据库")
                    withBackupDatabase(context, snapshot) { source ->
                        val db = source.openHelper.readableDatabase
                        db.query("PRAGMA integrity_check").use { cursor ->
                            require(cursor.moveToFirst() && cursor.getString(0) == "ok") {
                                "备份数据库已损坏"
                            }
                        }
                        val counts = recordTables.map { table ->
                            count(db, table).also { actual ->
                                require(actual == manifest.getLong(table)) {
                                    "备份中的记录数量与清单不符"
                                }
                            }
                        }
                        val referenced = referencedPhotos(db)
                        val photoNames = photos.mapTo(HashSet()) { it.name }
                        Preview(
                            directory,
                            restoredIdentity,
                            photos,
                            counts[0],
                            counts[1],
                            counts[2],
                            referenced.count { it !in photoNames }
                        )
                    }
                }
            }
            if (result.isSuccess) staging = null
            return result
        } finally {
            staging?.let { withContext(Dispatchers.IO + NonCancellable) { it.deleteRecursively() } }
        }
    }

    suspend fun restore(context: Context, preview: Preview): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatchingCancellable {
                val target = AppDatabase.getInstance(context)
                val databaseFile = File(preview.directory, DATABASE)
                require(databaseFile.isFile && preview.photoFiles.all { it.isFile }) {
                    "临时备份已清理，请重新选择备份"
                }
                withBackupDatabase(context, databaseFile) { source ->
                    val snapshot = source.openHelper.readableDatabase
                    val installedPhotos = mutableListOf<File>()
                    var originalIdentity: BackupIdentity? = null
                    var committed = false
                    try {
                        // 开始安装后完整提交或回滚，避免离开设置页时中断。
                        withContext(NonCancellable) {
                            target.withTransaction {
                                val db = target.openHelper.writableDatabase
                                checkCanRestore(context, db)
                                copyRecords(snapshot, db)
                                for (photo in preview.photoFiles) {
                                    val destination = PhotoStore.file(context, photo.name)
                                    // 上次恢复被系统中止时，可能已安装这张照片。不同内容不得覆盖。
                                    if (destination.exists()) {
                                        require(sameContent(photo, destination)) {
                                            "本机已有同名照片，请先保留本机数据"
                                        }
                                    } else {
                                        Files.move(photo.toPath(), destination.toPath())
                                        installedPhotos += destination
                                    }
                                }
                                originalIdentity = SettingsStore.backupIdentity(context)
                                check(SettingsStore.restoreBackupIdentity(context, preview.identity)) {
                                    "无法保存原设备身份"
                                }
                            }
                            committed = true
                        }
                    } finally {
                        if (!committed) {
                            installedPhotos.forEach { it.delete() }
                            originalIdentity?.let { SettingsStore.restoreBackupIdentity(context, it) }
                        }
                    }
                }
            }
        }

    private inline fun <T> withBackupDatabase(
        context: Context,
        file: File,
        action: (AppDatabase) -> T
    ): T {
        val backup = AppDatabase.openBackup(context, file)
        try {
            return action(backup)
        } finally {
            backup.close()
        }
    }

    private fun checkCanRestore(context: Context, db: SupportSQLiteDatabase) {
        require(!SettingsStore.isSyncConfigured(context)) { "请在配置云同步前恢复备份" }
        require(tables.none { count(db, it) > 0L }) { "只能在没有本机记录的安装中恢复备份" }
    }

    private fun count(db: SupportSQLiteDatabase, table: String): Long =
        db.query("SELECT COUNT(*) FROM $table").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }

    private fun referencedPhotos(db: SupportSQLiteDatabase): Set<String> = buildSet {
        for (table in recordTables) {
            db.query("SELECT photoPath FROM $table WHERE photoPath IS NOT NULL").use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)
                    if (name.isBlank()) continue
                    require(isFileName(name)) { "备份中包含无效照片文件名" }
                    add(name)
                }
            }
        }
    }

    private fun copyRecords(source: SupportSQLiteDatabase, target: SupportSQLiteDatabase) {
        for (table in tables) {
            source.query("SELECT * FROM $table").use { cursor ->
                while (cursor.moveToNext()) {
                    val row = ContentValues(cursor.columnCount)
                    for (index in 0 until cursor.columnCount) {
                        val column = cursor.getColumnName(index)
                        when (cursor.getType(index)) {
                            Cursor.FIELD_TYPE_NULL -> row.putNull(column)
                            Cursor.FIELD_TYPE_INTEGER -> row.put(column, cursor.getLong(index))
                            Cursor.FIELD_TYPE_STRING -> row.put(column, cursor.getString(index))
                            else -> error("备份中包含无法恢复的记录字段")
                        }
                    }
                    check(target.insert(table, SQLiteDatabase.CONFLICT_ABORT, row) != -1L) {
                        "无法写入备份记录"
                    }
                }
            }
        }
    }

    private suspend fun unpack(context: Context, uri: Uri, directory: File): Map<String, File> {
        val files = mutableMapOf<String, File>()
        val input = context.contentResolver.openInputStream(uri) ?: error("无法打开所选备份")
        val coroutine = currentCoroutineContext()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                coroutine.ensureActive()
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (entry.isDirectory && (name == "db/" || name == PHOTOS)) continue
                require(!entry.isDirectory && isBackupEntry(name) && name !in files) {
                    "备份包含未知或重复的文件"
                }
                val target = File(directory, name)
                target.parentFile?.mkdirs()
                target.outputStream().buffered().use { out ->
                    var bytes = 0L
                    while (true) {
                        coroutine.ensureActive()
                        val length = zip.read(buffer)
                        if (length < 0) break
                        bytes += length
                        require((name != MANIFEST && name != IDENTITY) || bytes <= JSON_LIMIT) {
                            "备份资料文件过大"
                        }
                        out.write(buffer, 0, length)
                    }
                }
                zip.closeEntry()
                files[name] = target
            }
        }
        // SQLite 会按 WAL 重建共享内存索引，不使用备份里的进程状态。
        files["$DATABASE-shm"]?.delete()
        return files
    }

    private fun readJson(file: File?, label: String): JSONObject {
        require(file != null) { "备份缺少$label" }
        return JSONObject(file.readText())
    }

    private fun isBackupEntry(name: String): Boolean =
        name == MANIFEST || name == IDENTITY || name == DATABASE ||
            name == "$DATABASE-wal" || name == "$DATABASE-shm" ||
            (name.startsWith(PHOTOS) && isFileName(name.removePrefix(PHOTOS)))

    private fun isFileName(name: String): Boolean =
        name.isNotBlank() && name != "." && name != ".." &&
            name.none { it.isISOControl() || it == '/' || it == '\\' }

    private fun sameContent(source: File, destination: File): Boolean {
        if (source.length() != destination.length() || !destination.isFile) return false
        source.inputStream().buffered().use { first ->
            destination.inputStream().buffered().use { second ->
                val left = ByteArray(DEFAULT_BUFFER_SIZE)
                val right = ByteArray(DEFAULT_BUFFER_SIZE)
                var amount = first.read(left)
                while (amount >= 0) {
                    if (amount != second.read(right)) return false
                    for (index in 0 until amount) {
                        if (left[index] != right[index]) return false
                    }
                    amount = first.read(left)
                }
                return second.read() == -1
            }
        }
    }
}
