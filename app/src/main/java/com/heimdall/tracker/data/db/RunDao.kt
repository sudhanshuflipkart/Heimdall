package com.heimdall.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: RunEntity): Long

    @Query("SELECT * FROM runs ORDER BY dateTimestamp DESC")
    fun getAllRuns(): Flow<List<RunEntity>>

    @Query("SELECT * FROM runs WHERE id = :runId")
    suspend fun getRunById(runId: Long): RunEntity?

    @Query("DELETE FROM runs WHERE id = :runId")
    suspend fun deleteRun(runId: Long)

    @Query("SELECT COUNT(*) FROM runs")
    suspend fun getRunCount(): Int

    @Query("SELECT SUM(distanceMeters) FROM runs")
    suspend fun getTotalDistance(): Double?

    @Query("SELECT SUM(durationMillis) FROM runs")
    suspend fun getTotalDuration(): Long?
}
