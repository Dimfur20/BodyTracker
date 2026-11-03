package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyMetricDao {
    @Query("SELECT * FROM body_metrics ORDER BY dateMillis DESC, id DESC")
    fun getAllEntriesDescending(): Flow<List<BodyMetricEntry>>

    @Query("SELECT * FROM body_metrics ORDER BY dateMillis ASC, id ASC")
    fun getAllEntriesAscending(): Flow<List<BodyMetricEntry>>

    @Query("SELECT * FROM body_metrics ORDER BY dateMillis DESC, id DESC LIMIT 1")
    suspend fun getLatestEntry(): BodyMetricEntry?

    @Query("SELECT * FROM body_metrics WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: Long): BodyMetricEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: BodyMetricEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<BodyMetricEntry>)

    @Update
    suspend fun update(entry: BodyMetricEntry)

    @Delete
    suspend fun delete(entry: BodyMetricEntry)

    @Query("DELETE FROM body_metrics WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM body_metrics")
    suspend fun deleteAll()
}
