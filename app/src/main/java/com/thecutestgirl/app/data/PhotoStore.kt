package com.thecutestgirl.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import com.thecutestgirl.app.sync.runCatchingCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * 照片统一保存在 APP 私有目录 files/photos/ 下，数据库里只存文件名。
 * 私有目录不需要任何存储权限，卸载 APP 时会一并清除。
 *
 * 所有照片（相册选的、拍的）在保存时都会压缩：
 * 长边最多 1920 像素、JPEG 85% 质量，单张约 300-500KB，
 * 手机屏幕上看不出差别，本地存储和同步流量都缩小约 10 倍。
 */
object PhotoStore {

    private const val DIR_NAME = "photos"
    private const val MAX_EDGE = 1920
    private const val JPEG_QUALITY = 85

    /** 草稿前缀：新选/新拍、还没保存进数据库的照片。保存时晋级为 img_，取消/超 24h 删除 */
    private const val DRAFT_PREFIX = "draft_"
    private const val DRAFT_TTL_MS = 24 * 60 * 60 * 1000L

    /** 缓存 application context 对应的照片目录（避免每次 file() 都 mkdirs） */
    private val cachedDir = AtomicReference<File?>(null)

    fun dir(context: Context): File =
        cachedDir.get() ?: synchronized(this) {
            cachedDir.get() ?: File(context.applicationContext.filesDir, DIR_NAME)
                .apply { mkdirs() }
                .also { cachedDir.set(it) }
        }

    /** 数据库里的文件名 -> 实际文件 */
    fun file(context: Context, name: String): File = File(dir(context), name)

    /** 新建一个给系统相机写入的临时文件，返回可直接给 TakePicture 用的 Uri */
    fun newCameraTarget(context: Context, draft: Boolean = false): Pair<File, Uri> {
        val name = "cam_${UUID.randomUUID()}.jpg"
        val file = File(dir(context), if (draft) DRAFT_PREFIX + name else name)
        file.createNewFile()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return file to uri
    }

