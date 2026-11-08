package com.thecutestgirl.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONObject
import java.time.LocalDate

/**
 * 所有记录的主键是全局唯一的 UUID 字符串——这样两台手机各自创建的记录
 * 合并到云端同一个空间时永远不会撞车。
 *
 * 同步相关字段：
 * - updatedAt 最后修改时间（云端冲突按它裁决，新的赢）
 * - origin    创建者的设备 ID（用于区分是自己的还是对方的记录）
 * - originName 创建者昵称（通知和卡片上显示）
 * - dirty     本地改过还没上传到云端
 */
@Entity(
    tableName = "outfits",
    indices = [Index("date"), Index("dirty")]
)
data class OutfitRecord(
    @PrimaryKey val id: String,
    /** 记录日期，格式 2026-10-24 */
    val date: String,
    /** 照片文件名（APP 私有 photos 目录 + 云端 photos 目录里同名） */
    val photoPath: String,
    val note: String = "",
    /**
     * 备注作者：noteDevice 判权限，noteName 显示标签。
     *
     * v2.7 及以前备注是两个人共写的，历史数据里「对方上传的照片 + 我写的备注」
     * 很常见。所以备注的归属不能拿 origin（上传人）猜，必须自己记一份。
     * 两个都空 = 老数据，谁都能改、也不贴「ta 的备注」标签（当年就是这样）。
     */
    val noteDevice: String = "",
    val noteName: String = "",
    /** 对方在这条记录下的评论：只有对方能写，上传人只读 */
    val reply: String = "",
    /** 评论作者昵称：两边看到同一条评论时都该显示「谁说的」 */
    val replyName: String = "",
    /**
     * note / reply 各自的最后修改时间（字段级合并用）。
     *
     * 为什么不能只靠记录级 updatedAt：上传人改备注和对方写评论是两个人几乎同时能做
     * 的两件事。只按记录版本裁决的话，谁的 updatedAt 大谁整个覆盖，另一方那一句就没了。
     * 两个字段各自有唯一写者，所以按字段比时间可以做到无损合并。
     * 0 表示老数据（迁移前），回退到记录 updatedAt。
     */
    val noteUpdatedAt: Long = 0L,
    val replyUpdatedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val origin: String = "",
    val originName: String = "",
    val dirty: Boolean = true
) {
    fun toJson(): String = JSONObject()
        .put("type", "outfit")
        .put("schema", 1)
        .put("id", id)
        .put("date", date)
        .put("photoPath", photoPath)
        .put("note", note)
        .put("noteDevice", noteDevice)
        .put("noteName", noteName)
        .put("reply", reply)
        .put("replyName", replyName)
        .put("noteUpdatedAt", noteUpdatedAt)
        .put("replyUpdatedAt", replyUpdatedAt)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
        .put("origin", origin)
        .put("originName", originName)
        .toString()
}

/** 餐次：0 早餐 1 午餐 2 晚餐 */
val MEAL_NAMES = listOf("早餐", "午餐", "晚餐")

@Entity(
    tableName = "meals",
    indices = [Index("date"), Index("dirty")]
)
data class MealRecord(
    @PrimaryKey val id: String,
    val date: String,
    /** 0 早餐 1 午餐 2 晚餐 */
    val mealType: Int,
    val photoPath: String? = null,
    val note: String = "",
    /** 备注作者（见 OutfitRecord 里的说明） */
    val noteDevice: String = "",
    val noteName: String = "",
    /** 对方在这条记录下的评论：只有对方能写，上传人只读 */
    val reply: String = "",
    /** 评论作者昵称 */
    val replyName: String = "",
    /** note / reply 各自的最后修改时间（字段级合并用，0 = 老数据回退到 updatedAt） */
    val noteUpdatedAt: Long = 0L,
    val replyUpdatedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val origin: String = "",
    val originName: String = "",
    val dirty: Boolean = true
) {
    fun toJson(): String = JSONObject()
        .put("type", "meal")
        .put("schema", 1)
        .put("id", id)
        .put("date", date)
        .put("mealType", mealType)
        .put("photoPath", photoPath ?: JSONObject.NULL)
        .put("note", note)
        .put("noteDevice", noteDevice)
        .put("noteName", noteName)
        .put("reply", reply)
        .put("replyName", replyName)
        .put("noteUpdatedAt", noteUpdatedAt)
        .put("replyUpdatedAt", replyUpdatedAt)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
        .put("origin", origin)
        .put("originName", originName)
        .toString()
}

