package com.thecutestgirl.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * APP 设置（含坚果云 WebDAV 账号）存在 SharedPreferences 里。
 * 应用密码只在两台手机本地 + HTTPS 传输，不会出现在日志里。
 */
object SettingsStore {

    private const val FILE = "settings"

    const val DEFAULT_DAV_URL = "https://dav.jianguoyun.com/dav/"

    // ---------------- 应用密码加密（P2-9，Android Keystore，无新增依赖） ----------------
    private const val PASS_KEY_ALIAS = "tcg_dav_pass"
    private const val PASS_PREFIX = "enc1:"

    private fun keystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(PASS_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                PASS_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encryptPass(plain: String): String? = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        PASS_PREFIX + Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }

    private fun decryptPass(stored: String): String? = try {
        val raw = Base64.decode(stored.removePrefix(PASS_PREFIX), Base64.NO_WRAP)
        val iv = raw.copyOfRange(0, 12)
        val encrypted = raw.copyOfRange(12, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun webdavUrl(context: Context): String =
        prefs(context).getString("webdav_url", DEFAULT_DAV_URL) ?: DEFAULT_DAV_URL

    fun webdavUser(context: Context): String =
        prefs(context).getString("webdav_user", "") ?: ""

    fun webdavPass(context: Context): String {
        val stored = prefs(context).getString("webdav_pass", "") ?: ""
        if (stored.isBlank()) return ""
        if (stored.startsWith(PASS_PREFIX)) return decryptPass(stored) ?: ""
        // 旧版本明文：读的时候顺手迁移为密文，用户无感
        encryptPass(stored)?.let { encrypted ->
            prefs(context).edit().putString("webdav_pass", encrypted).apply()
        }
        return stored
    }

    fun saveDavConfig(context: Context, url: String, user: String, pass: String) {
        // 加密失败（极少见）时回退明文，至少不丢功能；下次保存会再试
        val storedPass = if (pass.isBlank()) "" else encryptPass(pass) ?: pass
        prefs(context).edit()
            .putString("webdav_url", url.trim().ifBlank { DEFAULT_DAV_URL })
            .putString("webdav_user", user.trim())
            .putString("webdav_pass", storedPass)
            .apply()
    }

    /**
     * 输入框里的新配置 vs 已经保存的配置，有没有变。
     *
     * 归一化必须和 [saveDavConfig] 一字不差，否则「改了又改回去」会被误判成换了云盘，
     * 白重置一次账本。端点由 地址 + 账号 决定，应用密码也一起比：换密码同样意味着
     * 重新授权，顺手重置账本代价极小（下一轮多一次全量 GET + 3 次 MKCOL），
     * 但能避免旧云盘留下的增量指纹串到新云盘上。
     *
     * 比较基准就是「已经生效的配置」，不需要另存一份「上次确认的端点」：
     * App 重启、清数据都无所谓，SettingsStore 里存的就是当前真正在用的那一份。
     */
    fun davConfigChanged(context: Context, url: String, user: String, pass: String): Boolean =
        url.trim().ifBlank { DEFAULT_DAV_URL } != webdavUrl(context) ||
            user.trim() != webdavUser(context) ||
            pass != webdavPass(context)

    /** 账号密码都填了才算开启同步 */
    fun isSyncConfigured(context: Context): Boolean =
        webdavUser(context).isNotBlank() && webdavPass(context).isNotBlank()

    /** 本机唯一设备 ID（首次生成后固定），用于区分记录是谁创建的 */
    fun deviceId(context: Context): String {
        prefs(context).getString("device_id", null)?.let { return it }
        val id = UUID.randomUUID().toString()
        prefs(context).edit().putString("device_id", id).apply()
        return id
    }

    fun myName(context: Context): String =
        prefs(context).getString("my_name", "我") ?: "我"

    fun saveMyName(context: Context, name: String) {
        prefs(context).edit().putString("my_name", name.trim().ifBlank { "我" }).apply()
    }

    /** 已经把昵称同步刷进历史记录的名字；和 myName 不一致时，下次同步会刷新全部旧记录 */
    fun propagatedName(context: Context): String =
        prefs(context).getString("propagated_name", "") ?: ""

    fun setPropagatedName(context: Context, name: String) {
        prefs(context).edit().putString("propagated_name", name).apply()
    }

    /** 首次全量拉取完成前不弹通知（避免配对时历史记录轰炸） */
    fun firstSyncDone(context: Context): Boolean =
        prefs(context).getBoolean("first_sync_done", false)

    fun setFirstSyncDone(context: Context) {
        prefs(context).edit().putBoolean("first_sync_done", true).apply()
    }

    /**
     * 换云盘后清掉「首次配对已完成」：新服务器上的历史要当成一次安静的全量拉取，
     * 否则 firstTime=false 会把整批历史当成「对方刚传的」一次性狂弹通知。
     * 用 remove 而不是 putBoolean(false)，让不存在的默认值自然生效。
     */
    fun clearFirstSyncDone(context: Context) {
        prefs(context).edit().remove("first_sync_done").apply()
    }

    fun lastSyncAt(context: Context): Long =
        prefs(context).getLong("last_sync_at", 0L)

    fun lastSyncResult(context: Context): String =
        prefs(context).getString("last_sync_result", "") ?: ""

    fun saveSyncStatus(context: Context, at: Long, result: String) {
        val prefs = prefs(context)
        val oldResult = prefs.getString("last_sync_result", "") ?: ""
        val oldAt = prefs.getLong("last_sync_at", 0L)
        // 状态文案没变时，last_sync_at 最多每分钟写一次；成功/失败切换立即写
        if (result == oldResult && at - oldAt in 0 until 60_000L) return
        prefs.edit()
            .putLong("last_sync_at", at)
            .putString("last_sync_result", result)
            .apply()
    }
}
