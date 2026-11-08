package com.thecutestgirl.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface OutfitDao {
    /** 某个月的所有穿搭，新的在前；monthPrefix 形如 "2026-10" */
    @Query("SELECT * FROM outfits WHERE date LIKE :monthPrefix || '%' ORDER BY date DESC, createdAt DESC")
    fun observeByMonth(monthPrefix: String): Flow<List<OutfitRecord>>

    /** 某一天的所有穿搭（v2.4.4 按天聚合 / 指定某天上传时用），新的在前 */
    @Query("SELECT * FROM outfits WHERE date = :date ORDER BY createdAt DESC")
    fun observeByDate(date: String): Flow<List<OutfitRecord>>

    @Query("SELECT * FROM outfits WHERE id = :id")
    suspend fun getById(id: String): OutfitRecord?

    @Query("SELECT * FROM outfits WHERE dirty = 1")
    suspend fun getDirty(): List<OutfitRecord>

    /** 旧版迁移产生的 "L数字" id（两台手机/不同表之间会撞车，需改写） */
    @Query("SELECT * FROM outfits WHERE id LIKE 'L%'")
    suspend fun getLegacy(): List<OutfitRecord>

    /** 收到主人改写重传的记录后，清掉本地同源（同创建时间）的旧 L id 副本 */
    @Query("DELETE FROM outfits WHERE id LIKE 'L%' AND createdAt = :createdAt")
    suspend fun deleteLegacyByCreatedAt(createdAt: Long)

    @Query("SELECT photoPath FROM outfits WHERE photoPath IS NOT NULL")
    suspend fun getAllPhotoPaths(): List<String>

    /** 增量同步用：只取 id 判断「本地有没有这条」，比读全表轻 */
    @Query("SELECT id FROM outfits")
    suspend fun getAllIds(): List<String>

    @Query("UPDATE outfits SET dirty = 0 WHERE id = :id AND updatedAt = :uploadedAt")
    suspend fun markClean(id: String, uploadedAt: Long)

    @Query("UPDATE outfits SET origin = :origin WHERE id = :id")
    suspend fun setOrigin(id: String, origin: String)

    /** 昵称变更后批量刷新我名下记录的昵称（标记待上传） */
    @Query("UPDATE outfits SET originName = :name, dirty = 1, updatedAt = MAX(updatedAt + 1, :now) WHERE origin = :origin AND originName <> :name")
    suspend fun updateOriginName(origin: String, name: String, now: Long)

    /**
     * 上传人改自己的备注：只动 note 与 noteUpdatedAt，不碰 reply。
     * 必须同时抬高记录级 updatedAt，否则对方的设备按「记录没变」直接跳过这一条。
     */
    @Query("UPDATE outfits SET note = :note, noteDevice = :device, noteName = :name, noteUpdatedAt = :now, updatedAt = :now, dirty = 1 WHERE id = :id")
    suspend fun setNote(id: String, note: String, name: String, device: String, now: Long)

    /**
     * 对方写评论：只动 reply 与 replyUpdatedAt，不碰 note。
     * 同样抬高记录级 updatedAt，让上传人那台设备把这条重新拉一遍。
     */
    @Query("UPDATE outfits SET reply = :reply, replyName = :name, replyUpdatedAt = :now, updatedAt = :now, dirty = 1 WHERE id = :id")
    suspend fun setReply(id: String, reply: String, name: String, now: Long)




    @Insert
    suspend fun insert(record: OutfitRecord)

    @Update
    suspend fun update(record: OutfitRecord)

    @Upsert
    suspend fun upsert(record: OutfitRecord)

    @Delete
    suspend fun delete(record: OutfitRecord)
}

@Dao
interface MealDao {
    /** 某一天的三餐记录 */
    @Query("SELECT * FROM meals WHERE date = :date")
    fun observeByDate(date: String): Flow<List<MealRecord>>

    @Query("SELECT * FROM meals WHERE id = :id")
    suspend fun getById(id: String): MealRecord?

    @Query("SELECT * FROM meals WHERE dirty = 1")
    suspend fun getDirty(): List<MealRecord>

    /** 旧版迁移产生的 "L数字" id（两台手机/不同表之间会撞车，需改写） */
    @Query("SELECT * FROM meals WHERE id LIKE 'L%'")
    suspend fun getLegacy(): List<MealRecord>

    /** 收到主人改写重传的记录后，清掉本地同源（同创建时间）的旧 L id 副本 */
    @Query("DELETE FROM meals WHERE id LIKE 'L%' AND createdAt = :createdAt")
    suspend fun deleteLegacyByCreatedAt(createdAt: Long)

    @Query("SELECT photoPath FROM meals WHERE photoPath IS NOT NULL")
    suspend fun getAllPhotoPaths(): List<String>

    /** 增量同步用：只取 id 判断「本地有没有这条」，比读全表轻 */
    @Query("SELECT id FROM meals")
    suspend fun getAllIds(): List<String>

    @Query("UPDATE meals SET dirty = 0 WHERE id = :id AND updatedAt = :uploadedAt")
    suspend fun markClean(id: String, uploadedAt: Long)

