package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SpreadsheetDao {
    @Query("SELECT * FROM spreadsheets ORDER BY lastModified DESC")
    fun getAllSpreadsheets(): Flow<List<SpreadsheetEntity>>

    @Query("SELECT * FROM spreadsheets WHERE id = :id")
    suspend fun getSpreadsheetById(id: Long): SpreadsheetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpreadsheet(spreadsheet: SpreadsheetEntity): Long

    @Update
    suspend fun updateSpreadsheet(spreadsheet: SpreadsheetEntity)

    @Delete
    suspend fun deleteSpreadsheet(spreadsheet: SpreadsheetEntity)

    @Query("DELETE FROM spreadsheets WHERE id = :id")
    suspend fun deleteSpreadsheetById(id: Long)
}
