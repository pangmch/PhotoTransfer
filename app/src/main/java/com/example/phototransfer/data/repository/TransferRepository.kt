package com.example.phototransfer.data.repository

import com.example.phototransfer.data.local.dao.TransferRecordDao
import com.example.phototransfer.data.local.entity.TransferRecord
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferRepository @Inject constructor(
    private val transferRecordDao: TransferRecordDao
) {
    
    fun getRecentRecords(): Flow<List<TransferRecord>> {
        return transferRecordDao.getRecentRecords()
    }
    
    suspend fun getRecordById(id: Long): TransferRecord? {
        return transferRecordDao.getRecordById(id)
    }
    
    suspend fun insertRecord(record: TransferRecord): Long {
        return transferRecordDao.insertWithCleanup(record)
    }
    
    suspend fun updateRecord(record: TransferRecord) {
        transferRecordDao.update(record)
    }
    
    suspend fun deleteRecord(record: TransferRecord) {
        transferRecordDao.delete(record)
    }
    
    suspend fun clearAllRecords() {
        transferRecordDao.deleteAll()
    }
}
