package com.example.phototransfer.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.phototransfer.data.local.entity.TransferDirection
import com.example.phototransfer.data.local.entity.TransferRecord
import com.example.phototransfer.data.local.entity.TransferStatus
import com.example.phototransfer.data.repository.PhotoRepository
import com.example.phototransfer.data.repository.TransferRepository
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectionManager: ConnectionManager,
    private val transferRepository: TransferRepository,
    private val photoRepository: PhotoRepository
) {
    companion object {
        private const val TAG = "TransferManager"
    }

    private val connectionsClient by lazy { Nearby.getConnectionsClient(context) }
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    private val _transferProgress = MutableStateFlow<TransferProgress>(TransferProgress.Idle)
    val transferProgress: StateFlow<TransferProgress> = _transferProgress
    
    private val activeTransfers = mutableMapOf<Long, TransferInfo>()
    private val receivedPayloads = mutableMapOf<Long, Payload>()
    
    init {
        setupPayloadCallback()
    }
    
    private fun setupPayloadCallback() {
        connectionManager.payloadCallback = object : PayloadCallback() {
            override fun onPayloadReceived(endpointId: String, payload: Payload) {
                when (payload.type) {
                    Payload.Type.FILE -> {
                        payload.asFile()?.let { file ->
                            receivedPayloads[payload.id] = payload
                            _transferProgress.value = TransferProgress.Receiving(
                                payloadId = payload.id,
                                fileName = file.asUri()?.lastPathSegment ?: "unknown",
                                progress = 0
                            )
                        }
                    }
                    Payload.Type.BYTES -> {
                        // Could be used for metadata
                    }
                    else -> {}
                }
            }
            
            override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
                val payloadId = update.payloadId
                
                when (update.status) {
                    PayloadTransferUpdate.Status.IN_PROGRESS -> {
                        val progress = if (update.totalBytes > 0) {
                            (update.bytesTransferred * 100 / update.totalBytes).toInt()
                        } else 0
                        
                        activeTransfers[payloadId]?.let { info ->
                            _transferProgress.value = TransferProgress.Sending(
                                payloadId = payloadId,
                                fileName = info.fileName,
                                progress = progress,
                                retryCount = info.retryCount
                            )
                            
                            // Update transfer record in database
                            coroutineScope.launch {
                                info.recordId?.let { recordId ->
                                    transferRepository.getRecordById(recordId)?.let { record ->
                                        transferRepository.updateRecord(
                                            record.copy(status = TransferStatus.IN_PROGRESS)
                                        )
                                    }
                                }
                            }
                        } ?: run {
                            // This is a receiving transfer
                            receivedPayloads[payloadId]?.asFile()?.let { file ->
                                _transferProgress.value = TransferProgress.Receiving(
                                    payloadId = payloadId,
                                    fileName = file.asUri()?.lastPathSegment ?: "unknown",
                                    progress = progress
                                )
                            }
                        }
                    }
                    
                    PayloadTransferUpdate.Status.SUCCESS -> {
                        activeTransfers[payloadId]?.let { info ->
                            // Sending completed
                            coroutineScope.launch {
                                info.recordId?.let { recordId ->
                                    transferRepository.getRecordById(recordId)?.let { record ->
                                        transferRepository.updateRecord(
                                            record.copy(status = TransferStatus.SUCCESS)
                                        )
                                    }
                                }
                            }
                            activeTransfers.remove(payloadId)
                            _transferProgress.value = TransferProgress.Success(info.fileName)
                        } ?: run {
                            // Receiving completed
                            Log.d(TAG, "=== Receiving completed for payload $payloadId ===")
                            receivedPayloads[payloadId]?.asFile()?.let { payloadFile ->
                                // ✅ 使用 URI 而不是 File - Android 10+ Scoped Storage 兼容
                                val sourceUri = payloadFile.asUri()
                                val fileName = sourceUri?.lastPathSegment ?: "received_${System.currentTimeMillis()}.jpg"

                                Log.d(TAG, "Received file name: $fileName")
                                Log.d(TAG, "Source URI: $sourceUri")

                                if (sourceUri == null) {
                                    Log.e(TAG, "❌ Failed to get URI from payload")
                                    receivedPayloads.remove(payloadId)
                                    return@let
                                }

                                coroutineScope.launch {
                                    // ✅ 通过 URI 保存到系统相册 (Android 10+ Scoped Storage 兼容)
                                    Log.d(TAG, "Calling photoRepository.saveReceivedFileFromUri...")
                                    val savedUri = photoRepository.saveReceivedFileFromUri(sourceUri, fileName)
                                    Log.d(TAG, "Save result URI: $savedUri")

                                    // 获取文件大小
                                    val fileSize = try {
                                        context.contentResolver.openInputStream(sourceUri)?.use {
                                            it.available().toLong()
                                        } ?: 0L
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to get file size", e)
                                        0L
                                    }

                                    if (savedUri != null) {
                                        Log.d(TAG, "✅ Successfully saved to gallery: $savedUri")
                                        // 保存成功，记录到数据库
                                        val record = TransferRecord(
                                            fileName = fileName,
                                            filePath = savedUri.toString(),
                                            direction = TransferDirection.RECEIVE,
                                            status = TransferStatus.SUCCESS,
                                            remoteDeviceName = endpointId,
                                            fileSize = fileSize
                                        )
                                        transferRepository.insertRecord(record)
                                        Log.d(TAG, "Record saved to database")
                                        _transferProgress.value = TransferProgress.Success(fileName)
                                    } else {
                                        Log.e(TAG, "❌ Failed to save to gallery, trying private directory fallback...")

                                        // 回退方案：通过 URI 保存到应用私有目录
                                        val privateUri = photoRepository.saveToPrivateDirectoryFromUri(sourceUri, fileName)

                                        if (privateUri != null) {
                                            Log.d(TAG, "✅ Saved to private directory: $privateUri")
                                            val record = TransferRecord(
                                                fileName = fileName,
                                                filePath = privateUri.toString(),
                                                direction = TransferDirection.RECEIVE,
                                                status = TransferStatus.SUCCESS,
                                                remoteDeviceName = endpointId,
                                                fileSize = fileSize
                                            )
                                            transferRepository.insertRecord(record)
                                            Log.d(TAG, "Record saved to database (private storage)")
                                            _transferProgress.value = TransferProgress.Success(fileName)
                                        } else {
                                            Log.e(TAG, "❌ Failed to save to both gallery and private directory")
                                            val record = TransferRecord(
                                                fileName = fileName,
                                                filePath = sourceUri.toString(),
                                                direction = TransferDirection.RECEIVE,
                                                status = TransferStatus.FAILED,
                                                remoteDeviceName = endpointId,
                                                fileSize = fileSize
                                            )
                                            transferRepository.insertRecord(record)
                                            Log.d(TAG, "Failed record saved to database")
                                            _transferProgress.value = TransferProgress.Failed("Failed to save received file")
                                        }
                                    }

                                    Log.d(TAG, "=== Receiving processing completed ===")
                                }

                                receivedPayloads.remove(payloadId)
                            } ?: run {
                                Log.e(TAG, "❌ Payload file is null for payload $payloadId")
                            }
                        }
                    }
                    
                    PayloadTransferUpdate.Status.FAILURE,
                    PayloadTransferUpdate.Status.CANCELED -> {
                        activeTransfers[payloadId]?.let { info ->
                            handleTransferFailure(payloadId, info, endpointId)
                        } ?: run {
                            receivedPayloads.remove(payloadId)
                            _transferProgress.value = TransferProgress.Failed("Transfer failed")
                        }
                    }
                }
            }
        }
    }
    
    suspend fun sendPhoto(uri: Uri, fileName: String, remoteDeviceName: String) {
        val endpointId = connectionManager.getCurrentEndpointId()
        if (endpointId == null) {
            _transferProgress.value = TransferProgress.Failed("No device connected")
            return
        }
        
        try {
            val file = createFileFromUri(uri) ?: run {
                _transferProgress.value = TransferProgress.Failed("Failed to read file")
                return
            }
            
            // Create transfer record
            val record = TransferRecord(
                fileName = fileName,
                filePath = file.absolutePath,
                direction = TransferDirection.SEND,
                status = TransferStatus.PENDING,
                remoteDeviceName = remoteDeviceName,
                fileSize = file.length()
            )
            val recordId = transferRepository.insertRecord(record)
            
            // Send payload
            val payload = Payload.fromFile(file)
            val transferInfo = TransferInfo(
                payloadId = payload.id,
                fileName = fileName,
                uri = uri,
                recordId = recordId,
                retryCount = 0
            )
            activeTransfers[payload.id] = transferInfo
            
            connectionsClient.sendPayload(endpointId, payload)
            
        } catch (e: Exception) {
            _transferProgress.value = TransferProgress.Failed(e.message ?: "Send failed")
        }
    }
    
    private fun handleTransferFailure(payloadId: Long, info: TransferInfo, endpointId: String) {
        coroutineScope.launch {
            val newRetryCount = info.retryCount + 1
            
            if (newRetryCount < 3) {
                // Retry
                _transferProgress.value = TransferProgress.Retrying(
                    fileName = info.fileName,
                    retryCount = newRetryCount
                )
                
                val updatedInfo = info.copy(retryCount = newRetryCount)
                activeTransfers[payloadId] = updatedInfo
                
                // Wait a bit before retry
                kotlinx.coroutines.delay(1000)
                
                // Resend
                try {
                    val file = createFileFromUri(info.uri) ?: return@launch
                    val payload = Payload.fromFile(file)
                    activeTransfers.remove(payloadId)
                    activeTransfers[payload.id] = updatedInfo.copy(payloadId = payload.id)
                    connectionsClient.sendPayload(endpointId, payload)
                } catch (e: Exception) {
                    handleTransferFailure(payloadId, updatedInfo, endpointId)
                }
            } else {
                // Failed after 3 retries
                info.recordId?.let { recordId ->
                    transferRepository.getRecordById(recordId)?.let { record ->
                        transferRepository.updateRecord(
                            record.copy(
                                status = TransferStatus.FAILED,
                                retryCount = 3
                            )
                        )
                    }
                }
                activeTransfers.remove(payloadId)
                _transferProgress.value = TransferProgress.Failed("Transfer failed after 3 retries")
            }
        }
    }
    
    private fun createFileFromUri(uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.jpg")
            tempFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            tempFile
        } catch (e: Exception) {
            null
        }
    }
    
    fun resetProgress() {
        _transferProgress.value = TransferProgress.Idle
    }
}

data class TransferInfo(
    val payloadId: Long,
    val fileName: String,
    val uri: Uri,
    val recordId: Long?,
    val retryCount: Int
)

sealed class TransferProgress {
    object Idle : TransferProgress()
    data class Sending(
        val payloadId: Long,
        val fileName: String,
        val progress: Int,
        val retryCount: Int = 0
    ) : TransferProgress()
    data class Receiving(
        val payloadId: Long,
        val fileName: String,
        val progress: Int
    ) : TransferProgress()
    data class Retrying(val fileName: String, val retryCount: Int) : TransferProgress()
    data class Success(val fileName: String) : TransferProgress()
    data class Failed(val error: String) : TransferProgress()
}
