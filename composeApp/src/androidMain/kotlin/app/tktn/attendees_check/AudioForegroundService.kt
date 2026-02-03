package app.tktn.attendees_check

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.tktn.core_service.audio.AudioStreamingService
import org.koin.android.ext.android.inject

class AudioForegroundService : Service() {
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

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Streem Audio Service")
            .setContentText("Microphone is active and streaming...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
            
        // Must match manifest type="microphone"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
             startForeground(NOTIFICATION_ID, notification)
        }

        // Actually start the streaming logic
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