    /** 把相册选中的图片复制进私有目录并压缩，成功返回文件名。[draft] = 还没保存进数据库 */
    suspend fun saveFromUri(context: Context, uri: Uri, draft: Boolean = false): String? = withContext(Dispatchers.IO) {
        val name = if (draft) "draft_${UUID.randomUUID()}.jpg" else "img_${UUID.randomUUID()}.jpg"
        val dir = dir(context)
        val raw = File(dir, "raw_${UUID.randomUUID()}.jpg")
        try {
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                copyAtomically(raw, input)
            } ?: false
            if (!copied) return@withContext null
            compressInPlace(raw)
            val target = File(dir, name)
            if (!raw.renameTo(target)) {
                raw.copyTo(target, overwrite = true)
                raw.delete()
            }
            if (target.exists() && target.length() > 0) name else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            raw.delete()
            null
        }
    }

    fun delete(context: Context, name: String?) {
        if (name.isNullOrBlank()) return
        runCatchingCancellable { File(dir(context), name).delete() }
    }

    // ---------------- 草稿照片（P2-7） ----------------

    fun isDraft(name: String?): Boolean = name?.startsWith(DRAFT_PREFIX) == true

    /**
     * FIX-05：本地照片孤儿 GC，任何用户启动 App 都会跑，不依赖是否开启同步。
     *
     * 只删同时满足三个条件的文件：
     * - 不是 `draft_`（草稿由 [cleanupOldDrafts] 单独按 24h 处理）
     * - 数据库三张表都不引用
     * - 最后修改时间超过 24h（保护刚复制、还没写库的照片）
     */
    suspend fun cleanupOrphans(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val referenced = (
            db.outfitDao().getAllPhotoPaths() +
                db.mealDao().getAllPhotoPaths() +
                db.eventDao().getAllPhotoPaths()
            ).toHashSet()
        val cutoff = System.currentTimeMillis() - DRAFT_TTL_MS
        runCatchingCancellable {
            dir(context).listFiles { f ->
                f.isFile &&
                    !f.name.startsWith(DRAFT_PREFIX) &&
                    f.name !in referenced &&
                    f.lastModified() < cutoff
            }?.forEach { it.delete() }
        }
    }

    /** 相机临时文件 → 草稿名，进程被杀也只会留下可清理的 draft_ 文件 */
    fun makeDraft(context: Context, file: File): String {
        if (file.name.startsWith(DRAFT_PREFIX)) return file.name
        val target = File(dir(context), DRAFT_PREFIX + file.name)
        return if (file.renameTo(target)) target.name else file.name
    }

    /** 保存成功：草稿晋级为正式 img_ 文件，返回最终文件名 */
    fun promoteDraft(context: Context, name: String?): String? {
        if (name == null || !name.startsWith(DRAFT_PREFIX)) return name
        val src = File(dir(context), name)
        if (!src.exists()) return name
        val target = File(dir(context), "img_" + name.removePrefix(DRAFT_PREFIX))
        return if (src.renameTo(target)) target.name else name
    }

    /** 启动时清理超过 24h 的草稿（编辑一半被系统杀掉的残留） */
    fun cleanupOldDrafts(context: Context) {
        val cutoff = System.currentTimeMillis() - DRAFT_TTL_MS
        runCatchingCancellable {
            dir(context).listFiles { f ->
                f.isFile && f.name.startsWith(DRAFT_PREFIX) && f.lastModified() < cutoff
            }?.forEach { it.delete() }
        }
    }

    /**
     * 原子写入：先写 `xxx.tmp`，校验长度 > 0 后改名为目标文件。
     * 中途被杀 / 磁盘满不会在目标路径留下半个文件（下一次同步会重新下载）。
     */
    fun writeAtomically(target: File, bytes: ByteArray): Boolean = try {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(bytes)
            out.flush()
        }
        finishRename(tmp, target)
    } catch (e: Exception) {
        File(target.parentFile, target.name + ".tmp").delete()
        false
    }

    /** 原子复制：流式写临时文件再改名，不把整张图读进内存。 */
    fun copyAtomically(target: File, input: InputStream): Boolean = try {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(tmp).use { out -> input.copyTo(out) }
        finishRename(tmp, target)
    } catch (e: Exception) {
        File(target.parentFile, target.name + ".tmp").delete()
        false
    }

    private fun finishRename(tmp: File, target: File): Boolean {
        if (tmp.length() <= 0L) {
            tmp.delete()
            return false
        }
        if (tmp.renameTo(target)) return true
        // 某些文件系统 rename 覆盖会失败：复制兜底，成功后再删临时文件
        return runCatching {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
            target.length() > 0
        }.getOrDefault(false).also { if (!it) tmp.delete() }
    }

    /**
     * 原地压缩：按 EXIF 方向转正 → 缩到长边 1920 以内 → JPEG 85。
     * 如果压缩后反而更大（本来就是小图），保留原文件不动。
     */
    fun compressInPlace(file: File) {
        runCatchingCancellable {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return

            // 先用 inSampleSize 粗采样（必须是 2 的幂），再精确缩放
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= MAX_EDGE ||
                bounds.outHeight / (sample * 2) >= MAX_EDGE
            ) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bitmap = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return

            bitmap = applyExifRotation(file, bitmap)

            val longEdge = maxOf(bitmap.width, bitmap.height)
            if (longEdge > MAX_EDGE) {
                val scale = MAX_EDGE.toFloat() / longEdge
                val scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
                if (scaled != bitmap) bitmap.recycle()
                bitmap = scaled
            }

            val tmp = File(file.parentFile, file.name + ".tmp")
            FileOutputStream(tmp).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            bitmap.recycle()

            if (tmp.length() in 1 until file.length()) {
                // 不先删原图：rename 失败（跨文件系统/权限）时兜底复制，
                // 极端情况下宁可留一张大图，也不能把用户的照片弄丢
                if (!tmp.renameTo(file)) {
                    runCatching { tmp.copyTo(file, overwrite = true) }
                    tmp.delete()
                }
            } else {
                tmp.delete()
            }
        }
    }

    /** 竖拍照片的方向信息存在 EXIF 里，重新编码前先按它把像素转正 */
    private fun applyExifRotation(file: File, bitmap: Bitmap): Bitmap {
        val orientation = runCatchingCancellable {
            ExifInterface(file.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap

        return runCatchingCancellable {
            val matrix = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            rotated
        }.getOrDefault(bitmap)
    }
}
