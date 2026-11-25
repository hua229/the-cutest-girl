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
    private const val FILE_IDENTITY = "identity"
    private const val FILE_SECRET = "dav_secret"

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

    private fun identityPrefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE_IDENTITY, Context.MODE_PRIVATE)

    private fun secretPrefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE_SECRET, Context.MODE_PRIVATE)

    fun webdavUrl(context: Context): String =
        prefs(context).getString("webdav_url", DEFAULT_DAV_URL) ?: DEFAULT_DAV_URL

    fun webdavUser(context: Context): String =
        prefs(context).getString("webdav_user", "") ?: ""

    fun webdavPass(context: Context): String {
        val secret = secretPrefs(context)
        val stored = secret.getString("webdav_pass", "") ?: ""
        if (stored.isNotBlank()) {
            return if (stored.startsWith(PASS_PREFIX)) decryptPass(stored) ?: "" else stored
        }
        // 旧版本：密码在 settings.xml（明文或密文）。迁移到 dav_secret.xml 并从 settings 删除
        val legacy = prefs(context).getString("webdav_pass", "") ?: ""
        if (legacy.isBlank()) return ""
        val plain = if (legacy.startsWith(PASS_PREFIX)) decryptPass(legacy) ?: "" else legacy
        val migrated = if (plain.isBlank()) legacy else encryptPass(plain) ?: legacy
        secret.edit().putString("webdav_pass", migrated).apply()
        prefs(context).edit().remove("webdav_pass").apply()
        return plain
    }

    fun saveDavConfig(context: Context, url: String, user: String, pass: String) {
        // 加密失败（极少见）时回退明文，至少不丢功能；下次保存会再试
        val storedPass = if (pass.isBlank()) "" else encryptPass(pass) ?: pass
        prefs(context).edit()
            .putString("webdav_url", url.trim().ifBlank { DEFAULT_DAV_URL })
            .putString("webdav_user", user.trim())
            // 密码不再留在可备份的 settings.xml
            .remove("webdav_pass")
            .apply()
        secretPrefs(context).edit().putString("webdav_pass", storedPass).apply()
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

    /** 本机唯一设备 ID（首次生成后固定），用于区分记录是谁创建的。可随系统备份/备份 zip 恢复。 */
    fun deviceId(context: Context): String {
        identityPrefs(context).getString("device_id", null)?.let { return it }
        // 旧版本存在 settings.xml：迁移到 identity.xml，并从旧文件删掉
        val legacy = prefs(context).getString("device_id", null)
        if (legacy != null) {
            identityPrefs(context).edit().putString("device_id", legacy).apply()
            prefs(context).edit().remove("device_id").apply()
            return legacy
        }
        val id = UUID.randomUUID().toString()
        identityPrefs(context).edit().putString("device_id", id).apply()
        return id
    }

    fun myName(context: Context): String {
        identityPrefs(context).getString("my_name", null)?.let { return it }
        // 旧版本存在 settings.xml：迁移过去，避免身份和密码一起留在被排除的文件里
        val legacy = prefs(context).getString("my_name", null)
        if (legacy != null) {
            identityPrefs(context).edit().putString("my_name", legacy).apply()
            prefs(context).edit().remove("my_name").apply()
            return legacy
        }
        return "我"
    }

    fun saveMyName(context: Context, name: String) {
        identityPrefs(context).edit()
            .putString("my_name", name.trim().ifBlank { "我" })
            .apply()
    }

    /** 已经把昵称同步刷进历史记录的名字；和 myName 不一致时，下次同步会刷新全部旧记录 */
    fun propagatedName(context: Context): String {
        identityPrefs(context).getString("propagated_name", null)?.let { return it }
        val legacy = prefs(context).getString("propagated_name", null)
        if (legacy != null) {
            identityPrefs(context).edit().putString("propagated_name", legacy).apply()
            prefs(context).edit().remove("propagated_name").apply()
            return legacy
        }
        return ""
    }

    fun setPropagatedName(context: Context, name: String) {
        identityPrefs(context).edit().putString("propagated_name", name).apply()
    }

    internal fun backupIdentity(context: Context) =
        BackupIdentity(deviceId(context), myName(context), propagatedName(context))

    internal fun restoreBackupIdentity(context: Context, identity: BackupIdentity): Boolean =
        identityPrefs(context).edit()
            .putString("device_id", identity.deviceId)
            .putString("my_name", identity.myName)
            .putString("propagated_name", identity.propagatedName)
            .commit()

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

internal data class BackupIdentity(
    val deviceId: String,
    val myName: String,
    val propagatedName: String
)
