package com.thecutestgirl.app.data

import android.content.Context
import android.net.Uri
import android.os.Build
import com.thecutestgirl.app.sync.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 导出备份（P2-10）：数据库 + 全部正式照片 + manifest，打包成一个 zip。
 *
 * 不包含：同步密码（在 SharedPreferences，且已加密）、同步账本、草稿照片。
 * 导入功能下一轮再做（需要二次确认 + 导入前自动备份）。
 */
object BackupExporter {

    suspend fun export(context: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            val db = AppDatabase.getInstance(context)
            // 先把 WAL 合并进主库，导出的 db 才是完整快照
            runCatchingCancellable {
                db.openHelper.writableDatabase
                    .query("PRAGMA wal_checkpoint(TRUNCATE)")
                    .use { it.moveToFirst() }
            }

            val dbFile = context.getDatabasePath("the_cutest_girl.db")
            require(dbFile.exists()) { "数据库文件不存在" }
            val photosDir = PhotoStore.dir(context)
            val photoFiles = photosDir.listFiles { f ->
                f.isFile && !f.name.startsWith("draft_")
            }?.sortedBy { it.name } ?: emptyList()

            val versionName: String
            val versionCode: Long
            if (Build.VERSION.SDK_INT >= 28) {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                versionName = info.versionName ?: ""
                versionCode = info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                versionName = info.versionName ?: ""
                @Suppress("DEPRECATION")
                versionCode = info.versionCode.toLong()
            }

            val readable = db.openHelper.readableDatabase
            fun count(table: String): Long =
                readable.query("SELECT COUNT(*) FROM $table").use { cursor ->
                    if (cursor.moveToFirst()) cursor.getLong(0) else 0L
                }

            val manifest = JSONObject()
                .put("app", "the cutest girl")
                .put("versionName", versionName)
                .put("versionCode", versionCode)
                .put("exportedAt", System.currentTimeMillis())
                .put("outfits", count("outfits"))
                .put("meals", count("meals"))
                .put("events", count("events"))
                .put("photos", photoFiles.size)
                .put("note", "不含同步密码与同步账本；photos/ 是全部正式照片")

            val output = context.contentResolver.openOutputStream(uri)
                ?: error("无法写入所选文件")
            output.use { stream ->
                ZipOutputStream(BufferedOutputStream(stream)).use { zip ->
                    fun addEntry(name: String, file: File) {
                        if (!file.exists() || !file.isFile) return
                        zip.putNextEntry(ZipEntry(name))
                        file.inputStream().use { input -> input.copyTo(zip) }
                        zip.closeEntry()
                    }

                    zip.putNextEntry(ZipEntry("manifest.json"))
                    zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    addEntry("db/the_cutest_girl.db", dbFile)
                    // WAL/SHM 一并带上，兼容 checkpoint 没能完全清空的情况
                    addEntry("db/the_cutest_girl.db-wal", File(dbFile.path + "-wal"))
                    addEntry("db/the_cutest_girl.db-shm", File(dbFile.path + "-shm"))

                    photoFiles.forEach { file ->
                        addEntry("photos/${file.name}", file)
                    }
                }
            }
        }
    }
}