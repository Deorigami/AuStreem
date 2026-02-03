package app.tktn.core_service.utils

actual object DriverUtils {
    actual fun isInstallerAvailable(): Boolean = false
    actual fun installDriver() {
        // No-op on Android
    }
}
