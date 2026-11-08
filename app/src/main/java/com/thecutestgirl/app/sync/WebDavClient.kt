package com.thecutestgirl.app.sync

import kotlinx.coroutines.CancellationException
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileOutputStream
import java.io.StringReader
import java.net.URLDecoder
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/** WebDAV 目录里的一项：文件名 / 服务器上的修改时间（毫秒）/ 大小 */
data class DavEntry(val name: String, val lastModified: Long, val size: Long)

/**
 * 极简 WebDAV 客户端（OkHttp 实现），只做同步需要的五件事：
 * PROPFIND 列目录 / GET 下载 / PUT 上传 / MKCOL 建目录 / 连接测试。
 *
 * v4.1 P2-3：所有方法返回 [DavResult]，401/404/507/网络/解析可区分；
 * P2-4：取消异常原样抛出，不当作普通失败。
 */
class WebDavClient(baseUrl: String, user: String, pass: String) {

    private val root = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    private val auth = Credentials.basic(user, pass)
    // 全进程共享连接池/线程池，见 HttpClientProvider
    private val client = HttpClientProvider.client

    private fun url(path: String) = root + path.removePrefix("/")

    private fun errorFor(code: Int): DavError = when (code) {
        401, 403 -> DavError.Auth
        404 -> DavError.NotFound
        507 -> DavError.Quota
        else -> DavError.Network("服务器返回 HTTP $code")
    }

