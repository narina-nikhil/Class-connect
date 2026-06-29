package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PeriodDao {
    @Query("SELECT * FROM periods ORDER BY startMinutes ASC")
    fun getAllPeriods(): Flow<List<PeriodEntity>>

    @Query("SELECT * FROM periods WHERE scheduleOwnerName = :ownerName ORDER BY startMinutes ASC")
    fun getPeriodsByOwner(ownerName: String): Flow<List<PeriodEntity>>

    @Query("SELECT DISTINCT scheduleOwnerName FROM periods")
    fun getDistinctOwners(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeriod(period: PeriodEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeriods(periods: List<PeriodEntity>)

    @Delete
    suspend fun deletePeriod(period: PeriodEntity)

    @Query("DELETE FROM periods WHERE id = :id")
    suspend fun deletePeriodById(id: Long)

    @Query("DELETE FROM periods WHERE scheduleOwnerName = :ownerName")
    suspend fun deletePeriodsByOwner(ownerName: String)

    @Query("DELETE FROM periods")
    suspend fun clearAll()
}