/** 事件类型：0 日常点滴 1 纪念日 */
const val EVENT_TYPE_MOMENT = 0
const val EVENT_TYPE_ANNIVERSARY = 1

@Entity(
    tableName = "events",
    indices = [Index("date"), Index("dirty")]
)
data class EventRecord(
    @PrimaryKey val id: String,
    val type: Int,
    val title: String,
    val description: String = "",
    /** 备注作者（见 OutfitRecord 里的说明） */
    val noteDevice: String = "",
    val noteName: String = "",
    /** 对方在这条记录下的评论：只有对方能写，上传人只读 */
    val reply: String = "",
    /** 评论作者昵称 */
    val replyName: String = "",
    /** description / reply 各自的最后修改时间（字段级合并用，0 = 老数据回退到 updatedAt） */
    val noteUpdatedAt: Long = 0L,
    val replyUpdatedAt: Long = 0L,
    val photoPath: String? = null,
    /** 日常点滴=发生日期；纪念日=起始日期 */
    val date: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val origin: String = "",
    val originName: String = "",
    val dirty: Boolean = true
) {
    fun toJson(): String = JSONObject()
        .put("type", "event")
        .put("schema", 1)
        .put("id", id)
        .put("eventType", type)
        .put("title", title)
        .put("description", description)
        .put("noteDevice", noteDevice)
        .put("noteName", noteName)
        .put("reply", reply)
        .put("replyName", replyName)
        .put("noteUpdatedAt", noteUpdatedAt)
        .put("replyUpdatedAt", replyUpdatedAt)
        .put("photoPath", photoPath ?: JSONObject.NULL)
        .put("date", date)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
        .put("origin", origin)
        .put("originName", originName)
        .toString()
}

/**
 * 删除墓碑：本地删除的记录先记在这里，同步时上传成 T_<id>.json，
 * 对方看到墓碑就把对应记录（和照片）删掉，删除不会「复活」。
 */
@Entity(tableName = "tombstones")
data class Tombstone(
    @PrimaryKey val id: String,
    val updatedAt: Long = System.currentTimeMillis()
)

/** 从云端 JSON 解析出来的记录（dirty 一律为 false，来自云端即已同步） */
sealed class SyncRecord {
    abstract val id: String
    abstract val updatedAt: Long
    abstract val origin: String
    abstract val originName: String
    abstract val typeLabel: String

    /** 点通知要跳到的 tab 路由（和 AppRoot 里的 tab route 对齐） */
    abstract val route: String

    /** 这条记录属于哪一天，通知点进去直接落在这一天 */
    abstract val date: String

    data class Outfit(val record: OutfitRecord) : SyncRecord() {
        override val id get() = record.id
        override val updatedAt get() = record.updatedAt
        override val origin get() = record.origin
        override val originName get() = record.originName
        override val typeLabel get() = "穿搭"
        override val route get() = "outfit"
        override val date get() = record.date
    }

    data class Meal(val record: MealRecord) : SyncRecord() {
        override val id get() = record.id
        override val updatedAt get() = record.updatedAt
        override val origin get() = record.origin
        override val originName get() = record.originName
        override val typeLabel get() = "三餐"
        override val route get() = "meal"
        override val date get() = record.date
    }

    data class Event(val record: EventRecord) : SyncRecord() {
        override val id get() = record.id
        override val updatedAt get() = record.updatedAt
        override val origin get() = record.origin
        override val originName get() = record.originName
        override val typeLabel get() = "点滴"
        override val route get() = "event"
        override val date get() = record.date
    }
}

/**
 * 记录里的日期一律是 ISO 格式（`LocalDate.toString()`，形如 2026-10-26）。
 *
 * 云端 JSON 来自另一台手机或旧版本，不能假定它一定合法：一旦非法日期进了数据库，
 * 列表页分组、编辑页回填都会在 `LocalDate.parse` 上抛异常，而且该记录每轮同步都会
 * 被重新写入，等于永久崩溃。所以所有解析入口都走这个兜底。
 */
fun parseRecordDate(text: String): LocalDate? =
    if (text.isBlank()) null else runCatching { LocalDate.parse(text) }.getOrNull()

/** 取 JSON 里的 date 字段，非法/缺失返回 null（调用方据此判定整条记录不可用） */
private fun JSONObject.validDate(): String? =
    optString("date").takeIf { parseRecordDate(it) != null }

