package app.tktn.core_service.audio

import android.content.Context
import android.content.Intent
import android.os.Build
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

actual object PlatformAudioHook : KoinComponent {
    private val context: Context by inject()

    actual fun startService(action: () -> Unit) {
        val intent = Intent(context, AudioForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        // action() is called by the Service itself now!
        // or we can call it here too? No, service calls it to ensure lifecycle.
    }
    
    actual fun stopService(action: () -> Unit) {
        val intent = Intent(context, AudioForegroundService::class.java)
        intent.action = "STOP"
        context.startService(intent)
    }
}
