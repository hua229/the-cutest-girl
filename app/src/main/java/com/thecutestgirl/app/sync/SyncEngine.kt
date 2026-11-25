package com.thecutestgirl.app.sync

import android.content.Context
import androidx.room.withTransaction
import com.thecutestgirl.app.data.AppDatabase
import com.thecutestgirl.app.data.EventRecord
import com.thecutestgirl.app.data.MealRecord
import com.thecutestgirl.app.data.OutfitRecord
import com.thecutestgirl.app.data.Tombstone
import com.thecutestgirl.app.data.PhotoStore
import com.thecutestgirl.app.data.SettingsStore
import com.thecutestgirl.app.data.SyncRecord
import com.thecutestgirl.app.data.parseSyncRecord
import com.thecutestgirl.app.data.noteVersionOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * 双向同步引擎。
 *
 * 云端目录结构（坚果云）：
 * /thecutestgirl/records/<id>.json    每条记录一个文件
 * /thecutestgirl/records/T_<id>.json  删除墓碑（删了某条记录的标记）
 * /thecutestgirl/photos/<name>.jpg    压缩后的照片
 *
 * 规则：
 * - 下载：比较记录内的版本时间，不按创建者跳过，也不混用服务器时间
 * - 冲突：双方同时改一条记录，updatedAt 新的一方赢
 * - 删除：本地删 → 传墓碑；看到远端墓碑 → 本地也删（记录+照片），不会复活
 * - 首次配对：全量拉历史，但不弹通知
 */
class SyncEngine(private val context: Context) {

    /** 设置页「测试并开启同步」的结果：成功（含端点是否变化）或可读的失败原因 */
    sealed class DavTestResult {
        data class Ok(val changed: Boolean) : DavTestResult()
        data class Failure(val message: String) : DavTestResult()
    }

    /** 目录准备失败要单独识别：只有这种情况才允许把 dirsReady 清掉重试 */
    private class DirectorySetupException(message: String) : IOException(message)

    /**
     * FIX-03：同端点下云端目录被手动删掉 / 服务商重置目录。
     * 只有 `listDir(records/photos)` 的 404 才算这个错误，用来清 dirsReady 让下一轮重建。
     * 单条记录 GET 404 只是那条被删了，绝不能触发这里的逻辑。
     */
    private class CloudDirectoryMissingException(message: String) : IOException(message)

    /**
     * 对方新增/改动的一条内容，用来拼通知。
     *
     * [recordId] 是通知去重的钥匙（前台 20 秒轮询会反复扫到同一条记录）；
     * [route] / [date] 让用户点通知能直接落到那条记录所在的页面和日期。
     */
    data class PartnerItem(
        val typeLabel: String,
        val fromName: String,
        val recordId: String = "",
        val route: String = "",
        val date: String = "",
        val version: Long = 0L
    ) {
        /**
         * 通知去重键：记录 id + 版本。
         *
         * 只用 id 不行——对方改了同一条记录两次（比如先写备注再补一句评论），
         * 第二次会被当成「已经通知过」而静默掉。带上版本，同一条记录的每次改动都能提醒一次。
         */
        val notifyKey: String get() = if (recordId.isBlank()) "" else "$recordId@$version"
    }

    data class Result(
        val ok: Boolean,
        val uploaded: Int = 0,
        val downloaded: Int = 0,
        val uploadedPhotos: Int = 0,
        val downloadedPhotos: Int = 0,
        val shrunkPhotos: Int = 0,
        val partnerNew: List<PartnerItem> = emptyList(),
        /** 本轮没能传上去、仍标记为 dirty 的本地记录数（下一轮自动重试） */
        val pending: Int = 0,
        /** 本轮跳过的坏记录数（云端内容无法解析，例如日期非法） */
        val skipped: Int = 0,
        val error: String? = null,
        /** 值不值得让 WorkManager 退避重试（P2-3） */
        val retryable: Boolean = true,
        /** 失败时的结构化原因（P2-3） */
        val errorKind: DavError? = null
    )

    private val db = AppDatabase.getInstance(context)
    private val outfitDao = db.outfitDao()
    private val mealDao = db.mealDao()
    private val eventDao = db.eventDao()
    private val tombstoneDao = db.tombstoneDao()

