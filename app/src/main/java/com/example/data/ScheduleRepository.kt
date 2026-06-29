package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScheduleRepository(private val periodDao: PeriodDao) {

    val distinctOwners: Flow<List<String>> = periodDao.getDistinctOwners()

    fun getPeriodsForOwner(ownerName: String): Flow<List<PeriodEntity>> {
        return periodDao.getPeriodsByOwner(ownerName)
    }

    suspend fun initializeDatabaseIfEmpty() {
        withContext(Dispatchers.IO) {
            val currentOwners = periodDao.getDistinctOwners().first()
            if (currentOwners.isEmpty()) {
                periodDao.insertPeriods(DefaultTimetable.PRE_POPULATED_PERIODS)
            }
        }
    }

    suspend fun insertPeriod(period: PeriodEntity) {
        withContext(Dispatchers.IO) {
            periodDao.insertPeriod(period)
        }
    }

    suspend fun insertPeriods(periods: List<PeriodEntity>) {
        withContext(Dispatchers.IO) {
            periodDao.insertPeriods(periods)
        }
    }

    suspend fun deletePeriodById(id: Long) {
        withContext(Dispatchers.IO) {
            periodDao.deletePeriodById(id)
        }
    }

    suspend fun deletePeriodsByOwner(ownerName: String) {
        withContext(Dispatchers.IO) {
            periodDao.deletePeriodsByOwner(ownerName)
        }
    }

    suspend fun resetAllToDefaults() {
        withContext(Dispatchers.IO) {
            periodDao.clearAll()
            periodDao.insertPeriods(DefaultTimetable.PRE_POPULATED_PERIODS)
        }
    }

    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            periodDao.clearAll()
        }
    }
}
