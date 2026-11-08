package com.thecutestgirl.app.ui.outfit

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.thecutestgirl.app.data.AppDatabase
import com.thecutestgirl.app.data.OutfitRecord
import com.thecutestgirl.app.data.PhotoStore
import com.thecutestgirl.app.data.SettingsStore
import com.thecutestgirl.app.data.Tombstone
import com.thecutestgirl.app.data.parseRecordDate
import com.thecutestgirl.app.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.thecutestgirl.app.ui.savedDate
import com.thecutestgirl.app.ui.savedNullableString
import com.thecutestgirl.app.ui.savedString
import com.thecutestgirl.app.ui.savedStringList
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** 某一天的穿搭聚合：日期 + 这一天的全部照片记录（新的在前） */
data class OutfitDayGroup(
    val date: LocalDate,
    val records: List<OutfitRecord>
)

/**
 * 穿搭列表页：按月确定浏览范围，列表内**按天聚合**展示；
 * 另外维护一个「当前选中日期」，作为「记穿搭」上传入口的目标日期。
 */
class OutfitViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.getInstance(app).outfitDao()

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    /** 当前选中日期：上传入口把它带到上传页，照片统一归到这一天 */
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val outfits = _month
        .flatMapLatest { month -> dao.observeByMonth(month.toString()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 当月记录按日期分组：日期新的在前，同一天内按创建时间新的在前 */
    val dayGroups: StateFlow<List<OutfitDayGroup>> = outfits
        .map { list ->
            list.groupBy { it.date }
                // 日期非法的记录（云端脏数据）跳过分组，不能让它把整页崩在 LocalDate.parse 上
                .mapNotNull { (date, records) ->
                    parseRecordDate(date)?.let { parsed ->
                        OutfitDayGroup(
                            date = parsed,
                            records = records.sortedByDescending { it.createdAt }
                        )
                    }
                }
                .sortedByDescending { it.date }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 哪些日期已经有照片（日期条上的小圆点用它） */
    val daysWithPhotos: StateFlow<Set<LocalDate>> = dayGroups
        .map { groups -> groups.map { it.date }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun previousMonth() = applyMonth(_month.value.minusMonths(1))

    fun nextMonth() = applyMonth(_month.value.plusMonths(1))

    /** 月份弹窗选完直接跳到指定年月，省掉连点箭头 */
    fun setMonth(year: Int, month: Int) = applyMonth(YearMonth.of(year, month))

    /** 「回到本月」 */
    fun toCurrentMonth() = applyMonth(YearMonth.now())

    /** 日期条 / 分组头点选某一天 */
    fun setDate(date: LocalDate) {
        _selectedDate.value = date
    }

    /**
     * 从通知点进来时跳到某一天：**月份和选中日期一起改**。
     *
     * 只调 [setDate] 是不够的：列表按月订阅（`observeByMonth`），月份没变的话，
     * 十月的记录根本不在查询结果里，页面停在当月、选中日期也画不出来，
     * 用户点完通知看到的还是「今天」。这条路径必须两个状态一起落。
     */
    fun jumpToDate(date: LocalDate) {
        _month.value = YearMonth.from(date)
        _selectedDate.value = date
    }

    private fun applyMonth(target: YearMonth) {
        _month.value = target
        // 选中日期要落回这个月：今天在当月就用今天，否则用该月 1 号，避免上传页拿到月份外的日期
        if (YearMonth.from(_selectedDate.value) != target) {
            _selectedDate.value = if (YearMonth.now() == target) LocalDate.now() else target.atDay(1)
        }
    }
}

/** 添加 / 编辑单条穿搭（点列表里某张照片进来） */
class OutfitEditViewModel(app: Application, state: SavedStateHandle) : AndroidViewModel(app) {
    /** 兼容设备回归里 `OutfitEditViewModel(app)` 的直接构造 */
    constructor(app: Application) : this(app, SavedStateHandle())

    private val database = AppDatabase.getInstance(app)
    private val dao = database.outfitDao()

    var photoPath by savedNullableString(state, "photoPath")
    var date by savedDate(state, "date")
    var note by savedString(state, "note")
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

    /**
     * 我是不是这条记录的上传人（新建时当然是我）。
     * 备注区只有上传人能写、评论区只有对方能写，界面与保存逻辑都按这个开关走。
     */
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

    /** 上传人：照片必填；对方：改了评论才能存 */
    val canSave: Boolean
        get() = if (amUploader) photoPath != null else reply != originalReply

    /** 只有上传人能删除；对方只能写评论（UI 和 ViewModel 各一道门） */
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
                photoPath = record.photoPath
                date = parseRecordDate(record.date) ?: LocalDate.now()
                note = record.note
                reply = record.reply
                originalReply = record.reply
                originalReplyAt = record.replyUpdatedAt
                originalReplyName = record.replyName
                originalNote = record.note
            }
        }
    }

    /**
     * 保存。两个人共用同一个入口，但写的东西完全不同：
     * - 上传人：写照片 / 日期 / 备注，**评论原样保留**（用库里的最新值，不用内存里可能过期的副本）
     * - 对方：只调 `setReply` 写评论，一个字都不碰上传人的内容
     *
     * 这样两台手机各写各的字段，同步时按字段时间戳合并，谁也不会把对方那句顶掉。
     */
    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val latest = editingId?.let { dao.getById(it) }
            val now = maxOf(System.currentTimeMillis(), (latest?.updatedAt ?: 0L) + 1)
            val myDevice = SettingsStore.deviceId(getApplication())
            val myName = SettingsStore.myName(getApplication())

            // 我不是上传人 → 只写评论，一个字都不碰上传人的照片和备注
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

            val path = withContext(Dispatchers.IO) {
                PhotoStore.promoteDraft(getApplication(), photoPath)
            } ?: return@launch
            val owner = (latest?.origin ?: editingOrigin)
                .takeIf { it.isNotBlank() && it != "local" } ?: myDevice
            val baseNote = latest?.note ?: originalNote
            val baseReply = latest?.reply ?: originalReply
            val noteChanged = note.trim() != baseNote
            val replyChanged = reply.trim() != baseReply
            val record = OutfitRecord(
                id = editingId ?: UUID.randomUUID().toString(),
                date = date.toString(),
                photoPath = path,
                note = note.trim(),
                // 备注的作者要跟着「谁写的」走，不能拿 origin（上传人）顶替
                noteDevice = if (noteChanged) myDevice else (latest?.noteDevice ?: ""),
                noteName = if (noteChanged) myName else (latest?.noteName ?: ""),
                reply = reply.trim(),
                replyName = if (replyChanged) myName else (latest?.replyName ?: originalReplyName),
                // 没改过的字段保持原时间戳，否则等于用旧内容抢新版本
                noteUpdatedAt = if (noteChanged) now else (latest?.noteUpdatedAt ?: 0L),
                replyUpdatedAt = if (replyChanged) now else (latest?.replyUpdatedAt ?: originalReplyAt),
                createdAt = if (editingCreatedAt > 0) editingCreatedAt else now,
                updatedAt = now,
                // 编辑别人的记录只改内容，不改归属（💗昵称保留）
                origin = owner,
                originName = if (owner == myDevice) myName else latest?.originName ?: editingOriginName,
                dirty = true
            )
            if (editingId == null) {
                dao.insert(record)
            } else {
                dao.update(record)
                // 编辑时换了照片，删掉旧的照片文件
                if (originalPhoto != null && originalPhoto != path) {
                    PhotoStore.delete(getApplication(), originalPhoto)
                }
            }
            SyncManager.enqueueNow(getApplication())
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        // 第二道门：对方不能删上传人的记录，不写墓碑
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
                // 删除 + 墓碑同一事务，避免「记录没了、墓碑没写」-> 云端重新下载复活
                database.deleteOutfitWithTombstone(record)
                // 照片不参与事务；删失败不影响记录删除
                runCatching { PhotoStore.delete(getApplication(), record.photoPath) }
            }
            SyncManager.enqueueNow(getApplication())
            onDone()
        }
    }
}

