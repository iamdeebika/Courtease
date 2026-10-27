package com.simats.courtease.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CaseDao {
    @Query("SELECT * FROM cases ORDER BY caseNo")
    suspend fun getAll(): List<CaseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CaseEntity)

    @Query("DELETE FROM cases WHERE caseNo = :caseNo")
    suspend fun delete(caseNo: String)
}