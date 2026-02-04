package com.example.phototransfer.data.local.dao

import androidx.room.*
import com.example.phototransfer.data.local.entity.TransferRecord
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TransferRecordDao {

    @Query("SELECT * FROM transfer_records ORDER BY timestamp DESC LIMIT 10")
    abstract fun getRecentRecords(): Flow<List<TransferRecord>>

    @Query("SELECT * FROM transfer_records WHERE id = :id")
    abstract suspend fun getRecordById(id: Long): TransferRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(record: TransferRecord): Long

    @Update
    abstract suspend fun update(record: TransferRecord)

    @Delete
    abstract suspend fun delete(record: TransferRecord)

    @Query("DELETE FROM transfer_records")
    abstract suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM transfer_records")
    abstract suspend fun getCount(): Int

    @Query("DELETE FROM transfer_records WHERE id IN (SELECT id FROM transfer_records ORDER BY timestamp ASC LIMIT :count)")
    abstract suspend fun deleteOldest(count: Int)

    @Transaction
    open suspend fun insertWithCleanup(record: TransferRecord): Long {
        val recordId = insert(record)
        val totalCount = getCount()
        if (totalCount > 10) {
            deleteOldest(totalCount - 10)
        }
        return recordId
    }
}
