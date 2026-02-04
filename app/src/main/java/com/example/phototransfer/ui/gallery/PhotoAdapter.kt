package com.example.phototransfer.ui.gallery

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.phototransfer.databinding.ItemPhotoBinding

class PhotoAdapter(
    private val onPhotoClick: (Uri, Boolean) -> Unit
) : ListAdapter<Uri, PhotoAdapter.PhotoViewHolder>(PhotoDiffCallback()) {
    
    private val selectedItems = mutableSetOf<Uri>()
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PhotoViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val uri = getItem(position)
        holder.bind(uri, selectedItems.contains(uri))
    }
    
    inner class PhotoViewHolder(
        private val binding: ItemPhotoBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(uri: Uri, isSelected: Boolean) {
            binding.imageView.load(uri) {
                crossfade(true)
            }
            
            binding.imageView.alpha = if (isSelected) 0.5f else 1.0f
            
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val newSelectionState = !isSelected
                    if (newSelectionState) {
                        selectedItems.add(uri)
                    } else {
                        selectedItems.remove(uri)
                    }
                    notifyItemChanged(position)
                    onPhotoClick(uri, newSelectionState)
                }
            }
        }
    }
    
    class PhotoDiffCallback : DiffUtil.ItemCallback<Uri>() {
        override fun areItemsTheSame(oldItem: Uri, newItem: Uri) = oldItem == newItem
        override fun areContentsTheSame(oldItem: Uri, newItem: Uri) = oldItem == newItem
    }
}
