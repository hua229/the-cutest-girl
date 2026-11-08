package com.thecutestgirl.app.sync

import android.content.Context
import org.json.JSONObject

/**
 * 同步的「上次见到过什么」小账本。
 *
 * 为什么需要它：原来每轮同步都会把云端 records 目录下**每一个** JSON 文件 GET 一遍，
 * 再对 photos 目录做两次 PROPFIND。记录攒到几百条时，一轮要几百次网络往返，
 * 对方刚传的新内容就排在这些往返后面，表现出来就是「要好几分钟才看到」。
 *
 * WebDAV 的 PROPFIND 本来就把 getlastmodified 和 getcontentlength 给了我们
 * （`DavEntry.lastModified` / `DavEntry.size`），只是一直没用。
 * 这里把「文件名 → 上次下载时的服务器时间 + 文件大小」记下来，
 * 下一轮两者都没变、本地记录也在且不脏，就直接跳过这个文件。
 *
 * 为什么必须连 size 一起比：getlastmodified 只有秒精度，
 * 同一秒内改两次会得到一模一样的时间戳。加上字节长度之后，
 * 「内容变了但时间和长度都没变」在 JSON 记录上基本不可能发生
 * （备注/评论改一个字，长度就变了）。
 *
 * 只跳「本地已有且干净」的文件：本地缺记录（换手机/清过数据）或本地有未上传改动时
 * 一律照常下载，否则会漏掉真实变更。
 */
object SyncStateStore {

    private const val FILE = "sync_state"
    private const val KEY_FILE_SIG = "file_sig"
    private const val KEY_FILE_CONFIRMED = "file_confirmed"
    private const val KEY_NOTIFIED = "notified_ids"
    private const val KEY_DIRS_READY = "dirs_ready"
    private const val KEY_LAST_MAINTENANCE = "last_maintenance_at"

    /** 记住多少条已提醒记录，够去重就行，别把 prefs 撑大 */
    private const val NOTIFIED_KEEP = 400

    /** 多值字段的分隔符。用常量而不是内联转义，避免编辑器/脚本把换行写坏 */
    private const val SEP = "\n"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---------------- 云端文件指纹（时间 + 长度） ----------------

