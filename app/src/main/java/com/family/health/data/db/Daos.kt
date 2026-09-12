// DAO：各表镜像读写（下行 upsert 全字段行含 deleted 标志；读取时过滤墓碑）
package com.family.health.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Upsert suspend fun upsertAll(items: List<DeviceEntity>)
    @Query("SELECT * FROM devices WHERE deleted = 0") fun observeAll(): Flow<List<DeviceEntity>>
    @Query("SELECT * FROM devices") suspend fun all(): List<DeviceEntity>
}

@Dao
interface ProfileDao {
    @Upsert suspend fun upsertAll(items: List<ProfileEntity>)
    @Query("SELECT * FROM profiles WHERE deleted = 0") fun observeAll(): Flow<List<ProfileEntity>>
    @Query("SELECT * FROM profiles WHERE id = :id") suspend fun byId(id: String): ProfileEntity?
}

@Dao
interface MeasurementDao {
    @Upsert suspend fun upsertAll(items: List<MeasurementEntity>)
    @Query("SELECT * FROM measurements WHERE deleted = 0") fun observeAll(): Flow<List<MeasurementEntity>>
    @Query("SELECT * FROM measurements WHERE id = :id") suspend fun byId(id: String): MeasurementEntity?
}

@Dao
interface CheckupEventDao {
    @Upsert suspend fun upsertAll(items: List<CheckupEventEntity>)
    @Query("SELECT * FROM checkup_events WHERE deleted = 0") fun observeAll(): Flow<List<CheckupEventEntity>>
    @Query("SELECT * FROM checkup_events WHERE id = :id") suspend fun byId(id: String): CheckupEventEntity?
}

@Dao
interface ReportDao {
    @Upsert suspend fun upsertAll(items: List<ReportEntity>)
    @Query("SELECT * FROM reports WHERE deleted = 0") fun observeAll(): Flow<List<ReportEntity>>
}

@Dao
interface IndicatorItemDao {
    @Upsert suspend fun upsertAll(items: List<IndicatorItemEntity>)
    @Query("SELECT * FROM indicator_items WHERE deleted = 0") fun observeAll(): Flow<List<IndicatorItemEntity>>
}

@Dao
interface WatchItemDao {
    @Upsert suspend fun upsertAll(items: List<WatchItemEntity>)
    @Query("SELECT * FROM watch_items WHERE deleted = 0") fun observeAll(): Flow<List<WatchItemEntity>>
    @Query("SELECT * FROM watch_items WHERE id = :id") suspend fun byId(id: String): WatchItemEntity?
}

@Dao
interface MedicationItemDao {
    @Upsert suspend fun upsertAll(items: List<MedicationItemEntity>)
    @Query("SELECT * FROM medication_items WHERE deleted = 0") fun observeAll(): Flow<List<MedicationItemEntity>>
    @Query("SELECT * FROM medication_items WHERE id = :id") suspend fun byId(id: String): MedicationItemEntity?
}

@Dao
interface MedChangeDao {
    @Upsert suspend fun upsertAll(items: List<MedChangeEntity>)
    @Query("SELECT * FROM med_changes WHERE deleted = 0") fun observeAll(): Flow<List<MedChangeEntity>>
}

@Dao
interface ReminderDao {
    @Upsert suspend fun upsertAll(items: List<ReminderEntity>)
    @Query("SELECT * FROM reminders WHERE deleted = 0") fun observeAll(): Flow<List<ReminderEntity>>
    @Query("SELECT * FROM reminders WHERE profileId = :profileId AND deleted = 0")
    suspend fun byProfile(profileId: String): List<ReminderEntity>
}

@Dao
interface AttachmentDao {
    @Upsert suspend fun upsertAll(items: List<AttachmentEntity>)
    @Query("SELECT * FROM attachments WHERE deleted = 0") fun observeAll(): Flow<List<AttachmentEntity>>
}

@Dao
interface NoteDao {
    @Upsert suspend fun upsertAll(items: List<NoteEntity>)
    @Query("SELECT * FROM notes WHERE deleted = 0") fun observeAll(): Flow<List<NoteEntity>>
    @Query("SELECT * FROM notes WHERE id = :id") suspend fun byId(id: String): NoteEntity?
}

@Dao
interface DailyMedItemDao {
    @Upsert suspend fun upsertAll(items: List<DailyMedItemEntity>)
    @Query("SELECT * FROM daily_med_items WHERE deleted = 0") fun observeAll(): Flow<List<DailyMedItemEntity>>
    @Query("SELECT * FROM daily_med_items WHERE id = :id") suspend fun byId(id: String): DailyMedItemEntity?
    @Query("SELECT * FROM daily_med_items WHERE medicationItemId = :medicationItemId AND deleted = 0 LIMIT 1")
    suspend fun byMedicationId(medicationItemId: String): DailyMedItemEntity?
}

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<OutboxEntity>)

    @Query("SELECT * FROM outbox ORDER BY id ASC")
    suspend fun pending(): List<OutboxEntity>

    @Query("DELETE FROM outbox WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM outbox")
    fun observeCount(): Flow<Int>
}

@Dao
interface MetaDao {
    @Upsert suspend fun put(item: MetaEntity)
    @Query("SELECT value FROM meta WHERE `key` = :key") suspend fun get(key: String): String?
}
