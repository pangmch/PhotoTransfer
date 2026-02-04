package com.example.phototransfer.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.phototransfer.R
import com.example.phototransfer.data.local.entity.TransferDirection
import com.example.phototransfer.data.local.entity.TransferRecord
import com.example.phototransfer.data.local.entity.TransferStatus
import com.example.phototransfer.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(
    private val onItemClick: (TransferRecord) -> Unit
) : ListAdapter<TransferRecord, HistoryAdapter.HistoryViewHolder>(HistoryDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HistoryViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class HistoryViewHolder(
        private val binding: ItemHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(record: TransferRecord) {
            val context = binding.root.context
            
            binding.tvFileName.text = record.fileName
            
            binding.tvDirection.text = when (record.direction) {
                TransferDirection.SEND -> context.getString(R.string.sent_to, record.remoteDeviceName)
                TransferDirection.RECEIVE -> context.getString(R.string.received_from, record.remoteDeviceName)
            }
            
            binding.tvStatus.text = when (record.status) {
                TransferStatus.PENDING -> context.getString(R.string.status_pending)
                TransferStatus.IN_PROGRESS -> context.getString(R.string.status_in_progress)
                TransferStatus.SUCCESS -> context.getString(R.string.status_success)
                TransferStatus.FAILED -> "${context.getString(R.string.status_failed)} (${record.retryCount}/3)"
            }
            
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            binding.tvTimestamp.text = dateFormat.format(Date(record.timestamp))
            
            // Change status color based on status
            val statusColor = when (record.status) {
                TransferStatus.SUCCESS -> android.graphics.Color.GREEN
                TransferStatus.FAILED -> android.graphics.Color.RED
                TransferStatus.IN_PROGRESS -> android.graphics.Color.BLUE
                else -> android.graphics.Color.GRAY
            }
            binding.tvStatus.setTextColor(statusColor)
            
            binding.root.setOnClickListener {
                if (record.status == TransferStatus.FAILED) {
                    onItemClick(record)
                }
            }
        }
    }
    
    class HistoryDiffCallback : DiffUtil.ItemCallback<TransferRecord>() {
        override fun areItemsTheSame(oldItem: TransferRecord, newItem: TransferRecord) =
            oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: TransferRecord, newItem: TransferRecord) =
            oldItem == newItem
    }
}
