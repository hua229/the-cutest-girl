package com.thecutestgirl.app.ui.meal

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.thecutestgirl.app.data.AppDatabase
import com.thecutestgirl.app.data.MealRecord
import com.thecutestgirl.app.data.PhotoStore
import com.thecutestgirl.app.data.SettingsStore
import com.thecutestgirl.app.data.Tombstone
import com.thecutestgirl.app.sync.SyncManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.thecutestgirl.app.ui.savedNullableString
import com.thecutestgirl.app.ui.savedString
import java.time.LocalDate
import java.util.UUID

/** 饮食页：按天查看三餐 */
class MealViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.getInstance(app).mealDao()

    private val _date = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val meals = _date
        .flatMapLatest { day -> dao.observeByDate(day.toString()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setDate(date: LocalDate) {
        _date.value = date
    }

    fun previousDay() {
        _date.value = _date.value.minusDays(1)
    }

    fun nextDay() {
        _date.value = _date.value.plusDays(1)
    }
}

/** 记录某一餐 */
class MealEditViewModel(app: Application, state: SavedStateHandle) : AndroidViewModel(app) {
    /** 兼容设备回归里 `MealEditViewModel(app)` 的直接构造 */
    constructor(app: Application) : this(app, SavedStateHandle())

    private val database = AppDatabase.getInstance(app)
    private val dao = database.mealDao()

    var note by savedString(state, "note")
    var photoPath by savedNullableString(state, "photoPath")
    /** 对方写的评论：上传人只读 */
    var reply by savedString(state, "reply")

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

    /** 上传人：照片和文字至少有一样；对方：改了评论才能存 */
    val canSave: Boolean
        get() = if (amUploader) photoPath != null || note.isNotBlank() else reply != originalReply

    /** 只有上传人能删除；对方只能写评论 */
    val canDelete: Boolean
        get() = amUploader

    fun load(id: String) {
        if (editingId == id) return
        viewModelScope.launch {
            myDeviceId = SettingsStore.deviceId(getApplication())
            dao.getById(id)?.let { record ->
                editingId = id
                editingCreatedAt = record.createdAt
                editingOrigin = record.origin
                editingOriginName = record.originName
                originalPhoto = record.photoPath
                note = record.note
                photoPath = record.photoPath
                reply = record.reply
                originalReply = record.reply
                originalReplyAt = record.replyUpdatedAt
                originalReplyName = record.replyName
                originalNote = record.note
            }
        }
    }

    /**
     * 保存：上传人写照片 / 备注（评论原样保留），对方只写评论。
     * 字段级分工见 [OutfitEditViewModel.save] 的说明。
     */
    fun save(date: LocalDate, mealType: Int, onDone: () -> Unit) {
        if (!canSave) return
        viewModelScope.launch {
            val latest = editingId?.let { dao.getById(it) }
            // 手机时钟可能比对方慢，但一次编辑的版本必须高于它所修改的记录。
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
            val baseNote = latest?.note ?: originalNote
            val baseReply = latest?.reply ?: originalReply
            val noteChanged = note.trim() != baseNote
            val replyChanged = reply.trim() != baseReply
            val finalPhoto = withContext(Dispatchers.IO) {
                PhotoStore.promoteDraft(getApplication(), photoPath)
            }
            val record = MealRecord(
                id = editingId ?: UUID.randomUUID().toString(),
                date = date.toString(),
                mealType = mealType,
                photoPath = finalPhoto,
                note = note.trim(),
                noteDevice = if (noteChanged) myDevice else (latest?.noteDevice ?: ""),
                noteName = if (noteChanged) myName else (latest?.noteName ?: ""),
                reply = reply.trim(),
                replyName = if (replyChanged) myName else (latest?.replyName ?: originalReplyName),
                noteUpdatedAt = if (noteChanged) now else (latest?.noteUpdatedAt ?: 0L),
                replyUpdatedAt = if (replyChanged) now else (latest?.replyUpdatedAt ?: originalReplyAt),
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
                if (originalPhoto != null && originalPhoto != photoPath) {
                    PhotoStore.delete(getApplication(), originalPhoto)
                }
            }
            SyncManager.enqueueNow(getApplication())
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        // 第二道门：对方不能删上传人的记录
        if (!canDelete) {
            onDone()
            return
        }
        val id = editingId
        if (id == null) {
            onDone()
            return
        }
        viewModelScope.launch {
            dao.getById(id)?.let { record ->
                database.deleteMealWithTombstone(record)
                runCatching { PhotoStore.delete(getApplication(), record.photoPath) }
            }
            SyncManager.enqueueNow(getApplication())
            onDone()
        }
    }
}
