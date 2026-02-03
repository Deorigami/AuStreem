package app.tktn.core_service.audio

/**
 * Audio configuration for streaming.
 * Using PCM 16-bit mono at 44.1kHz for good quality and compatibility.
 */
object AudioConfig {
    const val SAMPLE_RATE = 48000
    const val CHANNEL_COUNT = 1 // Mono
    const val BITS_PER_SAMPLE = 16
    const val BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8
    
    // Buffer size in bytes - 50ms of audio at a time for low latency but stability
    const val BUFFER_SIZE_MS = 50
    val BUFFER_SIZE_BYTES = (SAMPLE_RATE * CHANNEL_COUNT * BYTES_PER_SAMPLE * BUFFER_SIZE_MS) / 1000
}
