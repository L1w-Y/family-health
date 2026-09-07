// Room 数据库：本地镜像（契约 docs/04 §3 Room 本地镜像缓存与离线查看）
package com.family.health.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DeviceEntity::class, ProfileEntity::class, MeasurementEntity::class,
        CheckupEventEntity::class, ReportEntity::class, IndicatorItemEntity::class,
        WatchItemEntity::class, MedicationItemEntity::class, MedChangeEntity::class,
        ReminderEntity::class, AttachmentEntity::class, NoteEntity::class,
        DailyMedItemEntity::class, OutboxEntity::class, MetaEntity::class,
    ],
    version = 1,
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

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "family-health.db",
            ).build().also { instance = it }
        }
    }
}