    private inline fun <T> guard(block: () -> DavResult<T>): DavResult<T> =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DavResult.Err(DavError.Network(e.message ?: "网络错误"))
        }

    /** 测试账号密码是否可用（对根目录发 PROPFIND Depth 0） */
    fun testConnection(): DavResult<Unit> = guard {
        val req = Request.Builder()
            .url(url(""))
            .header("Authorization", auth)
            .header("Depth", "0")
            .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML))
            .build()
        client.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) DavResult.Ok(Unit) else DavResult.Err(errorFor(resp.code))
        }
    }

    /** 建目录；目录已存在（405）也算成功 */
    fun mkcol(path: String): DavResult<Unit> = guard {
        val req = Request.Builder()
            .url(url(path))
            .header("Authorization", auth)
            .method("MKCOL", null)
            .build()
        client.newCall(req).execute().use { resp ->
            if (resp.isSuccessful || resp.code == 405) DavResult.Ok(Unit)
            else DavResult.Err(errorFor(resp.code))
        }
    }

    /** 列出目录下的文件（跳过子目录） */
    fun listDir(path: String): DavResult<List<DavEntry>> = guard {
        val req = Request.Builder()
            .url(url(path))
            .header("Authorization", auth)
            .header("Depth", "1")
            .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@guard DavResult.Err(errorFor(resp.code))
            val xml = resp.body?.string() ?: return@guard DavResult.Err(DavError.Parse)
            val entries = try {
                parseMultiStatus(xml)
            } catch (e: Exception) {
                return@guard DavResult.Err(DavError.Parse)
            }
            DavResult.Ok(entries)
        }
    }

    fun getString(path: String): DavResult<String> = guard {
        val req = Request.Builder().url(url(path)).header("Authorization", auth).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@guard DavResult.Err(errorFor(resp.code))
            val text = resp.body?.string() ?: return@guard DavResult.Err(DavError.Parse)
            DavResult.Ok(text)
        }
    }

    fun getBytes(path: String): DavResult<ByteArray> = guard {
        val req = Request.Builder().url(url(path)).header("Authorization", auth).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@guard DavResult.Err(errorFor(resp.code))
            val bytes = resp.body?.bytes() ?: return@guard DavResult.Err(DavError.Parse)
            DavResult.Ok(bytes)
        }
    }

    fun putBytes(path: String, bytes: ByteArray, contentType: String): DavResult<Unit> = guard {
        val req = Request.Builder()
            .url(url(path))
            .header("Authorization", auth)
            .put(bytes.toRequestBody(contentType.toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) DavResult.Ok(Unit) else DavResult.Err(errorFor(resp.code))
        }
    }

    /** 删除云端文件（404 也算成功，本来就没有） */
    fun delete(path: String): DavResult<Unit> = guard {
        val req = Request.Builder()
            .url(url(path))
            .header("Authorization", auth)
            .delete()
            .build()
        client.newCall(req).execute().use { resp ->
            if (resp.isSuccessful || resp.code == 404) DavResult.Ok(Unit)
            else DavResult.Err(errorFor(resp.code))
        }
    }

    /**
     * 流式下载到目标文件：先写 `name.download` 临时文件，成功后原子改名。
     * 中断/磁盘满不会在目标路径留下半个文件。
     */
    fun downloadTo(path: String, target: File): DavResult<Unit> = guard {
        val req = Request.Builder().url(url(path)).header("Authorization", auth).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@guard DavResult.Err(errorFor(resp.code))
            val body = resp.body ?: return@guard DavResult.Err(DavError.Parse)
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, target.name + ".download")
            body.byteStream().use { input ->
                FileOutputStream(tmp).use { out -> input.copyTo(out) }
            }
            if (tmp.length() <= 0L) {
                tmp.delete()
                return@guard DavResult.Err(DavError.Parse)
            }
            if (tmp.renameTo(target)) {
                DavResult.Ok(Unit)
            } else {
                val copied = try {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                    target.length() > 0
                } catch (e: Exception) {
                    false
                }
                if (copied) DavResult.Ok(Unit) else DavResult.Err(DavError.Network("照片写入失败"))
            }
        }
    }

    /** 流式上传本地文件（不把整张照片读进内存）。 */
    fun uploadFrom(path: String, file: File, contentType: String): DavResult<Unit> = guard {
        val req = Request.Builder()
            .url(url(path))
            .header("Authorization", auth)
            .put(file.asRequestBody(contentType.toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) DavResult.Ok(Unit) else DavResult.Err(errorFor(resp.code))
        }
    }

    companion object {
        private val XML = "application/xml".toMediaType()
        private val JSON = "application/json"
        private val JPEG = "image/jpeg"

        fun jsonType() = JSON
        fun jpegType() = JPEG

        private val PROPFIND_BODY = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:">
              <d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/></d:prop>
            </d:propfind>
        """.trimIndent()

        private val HTTP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)

        /** 解析 WebDAV 207 Multi-Status 响应（用系统自带的 XmlPullParser，不引第三方库） */
        private fun parseMultiStatus(xml: String): List<DavEntry> {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            // 必须开启命名空间处理，否则 <D:response> 读出来的是带前缀的 "D:response"
            // 而不是本地名 "response"，所有分支都匹配不上
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(StringReader(xml))
            val entries = mutableListOf<DavEntry>()
            var inResponse = false
            var href: String? = null
            var lastModified = 0L
            var size = 0L
            var isCollection = false

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "response" -> {
                            inResponse = true; href = null; lastModified = 0; size = 0; isCollection = false
                        }
                        "href" -> if (inResponse) href = parser.nextText()
                        "getlastmodified" -> if (inResponse) {
                            lastModified = parseHttpDate(parser.nextText())
                        }
                        "getcontentlength" -> if (inResponse) {
                            size = parser.nextText().trim().toLongOrNull() ?: 0L
                        }
                        "collection" -> if (inResponse) isCollection = true
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "response" && inResponse) {
                        inResponse = false
                        val h = href
                        if (h != null && !isCollection && !h.endsWith("/")) {
                            val name = URLDecoder.decode(h, "UTF-8")
                                .trimEnd('/')
                                .substringAfterLast('/')
                            if (name.isNotBlank()) entries.add(DavEntry(name, lastModified, size))
                        }
                    }
                }
                event = parser.next()
            }
            return entries
        }

        private fun parseHttpDate(text: String): Long = try {
            Instant.from(HTTP_DATE.parse(text.trim())).toEpochMilli()
        } catch (e: Exception) {
            0L
        }
    }
}