    /** 文件名 → "服务器时间:字节数" */
    fun fileSignatures(context: Context): MutableMap<String, String> {
        val raw = prefs(context).getString(KEY_FILE_SIG, null) ?: return mutableMapOf()
        return runCatching {
            val json = JSONObject(raw)
            val map = mutableMapOf<String, String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.optString(key, "")
                if (value.isNotBlank()) map[key] = value
            }
            map
        }.getOrDefault(mutableMapOf())
    }

    fun saveFileSignatures(context: Context, map: Map<String, String>) {
        val json = JSONObject()
        map.forEach { (name, sig) -> if (sig.isNotBlank()) json.put(name, sig) }
        val text = json.toString()
        // 内容没变就不写盘：前台轮询每轮都调这里，避免无意义的 apply()
        if (prefs(context).getString(KEY_FILE_SIG, null) == text) return
        prefs(context).edit().putString(KEY_FILE_SIG, text).apply()
    }

    /** 拼指纹：时间与长度任一变化都算「变了」 */
    fun signatureOf(lastModified: Long, size: Long): String = "$lastModified:$size"

    /**
     * 「这个指纹已经稳定观察到过一次」。
     *
     * 时间 + 长度都恰好没变的情况极少但可能（JSON 里数字 1 换成 true 之类），
     * 所以第一次见到某个指纹时仍然照常下载，第二次确认稳定后才开始跳过。
     */
    fun confirmedFiles(context: Context): MutableSet<String> {
        val raw = prefs(context).getString(KEY_FILE_CONFIRMED, null) ?: return mutableSetOf()
        return raw.split(SEP).filter { it.isNotBlank() }.toMutableSet()
    }

    fun saveConfirmedFiles(context: Context, names: Set<String>) {
        val text = names.joinToString(SEP)
        if (prefs(context).getString(KEY_FILE_CONFIRMED, null) == text) return
        prefs(context).edit().putString(KEY_FILE_CONFIRMED, text).apply()
    }

    // ---------------- 维护轮（瘦身 / 大图扫描） ----------------

    /** 上次做照片瘦身的时间（毫秒）。0 = 从没做过 */
    fun lastMaintenanceAt(context: Context): Long =
        prefs(context).getLong(KEY_LAST_MAINTENANCE, 0L)

    fun setLastMaintenanceAt(context: Context, at: Long) {
        prefs(context).edit().putLong(KEY_LAST_MAINTENANCE, at).apply()
    }

    // ---------------- 已提醒过的记录 ----------------

    fun notifiedIds(context: Context): MutableSet<String> {
        val raw = prefs(context).getString(KEY_NOTIFIED, null) ?: return mutableSetOf()
        return raw.split(SEP).filter { it.isNotBlank() }.toMutableSet()
    }

    fun rememberNotified(context: Context, ids: Collection<String>) {
        if (ids.isEmpty()) return
        val merged = notifiedIds(context).apply { addAll(ids) }
        // 只保留最近的一批：这条链没有顺序含义，纯粹是「别重复弹」的集合
        val trimmed = if (merged.size > NOTIFIED_KEEP) {
            merged.toList().takeLast(NOTIFIED_KEEP).toSet()
        } else {
            merged
        }
        prefs(context).edit().putString(KEY_NOTIFIED, trimmed.joinToString(SEP)).apply()
    }

    // ---------------- 目录是否已建好 ----------------

    /**
     * 三个云端目录只建一次。原来每轮同步开头都发 3 次 MKCOL，
     * 前台轮询 20 秒一次的时候这 3 次往返纯属浪费。
     * 任何一次同步失败都会把它重置，下一轮重新建一遍兜底。
     */
    fun dirsReady(context: Context): Boolean = prefs(context).getBoolean(KEY_DIRS_READY, false)

    fun setDirsReady(context: Context, ready: Boolean) {
        prefs(context).edit().putBoolean(KEY_DIRS_READY, ready).apply()
    }

    // ---------------- 换云盘：整本账作废 ----------------

    /**
     * 换了 WebDAV 地址 / 账号 / 密码，就把这本「本机记的增量账」整个清掉。
     *
     * 这四个键记的都是**旧云盘**的状态，跨云盘沿用会出两类错：
     * - 文件指纹、已确认集合：新云盘恰好有同名、同时间、同长度的文件时会被误跳，
     *   老记录永远同步不到；
     * - dirsReady：旧云盘目录建过了 → 新云盘跳过 MKCOL → listDir 404 → 整轮失败；
     * - notifiedIds：旧云盘的去重键串到新云盘（键是 id@version，撞上的概率小，但没理由留）。
     *
     * 清成 false / 空之后，下一轮同步会重新发 3 次 MKCOL、把新云盘的记录全量 GET 一遍，
     * 再重建指纹 —— 相当于一次干净的首次配对，且**不动本地数据库和照片**。
     *
     * 用 commit() 同步落盘：紧接着就会 enqueueNow 触发同步，必须保证同步线程读到的
     * 已经是清干净的状态，不能让异步 apply 和 Worker 抢先后。
     */
    @Suppress("ApplySharedPref") // 刻意同步落盘，见上方 KDoc；apply 会和紧随其后的 Worker 抢先后
    fun resetForNewEndpoint(context: Context) {
        prefs(context).edit()
            .remove(KEY_FILE_SIG)
            .remove(KEY_FILE_CONFIRMED)
            .remove(KEY_NOTIFIED)
            .remove(KEY_DIRS_READY)
            .remove(KEY_LAST_MAINTENANCE)
            .commit()
    }
}
