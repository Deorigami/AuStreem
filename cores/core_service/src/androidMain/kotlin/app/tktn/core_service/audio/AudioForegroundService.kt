package app.tktn.core_service.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AudioForegroundService : Service(), KoinComponent {
    private val audioService: AudioStreamingService by inject()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP") {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            audioService.stopStreaming()
            return START_NOT_STICKY
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, CHANNEL_ID)
        } else {
            android.app.Notification.Builder(this)
        }

        val notification = builder
            .setContentTitle("Streem Audio Service")
            .setContentText("Microphone is active and streaming...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(android.app.Notification.PRIORITY_LOW)
            .build()
            
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
             startForeground(NOTIFICATION_ID, notification)
        }

        audioService.startStreaming()

        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        audioService.stopStreaming()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Audio Streaming Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    companion object {
        const val CHANNEL_ID = "AudioServiceChannel"
        const val NOTIFICATION_ID = 101
    }
}
