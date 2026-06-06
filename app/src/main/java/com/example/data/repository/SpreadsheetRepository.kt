package com.example.data.repository

import com.example.data.database.SpreadsheetDao
import com.example.data.database.SpreadsheetEntity
import kotlinx.coroutines.flow.Flow

class SpreadsheetRepository(private val spreadsheetDao: SpreadsheetDao) {
    val allSpreadsheets: Flow<List<SpreadsheetEntity>> = spreadsheetDao.getAllSpreadsheets()

    suspend fun getSpreadsheetById(id: Long): SpreadsheetEntity? {
        return spreadsheetDao.getSpreadsheetById(id)
    }

    suspend fun insert(spreadsheet: SpreadsheetEntity): Long {
        return spreadsheetDao.insertSpreadsheet(spreadsheet)
    }

    suspend fun update(spreadsheet: SpreadsheetEntity) {
        spreadsheetDao.updateSpreadsheet(spreadsheet)
    }

    suspend fun delete(spreadsheet: SpreadsheetEntity) {
        spreadsheetDao.deleteSpreadsheet(spreadsheet)
    }

    suspend fun deleteById(id: Long) {
        spreadsheetDao.deleteSpreadsheetById(id)
    }
}
