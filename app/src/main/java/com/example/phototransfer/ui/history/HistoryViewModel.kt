package com.example.phototransfer.ui.history

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.phototransfer.data.local.entity.TransferRecord
import com.example.phototransfer.data.repository.TransferRepository
import com.example.phototransfer.service.TransferManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val transferRepository: TransferRepository,
    private val transferManager: TransferManager
) : ViewModel() {
    
    val transferRecords: StateFlow<List<TransferRecord>> = transferRepository.getRecentRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    fun clearHistory() {
        viewModelScope.launch {
            transferRepository.clearAllRecords()
        }
    }
    
    fun retryTransfer(record: TransferRecord) {
        viewModelScope.launch {
            // Reset retry count and status
            transferRepository.updateRecord(
                record.copy(
                    retryCount = 0,
                    status = com.example.phototransfer.data.local.entity.TransferStatus.PENDING
                )
            )
            
            // Retry sending the file
            val uri = Uri.parse(record.filePath)
            transferManager.sendPhoto(uri, record.fileName, record.remoteDeviceName)
        }
    }
}