    @Query("UPDATE meals SET origin = :origin WHERE id = :id")
    suspend fun setOrigin(id: String, origin: String)

    /** 昵称变更后批量刷新我名下记录的昵称（标记待上传） */
    @Query("UPDATE meals SET originName = :name, dirty = 1, updatedAt = MAX(updatedAt + 1, :now) WHERE origin = :origin AND originName <> :name")
    suspend fun updateOriginName(origin: String, name: String, now: Long)

    /**
     * 上传人改自己的备注：只动 note 与 noteUpdatedAt，不碰 reply。
     * 必须同时抬高记录级 updatedAt，否则对方的设备按「记录没变」直接跳过这一条。
     */
    @Query("UPDATE meals SET note = :note, noteDevice = :device, noteName = :name, noteUpdatedAt = :now, updatedAt = :now, dirty = 1 WHERE id = :id")
    suspend fun setNote(id: String, note: String, name: String, device: String, now: Long)

    /**
     * 对方写评论：只动 reply 与 replyUpdatedAt，不碰 note。
     * 同样抬高记录级 updatedAt，让上传人那台设备把这条重新拉一遍。
     */
    @Query("UPDATE meals SET reply = :reply, replyName = :name, replyUpdatedAt = :now, updatedAt = :now, dirty = 1 WHERE id = :id")
    suspend fun setReply(id: String, reply: String, name: String, now: Long)

    @Insert
    suspend fun insert(record: MealRecord)

    @Update
    suspend fun update(record: MealRecord)

    @Upsert
    suspend fun upsert(record: MealRecord)

    @Delete
    suspend fun delete(record: MealRecord)
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events")
    fun observeAll(): Flow<List<EventRecord>>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getById(id: String): EventRecord?

    @Query("SELECT * FROM events WHERE dirty = 1")
    suspend fun getDirty(): List<EventRecord>

    /** 旧版迁移产生的 "L数字" id（两台手机/不同表之间会撞车，需改写） */
    @Query("SELECT * FROM events WHERE id LIKE 'L%'")
    suspend fun getLegacy(): List<EventRecord>

    /** 收到主人改写重传的记录后，清掉本地同源（同创建时间）的旧 L id 副本 */
    @Query("DELETE FROM events WHERE id LIKE 'L%' AND createdAt = :createdAt")
    suspend fun deleteLegacyByCreatedAt(createdAt: Long)

    @Query("SELECT photoPath FROM events WHERE photoPath IS NOT NULL")
    suspend fun getAllPhotoPaths(): List<String>

    /** 增量同步用：只取 id 判断「本地有没有这条」，比读全表轻 */
    @Query("SELECT id FROM events")
    suspend fun getAllIds(): List<String>

    @Query("UPDATE events SET dirty = 0 WHERE id = :id AND updatedAt = :uploadedAt")
    suspend fun markClean(id: String, uploadedAt: Long)

    @Query("UPDATE events SET origin = :origin WHERE id = :id")
    suspend fun setOrigin(id: String, origin: String)

    /** 昵称变更后批量刷新我名下记录的昵称（标记待上传） */
    @Query("UPDATE events SET originName = :name, dirty = 1, updatedAt = MAX(updatedAt + 1, :now) WHERE origin = :origin AND originName <> :name")
    suspend fun updateOriginName(origin: String, name: String, now: Long)

    /**
     * 上传人改自己的备注：只动 note 与 noteUpdatedAt，不碰 reply。
     * 必须同时抬高记录级 updatedAt，否则对方的设备按「记录没变」直接跳过这一条。
     */
    @Query("UPDATE events SET description = :note, noteDevice = :device, noteName = :name, noteUpdatedAt = :now, updatedAt = :now, dirty = 1 WHERE id = :id")
    suspend fun setNote(id: String, note: String, name: String, device: String, now: Long)

    /**
     * 对方写评论：只动 reply 与 replyUpdatedAt，不碰 note。
     * 同样抬高记录级 updatedAt，让上传人那台设备把这条重新拉一遍。
     */
    @Query("UPDATE events SET reply = :reply, replyName = :name, replyUpdatedAt = :now, updatedAt = :now, dirty = 1 WHERE id = :id")
    suspend fun setReply(id: String, reply: String, name: String, now: Long)

    @Insert
    suspend fun insert(record: EventRecord)

    @Update
    suspend fun update(record: EventRecord)

    @Upsert
    suspend fun upsert(record: EventRecord)

    @Delete
    suspend fun delete(record: EventRecord)
}

@Dao
interface TombstoneDao {
    @Query("SELECT * FROM tombstones")
    suspend fun getAll(): List<Tombstone>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tombstone: Tombstone)

    @Query("DELETE FROM tombstones WHERE id = :id")
    suspend fun delete(id: String)

    /** 清掉旧格式 id 的墓碑（改写 id 时不传墓碑，避免误删对方同 id 的记录） */
    @Query("DELETE FROM tombstones WHERE id LIKE 'L%'")
    suspend fun deleteLegacy()
}