fun parseSyncRecord(text: String): SyncRecord? = runCatching {
    val o = JSONObject(text)
    when (o.optString("type")) {
        "outfit" -> {
            val date = o.validDate() ?: return@runCatching null
            // P2-5 校验：id 和照片必须有；坏记录只跳过这一条
            val id = o.optString("id")
            val photo = if (o.isNull("photoPath")) "" else o.optString("photoPath")
            if (id.isBlank() || photo.isBlank()) return@runCatching null
            SyncRecord.Outfit(
                OutfitRecord(
                    id = id,
                    date = date,
                    photoPath = photo,
                    note = o.optString("note"),
                    noteDevice = o.optString("noteDevice"),
                    noteName = o.optString("noteName"),
                    reply = o.optString("reply"),
                    replyName = o.optString("replyName"),
                    // 老版本的 JSON 没有这两个字段：note 视为随 updatedAt 一起写的，
                    // reply 视为从未写过（0），这样首次合并不会误判成"本地更新"
                    noteUpdatedAt = o.optLong("noteUpdatedAt", 0L)
                        .takeIf { it > 0L } ?: o.optLong("updatedAt"),
                    replyUpdatedAt = o.optLong("replyUpdatedAt", 0L),
                    createdAt = o.optLong("createdAt"),
                    updatedAt = o.optLong("updatedAt"),
                    origin = o.optString("origin"),
                    originName = o.optString("originName"),
                    dirty = false
                )
            )
        }
        "meal" -> {
            val date = o.validDate() ?: return@runCatching null
            // P2-5 校验：id 非空、餐次 0..2；photoPath 空视为无图
            val id = o.optString("id")
            val mealType = o.optInt("mealType", -1)
            if (id.isBlank() || mealType !in 0..2) return@runCatching null
            val photo = if (o.isNull("photoPath")) null
            else o.optString("photoPath").takeIf { it.isNotBlank() }
            SyncRecord.Meal(
                MealRecord(
                    id = id,
                    date = date,
                    mealType = mealType,
                    photoPath = photo,
                    note = o.optString("note"),
                    noteDevice = o.optString("noteDevice"),
                    noteName = o.optString("noteName"),
                    reply = o.optString("reply"),
                    replyName = o.optString("replyName"),
                    noteUpdatedAt = o.optLong("noteUpdatedAt", 0L)
                        .takeIf { it > 0L } ?: o.optLong("updatedAt"),
                    replyUpdatedAt = o.optLong("replyUpdatedAt", 0L),
                    createdAt = o.optLong("createdAt"),
                    updatedAt = o.optLong("updatedAt"),
                    origin = o.optString("origin"),
                    originName = o.optString("originName"),
                    dirty = false
                )
            )
        }
        "event" -> {
            val date = o.validDate() ?: return@runCatching null
            // P2-5 校验：id 非空、事件类型 0..1、标题非空；photoPath 空视为无图
            val id = o.optString("id")
            val type = o.optInt("eventType", -1)
            val title = o.optString("title")
            if (id.isBlank() || type !in 0..1 || title.isBlank()) return@runCatching null
            val photo = if (o.isNull("photoPath")) null
            else o.optString("photoPath").takeIf { it.isNotBlank() }
            SyncRecord.Event(
                EventRecord(
                    id = id,
                    type = type,
                    title = title,
                    description = o.optString("description"),
                    noteDevice = o.optString("noteDevice"),
                    noteName = o.optString("noteName"),
                    reply = o.optString("reply"),
                    replyName = o.optString("replyName"),
                    noteUpdatedAt = o.optLong("noteUpdatedAt", 0L)
                        .takeIf { it > 0L } ?: o.optLong("updatedAt"),
                    replyUpdatedAt = o.optLong("replyUpdatedAt", 0L),
                    photoPath = photo,
                    date = date,
                    createdAt = o.optLong("createdAt"),
                    updatedAt = o.optLong("updatedAt"),
                    origin = o.optString("origin"),
                    originName = o.optString("originName"),
                    dirty = false
                )
            )
        }
        else -> null
    }
}.getOrNull()

/**
 * note 的有效版本时间。老数据（v2 及以前）没有 noteUpdatedAt，
 * 那时候 note 就是跟着记录 updatedAt 一起改的，所以回退到它。
 */
fun noteVersionOf(noteUpdatedAt: Long, updatedAt: Long): Long =
    if (noteUpdatedAt > 0L) noteUpdatedAt else updatedAt
