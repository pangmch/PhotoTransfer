package com.example.phototransfer.ui.gallery

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.phototransfer.R
import com.example.phototransfer.databinding.FragmentGalleryBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GalleryFragment : Fragment() {
    
    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: GalleryViewModel by viewModels()
    private lateinit var photoAdapter: PhotoAdapter
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.loadPhotos()
        } else {
            Toast.makeText(requireContext(), "Storage permission is required", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        
        if (hasStoragePermission()) {
            viewModel.loadPhotos()
        } else {
            requestStoragePermission()
        }
    }
    
    private fun setupRecyclerView() {
        photoAdapter = PhotoAdapter { uri, isSelected ->
            viewModel.togglePhotoSelection(uri)
        }
        
        binding.recyclerView.apply {
            layoutManager = GridLayoutManager(context, 3)
            adapter = photoAdapter
        }
    }
    
    private fun setupClickListeners() {
        binding.fabSend.setOnClickListener {
            findNavController().navigate(R.id.action_gallery_to_transfer)
        }
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.photos.collect { photos ->
                if (photos.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    binding.recyclerView.visibility = View.VISIBLE
                    photoAdapter.submitList(photos)
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedPhotos.collect { selected ->
                binding.fabSend.visibility = if (selected.isNotEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
    
    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10-12
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 9 and below
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun requestStoragePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            Manifest.permission.READ_MEDIA_IMAGES
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10-12
            Manifest.permission.READ_EXTERNAL_STORAGE
        } else {
            // Android 9 and below
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
        permissionLauncher.launch(permission)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