    companion object {
        /** 超过 1MB 的视为未压缩的历史大图（正常压缩后都在 500KB 内） */
        private const val SHRINK_THRESHOLD = 1_048_576L

        /** 大图瘦身 / 照片目录扫描的最低间隔：6 小时 */
        private const val MAINTENANCE_INTERVAL_MS = 6 * 60 * 60 * 1000L

        /** 云端孤儿照片 GC 间隔：24 小时；云端照片保留 7 天再删 */
        private const val CLOUD_GC_INTERVAL_MS = 24 * 60 * 60 * 1000L
        private const val CLOUD_PHOTO_TTL_MS = 7 * 24 * 60 * 60 * 1000L

        private val syncMutex = Mutex()

        /**
         * 设置页「测试并开启同步」用：保存新配置，端点真的变了就把本机增量账本整本作废。
         *
         * 换云盘地址 / 账号 / 应用密码时，[SyncStateStore] 里那本账记的全是**旧云盘**的状态：
         * 文件指纹、已确认集合、dirsReady、已通知 id，加上 `first_sync_done`。跨云盘沿用会出三种错：
         * - 新云盘恰好有同名、同时间、同长度的文件 → 被当成没变跳过，老记录永远同步不到；
         * - dirsReady 还记着「旧云盘目录建过了」→ 新云盘跳过 MKCOL → listDir 404 → 整轮失败；
         * - first_sync_done=true → 新云盘整批历史被当「对方刚传的」一次性狂弹通知。
         *
         * 清干净之后，下一轮同步等于一次安静的全量首次配对，且**不动本地数据库和照片**——
         * 改设置本来就不该删用户手机上的任何东西。
         *
         * @return 端点是否变化。调用方据此决定要不要对新云盘重新发 MKCOL（见 [testConnection]）。
         */
        fun applyDavConfig(context: Context, url: String, user: String, pass: String): Boolean {
            val changed = SettingsStore.davConfigChanged(context, url, user, pass)
            SettingsStore.saveDavConfig(context, url, user, pass)
            if (changed) {
                SyncStateStore.resetForNewEndpoint(context)
                SettingsStore.clearFirstSyncDone(context)
            }
            return changed
        }

        /**
         * 「先验证、后提交」：候选配置先连一次（换端点时把三层目录建好并真列一次），
         * 全部成功才保存，失败不动旧配置、不动账本。
         *
         * 为什么必须这样：旧入口是 applyDavConfig 先落盘，密码打错一个字符，
         * 旧配置就被覆盖、账本也被重置，用户退回设置页后同步直接坏掉。
         */
        suspend fun verifyAndApplyDavConfig(
            context: Context,
            url: String,
            user: String,
            pass: String
        ): DavTestResult = withContext(Dispatchers.IO) {
            try {
                val changed = SettingsStore.davConfigChanged(context, url, user, pass)
                val candidate = WebDavClient(
                    url.trim().ifBlank { SettingsStore.DEFAULT_DAV_URL },
                    user.trim(),
                    pass
                )
                when (val test = candidate.testConnection()) {
                    is DavResult.Err -> return@withContext DavTestResult.Failure(test.error.userMessage())
                    is DavResult.Ok -> Unit
                }
                if (changed) {
                    val dirsOk = candidate.mkcol("thecutestgirl") is DavResult.Ok &&
                        candidate.mkcol("thecutestgirl/records") is DavResult.Ok &&
                        candidate.mkcol("thecutestgirl/photos") is DavResult.Ok &&
                        candidate.listDir("thecutestgirl/records") is DavResult.Ok &&
                        candidate.listDir("thecutestgirl/photos") is DavResult.Ok
                    if (!dirsOk) {
                        return@withContext DavTestResult.Failure(
                            "云端目录建不出来：请检查账号、配额或网络"
                        )
                    }
                }
                SettingsStore.saveDavConfig(context, url, user, pass)
                if (changed) {
                    SyncStateStore.resetForNewEndpoint(context)
                    SettingsStore.clearFirstSyncDone(context)
                    SyncStateStore.setDirsReady(context, true)
                }
                DavTestResult.Ok(changed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DavTestResult.Failure(
                    e.message?.takeIf { it.isNotBlank() } ?: "连接失败，请稍后重试"
                )
            }
        }
    }

    suspend fun syncOnce(): Result = syncMutex.withLock { syncOnceLocked() }

    private suspend fun syncOnceLocked(): Result = withContext(Dispatchers.IO) {
        val dav = WebDavClient(
            SettingsStore.webdavUrl(context),
            SettingsStore.webdavUser(context),
            SettingsStore.webdavPass(context)
        )
        try {
            val myDevice = SettingsStore.deviceId(context)
            val firstTime = !SettingsStore.firstSyncDone(context)

            // 三个目录只建一次。前台轮询 20 秒一轮时，每轮 3 次 MKCOL 纯属白跑一趟网络。
            // 建目录 + 真列一次才算成功；失败则保持/写回 dirsReady=false，下一轮重试。
            // 只有目录准备失败才重置标记（见 catch），普通网络失败不重置，避免每轮多 3 次 MKCOL。
            if (!SyncStateStore.dirsReady(context)) {
                val dirsOk = dav.mkcol("thecutestgirl") is DavResult.Ok &&
                    dav.mkcol("thecutestgirl/records") is DavResult.Ok &&
                    dav.mkcol("thecutestgirl/photos") is DavResult.Ok &&
                    dav.listDir("thecutestgirl/records") is DavResult.Ok &&
                    dav.listDir("thecutestgirl/photos") is DavResult.Ok
                SyncStateStore.setDirsReady(context, dirsOk)
                if (!dirsOk) {
                    throw DirectorySetupException("云端目录准备失败，请检查账号、配额或网络")
                }
            }

            // ============ 0. 旧迁移 id 撞车修复 ============
            // v1→v2 迁移时两台手机、三张表各自产生了 "L1、L2…" 这类会互相撞车的 id，
            // 同步合并时同 id 记录"最后写入的赢"，对方的记录会被覆盖掉（"事件消失"的根因）。
            // 规则：只有记录的「主人」（origin 是本机，或还没认领的 "local"）才改名重传，
            // 改成 "M-设备指纹-编号" 的全局唯一 id；对方设备等主人改完后，
            // 收到 M- 记录时自动清掉本地同源（同 createdAt）的旧 L 副本，避免重复。
            val deviceTag = SettingsStore.deviceId(context).take(8)
            val isOwn = { origin: String -> origin == myDevice || origin == "local" }
            for (r in outfitDao.getLegacy()) {
                if (!isOwn(r.origin)) continue
                outfitDao.upsert(
                    r.copy(id = "M-$deviceTag-${r.id.removePrefix("L")}", updatedAt = System.currentTimeMillis(), dirty = true)
                )
                outfitDao.delete(r)
                dav.delete("thecutestgirl/records/${r.id}.json")
            }
            for (r in mealDao.getLegacy()) {
                if (!isOwn(r.origin)) continue
                mealDao.upsert(
                    r.copy(id = "M-$deviceTag-${r.id.removePrefix("L")}", updatedAt = System.currentTimeMillis(), dirty = true)
                )
                mealDao.delete(r)
                dav.delete("thecutestgirl/records/${r.id}.json")
            }
            for (r in eventDao.getLegacy()) {
                if (!isOwn(r.origin)) continue
                eventDao.upsert(
                    r.copy(id = "M-$deviceTag-${r.id.removePrefix("L")}", updatedAt = System.currentTimeMillis(), dirty = true)
                )
                eventDao.delete(r)
                dav.delete("thecutestgirl/records/${r.id}.json")
            }
            // 旧 id 的墓碑一并清掉：现在传墓碑反而可能误删对方还未升级的同名记录
            tombstoneDao.deleteLegacy()

            val remoteRecords = when (val r = dav.listDir("thecutestgirl/records")) {
                is DavResult.Ok -> r.value
                is DavResult.Err -> {
                    // 目录 404 = 云端目录被删了：清掉 dirsReady，下一轮重建
                    if (r.error == DavError.NotFound) {
                        throw CloudDirectoryMissingException("云端记录目录不见了，正在自动重建")
                    }
                    throw DavException(r.error)
                }
            }
            // photos 目录一次 PROPFIND 同时服务「上传去重」「补漏」「瘦身」三处。
            // 目录 404 同样要触发重建；其他错误仍可容忍（照片链路失败不阻塞记录同步）。
            val remotePhotos = when (val r = dav.listDir("thecutestgirl/photos")) {
                is DavResult.Ok -> r.value
                is DavResult.Err -> {
                    if (r.error == DavError.NotFound) {
                        throw CloudDirectoryMissingException("云端照片目录不见了，正在自动重建")
                    }
                    null
                }
            }
            val remotePhotoNames = remotePhotos?.map { it.name }?.toHashSet() ?: hashSetOf()
            val remotePhotosBySize: Map<String, Long> =
                remotePhotos?.associate { it.name to it.size } ?: emptyMap()

            // 增量同步：PROPFIND 已经给了每个文件的服务器时间与字节数，
            // 拿它们和上次下载时记下的指纹比。指纹没变 + 本地有这条 + 本地没有未上传改动
            // → 这个文件这轮不用下载。记录攒到几百条时，这是从「几百次往返」降到「几次」的关键。
            //
            // 三道安全闸，缺一不可：
            // 1. 只跳「本地已有且干净」的文件：换手机、清过数据、本地有改动时一律照常下载
            // 2. 指纹 = 服务器时间 + 字节数：getlastmodified 只有秒精度，同一秒内改两次
            //    时间戳会一模一样，靠字节数才能区分开
            // 3. 只跳「同一个指纹已经见过两次」的文件：万一时间和长度都恰好没变，
            //    第一次见到时仍然照常下载，第二次确认这个指纹稳定了才开始跳。
            //    代价是改一次多下一次，换来的是不会漏内容——这个方向不能反。
            val knownSigs = SyncStateStore.fileSignatures(context)
            val knownConfirmed = SyncStateStore.confirmedFiles(context)
            val nextSigs = mutableMapOf<String, String>()
            val nextConfirmed = mutableSetOf<String>()
            val localIds = (
                outfitDao.getAllIds() + mealDao.getAllIds() + eventDao.getAllIds()
                ).toHashSet()
            val localDirtyIds = (
                outfitDao.getDirty().map { it.id } +
                    mealDao.getDirty().map { it.id } +
                    eventDao.getDirty().map { it.id }
                ).toHashSet()

            var uploaded = 0
            var downloaded = 0
            var uploadedPhotos = 0
            var downloadedPhotos = 0
            val partnerNew = mutableListOf<PartnerItem>()
            // P1-PERF-7：先收集远端记录，网络循环结束后在一个事务里批量合并
            val pendingMerges = mutableListOf<SyncRecord>()
            val partnerCandidates = mutableListOf<PartnerItem>()
            // 单条上传失败的原因（不中断本轮，只在状态里如实报告）
            val uploadErrors = mutableListOf<String>()
            // 云端读不动、被跳过的坏记录数
            var skipped = 0

            // ============ 1. 处理远端：墓碑删除 + 下载新记录 ============
            // 墓碑优先：远端墓碑 + 本地待上传的墓碑，命中的记录文件一律跳过，
            // 防止「刚删的记录又被自己/对方同步回来」
            val pendingLocalTombstones = tombstoneDao.getAll().map { it.id }.toHashSet()
            val remoteTombstoneIds = remoteRecords
                .filter { it.name.startsWith("T_") }
                .map { it.name.removePrefix("T_").removeSuffix(".json") }
                .filter { it.isNotBlank() }
                .toHashSet()

            for (entry in remoteRecords) {
                val name = entry.name
                if (name.startsWith("T_")) {
                    val id = name.removePrefix("T_").removeSuffix(".json")
                    if (id.isNotBlank()) applyTombstone(id)
                    continue
                }
                if (!name.endsWith(".json")) continue
                val id = name.removeSuffix(".json")
                if (id.isBlank() || id in remoteTombstoneIds || id in pendingLocalTombstones) continue

                // 增量跳过：指纹没变 + 这个指纹已确认稳定 + 本地已有这条 + 本地没有未上传改动。
                // 注意本地 dirty 时不能跳：本地有改动时还得靠合并把对方的新内容并进来。
                val sig = SyncStateStore.signatureOf(entry.lastModified, entry.size)
                val known = knownSigs[name]
                if (known != null && known == sig && name in knownConfirmed &&
                    id in localIds && id !in localDirtyIds
                ) {
                    nextSigs[name] = sig
                    nextConfirmed += name
                    continue
                }

                // 归属只是创建者身份；对方也能修改这条记录的备注。
                // 必须读取 JSON 里的版本，服务器 Last-Modified 与手机时钟无法直接比较。
                // 下载失败时停止本轮，避免用尚未合并的旧内容覆盖云端备注。
                // 但单条 404 只代表这条记录在 PROPFIND 之后被删了：跳过它，不能重置 dirsReady。
                val text = when (val r = dav.getString("thecutestgirl/records/$name")) {
                    is DavResult.Ok -> r.value
                    is DavResult.Err -> {
                        if (r.error == DavError.NotFound) {
                            skipped++
                            continue
                        }
                        throw DavException(r.error)
                    }
                }
                // 记下这轮看到的指纹；「同一个指纹第二次出现」才算确认稳定
                nextSigs[name] = sig
                if (known != null && known == sig) nextConfirmed += name
                // 坏记录（编号对不上、日期非法、字段缺失）只跳过这一条：
                // 抛异常会让每轮同步都死在同一份脏数据上，从此再也同步不了任何新内容。
                val record = parseSyncRecord(text)
                if (record == null || record.id != id) {
                    skipped++
                    continue
                }
                // 网络阶段只下载、不写库；合并放到循环后的单事务里
                pendingMerges += record

                // 记录带的照片本地没有 → 下载
                val photoName = when (record) {
                    is SyncRecord.Outfit -> record.record.photoPath
                    is SyncRecord.Meal -> record.record.photoPath
                    is SyncRecord.Event -> record.record.photoPath
                }
                if (photoName != null && !PhotoStore.file(context, photoName).exists()) {
                    if (dav.downloadTo(
                            "thecutestgirl/photos/$photoName",
                            PhotoStore.file(context, photoName)
                        ) is DavResult.Ok
                    ) {
                        downloadedPhotos++
                    }
                }

                if (!firstTime && record.origin.isNotBlank() && record.origin != myDevice) {
                    partnerCandidates += PartnerItem(
                        typeLabel = record.typeLabel,
                        fromName = record.originName,
                        recordId = record.id,
                        route = record.route,
                        date = record.date,
                        version = record.updatedAt
                    )
                }
            }

            // 网络请求全部结束后，一个事务写库：100 条记录只产生 1 次事务/fsync。
            // 事务内重新读本地版本（mergeXxx 内部就是这么做的），不会覆盖下载期间刚保存的内容。
            // FIX-07：墓碑 id 在事务外查一次，避免每条远端记录都 getAll()（N+1）。
            val tombstoneIds = tombstoneDao.getAll().map { it.id }.toHashSet()
            val mergedIds = mutableSetOf<String>()
            db.withTransaction {
                for (record in pendingMerges) {
                    if (mergeRemoteLocked(record, tombstoneIds)) mergedIds += record.id
                }
            }
            downloaded += mergedIds.size
            partnerNew += partnerCandidates.filter { it.recordId in mergedIds }

            // ============ 1.5 昵称传播 ============
            // 先合并对方的备注，再刷新创建者昵称，避免改名抬高旧内容版本、吞掉对方的备注。
            // 每轮校正旧快照：即使 propagatedName 已相同，也可能刚下载到对方保留的旧昵称。
            val currentName = SettingsStore.myName(context)
            val nowForName = System.currentTimeMillis()
            db.withTransaction {
                outfitDao.updateOriginName(myDevice, currentName, nowForName)
                mealDao.updateOriginName(myDevice, currentName, nowForName)
                eventDao.updateOriginName(myDevice, currentName, nowForName)
            }

            // ============ 1.6 照片补漏 ============
            // 本地记录引用的照片文件如果缺失（下载时网络失败等），自动从云端补下载。
            // 每次同步都检查一遍，任何一张漏掉的照片最终都会自动补齐。
            val localPhotoRefs = (
                outfitDao.getAllPhotoPaths() +
                    mealDao.getAllPhotoPaths() +
                    eventDao.getAllPhotoPaths()
                ).distinct()
            for (photoName in localPhotoRefs) {
                if (PhotoStore.file(context, photoName).exists()) continue
                if (photoName in remotePhotoNames) {
                    if (dav.downloadTo(
                            "thecutestgirl/photos/$photoName",
                            PhotoStore.file(context, photoName)
                        ) is DavResult.Ok
                    ) {
                        downloadedPhotos++
                    }
                }
            }

            // ============ 2. 上传本地改动（记录 + 照片） ============
            // 单条失败只跳过这一条：一条"毒记录"（超配额、文件名被服务端拒收等）不能
            // 让排在它后面的所有待上传内容永远传不上去。失败记录保持 dirty，下一轮再来。
            suspend fun uploadRecord(json: String, id: String, photoName: String?): Boolean {
                if (dav.putBytes("thecutestgirl/records/$id.json", json.toByteArray(), WebDavClient.jsonType()) is DavResult.Err) {
                    uploadErrors += "$id: 上传记录失败"
                    return false
                }
                uploaded++
                if (photoName != null && photoName !in remotePhotoNames) {
                    val local = PhotoStore.file(context, photoName)
                    if (local.exists()) {
                        if (dav.uploadFrom("thecutestgirl/photos/$photoName", local, WebDavClient.jpegType()) is DavResult.Ok) {
                            uploadedPhotos++
                            // 及时补进已上传集合，同一张照片被多条记录引用时不再重复 PUT
                            remotePhotoNames += photoName
                        } else {
                            // 照片没上去就先别把记录标成已同步，否则对方永远等不到这张图
                            uploadErrors += "$id: 上传照片失败"
                            return false
                        }
                    }
                }
                return true
            }

            suspend fun uploadOutfits() {
                for (r in outfitDao.getDirty()) {
                    if (!uploadRecord(r.toJson(), r.id, r.photoPath)) continue
                    outfitDao.markClean(r.id, r.updatedAt)
                    // 只认领旧迁移记录（origin="local"）；对方创建、我编辑过的记录归属保持不变
                    if (r.origin == "local") outfitDao.setOrigin(r.id, myDevice)
                }
            }
            suspend fun uploadMeals() {
                for (r in mealDao.getDirty()) {
                    if (!uploadRecord(r.toJson(), r.id, r.photoPath)) continue
                    mealDao.markClean(r.id, r.updatedAt)
                    if (r.origin == "local") mealDao.setOrigin(r.id, myDevice)
                }
            }
            suspend fun uploadEvents() {
                for (r in eventDao.getDirty()) {
                    if (!uploadRecord(r.toJson(), r.id, r.photoPath)) continue
                    eventDao.markClean(r.id, r.updatedAt)
                    if (r.origin == "local") eventDao.setOrigin(r.id, myDevice)
                }
            }
            uploadOutfits()
            uploadMeals()
            uploadEvents()

            // ============ 3. 上传删除墓碑，并清掉云端对应的记录文件 ============
            for (t in tombstoneDao.getAll()) {
                val body = """{"type":"tombstone","id":"${t.id}","updatedAt":${t.updatedAt}}"""
                if (dav.putBytes("thecutestgirl/records/T_${t.id}.json", body.toByteArray(), WebDavClient.jsonType()) is DavResult.Ok) {
                    tombstoneDao.delete(t.id)
                    // 对方设备靠墓碑删除本地；记录文件及时清掉，避免另一台设备下载到残留
                    dav.delete("thecutestgirl/records/${t.id}.json")
                }
            }

            // ============ 4. 历史大图瘦身 ============
            // 旧版本(v1)上传的未压缩照片有几 MB，这里自动压缩：
            // - 本地大、云端没有或云端也大 → 本地压缩 + 覆盖上传云端
            // - 本地大、云端明显更小（对方已压过）→ 下载压缩版覆盖本地
            // 每轮扫描整个照片目录代价太高（前台 30 秒一轮），改为最多每 6 小时一次；
            // 首次同步 / 换端点后（last_maintenance_at 被清掉）立即跑一次。
            var shrunkPhotos = 0
            val nowForMaintenance = System.currentTimeMillis()
            val lastMaintenance = SyncStateStore.lastMaintenanceAt(context)
            val maintenanceDue = lastMaintenance == 0L ||
                nowForMaintenance - lastMaintenance >= MAINTENANCE_INTERVAL_MS
            if (maintenanceDue) {
                PhotoStore.dir(context)
                    .listFiles { f -> f.isFile && f.length() > SHRINK_THRESHOLD }
                    ?.forEach { file ->
                        val remoteSize = remotePhotosBySize[file.name]
                        val localSize = file.length()
                        if (remoteSize != null && remoteSize in 1 until (localSize * 6 / 10)) {
                            // 云端已是压缩版，流式拉下来替换本地大图（原子写入）
                            if (dav.downloadTo("thecutestgirl/photos/${file.name}", file) is DavResult.Ok) {
                                shrunkPhotos++
                            }
                        } else {
                            // 压缩本地并覆盖云端
                            PhotoStore.compressInPlace(file)
                            if (file.length() < localSize) {
                                if (dav.uploadFrom(
                                        "thecutestgirl/photos/${file.name}",
                                        file,
                                        WebDavClient.jpegType()
                                    ) is DavResult.Ok
                                ) {
                                    shrunkPhotos++
                                }
                            }
                        }
                    }
                SyncStateStore.setLastMaintenanceAt(context, nowForMaintenance)

                // P2-8 本地照片 GC（FIX-05：与启动时共用同一个实现，只删数据库不引用 + 非草稿 + 超过 24h）
                PhotoStore.cleanupOrphans(context)

                // P2-8 云端照片 GC（每天一次，保守策略）：
                // 先下载所有远端记录收集照片引用；任一条解析失败就整轮跳过，避免误删。
                val lastCloudGc = SyncStateStore.lastCloudGcAt(context)
                val cloudGcDue = lastCloudGc == 0L ||
                    nowForMaintenance - lastCloudGc >= CLOUD_GC_INTERVAL_MS
                if (cloudGcDue && remotePhotos != null) {
                    val remoteRefs = mutableSetOf<String>()
                    var remoteParseFailed = false
                    for (entry in remoteRecords) {
                        if (entry.name.startsWith("T_") || !entry.name.endsWith(".json")) continue
                        val text = (dav.getString("thecutestgirl/records/${entry.name}") as? DavResult.Ok)?.value
                        val rec = text?.let { parseSyncRecord(it) }
                        if (rec == null) {
                            remoteParseFailed = true
                            break
                        }
                        val photo = when (rec) {
                            is SyncRecord.Outfit -> rec.record.photoPath
                            is SyncRecord.Meal -> rec.record.photoPath
                            is SyncRecord.Event -> rec.record.photoPath
                        }
                        if (!photo.isNullOrBlank()) remoteRefs += photo
                    }
                    if (!remoteParseFailed) {
                        val keep = remoteRefs + localPhotoRefs
                        val cloudOldCutoff = nowForMaintenance - CLOUD_PHOTO_TTL_MS
                        remotePhotos.forEach { entry ->
                            if (entry.name !in keep && entry.lastModified in 1 until cloudOldCutoff) {
                                dav.delete("thecutestgirl/photos/${entry.name}")
                            }
                        }
                        SyncStateStore.setLastCloudGcAt(context, nowForMaintenance)
                    }
                }
            }

            SettingsStore.setPropagatedName(context, currentName)
            if (firstTime) SettingsStore.setFirstSyncDone(context)
            // 记下本轮见到的文件与它们的指纹。nextSigs 是这一轮现场重建的，
            // 云端已删掉的文件自然不会被带进下一轮（自动瘦身，不会越积越多）。
            SyncStateStore.saveFileSignatures(context, nextSigs)
            SyncStateStore.saveConfirmedFiles(context, nextConfirmed)
            // 单条上传失败不算整轮失败：已经传上去的都算成功，失败的保持 dirty 等下一轮。
            // 状态里如实写出来，否则用户会以为全同步完了。
            val statusText = buildString {
                append("同步成功")
                if (shrunkPhotos > 0) append("（瘦身 $shrunkPhotos 张历史大图）")
                if (uploadErrors.isNotEmpty()) append("，${uploadErrors.size} 条待重传")
                if (skipped > 0) append("，跳过 $skipped 条异常数据")
            }
            SettingsStore.saveSyncStatus(context, System.currentTimeMillis(), statusText)
            Result(
                ok = true,
                uploaded = uploaded,
                downloaded = downloaded,
                uploadedPhotos = uploadedPhotos,
                downloadedPhotos = downloadedPhotos,
                shrunkPhotos = shrunkPhotos,
                partnerNew = partnerNew,
                pending = uploadErrors.size,
                skipped = skipped,
                error = uploadErrors.firstOrNull()
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: DavException) {
            val kind = e.error
            val message = kind.userMessage()
            SettingsStore.saveSyncStatus(
                context, System.currentTimeMillis(), "同步失败：$message"
            )
            Result(
                ok = false,
                error = message,
                retryable = kind.retryable(),
                errorKind = kind
            )
        } catch (e: CloudDirectoryMissingException) {
            // FIX-03：目录在同步过程中消失了（被手动删/服务商重置）。
            // 清掉 dirsReady，下一轮会重新 MKCOL + listDir，不会永久失败。
            SyncStateStore.setDirsReady(context, false)
            val message = e.message ?: "云端目录不见了，正在自动重建"
            SettingsStore.saveSyncStatus(
                context, System.currentTimeMillis(), "同步失败：$message"
            )
            Result(
                ok = false,
                error = message,
                retryable = true,
                errorKind = DavError.NotFound
            )
        } catch (e: DirectorySetupException) {
            // 目录没建好：明确把标记清掉，下一轮重新 MKCOL，不要永久卡住
            SyncStateStore.setDirsReady(context, false)
            val message = e.message ?: "云端目录准备失败"
            SettingsStore.saveSyncStatus(
                context, System.currentTimeMillis(), "同步失败：$message"
            )
            Result(
                ok = false,
                error = message,
                retryable = true,
                errorKind = DavError.Network(message)
            )
        } catch (e: Exception) {
            SettingsStore.saveSyncStatus(
                context, System.currentTimeMillis(),
                "同步失败：${e.message ?: "网络错误"}"
            )
            Result(
                ok = false,
                error = e.message ?: "网络错误",
                retryable = true,
                errorKind = DavError.Network(e.message ?: "网络错误")
            )
        }
    }

    /**
     * 合并一条远端记录（调用方负责包事务；P1-PERF-7 批量合并）。
     * 网络请求必须已经在事务外完成；[tombstoneIds] 由调用方在事务外查一次传入。
     */
    private suspend fun mergeRemoteLocked(record: SyncRecord, tombstoneIds: Set<String>): Boolean {
        if (record.id in tombstoneIds) return false
        return when (record) {
            is SyncRecord.Outfit -> mergeOutfit(record.record)
            is SyncRecord.Meal -> mergeMeal(record.record)
            is SyncRecord.Event -> mergeEvent(record.record)
        }
    }

    private suspend fun mergeOutfit(remote: OutfitRecord): Boolean {
        val local = outfitDao.getById(remote.id)
        if (local == null) {
            outfitDao.upsert(remote)
            if (remote.id.startsWith("M-")) outfitDao.deleteLegacyByCreatedAt(remote.createdAt)
            return true
        }
        val fields = mergeFields(
            localNote = local.note,
            localNoteDevice = local.noteDevice,
            localNoteName = local.noteName,
            localNoteAt = noteVersionOf(local.noteUpdatedAt, local.updatedAt),
            remoteNote = remote.note,
            remoteNoteDevice = remote.noteDevice,
            remoteNoteName = remote.noteName,
            remoteNoteAt = noteVersionOf(remote.noteUpdatedAt, remote.updatedAt),
            localReply = local.reply,
            localReplyName = local.replyName,
            localReplyAt = local.replyUpdatedAt,
            remoteReply = remote.reply,
            remoteReplyName = remote.replyName,
            remoteReplyAt = remote.replyUpdatedAt
        )
        val remoteWinsRest = remote.updatedAt > local.updatedAt
        val candidate = local.copy(
            date = if (remoteWinsRest) remote.date else local.date,
            photoPath = if (remoteWinsRest) remote.photoPath else local.photoPath,
            note = fields.note,
            noteDevice = fields.noteDevice,
            noteName = fields.noteName,
            reply = fields.reply,
            replyName = fields.replyName,
            noteUpdatedAt = fields.noteAt,
            replyUpdatedAt = fields.replyAt,
            updatedAt = maxOf(local.updatedAt, remote.updatedAt) +
                if (fields.needsUpload) 1L else 0L,
            // 归属（origin）跟着创建者走，合并不改；本地还是 "local"（从没传过）时采纳远端
            origin = if (local.origin.isBlank() || local.origin == "local") remote.origin else local.origin,
            // 昵称跟着记录级版本走：对方改昵称后本机必须能刷到（nicknameRefresh 用例覆盖）。
            originName = if (remoteWinsRest) remote.originName else local.originName,
            dirty = local.dirty || fields.needsUpload
        )
        if (candidate.copy(dirty = false) == local.copy(dirty = false)) return false
        outfitDao.upsert(candidate)
        if (candidate.id.startsWith("M-")) outfitDao.deleteLegacyByCreatedAt(candidate.createdAt)
        return true
    }

    private suspend fun mergeMeal(remote: MealRecord): Boolean {
        val local = mealDao.getById(remote.id)
        if (local == null) {
            mealDao.upsert(remote)
            if (remote.id.startsWith("M-")) mealDao.deleteLegacyByCreatedAt(remote.createdAt)
            return true
        }
        val fields = mergeFields(
            localNote = local.note,
            localNoteDevice = local.noteDevice,
            localNoteName = local.noteName,
            localNoteAt = noteVersionOf(local.noteUpdatedAt, local.updatedAt),
            remoteNote = remote.note,
            remoteNoteDevice = remote.noteDevice,
            remoteNoteName = remote.noteName,
            remoteNoteAt = noteVersionOf(remote.noteUpdatedAt, remote.updatedAt),
            localReply = local.reply,
            localReplyName = local.replyName,
            localReplyAt = local.replyUpdatedAt,
            remoteReply = remote.reply,
            remoteReplyName = remote.replyName,
            remoteReplyAt = remote.replyUpdatedAt
        )
        val remoteWinsRest = remote.updatedAt > local.updatedAt
        val candidate = local.copy(
            date = if (remoteWinsRest) remote.date else local.date,
            mealType = if (remoteWinsRest) remote.mealType else local.mealType,
            photoPath = if (remoteWinsRest) remote.photoPath else local.photoPath,
            note = fields.note,
            noteDevice = fields.noteDevice,
            noteName = fields.noteName,
            reply = fields.reply,
            replyName = fields.replyName,
            noteUpdatedAt = fields.noteAt,
            replyUpdatedAt = fields.replyAt,
            updatedAt = maxOf(local.updatedAt, remote.updatedAt) +
                if (fields.needsUpload) 1L else 0L,
            origin = if (local.origin.isBlank() || local.origin == "local") remote.origin else local.origin,
            // 昵称跟着记录级版本走：对方改昵称后本机必须能刷到（nicknameRefresh 用例覆盖）。
            originName = if (remoteWinsRest) remote.originName else local.originName,
            dirty = local.dirty || fields.needsUpload
        )
        if (candidate.copy(dirty = false) == local.copy(dirty = false)) return false
        mealDao.upsert(candidate)
        if (candidate.id.startsWith("M-")) mealDao.deleteLegacyByCreatedAt(candidate.createdAt)
        return true
    }

    private suspend fun mergeEvent(remote: EventRecord): Boolean {
        val local = eventDao.getById(remote.id)
        if (local == null) {
            eventDao.upsert(remote)
            if (remote.id.startsWith("M-")) eventDao.deleteLegacyByCreatedAt(remote.createdAt)
            return true
        }
        val fields = mergeFields(
            localNote = local.description,
            localNoteDevice = local.noteDevice,
            localNoteName = local.noteName,
            localNoteAt = noteVersionOf(local.noteUpdatedAt, local.updatedAt),
            remoteNote = remote.description,
            remoteNoteDevice = remote.noteDevice,
            remoteNoteName = remote.noteName,
            remoteNoteAt = noteVersionOf(remote.noteUpdatedAt, remote.updatedAt),
            localReply = local.reply,
            localReplyName = local.replyName,
            localReplyAt = local.replyUpdatedAt,
            remoteReply = remote.reply,
            remoteReplyName = remote.replyName,
            remoteReplyAt = remote.replyUpdatedAt
        )
        val remoteWinsRest = remote.updatedAt > local.updatedAt
        val candidate = local.copy(
            type = if (remoteWinsRest) remote.type else local.type,
            title = if (remoteWinsRest) remote.title else local.title,
            description = fields.note,
            photoPath = if (remoteWinsRest) remote.photoPath else local.photoPath,
            date = if (remoteWinsRest) remote.date else local.date,
            noteDevice = fields.noteDevice,
            noteName = fields.noteName,
            reply = fields.reply,
            replyName = fields.replyName,
            noteUpdatedAt = fields.noteAt,
            replyUpdatedAt = fields.replyAt,
            updatedAt = maxOf(local.updatedAt, remote.updatedAt) +
                if (fields.needsUpload) 1L else 0L,
            origin = if (local.origin.isBlank() || local.origin == "local") remote.origin else local.origin,
            // 昵称跟着记录级版本走：对方改昵称后本机必须能刷到（nicknameRefresh 用例覆盖）。
            originName = if (remoteWinsRest) remote.originName else local.originName,
            dirty = local.dirty || fields.needsUpload
        )
        if (candidate.copy(dirty = false) == local.copy(dirty = false)) return false
        eventDao.upsert(candidate)
        if (candidate.id.startsWith("M-")) eventDao.deleteLegacyByCreatedAt(candidate.createdAt)
        return true
    }

    private data class MergedFields(
        val note: String,
        val noteDevice: String,
        val noteName: String,
        val noteAt: Long,
        val reply: String,
        val replyName: String,
        val replyAt: Long,
        /** 本地有对方版本里没有的内容 → 合并结果要回传，否则对方永远看不到 */
        val needsUpload: Boolean
    )

    /**
     * 两个字段各自比时间，谁新用谁。
     *
     * 相等时取本地：本地刚写的内容时间戳会严格大于旧值，平局只可能是
     * 「两边都没动过」，此时保留本地即可，不会产生多余上传。
     */
    private fun mergeFields(
        localNote: String,
        localNoteDevice: String,
        localNoteName: String,
        localNoteAt: Long,
        remoteNote: String,
        remoteNoteDevice: String,
        remoteNoteName: String,
        remoteNoteAt: Long,
        localReply: String,
        localReplyName: String,
        localReplyAt: Long,
        remoteReply: String,
        remoteReplyName: String,
        remoteReplyAt: Long
    ): MergedFields {
        val noteFromRemote = remoteNoteAt > localNoteAt
        val replyFromRemote = remoteReplyAt > localReplyAt
        val note = if (noteFromRemote) remoteNote else localNote
        val reply = if (replyFromRemote) remoteReply else localReply

        // 本地这一侧有对方版本里没有的内容 → 合并后必须回传
        val localHasNewerNote = !noteFromRemote && note != remoteNote
        val localHasNewerReply = !replyFromRemote && reply != remoteReply
        // 内容一样、但本地多带了署名（对方那版还是空的）时也要回传：
        // 否则这条记录的作者信息永远只活在本机，换台手机又变回「ta 的备注」。
        val noteAuthorStale = !noteFromRemote && note == remoteNote &&
            ((localNoteDevice.isNotBlank() && localNoteDevice != remoteNoteDevice) ||
                (localNoteName.isNotBlank() && localNoteName != remoteNoteName))
        val replyAuthorStale = !replyFromRemote && reply == remoteReply &&
            localReplyName.isNotBlank() && localReplyName != remoteReplyName

        return MergedFields(
            note = note,
            // 备注署名跟着备注内容走：谁写的那段字，标签就写谁。
            // 老数据两边都是空 → 保持空，界面不贴「ta 的备注」，谁都能改。
            noteDevice = if (noteFromRemote) remoteNoteDevice else localNoteDevice,
            noteName = if (noteFromRemote) remoteNoteName else localNoteName,
            noteAt = maxOf(localNoteAt, remoteNoteAt),
            reply = reply,
            // 署名跟着评论内容走：谁写的评论，标签就写谁
            replyName = if (replyFromRemote) remoteReplyName else localReplyName,
            replyAt = maxOf(localReplyAt, remoteReplyAt),
            needsUpload = localHasNewerNote || localHasNewerReply ||
                noteAuthorStale || replyAuthorStale
        )
    }

    /** 看到远端墓碑：把本地对应记录和照片一起删掉 */
    private suspend fun applyTombstone(id: String) {
        outfitDao.getById(id)?.let {
            outfitDao.delete(it)
            PhotoStore.delete(context, it.photoPath)
        }
        mealDao.getById(id)?.let {
            mealDao.delete(it)
            PhotoStore.delete(context, it.photoPath)
        }
        eventDao.getById(id)?.let {
            eventDao.delete(it)
            PhotoStore.delete(context, it.photoPath)
        }
        // 如果是自己挂的墓碑转了一圈回来，清掉避免重复上传
        tombstoneDao.delete(id)
    }

    /**
     * 设置页「测试连接」用。
     *
     * [prepareDirectories] 为 true 时（换云盘的第一轮），除了根账号 PROPFIND，
     * 还会顺手把 thecutestgirl 三层目录建出来并真的 listDir 一次。
     * 原来只测根目录 → 新云盘没有 records 时「测试成功」但下一轮同步必 404，
     * 用户看到的就是「显示成功却一直同步失败」。现在按钮本身就把「手动建文件夹」那一步做掉了。
     */
    suspend fun testConnection(prepareDirectories: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val dav = WebDavClient(
            SettingsStore.webdavUrl(context),
            SettingsStore.webdavUser(context),
            SettingsStore.webdavPass(context)
        )
        if (dav.testConnection() is DavResult.Err) return@withContext false
        if (!prepareDirectories) return@withContext true
        val ok = dav.mkcol("thecutestgirl") is DavResult.Ok &&
            dav.mkcol("thecutestgirl/records") is DavResult.Ok &&
            dav.mkcol("thecutestgirl/photos") is DavResult.Ok &&
            dav.listDir("thecutestgirl/records") is DavResult.Ok &&
            dav.listDir("thecutestgirl/photos") is DavResult.Ok
        if (ok) SyncStateStore.setDirsReady(context, true)
        ok
    }
}
