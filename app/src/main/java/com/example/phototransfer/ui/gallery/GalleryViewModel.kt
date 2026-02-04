package com.example.phototransfer.ui.gallery

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.phototransfer.data.repository.PhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val photoRepository: PhotoRepository
) : ViewModel() {
    
    private val _photos = MutableStateFlow<List<Uri>>(emptyList())
    val photos: StateFlow<List<Uri>> = _photos
    
    private val _selectedPhotos = MutableStateFlow<Set<Uri>>(emptySet())
    val selectedPhotos: StateFlow<Set<Uri>> = _selectedPhotos
    
    fun loadPhotos() {
        viewModelScope.launch {
            _photos.value = photoRepository.getPhotosFromGallery()
        }
    }
    
    fun togglePhotoSelection(uri: Uri) {
        val currentSelection = _selectedPhotos.value.toMutableSet()
        if (currentSelection.contains(uri)) {
            currentSelection.remove(uri)
        } else {
            currentSelection.add(uri)
        }
        _selectedPhotos.value = currentSelection
    }
    
    fun clearSelection() {
        _selectedPhotos.value = emptySet()
    }
}
