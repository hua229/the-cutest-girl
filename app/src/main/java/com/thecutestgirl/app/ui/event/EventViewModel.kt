package com.thecutestgirl.app.ui.event

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thecutestgirl.app.data.AppDatabase
import com.thecutestgirl.app.data.EVENT_TYPE_MOMENT
import com.thecutestgirl.app.data.EventRecord
import com.thecutestgirl.app.data.PhotoStore
import com.thecutestgirl.app.data.SettingsStore
import com.thecutestgirl.app.data.Tombstone
import com.thecutestgirl.app.data.parseRecordDate
import com.thecutestgirl.app.sync.SyncManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/** 事件页数据 */
class EventViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.getInstance(app).eventDao()

    val events = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(record: EventRecord) {
        viewModelScope.launch {
            dao.delete(record)
            AppDatabase.getInstance(getApplication()).tombstoneDao()
                .insert(Tombstone(record.id, System.currentTimeMillis()))
            PhotoStore.delete(getApplication(), record.photoPath)
            SyncManager.enqueueNow(getApplication())
        }
    }
}

/** 添加 / 编辑事件 */
class EventEditViewModel(app: Application) : AndroidViewModel(app) {
    private val database = AppDatabase.getInstance(app)
    private val dao = database.eventDao()

    var type by mutableStateOf(EVENT_TYPE_MOMENT)
    var title by mutableStateOf("")
    var description by mutableStateOf("")
    var photoPath by mutableStateOf<String?>(null)
    var date by mutableStateOf(LocalDate.now())
    /** 对方写的评论：上传人只读 */
    var reply by mutableStateOf("")

    private var editingId: String? = null
    private var editingCreatedAt: Long = 0
    private var editingOrigin: String = ""
    private var editingOriginName: String = ""
    private var originalPhoto: String? = null
    private var originalReply: String = ""
    private var originalReplyAt: Long = 0L
    private var originalReplyName: String = ""
    private var originalNote: String = ""
    private var myDeviceId: String = ""

    /** 我是不是这条记录的上传人（新建时当然是我） */
    val amUploader: Boolean
        get() = editingId == null ||
            editingOrigin.isBlank() ||
            editingOrigin == "local" ||
            editingOrigin == myDeviceId

    /**
     * 备注区永远是上传人写的：我不是上传人时贴对方昵称，我上传时不贴标签。
     *
     * v2.8.2 曾按 noteDevice「谁写的谁能改」，结果把「对方上传的照片 + 我写的备注」变成我也能改，
     * 等于让评论者改上传人的字，已经撤回。现在的规则只有一条：**备注归上传人，评论归对方**。
     */
    val noteAuthorName: String
        get() = if (amUploader) "" else editingOriginName.ifBlank { "ta" }

    /** 评论是谁写的：空 = 还没有评论 */
    val replyAuthorName: String
        get() = originalReplyName

    /** 上传人：标题必填；对方：改了评论才能存 */
    val canSave: Boolean
        get() = if (amUploader) title.isNotBlank() else reply != originalReply

    fun load(id: String) {
        if (editingId == id) return
        viewModelScope.launch {
            myDeviceId = SettingsStore.deviceId(getApplication())
            dao.getById(id)?.let { record ->
                editingId = id
                editingCreatedAt = record.createdAt
                editingOrigin = record.origin
                editingOriginName = record.originName
                type = record.type
                title = record.title
                description = record.description
                photoPath = record.photoPath
                date = parseRecordDate(record.date) ?: LocalDate.now()
                originalPhoto = record.photoPath
                reply = record.reply
                originalReply = record.reply
                originalReplyAt = record.replyUpdatedAt
                originalReplyName = record.replyName
                originalNote = record.description
            }
        }
    }

    /**
     * 保存：上传人写标题 / 描述（评论原样保留），对方只写评论。
     * 字段级分工见 [OutfitEditViewModel.save] 的说明。
     */
    fun save(onDone: () -> Unit) {
        if (!canSave) return
        viewModelScope.launch {
            val latest = editingId?.let { dao.getById(it) }
            val now = maxOf(System.currentTimeMillis(), (latest?.updatedAt ?: 0L) + 1)
            val myDevice = SettingsStore.deviceId(getApplication())
            val myName = SettingsStore.myName(getApplication())

            if (!amUploader && latest != null) {
                if (reply == latest.reply) {
                    onDone()
                    return@launch
                }
                dao.setReply(latest.id, reply.trim(), myName, now)
                SyncManager.enqueueNow(getApplication())
                onDone()
                return@launch
            }

            val owner = (latest?.origin ?: editingOrigin).takeIf { it.isNotBlank() && it != "local" } ?: myDevice
            // 两种事件都保留备注；只有日常点滴配照片。
            val isMoment = type == EVENT_TYPE_MOMENT
            val baseNote = latest?.description ?: originalNote
            val baseReply = latest?.reply ?: originalReply
            val noteChanged = description.trim() != baseNote
            val replyChanged = reply.trim() != baseReply
            val record = EventRecord(
                id = editingId ?: UUID.randomUUID().toString(),
                type = type,
                title = title.trim(),
                description = description.trim(),
                noteDevice = if (noteChanged) myDevice else (latest?.noteDevice ?: ""),
                noteName = if (noteChanged) myName else (latest?.noteName ?: ""),
                reply = reply.trim(),
                replyName = if (replyChanged) myName else (latest?.replyName ?: originalReplyName),
                noteUpdatedAt = if (noteChanged) now else (latest?.noteUpdatedAt ?: 0L),
                replyUpdatedAt = if (replyChanged) now else (latest?.replyUpdatedAt ?: originalReplyAt),
                photoPath = if (isMoment) photoPath else null,
                date = date.toString(),
                createdAt = if (editingCreatedAt > 0) editingCreatedAt else now,
                updatedAt = now,
                origin = owner,
                originName = if (owner == myDevice) myName else latest?.originName ?: editingOriginName,
                dirty = true
            )
            if (editingId == null) {
                dao.insert(record)
            } else {
                dao.update(record)
                if (originalPhoto != null && originalPhoto != record.photoPath) {
                    PhotoStore.delete(getApplication(), originalPhoto)
                }
            }
            SyncManager.enqueueNow(getApplication())
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = editingId
        if (id == null) {
            onDone()
            return
        }
        viewModelScope.launch {
            dao.getById(id)?.let { record ->
                dao.delete(record)
                database.tombstoneDao().insert(Tombstone(id, System.currentTimeMillis()))
                PhotoStore.delete(getApplication(), record.photoPath)
            }
            SyncManager.enqueueNow(getApplication())
            onDone()
        }
    }
}
