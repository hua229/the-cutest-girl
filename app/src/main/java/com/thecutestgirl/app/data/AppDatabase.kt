package com.thecutestgirl.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        OutfitRecord::class,
        MealRecord::class,
        EventRecord::class,
        Tombstone::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun outfitDao(): OutfitDao
    abstract fun mealDao(): MealDao
    abstract fun eventDao(): EventDao
    abstract fun tombstoneDao(): TombstoneDao

    /**
     * 删除记录 + 写墓碑必须在同一个事务里：
     * 两步之间被杀/异常时，只删了记录没写墓碑，下一轮同步会把云端记录重新下载回来（记录复活）。
     * 照片文件不参与数据库事务，调用方在事务成功后单独删。
     */
    suspend fun deleteOutfitWithTombstone(record: OutfitRecord) = withTransaction {
        outfitDao().delete(record)
        tombstoneDao().insert(Tombstone(record.id, System.currentTimeMillis()))
    }

    suspend fun deleteMealWithTombstone(record: MealRecord) = withTransaction {
        mealDao().delete(record)
        tombstoneDao().insert(Tombstone(record.id, System.currentTimeMillis()))
    }

    suspend fun deleteEventWithTombstone(record: EventRecord) = withTransaction {
        eventDao().delete(record)
        tombstoneDao().insert(Tombstone(record.id, System.currentTimeMillis()))
    }

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /**
         * v1 → v2：主键从自增数字换成 UUID 字符串，并加上同步字段。
         * 旧记录原样保留，id 前拼个 "L" 变成字符串（如 3 → "L3"），
         * 全部标记为待上传（dirty=1），首次同步时会自动带上云端。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE outfits_new (id TEXT NOT NULL PRIMARY KEY, date TEXT NOT NULL, " +
                        "photoPath TEXT NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, origin TEXT NOT NULL, originName TEXT NOT NULL, " +
                        "dirty INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO outfits_new (id, date, photoPath, note, createdAt, updatedAt, origin, originName, dirty) " +
                        "SELECT 'L' || id, date, photoPath, note, createdAt, createdAt, 'local', '', 1 FROM outfits"
                )
                db.execSQL("DROP TABLE outfits")
                db.execSQL("ALTER TABLE outfits_new RENAME TO outfits")

                db.execSQL(
                    "CREATE TABLE meals_new (id TEXT NOT NULL PRIMARY KEY, date TEXT NOT NULL, " +
                        "mealType INTEGER NOT NULL, photoPath TEXT, note TEXT NOT NULL, createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, origin TEXT NOT NULL, originName TEXT NOT NULL, " +
                        "dirty INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO meals_new (id, date, mealType, photoPath, note, createdAt, updatedAt, origin, originName, dirty) " +
                        "SELECT 'L' || id, date, mealType, photoPath, note, createdAt, createdAt, 'local', '', 1 FROM meals"
                )
                db.execSQL("DROP TABLE meals")
                db.execSQL("ALTER TABLE meals_new RENAME TO meals")

                db.execSQL(
                    "CREATE TABLE events_new (id TEXT NOT NULL PRIMARY KEY, type INTEGER NOT NULL, " +
                        "title TEXT NOT NULL, description TEXT NOT NULL, photoPath TEXT, date TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, origin TEXT NOT NULL, " +
                        "originName TEXT NOT NULL, dirty INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO events_new (id, type, title, description, photoPath, date, createdAt, updatedAt, origin, originName, dirty) " +
                        "SELECT 'L' || id, type, title, description, photoPath, date, createdAt, createdAt, 'local', '', 1 FROM events"
                )
                db.execSQL("DROP TABLE events")
                db.execSQL("ALTER TABLE events_new RENAME TO events")

                db.execSQL("CREATE TABLE tombstones (id TEXT NOT NULL PRIMARY KEY, updatedAt INTEGER NOT NULL)")
            }
        }

        /**
         * v2 → v3：备注拆成「上传人备注 note」+「对方评论 reply」，
         * 并给两者各加一个字段级时间戳，用来做无损合并。
         *
         * 全部用 ALTER TABLE ADD COLUMN + DEFAULT：老记录原样保留，
         * noteUpdatedAt = 0 会在读取时回退到 updatedAt（见 noteVersionOf），
         * 所以升级后第一轮同步不会把老备注当成"刚改过"而压掉对方的评论。
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (table in listOf("outfits", "meals", "events")) {
                    db.execSQL("ALTER TABLE $table ADD COLUMN reply TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE $table ADD COLUMN noteUpdatedAt INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE $table ADD COLUMN replyUpdatedAt INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        /**
         * v3 → v4：评论要记作者。
         *
         * 之前界面上拿 originName（上传人昵称）给评论署名，结果自己上传的记录
         * 收到对方评论时显示成「我的评论」。评论作者只有一个人，直接存下来最省事。
         * 老记录留空，界面回退成「ta」。
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (table in listOf("outfits", "meals", "events")) {
                    db.execSQL("ALTER TABLE $table ADD COLUMN replyName TEXT NOT NULL DEFAULT ''")
                }
            }
        }

        /**
         * v4 → v5：备注也要记作者。
         *
         * v2.8.1 用 origin（上传人）判断谁写的备注，结果「对方上传的照片 + 我写的备注」
         * 被当成对方的备注锁住了。备注作者必须单独存。
         * 老数据留空，界面按「自己的备注」处理，不贴标签。
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (table in listOf("outfits", "meals", "events")) {
                    db.execSQL("ALTER TABLE $table ADD COLUMN noteDevice TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE $table ADD COLUMN noteName TEXT NOT NULL DEFAULT ''")
                }
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "the_cutest_girl.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }
    }
}
