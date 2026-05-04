package com.heimdall.tracker.data.repository

import com.heimdall.tracker.data.db.RunDao
import com.heimdall.tracker.data.db.RunEntity
import kotlinx.coroutines.flow.Flow

class RunRepository(private val runDao: RunDao) {

    val allRuns: Flow<List<RunEntity>> = runDao.getAllRuns()

    suspend fun insertRun(run: RunEntity): Long = runDao.insertRun(run)

    suspend fun getRunById(runId: Long): RunEntity? = runDao.getRunById(runId)

    suspend fun deleteRun(runId: Long) = runDao.deleteRun(runId)

    suspend fun getRunCount(): Int = runDao.getRunCount()

    suspend fun getTotalDistance(): Double = runDao.getTotalDistance() ?: 0.0

    suspend fun getTotalDuration(): Long = runDao.getTotalDuration() ?: 0L
}
