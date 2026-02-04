package com.example.phototransfer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.phototransfer.R
import com.example.phototransfer.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TransferService : Service() {
    
    @Inject
    lateinit var transferManager: TransferManager
    
    @Inject
    lateinit var connectionManager: ConnectionManager
    
    private val binder = TransferServiceBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    
    private val notificationId = 1001
    private val channelId = "transfer_channel"
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(notificationId, createNotification("Ready to transfer"))
        
        // Monitor transfer progress
        serviceScope.launch {
            transferManager.transferProgress.collectLatest { progress ->
                when (progress) {
                    is TransferProgress.Sending -> {
                        updateNotification("Sending: ${progress.fileName} (${progress.progress}%)")
                    }
                    is TransferProgress.Receiving -> {
                        updateNotification("Receiving: ${progress.fileName} (${progress.progress}%)")
                    }
                    is TransferProgress.Retrying -> {
                        updateNotification("Retrying: ${progress.fileName} (${progress.retryCount}/3)")
                    }
                    is TransferProgress.Success -> {
                        updateNotification("Transfer complete: ${progress.fileName}")
                    }
                    is TransferProgress.Failed -> {
                        updateNotification("Transfer failed: ${progress.error}")
                    }
                    else -> {}
                }
            }
        }
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.launch {
            connectionManager.disconnect()
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Photo Transfer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for photo transfer progress"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(contentText: String) =
        NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.transfer_service_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(createPendingIntent())
            .build()
    
    private fun updateNotification(contentText: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(notificationId, createNotification(contentText))
    }
    
    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
    
    inner class TransferServiceBinder : Binder() {
        fun getService(): TransferService = this@TransferService
    }
}
