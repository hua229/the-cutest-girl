package com.thecutestgirl.app.sync

import android.app.Application
import android.app.Instrumentation
import android.os.Bundle
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.thecutestgirl.app.data.*
import com.thecutestgirl.app.ui.event.EventEditViewModel
import com.thecutestgirl.app.ui.meal.MealEditViewModel
import com.thecutestgirl.app.ui.outfit.OutfitEditViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Runs real Room + WebDAV + ViewModel behavior, in a separate verification APK. */
class SyncRegressionRunner : Instrumentation() {
    private val db get() = AppDatabase.getInstance(targetContext)
    private lateinit var dav: WebDavClient
    private val oldTime = System.currentTimeMillis() - 120_000

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        check(targetContext.packageName.endsWith(".verification")) {
            "Run with -PverificationApp; never run destructive fixtures against the personal app"
        }
        val tests: List<Pair<String, suspend () -> Unit>> = listOf(
            "outfit notes travel from B back to creator A and back again" to { outfitRoundTrip() },
            "moment notes travel in both directions" to { eventRoundTrip(EVENT_TYPE_MOMENT) },
            "anniversary notes travel in both directions" to { eventRoundTrip(EVENT_TYPE_ANNIVERSARY) },
            "nickname refresh is received within the same minute" to { nicknameRefresh() },
            "renaming does not overwrite a partner's pending shared note" to { renamePreservesNote() },
            "older remote content never rolls back clean local content" to { staleRemote() },
            "newer unsent local note survives sync" to { pendingLocal() },
            "unchanged sync does not repeat downloads or notifications" to { unchangedSync() },
            "tombstones prevent shared records from coming back" to { deletedRecord() },
            "anniversary editor actually saves its note" to { anniversaryEditor() },
            "a second note saved during upload remains pending" to { editDuringUpload() },
            "outfit editor advances a version from a faster partner clock" to { outfitClockSkew() },
            "meal editor advances a version from a faster partner clock" to { mealClockSkew() },
            "event editor advances a version from a faster partner clock" to { eventClockSkew() },
            "a malformed remote record is skipped instead of stalling every sync" to { malformedRemoteRecord() },
            "a record the server rejects does not stall the other uploads" to { rejectedUploadIsIsolated() },
            "immediate sync bypasses a previous failed request's retry delay" to { retryDoesNotBlockFreshRequest() },
            "uploader note and partner reply both survive one sync round" to { noteAndReplyCoexist() },
            "partner reply alone does not touch the uploader's note" to { replyDoesNotClobberNote() },
            "reply written during an upload is not lost" to { replySurvivesConcurrentUpload() },
            "partner cannot edit the uploader's note from the editor" to { uploaderNoteIsReadOnly() },
            "note block is labelled with the uploader's nickname" to { noteLabelFollowsUploader() },
            "an unconfirmed file signature is still downloaded" to { unconfirmedSignatureIsDownloaded() },
            "incremental sync stops re-downloading untouched records" to { incrementalSkipsUntouched() },
            "switching the WebDAV endpoint resets the ledger and prepares folders" to { endpointSwitchResetsLedger() },
            "re-saving the same endpoint keeps the incremental ledger" to { sameEndpointKeepsLedger() }
        )
        val reports = mutableListOf<String>()
        var failures = 0
        for ((name, test) in tests) {
            try {
                runBlocking { fixture(); test() }
                reports += "PASS: $name"
            } catch (error: Throwable) {
                failures++
                reports += "FAIL: $name: ${error.message}\n${error.stackTraceToString()}"
            }
            sendStatus(0, Bundle().apply { putString("stream", reports.last() + "\n") })
        }
        val summary = "${tests.size - failures}/${tests.size} tests passed"
        finish(if (failures == 0) -1 else 0, Bundle().apply {
            putString("stream", summary)
            putInt("failures", failures)
        })
    }

    private fun fixture() {
        db.clearAllTables()
        val url = "http://10.0.2.2:18765/${UUID.randomUUID()}/"
        targetContext.getSharedPreferences("settings", 0).edit().clear()
            .putString("device_id", "device-A").putString("my_name", "Alice")
            .putString("propagated_name", "Alice").putBoolean("first_sync_done", true).commit()
        // 增量同步的账本必须一起清：每个用例都是全新的随机云端目录，
        // 留着上一个用例的时间戳会让「文件名相同但内容不同」被误判成没变。
        targetContext.getSharedPreferences("sync_state", 0).edit().clear().commit()
        SettingsStore.saveDavConfig(targetContext, url, "test", "test")
        dav = WebDavClient(url, "test", "test")
        check(dav.mkcol("thecutestgirl/records")) { "Local test server unavailable" }
        check(dav.mkcol("thecutestgirl/photos"))
    }

    // v2.8.0 起 note / reply 是两个字段，各自带时间戳；这里一律用具名参数，
    // 免得以后再加字段时位置悄悄错开（之前就是靠位置传的，加字段必炸）。
    private fun outfit(
        note: String = "original",
        at: Long = oldTime,
        origin: String = "device-A",
        reply: String = "",
        replyAt: Long = 0L,
        noteDevice: String = "",
        noteName: String = "",
        replyName: String = ""
    ) = OutfitRecord(
        id = "outfit-1", date = "2026-10-26", photoPath = "missing-test.jpg",
        note = note, noteDevice = noteDevice, noteName = noteName,
        reply = reply, replyName = replyName,
        noteUpdatedAt = at, replyUpdatedAt = replyAt,
        createdAt = oldTime, updatedAt = at,
        origin = origin, originName = "Alice", dirty = false
    )

    private fun event(
        type: Int,
        note: String = "original",
        at: Long = oldTime,
        reply: String = "",
        replyAt: Long = 0L
    ) = EventRecord(
        id = "event-1", type = type, title = "Together", description = note,
        reply = reply, noteUpdatedAt = at, replyUpdatedAt = replyAt,
        photoPath = null, date = "2026-10-26",
        createdAt = oldTime, updatedAt = at,
        origin = "device-A", originName = "Alice", dirty = false
    )

    private fun meal(
        note: String = "original",
        at: Long = oldTime,
        origin: String = "device-A",
        reply: String = "",
        replyAt: Long = 0L
    ) = MealRecord(
        id = "meal-1", date = "2026-10-26", mealType = 1, photoPath = null,
        note = note, reply = reply,
        noteUpdatedAt = at, replyUpdatedAt = replyAt,
        createdAt = oldTime, updatedAt = at,
        origin = origin, originName = "Alice", dirty = false
    )

    private fun remote(id: String, json: String) {
        check(dav.putBytes("thecutestgirl/records/$id.json", json.toByteArray(), WebDavClient.jsonType()))
    }

    private suspend fun sync(): SyncEngine.Result = SyncEngine(targetContext).syncOnce().also {
        check(it.ok) { "Sync failed: ${it.error}" }
    }

    private fun deviceB() {
        targetContext.getSharedPreferences("settings", 0).edit()
            .putString("device_id", "device-B").putString("my_name", "Bob")
            .putString("propagated_name", "Bob").commit()
    }

    private suspend fun outfitRoundTrip() {
        val original = outfit()
        db.outfitDao().upsert(original)
        remote(
            original.id,
            original.copy(
                note = "note from Bob",
                noteUpdatedAt = oldTime + 1,
                updatedAt = oldTime + 1
            ).toJson()
        )
        sync()
        val received = db.outfitDao().getById(original.id)!!
        check(received.note == "note from Bob") { "Creator still sees '${received.note}'" }
        check(received.origin == "device-A") { "Editing transferred record ownership" }
        db.outfitDao().upsert(
            received.copy(
                note = "reply from Alice",
                noteUpdatedAt = oldTime + 2,
                updatedAt = oldTime + 2,
                dirty = true
            )
        )
        sync()
        deviceB()
        db.outfitDao().upsert(received)
        sync()
        check(db.outfitDao().getById(original.id)!!.note == "reply from Alice")
    }

    private suspend fun eventRoundTrip(type: Int) {
        val original = event(type)
        db.eventDao().upsert(original)
        remote(
            original.id,
            original.copy(
                description = "note from Bob",
                noteUpdatedAt = oldTime + 1,
                updatedAt = oldTime + 1
            ).toJson()
        )
        sync()
        val received = db.eventDao().getById(original.id)!!
        check(received.description == "note from Bob") { "Creator still sees '${received.description}'" }
        check(received.origin == "device-A")
        db.eventDao().upsert(
            received.copy(
                description = "reply from Alice",
                noteUpdatedAt = oldTime + 2,
                updatedAt = oldTime + 2,
                dirty = true
            )
        )
        sync()
        deviceB()
        db.eventDao().upsert(received)
        sync()
        check(db.eventDao().getById(original.id)!!.description == "reply from Alice")
    }

    private suspend fun nicknameRefresh() {
        val original = outfit(at = System.currentTimeMillis(), origin = "device-B")
        db.outfitDao().upsert(original)
        remote(original.id, original.copy(originName = "New Bob", updatedAt = original.updatedAt + 1).toJson())
        sync()
        check(db.outfitDao().getById(original.id)!!.originName == "New Bob") { "Old uploader nickname remains" }
    }

    private suspend fun renamePreservesNote() {
        val original = outfit()
        db.outfitDao().upsert(original)
        remote(
            original.id,
            original.copy(
                note = "Bob's shared note",
                noteUpdatedAt = oldTime + 1,
                updatedAt = oldTime + 1
            ).toJson()
        )
        SettingsStore.saveMyName(targetContext, "New Alice")
        sync()
        val received = db.outfitDao().getById(original.id)!!
        check(received.note == "Bob's shared note") { "Nickname propagation overwrote the note: '${received.note}'" }
        check(received.originName == "New Alice")
        val uploaded = parseSyncRecord(dav.getString("thecutestgirl/records/${original.id}.json")!!) as SyncRecord.Outfit
        check(uploaded.record.note == "Bob's shared note" && uploaded.originName == "New Alice")
    }

    private suspend fun staleRemote() {
        val newest = outfit("newest", origin = "device-B")
        db.outfitDao().upsert(newest)
        remote(newest.id, newest.copy(note = "stale", updatedAt = oldTime - 1).toJson())
        sync()
        check(db.outfitDao().getById(newest.id)!!.note == "newest")
    }

    private suspend fun pendingLocal() {
        val newest = outfit("unsent", origin = "device-B").copy(dirty = true)
        db.outfitDao().upsert(newest)
        remote(newest.id, newest.copy(note = "stale", updatedAt = oldTime - 1).toJson())
        sync()
        check(db.outfitDao().getById(newest.id)!!.note == "unsent")
        val uploaded = parseSyncRecord(dav.getString("thecutestgirl/records/${newest.id}.json")!!) as SyncRecord.Outfit
        check(uploaded.record.note == "unsent" && uploaded.origin == "device-B")
    }

    private suspend fun unchangedSync() {
        val original = outfit(origin = "device-B")
        remote(original.id, original.toJson())
        sync()
        val repeated = sync()
        check(repeated.downloaded == 0 && repeated.uploaded == 0 && repeated.partnerNew.isEmpty())
    }

    private suspend fun deletedRecord() {
        val original = outfit()
        remote(original.id, original.toJson())
        db.tombstoneDao().insert(Tombstone(original.id))
        sync()
        check(db.outfitDao().getById(original.id) == null)
        check(dav.getString("thecutestgirl/records/T_${original.id}.json") != null)
        sync()
        check(db.outfitDao().getById(original.id) == null)
    }

    private suspend fun anniversaryEditor() {
        // Save through the actual editor ViewModel. Disable background work for this fixture.
        SettingsStore.saveDavConfig(targetContext, SettingsStore.webdavUrl(targetContext), "", "")
        val saved = CountDownLatch(1)
        runOnMainSync {
            EventEditViewModel(targetContext.applicationContext as Application).apply {
                type = EVENT_TYPE_ANNIVERSARY
                title = "Anniversary"
                description = "A note for both of us"
                save { saved.countDown() }
            }
        }
        check(saved.await(10, TimeUnit.SECONDS)) { "Editor save did not complete" }
        val record = db.eventDao().observeAll().first().single()
        check(record.description == "A note for both of us") { "Anniversary note was erased: '${record.description}'" }
    }

    private suspend fun editDuringUpload() = coroutineScope {
        val original = outfit().copy(id = "slow-upload", dirty = true)
        db.outfitDao().upsert(original)
        val running = async { sync() }
        withTimeout(10_000) {
            while (dav.getString("upload-started") == null) delay(20)
        }
        db.outfitDao().upsert(
            original.copy(
                note = "saved during upload",
                noteUpdatedAt = oldTime + 1,
                updatedAt = oldTime + 1
            )
        )
        running.await()
        val latest = db.outfitDao().getById(original.id)!!
        check(latest.note == "saved during upload" && latest.dirty) { "The newer unsent edit was marked clean" }
        sync()
        val uploaded = parseSyncRecord(dav.getString("thecutestgirl/records/${original.id}.json")!!) as SyncRecord.Outfit
        check(uploaded.record.note == "saved during upload")
    }

    private fun noBackgroundWork() {
        SettingsStore.saveDavConfig(targetContext, SettingsStore.webdavUrl(targetContext), "", "")
    }

    private suspend fun awaitMain(condition: () -> Boolean) {
        withTimeout(10_000) {
            while (true) {
                var ready = false
                runOnMainSync { ready = condition() }
                if (ready) break
                delay(20)
            }
        }
    }

    private suspend fun outfitClockSkew() {
        noBackgroundWork()
        val original = outfit(at = System.currentTimeMillis() + 120_000, origin = "device-B")
        db.outfitDao().upsert(original)
        lateinit var vm: OutfitEditViewModel
        runOnMainSync {
            vm = OutfitEditViewModel(targetContext.applicationContext as Application)
            vm.load(original.id)
        }
        awaitMain { vm.photoPath != null }
        val saved = CountDownLatch(1)
        // v2.8.0：记录属于 device-B，所以本机是「对方」，能写的是评论不是备注
        runOnMainSync { vm.reply = "local reply"; vm.save { saved.countDown() } }
        check(saved.await(10, TimeUnit.SECONDS))
        val result = db.outfitDao().getById(original.id)!!
        check(result.updatedAt > original.updatedAt) { "Saved reply has an older version than the record it edited" }
        check(result.origin == "device-B" && result.reply == "local reply")
        check(result.note == "original") { "Partner must not overwrite the uploader's note" }
    }

    private suspend fun mealClockSkew() {
        noBackgroundWork()
        val original = meal(at = System.currentTimeMillis() + 120_000, origin = "device-B")
        db.mealDao().upsert(original)
        lateinit var vm: MealEditViewModel
        runOnMainSync {
            vm = MealEditViewModel(targetContext.applicationContext as Application)
            vm.load(original.id)
        }
        awaitMain { vm.note == "original" }
        val saved = CountDownLatch(1)
        runOnMainSync { vm.reply = "local reply"; vm.save(LocalDate.parse("2026-10-26"), 1) { saved.countDown() } }
        check(saved.await(10, TimeUnit.SECONDS))
        val result = db.mealDao().getById(original.id)!!
        check(result.updatedAt > original.updatedAt) { "Saved reply has an older version than the record it edited" }
        check(result.origin == "device-B" && result.reply == "local reply")
        check(result.note == "original") { "Partner must not overwrite the uploader's note" }
    }

    private suspend fun eventClockSkew() {
        noBackgroundWork()
        val original = event(EVENT_TYPE_ANNIVERSARY, at = System.currentTimeMillis() + 120_000)
            .copy(origin = "device-B")
        db.eventDao().upsert(original)
        lateinit var vm: EventEditViewModel
        runOnMainSync {
            vm = EventEditViewModel(targetContext.applicationContext as Application)
            vm.load(original.id)
        }
        awaitMain { vm.title == "Together" }
        val saved = CountDownLatch(1)
        runOnMainSync { vm.reply = "local reply"; vm.save { saved.countDown() } }
        check(saved.await(10, TimeUnit.SECONDS))
        val result = db.eventDao().getById(original.id)!!
        check(result.updatedAt > original.updatedAt) { "Saved reply has an older version than the record it edited" }
        check(result.origin == "device-B" && result.reply == "local reply")
        check(result.description == "original") { "Partner must not overwrite the uploader's note" }
    }

    /**
     * 云端存在一条日期非法的记录（另一台手机或旧版本写坏的）。
     * 它必须只被跳过，不能每轮同步都抛异常，否则新内容永远传不上去。
     */
    private suspend fun malformedRemoteRecord() {
        val broken = """{"type":"outfit","id":"broken-1","date":"2026-13-45",
            |"photoPath":"missing-test.jpg","note":"bad","createdAt":1,"updatedAt":1,
            |"origin":"device-B","originName":"Bob"}""".trimMargin()
        remote("broken-1", broken)
        val healthy = outfit("healthy")
        db.outfitDao().upsert(healthy.copy(dirty = true))
        val result = sync()
        check(result.skipped == 1) { "Expected one skipped record, got ${result.skipped}" }
        check(dav.getString("thecutestgirl/records/${healthy.id}.json") != null) {
            "A single bad record blocked the healthy upload"
        }
        check(db.outfitDao().getById(healthy.id)!!.dirty.not())
    }

    /** 服务端始终拒收的一条记录不能拖住其它记录：其它记录照常上传，它保持 dirty 等重试。 */
    private suspend fun rejectedUploadIsIsolated() {
        val rejected = outfit("rejected").copy(id = "reject-1", dirty = true)
        val healthy = outfit("healthy").copy(id = "healthy-1", dirty = true)
        db.outfitDao().upsert(rejected)
        db.outfitDao().upsert(healthy)
        val result = sync()
        check(result.ok) { "Sync failed: ${result.error}" }
        check(result.pending == 1) { "Expected one pending upload, got ${result.pending}" }
        check(db.outfitDao().getById("reject-1")!!.dirty) { "Rejected record must stay dirty for retry" }
        check(!db.outfitDao().getById("healthy-1")!!.dirty) { "Healthy record was left behind" }
        check(dav.getString("thecutestgirl/records/healthy-1.json") != null)
    }

    private suspend fun retryDoesNotBlockFreshRequest() {
        val manager = WorkManager.getInstance(targetContext)
        manager.cancelAllWork().result.get(10, TimeUnit.SECONDS)
        val goodUrl = SettingsStore.webdavUrl(targetContext)
        // A real failed sync sits in a long retry backoff, as after an incorrect account/network outage.
        SettingsStore.saveDavConfig(targetContext, goodUrl + "force-failure/", "test", "test")
        val failing = OneTimeWorkRequestBuilder<SyncWorker>()
            .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.HOURS).build()
        manager.enqueueUniqueWork("tcg_sync_now", ExistingWorkPolicy.REPLACE, failing)
            .result.get(10, TimeUnit.SECONDS)
        try {
            withTimeout(10_000) {
                while (true) {
                    val info = manager.getWorkInfoById(failing.id).get(10, TimeUnit.SECONDS)!!
                    if (info.state == WorkInfo.State.ENQUEUED && info.runAttemptCount > 0) break
                    delay(30)
                }
            }
            SettingsStore.saveDavConfig(targetContext, goodUrl, "test", "test")
            val fresh = outfit("fresh user request").copy(dirty = true)
            db.outfitDao().upsert(fresh)
            SyncManager.enqueueNow(targetContext)
            withTimeout(8_000) {
                while (dav.getString("thecutestgirl/records/${fresh.id}.json") == null) delay(50)
            }
            check(!db.outfitDao().getById(fresh.id)!!.dirty)
        } finally {
            manager.cancelAllWork().result.get(10, TimeUnit.SECONDS)
            noBackgroundWork()
        }
    }
    // ---------- v2.8.0：备注 / 评论双区 ----------

    /**
     * 最核心的一条：上传人改备注、对方写评论，两件事**在同一轮同步里**发生，
     * 结果两边都要在。这是把 note / reply 拆成两个字段的全部理由——
     * 只按记录级 updatedAt 裁决的话，后写的那一侧会整条覆盖，另一句就没了。
     */
    private suspend fun noteAndReplyCoexist() {
        val original = outfit(note = "uploader note", at = oldTime)
        db.outfitDao().upsert(original)

        // 对方（device-B）给这条记录写评论，记录级版本更高
        val withReply = original.copy(
            reply = "partner reply",
            replyName = "Bob",
            replyUpdatedAt = oldTime + 5,
            updatedAt = oldTime + 5
        )
        remote(original.id, withReply.toJson())
        sync()

        val merged = db.outfitDao().getById(original.id)!!
        check(merged.note == "uploader note") { "Uploader note was lost: '${merged.note}'" }
        check(merged.reply == "partner reply") { "Partner reply was not received: '${merged.reply}'" }
        check(merged.replyName == "Bob") { "Reply author was lost: '${merged.replyName}'" }
        check(merged.replyUpdatedAt == oldTime + 5)
    }

    /** 对方写评论时如果只写 reply 字段，上传人的 note 必须原样留在云端，不被空值顶掉。 */
    private suspend fun replyDoesNotClobberNote() {
        val original = outfit(note = "keep me", at = oldTime)
        db.outfitDao().upsert(original)
        remote(original.id, original.toJson())
        sync()

        // 对方只写评论
        db.outfitDao().setReply(original.id, "nice photo", "Bob", oldTime + 3)
        sync()

        val uploaded = parseSyncRecord(dav.getString("thecutestgirl/records/${original.id}.json")!!)
            as SyncRecord.Outfit
        check(uploaded.record.note == "keep me") { "Note was wiped by a reply-only edit" }
        check(uploaded.record.reply == "nice photo")
        // 署名必须是写评论的那个人，不能拿上传人昵称顶替
        check(uploaded.record.replyName == "Bob") { "Reply lost its author: '${uploaded.record.replyName}'" }
    }

    /**
     * 备注区只归上传人：对方打开编辑页时备注必须是只读的，
     * 就算内存里的 note 被改动也不该写进库（v2.8.2 曾允许改，已撤回）。
     */
    private suspend fun uploaderNoteIsReadOnly() {
        noBackgroundWork()
        val theirs = outfit(note = "他写的备注", at = oldTime, origin = "device-B")
            .copy(originName = "Bob")
        db.outfitDao().upsert(theirs)

        lateinit var vm: OutfitEditViewModel
        runOnMainSync {
            vm = OutfitEditViewModel(targetContext.applicationContext as Application)
            vm.load(theirs.id)
        }
        awaitMain { vm.photoPath != null }
        runOnMainSync {
            check(!vm.amUploader) { "Record owned by device-B must not look like mine" }
            // 备注被界面锁住时仍可能被程序改，保存也不能写进去
            vm.note = "我想偷偷改他的备注"
            vm.reply = "评论才是我的"
        }
        val saved = CountDownLatch(1)
        runOnMainSync { vm.save { saved.countDown() } }
        check(saved.await(10, TimeUnit.SECONDS))

        val result = db.outfitDao().getById(theirs.id)!!
        check(result.note == "他写的备注") { "Partner edited the uploader's note: '${result.note}'" }
        check(result.reply == "评论才是我的") { "Reply was not saved: '${result.reply}'" }
        check(result.origin == "device-B") { "Ownership changed: '${result.origin}'" }
    }

    /**
     * 备注区标签跟着上传人走：对方看时贴上传人昵称，自己上传时不贴标签。
     * 这条替代了 v2.8.1/v2.8.2 的 noteDevice 署名模型——备注不再有「作者」，只有「上传人」。
     */
    private suspend fun noteLabelFollowsUploader() {
        noBackgroundWork()
        val theirs = outfit(note = "他写的备注", at = oldTime, origin = "device-B")
            .copy(originName = "Bob")
        db.outfitDao().upsert(theirs)
        lateinit var vm: OutfitEditViewModel
        runOnMainSync {
            vm = OutfitEditViewModel(targetContext.applicationContext as Application)
            vm.load(theirs.id)
        }
        awaitMain { vm.photoPath != null }
        runOnMainSync {
            check(vm.noteAuthorName == "Bob") { "Note label should be the uploader: '${vm.noteAuthorName}'" }
        }

        val mine = outfit(note = "我写的备注", at = oldTime, origin = "device-A")
        db.outfitDao().upsert(mine)
        lateinit var own: OutfitEditViewModel
        runOnMainSync {
            own = OutfitEditViewModel(targetContext.applicationContext as Application)
            own.load(mine.id)
        }
        awaitMain { own.photoPath != null }
        runOnMainSync {
            check(own.amUploader) { "Record owned by device-A must look like mine" }
            check(own.noteAuthorName.isBlank()) { "Own note must not be labelled: '${own.noteAuthorName}'" }
        }
    }

    /**
     * 上传正在传的过程中对方写了评论：上传完成后评论不能因为「记录已经传过了」
     * 而被本地那份更旧的副本盖掉（原报告里 editDuringUpload 的同源问题）。
     */
    private suspend fun replySurvivesConcurrentUpload() = coroutineScope {
        val original = outfit(note = "uploading", at = oldTime)
            .copy(id = "slow-reply", dirty = true)
        db.outfitDao().upsert(original)

        val running = async { sync() }
        withTimeout(10_000) {
            while (dav.getString("upload-started") == null) delay(20)
        }
        // 上传还在飞的时候，对方写了评论
        db.outfitDao().setReply(original.id, "written mid-upload", "Bob", oldTime + 7)
        running.await()

        val local = db.outfitDao().getById(original.id)!!
        check(local.reply == "written mid-upload") { "Mid-upload reply was lost: '${local.reply}'" }
        check(local.note == "uploading")

        sync()
        val uploaded = parseSyncRecord(dav.getString("thecutestgirl/records/${original.id}.json")!!)
            as SyncRecord.Outfit
        check(uploaded.record.reply == "written mid-upload") { "Reply never reached the cloud" }
        check(uploaded.record.note == "uploading")
    }

    /**
     * 「指纹还没确认稳定」时必须照常下载。
     *
     * 增量同步靠「服务器时间 + 字节数」判断文件变没变，可这两者都可能恰好没变
     * （同一秒内改两次、或者改完长度又回到原样）。所以加了一道闸：
     * 同一个指纹要连续见到两次才敢跳过，第一次见到时照常下载。
     *
     * 这条用例守的就是这道闸。注意不能用 Result.downloaded 数：
     * 那个值只在「真的合并了新内容」时才加一，重新下载一份内容没变的文件仍然是 0。
     * 要证明「发了请求」，只能看服务器侧的 GET 日志。
     */
    private suspend fun unconfirmedSignatureIsDownloaded() {
        val original = outfit(note = "first", at = oldTime, origin = "device-B")
        remote(original.id, original.toJson())
        // 两轮：第一轮下载，第二轮把这个指纹记成「已确认稳定」
        sync()
        sync()
        check(db.outfitDao().getById(original.id)!!.note == "first")

        // 已确认时不该再发 GET
        val confirmedBase = recordGets()
        sync()
        check(recordGets() == confirmedBase) {
            "Confirmed signature was fetched again (${recordGets() - confirmedBase} extra GETs)"
        }

        // 把「已确认」清空：指纹还在，但「稳定」这个判断没了 → 必须再发一次 GET
        targetContext.getSharedPreferences("sync_state", 0).edit()
            .putString("file_confirmed", "").commit()
        val unconfirmedBase = recordGets()
        sync()
        val extra = recordGets() - unconfirmedBase
        check(extra == 1) { "An unconfirmed signature must still be fetched, got $extra" }

        // 这一轮又把它确认回来了，下一轮重新跳过
        val reConfirmedBase = recordGets()
        sync()
        check(recordGets() == reConfirmedBase) { "Signature was never re-confirmed" }
    }

    // Kotlin 的块注释是可以嵌套的：KDoc 里写 records/*.json 会多开一层注释，
    // 末尾那个 */ 只关掉里面这层，整个 KDoc 变成未闭合。这里改用行注释。
    // 测试服务器把每一次 records 目录下的 json GET 都记在 fixture 根目录的 get-log 里。

    private suspend fun recordGets(): Int =
        (dav.getString("get-log") ?: "").lineSequence().count { it.isNotBlank() }

    /**
     * 没变过的记录不该被反复下载。
     *
     * 这是「对方要好几分钟才看到」的直接原因：原来每轮同步把云端每个 JSON 都 GET 一遍，
     * 几百条记录就是几百次往返，新内容排在这些往返后面。
     */
    private suspend fun incrementalSkipsUntouched() {
        // 造 5 条云端记录，全部先同步下来
        val ids = (1..5).map { "bulk-$it" }
        ids.forEach { id ->
            val r = outfit(note = "note-$id", at = oldTime, origin = "device-B").copy(id = id)
            remote(id, r.toJson())
        }
        val first = sync()
        check(first.downloaded == 5) { "First sync should download all 5, got ${first.downloaded}" }

        // 再来两轮：把每个时间戳确认成稳定，之后就该全部跳过
        sync()
        val third = sync()
        check(third.downloaded == 0) {
            "Unchanged records were re-downloaded ${third.downloaded} times"
        }

        // 只改其中一条：这一轮应当只下载这 1 条
        val target = outfit(note = "changed", at = oldTime + 60_000, origin = "device-B")
            .copy(id = "bulk-3")
        remote("bulk-3", target.toJson())
        val fourth = sync()
        check(fourth.downloaded == 1) {
            "Expected exactly 1 download after a single change, got ${fourth.downloaded}"
        }
        check(db.outfitDao().getById("bulk-3")!!.note == "changed")
        // 改完之后指纹要再确认一次，之后又回到 0
        sync()
        check(sync().downloaded == 0) { "Incremental sync stopped skipping after a change" }
    }

    /**
     * 换 WebDAV 地址 / 账号后，那本「本机记的增量账」必须整本作废，
     * 并且「测试并开启同步」这一轮要顺手把新云盘的三层目录建出来。
     *
     * 守的是两个真实故障：
     * - 旧 dirsReady=true 会让新云盘跳过 MKCOL，接着 listDir 404，整轮同步直接失败；
     * - 旧文件指纹串到新云盘，同名同时间同长度的历史会被误判成「没变」永远同步不到。
     *
     * 新端点只把「账号根目录」建出来（真实云盘的根一定存在），故意不建 thecutestgirl，
     * 用来证明按钮那轮确实把子目录补上了。
     */
    private suspend fun endpointSwitchResetsLedger() {
        // 先用一轮真实同步把「旧云盘」的账本做实，再补一个已通知 id
        val old = outfit(note = "old-cloud", at = oldTime, origin = "device-B")
        remote(old.id, old.toJson())
        sync()
        sync() // 第二次把指纹确认成稳定
        SyncStateStore.rememberNotified(targetContext, listOf("outfit-1@1"))

        check(SyncStateStore.dirsReady(targetContext)) { "fixture: dirsReady should be true after a sync" }
        check(SyncStateStore.fileSignatures(targetContext).isNotEmpty()) { "fixture: signatures should exist" }
        check(SyncStateStore.confirmedFiles(targetContext).isNotEmpty()) { "fixture: confirmed files should exist" }
        check(SyncStateStore.notifiedIds(targetContext).isNotEmpty()) { "fixture: notified ids should exist" }
        check(SettingsStore.firstSyncDone(targetContext)) { "fixture: first sync should be done" }

        // 换到一个全新的空端点：只建账号根目录，不建 thecutestgirl
        val newUrl = "http://10.0.2.2:18765/${UUID.randomUUID()}/"
        val newDav = WebDavClient(newUrl, "test", "test")
        check(newDav.mkcol("")) { "Could not create the new account root" }

        val changed = SyncEngine.applyDavConfig(targetContext, newUrl, "test", "test")
        check(changed) { "Switching to a new endpoint must be detected" }

        // 账本整本作废
        check(!SyncStateStore.dirsReady(targetContext)) { "dirsReady was not reset" }
        check(SyncStateStore.fileSignatures(targetContext).isEmpty()) { "file signatures were not reset" }
        check(SyncStateStore.confirmedFiles(targetContext).isEmpty()) { "confirmed files were not reset" }
        check(SyncStateStore.notifiedIds(targetContext).isEmpty()) { "notified ids were not reset" }
        check(!SettingsStore.firstSyncDone(targetContext)) { "first_sync_done was not reset" }

        // 按钮那轮：测试连接 + 顺手把新云盘三层目录建出来
        val ok = SyncEngine(targetContext).testConnection(prepareDirectories = changed)
        check(ok) { "Test-and-enable on the new endpoint should succeed" }
        check(newDav.listDir("thecutestgirl/records") != null) { "records folder was not created" }
        check(newDav.listDir("thecutestgirl/photos") != null) { "photos folder was not created" }
        check(SyncStateStore.dirsReady(targetContext)) { "dirsReady should be set after preparing folders" }

        // 紧接着的整轮同步不能再报 404（原来「显示成功却一直同步失败」的坑）
        dav = newDav
        val fresh = outfit(note = "new-cloud", at = oldTime + 60_000, origin = "device-B")
            .copy(id = "fresh-1")
        remote(fresh.id, fresh.toJson())
        val result = sync()
        check(result.ok) { "Sync after switching endpoint failed: ${result.error}" }
        check(db.outfitDao().getById(fresh.id)?.note == "new-cloud") {
            "Record from the new cloud was not merged"
        }
    }

    /**
     * 没真换端点时不能白清账本：设置页点一次「测试并开启同步」不该让下一轮退成全量 GET。
     * 顺带验证地址/账号前后带空格、以及空地址回落默认值时的归一化与 saveDavConfig 一致。
     */
    private suspend fun sameEndpointKeepsLedger() {
        val record = outfit(note = "stable", at = oldTime, origin = "device-B")
        remote(record.id, record.toJson())
        sync()
        sync()
        SyncStateStore.rememberNotified(targetContext, listOf("outfit-1@1"))
        check(SyncStateStore.fileSignatures(targetContext).isNotEmpty())
        check(SyncStateStore.confirmedFiles(targetContext).isNotEmpty())

        val currentUrl = SettingsStore.webdavUrl(targetContext)
        val changed = SyncEngine.applyDavConfig(
            targetContext, currentUrl,
            SettingsStore.webdavUser(targetContext), SettingsStore.webdavPass(targetContext)
        )
        check(!changed) { "Re-saving the identical config must not look like an endpoint switch" }
        check(SyncStateStore.dirsReady(targetContext)) { "dirsReady was wiped without an endpoint change" }
        check(SyncStateStore.fileSignatures(targetContext).isNotEmpty()) { "file signatures were wiped" }
        check(SyncStateStore.confirmedFiles(targetContext).isNotEmpty()) { "confirmed files were wiped" }
        check(SyncStateStore.notifiedIds(targetContext).isNotEmpty()) { "notified ids were wiped" }
        check(SettingsStore.firstSyncDone(targetContext)) { "first_sync_done was wiped" }

        val whitespace = SyncEngine.applyDavConfig(
            targetContext, "  $currentUrl  ", "  test  ", "test"
        )
        check(!whitespace) { "Whitespace-only changes must normalise to the same endpoint" }
        check(SyncStateStore.fileSignatures(targetContext).isNotEmpty()) {
            "Whitespace-only save wiped the ledger"
        }
    }

}
