package com.example.phototransfer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.phototransfer.data.local.dao.TransferRecordDao
import com.example.phototransfer.data.local.entity.TransferRecord

@Database(
    entities = [TransferRecord::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transferRecordDao(): TransferRecordDao
}
