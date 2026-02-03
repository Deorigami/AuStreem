package app.tktn.core_service.audio

actual object PlatformAudioHook {
    actual fun startService(action: () -> Unit) {
        // Desktop doesn't need special background handling, just run the action
        action()
    }
    
    actual fun stopService(action: () -> Unit) {
        action()
    }
}
