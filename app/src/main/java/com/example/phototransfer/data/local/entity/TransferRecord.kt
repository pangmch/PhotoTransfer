package com.example.phototransfer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfer_records")
data class TransferRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val filePath: String,
    val direction: TransferDirection,
    val status: TransferStatus,
    val remoteDeviceName: String,
    val retryCount: Int = 0,
    val fileSize: Long,
    val timestamp: Long = System.currentTimeMillis()
)

enum class TransferDirection {
    SEND, RECEIVE
}

enum class TransferStatus {
    PENDING, IN_PROGRESS, SUCCESS, FAILED
}
