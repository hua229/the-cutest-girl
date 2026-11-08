package com.thecutestgirl.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

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

    fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    /** 数据库里的文件名 -> 实际文件 */
    fun file(context: Context, name: String): File = File(dir(context), name)

    /** 新建一个给系统相机写入的临时文件，返回可直接给 TakePicture 用的 Uri */
    fun newCameraTarget(context: Context): Pair<File, Uri> {
        val file = File(dir(context), "cam_${UUID.randomUUID()}.jpg")
        file.createNewFile()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return file to uri
    }

    /** 把相册选中的图片复制进私有目录并压缩，成功返回文件名 */
    suspend fun saveFromUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val name = "img_${UUID.randomUUID()}.jpg"
            val output = File(dir(context), name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(output).use { input.copyTo(it) }
            } ?: return@runCatching null
            compressInPlace(output)
            name
        }.getOrNull()
    }

    fun delete(context: Context, name: String?) {
        if (name.isNullOrBlank()) return
        runCatching { File(dir(context), name).delete() }
    }

    /**
     * 原地压缩：按 EXIF 方向转正 → 缩到长边 1920 以内 → JPEG 85。
     * 如果压缩后反而更大（本来就是小图），保留原文件不动。
     */
    fun compressInPlace(file: File) {
        runCatching {
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
                file.delete()
                tmp.renameTo(file)
            } else {
                tmp.delete()
            }
        }
    }

    /** 竖拍照片的方向信息存在 EXIF 里，重新编码前先按它把像素转正 */
    private fun applyExifRotation(file: File, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
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

        return runCatching {
            val matrix = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            rotated
        }.getOrDefault(bitmap)
    }
}
