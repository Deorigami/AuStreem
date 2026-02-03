package app.tktn.core_service.audio

expect object PlatformAudioHook {
    /**
     * Start the audio service in a way that persists in background (Foreground Service on Android).
     */
    fun startService(action: () -> Unit)
    
    /**
     * Stop the persistent service.
     */
    fun stopService(action: () -> Unit)
}