/**
 * 指定某一天的穿搭上传页（v2.4.4）：
 * - [existing] 实时观察这一天的已有记录，展示并支持删除（删除立即生效并同步墓碑）
 * - [newPhotos] 本次新选的照片，点保存时为每张照片各建一条记录，统一归到 [date] 下
 */
class OutfitUploadViewModel(app: Application, private val state: SavedStateHandle) : AndroidViewModel(app) {
    /** 兼容设备回归里 `OutfitUploadViewModel(app)` 的直接构造 */
    constructor(app: Application) : this(app, SavedStateHandle())

    private val database = AppDatabase.getInstance(app)
    private val dao = database.outfitDao()

    private val _date = MutableStateFlow(
        parseRecordDate(state.get<String>("date") ?: "") ?: LocalDate.now()
    )
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    /** 本次待保存的新照片文件名（草稿照片，保存时晋级为正式文件） */
    var newPhotos by savedStringList(state, "newPhotos")
    var note by savedString(state, "note")

    @OptIn(ExperimentalCoroutinesApi::class)
    val existing = _date
        .flatMapLatest { day -> dao.observeByDate(day.toString()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val canSave: Boolean get() = newPhotos.isNotEmpty()

    private var dateInitialized = false

    /**
     * 只在首次进入页面时用路由带来的日期初始化。
     * 之后用户在页面内改过日期，即使发生旋转重建也不能把它打回初始值。
     */
    fun ensureDate(dateArg: String) {
        if (dateInitialized) return
        dateInitialized = true
        runCatching { LocalDate.parse(dateArg) }
            .getOrNull()
            ?.let { _date.value = it }
    }

    fun setDate(date: LocalDate) {
        _date.value = date
        state["date"] = date.toString()
    }

    fun addPhotos(names: List<String>) {
        newPhotos = (newPhotos + names)
    }

    /** 还没保存的新照片，从列表里去掉并删掉已复制进私有目录的文件 */
    fun removeNew(name: String) {
        newPhotos = newPhotos - name
        PhotoStore.delete(getApplication(), name)
    }

    /** 删除这一天已有的某条记录：删库 + 墓碑 + 删文件 + 触发同步 */
    fun deleteExisting(record: OutfitRecord) {
        // 只能删自己上传的；对方上传的照片只读（origin "local"/空视为本机旧数据）
        val myDevice = SettingsStore.deviceId(getApplication())
        if (record.origin.isNotBlank() && record.origin != "local" && record.origin != myDevice) return
        viewModelScope.launch {
            database.deleteOutfitWithTombstone(record)
            runCatching { PhotoStore.delete(getApplication(), record.photoPath) }
            SyncManager.enqueueNow(getApplication())
        }
    }

    /** 保存：每张新照片一条记录，共享同一日期与备注 */
    fun save(onDone: () -> Unit) {
        val photos = newPhotos
        if (photos.isEmpty()) {
            onDone()
            return
        }
        viewModelScope.launch {
            val day = _date.value
            val base = System.currentTimeMillis()
            val myDevice = SettingsStore.deviceId(getApplication())
            val myName = SettingsStore.myName(getApplication())
            // 草稿照片晋级为正式文件，再写库；进程被杀留下的 draft_ 由启动清理兜底
            val finalPhotos = withContext(Dispatchers.IO) {
                photos.mapNotNull { PhotoStore.promoteDraft(getApplication(), it) }
            }
            finalPhotos.forEachIndexed { index, path ->
                dao.insert(
                    OutfitRecord(
                        id = UUID.randomUUID().toString(),
                        date = day.toString(),
                        photoPath = path,
                        note = note.trim(),
                        // 新建时备注就是这一刻写的；评论还没人写，留 0
                        noteUpdatedAt = base + index,
                        createdAt = base + index,
                        updatedAt = base + index,
                        origin = myDevice,
                        originName = myName,
                        dirty = true
                    )
                )
            }
            newPhotos = emptyList()
            note = ""
            SyncManager.enqueueNow(getApplication())
            onDone()
        }
    }
}
