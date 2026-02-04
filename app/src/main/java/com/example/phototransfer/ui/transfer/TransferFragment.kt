package com.example.phototransfer.ui.transfer

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.phototransfer.R
import com.example.phototransfer.databinding.FragmentTransferBinding
import com.example.phototransfer.service.ConnectionState
import com.example.phototransfer.service.TransferProgress
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TransferFragment : Fragment() {
    
    private var _binding: FragmentTransferBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: TransferViewModel by viewModels()
    
    private lateinit var deviceAdapter: DeviceAdapter

    private var selectedPhotoUri: Uri? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            startTransferMode()
        } else {
            Toast.makeText(requireContext(), "Permissions required for transfer", Toast.LENGTH_SHORT).show()
        }
    }
    
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(requireContext(), "Storage permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Storage permission denied - cannot save received files", Toast.LENGTH_LONG).show()
        }
    }

    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedPhotoUri = it
            binding.btnSelectPhoto.text = getString(R.string.photo_selected)
            binding.btnSendPhoto.isEnabled = true
            Toast.makeText(requireContext(), getString(R.string.photo_selected), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransferBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        
        if (hasRequiredPermissions()) {
            startTransferMode()
        } else {
            requestPermissions()
        }
    }
    
    private fun setupRecyclerView() {
        deviceAdapter = DeviceAdapter { device ->
            viewModel.connectToDevice(device.endpointId)
            Toast.makeText(requireContext(), getString(R.string.connecting), Toast.LENGTH_SHORT).show()
        }
        binding.recyclerViewDevices.adapter = deviceAdapter
    }

    private fun setupClickListeners() {
        binding.btnStartAdvertising.setOnClickListener {
            viewModel.startAdvertising()
        }
        
        binding.btnStartDiscovery.setOnClickListener {
            viewModel.startDiscovery()
        }

        binding.btnSelectPhoto.setOnClickListener {
            photoPickerLauncher.launch("image/*")
        }

        binding.btnSendPhoto.setOnClickListener {
            selectedPhotoUri?.let { uri ->
                val fileName = "photo_${System.currentTimeMillis()}.jpg"
                viewModel.sendPhoto(uri, fileName)
                binding.btnSendPhoto.isEnabled = false
            } ?: run {
                Toast.makeText(requireContext(), getString(R.string.select_photo_first), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnDisconnect.setOnClickListener {
            viewModel.disconnect()
            resetPhotoSelection()
            Toast.makeText(requireContext(), getString(R.string.disconnected), Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetPhotoSelection() {
        selectedPhotoUri = null
        binding.btnSelectPhoto.text = getString(R.string.select_photo)
        binding.btnSendPhoto.isEnabled = false
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.connectionState.collect { state ->
                when (state) {
                    is ConnectionState.Idle -> {
                        binding.tvStatus.text = getString(R.string.status_disconnected)
                        updateUIForConnectionState(false)
                    }
                    is ConnectionState.Advertising -> {
                        binding.tvStatus.text = getString(R.string.status_advertising)
                    }
                    is ConnectionState.Discovering -> {
                        binding.tvStatus.text = getString(R.string.status_discovering)
                    }
                    is ConnectionState.AdvertisingAndDiscovering -> {
                        binding.tvStatus.text = getString(R.string.status_searching)
                    }
                    is ConnectionState.Connected -> {
                        binding.tvStatus.text = getString(R.string.status_connected)
                        binding.tvDeviceName.text = getString(R.string.device_name, state.endpointId)
                        updateUIForConnectionState(true)
                        Toast.makeText(requireContext(), getString(R.string.connected), Toast.LENGTH_SHORT).show()
                        // Request storage permission when connected to ensure we can save received files
                        checkAndRequestStoragePermission()
                    }
                    is ConnectionState.Error -> {
                        binding.tvStatus.text = getString(R.string.status_error, state.message)
                        updateUIForConnectionState(false)
                    }
                }
            }
        }
        
        // Observe discovered devices list
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.discoveredDevices.collect { devices ->
                deviceAdapter.submitList(devices)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.transferProgress.collect { progress ->
                when (progress) {
                    is TransferProgress.Sending -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.tvProgress.visibility = View.VISIBLE
                        binding.progressBar.progress = progress.progress
                        binding.tvProgress.text = getString(R.string.sending_progress, progress.fileName, progress.progress)
                        binding.btnSendPhoto.isEnabled = false
                    }
                    is TransferProgress.Receiving -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.tvProgress.visibility = View.VISIBLE
                        binding.progressBar.progress = progress.progress
                        binding.tvProgress.text = getString(R.string.receiving_progress, progress.fileName, progress.progress)
                    }
                    is TransferProgress.Success -> {
                        binding.progressBar.visibility = View.GONE
                        binding.tvProgress.visibility = View.GONE
                        Toast.makeText(requireContext(), "Transfer complete: ${progress.fileName}", Toast.LENGTH_SHORT).show()
                        resetPhotoSelection()
                    }
                    is TransferProgress.Failed -> {
                        binding.progressBar.visibility = View.GONE
                        binding.tvProgress.visibility = View.GONE
                        Toast.makeText(requireContext(), "Transfer failed: ${progress.error}", Toast.LENGTH_SHORT).show()
                        binding.btnSendPhoto.isEnabled = selectedPhotoUri != null
                    }
                    else -> {
                        binding.progressBar.visibility = View.GONE
                        binding.tvProgress.visibility = View.GONE
                    }
                }
            }
        }
    }
    
    private fun updateUIForConnectionState(isConnected: Boolean) {
        binding.layoutDiscoveryButtons.isVisible = !isConnected
        binding.recyclerViewDevices.isVisible = !isConnected
        binding.layoutTransferButtons.isVisible = isConnected

        if (isConnected) {
            binding.btnSelectPhoto.isEnabled = true
        } else {
            binding.btnSelectPhoto.isEnabled = false
            binding.btnSendPhoto.isEnabled = false
            resetPhotoSelection()
        }
    }

    private fun startTransferMode() {
        // Start both advertising and discovery for bidirectional mode
        viewModel.startAdvertising()
        viewModel.startDiscovery()
    }
    
    private fun hasRequiredPermissions(): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun requestPermissions() {
        permissionLauncher.launch(getRequiredPermissions())
    }
    
    private fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+)
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_MEDIA_IMAGES
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 (API 31-32)
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10-11 (API 29-30)
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        } else {
            // Android 9 and below (API ≤28)
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
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

    private fun checkAndRequestStoragePermission() {
        if (!hasStoragePermission()) {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Manifest.permission.READ_EXTERNAL_STORAGE
            } else {
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            }
            storagePermissionLauncher.launch(permission)
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
