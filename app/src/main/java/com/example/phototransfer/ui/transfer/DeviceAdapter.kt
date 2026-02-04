package com.example.phototransfer.ui.transfer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.phototransfer.databinding.ItemDeviceBinding

class DeviceAdapter(
    private val onConnectClick: (DiscoveredDevice) -> Unit
) : ListAdapter<DiscoveredDevice, DeviceAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DeviceViewHolder(
        private val binding: ItemDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: DiscoveredDevice) {
            binding.tvDeviceName.text = device.deviceName
            binding.tvEndpointId.text = device.endpointId

            binding.btnConnect.setOnClickListener {
                onConnectClick(device)
            }
        }
    }

    class DeviceDiffCallback : DiffUtil.ItemCallback<DiscoveredDevice>() {
        override fun areItemsTheSame(oldItem: DiscoveredDevice, newItem: DiscoveredDevice): Boolean {
            return oldItem.endpointId == newItem.endpointId
        }

        override fun areContentsTheSame(oldItem: DiscoveredDevice, newItem: DiscoveredDevice): Boolean {
            return oldItem == newItem
        }
    }
}
