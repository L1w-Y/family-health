// Room 数据库：本地镜像（契约 docs/04 §3 Room 本地镜像缓存与离线查看）
package com.family.health.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DeviceEntity::class, ProfileEntity::class, MeasurementEntity::class,
        CheckupEventEntity::class, ReportEntity::class, IndicatorItemEntity::class,
        WatchItemEntity::class, MedicationItemEntity::class, MedChangeEntity::class,
        ReminderEntity::class, AttachmentEntity::class, NoteEntity::class,
        DailyMedItemEntity::class, OutboxEntity::class, MetaEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun profileDao(): ProfileDao
    abstract fun measurementDao(): MeasurementDao
    abstract fun checkupEventDao(): CheckupEventDao
    abstract fun reportDao(): ReportDao
    abstract fun indicatorItemDao(): IndicatorItemDao
    abstract fun watchItemDao(): WatchItemDao
    abstract fun medicationItemDao(): MedicationItemDao
    abstract fun medChangeDao(): MedChangeDao
    abstract fun reminderDao(): ReminderDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun noteDao(): NoteDao
    abstract fun dailyMedItemDao(): DailyMedItemDao
    abstract fun outboxDao(): OutboxDao
    abstract fun metaDao(): MetaDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** v1 → v2：measurements 增加 payloadJson，镜像服务端预留 payload 列 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE measurements ADD COLUMN payloadJson TEXT")
            }
        }

        /** v2 → v3：西药一次用量结构化；旧今日用药按产品决定清空并改为药品关联的分时段库存。 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medication_items ADD COLUMN doseQty REAL")
                db.execSQL("ALTER TABLE medication_items ADD COLUMN doseUnit TEXT")
                db.execSQL("DROP TABLE daily_med_items")
                db.execSQL(
                    """CREATE TABLE daily_med_items (
                        id TEXT NOT NULL PRIMARY KEY,
                        profileId TEXT NOT NULL,
                        medicationItemId TEXT NOT NULL,
                        stockBySlotJson TEXT NOT NULL,
                        stockCountedAtMs INTEGER NOT NULL,
                        tzOffsetMin INTEGER NOT NULL,
                        deleted INTEGER NOT NULL,
                        seq INTEGER NOT NULL
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_med_items_medicationItemId ON daily_med_items(medicationItemId)")
            }
        }

        /** v3 → v4：一天次数改为独立字段；旧条目由用户后续在界面补齐。 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medication_items ADD COLUMN doseTimesPerDay INTEGER")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "family-health.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
        }
    }
}
