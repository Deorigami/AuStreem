package app.tktn.core_service.utils

import co.touchlab.kermit.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.awt.Desktop

object DriverInstaller {
    private const val DRIVER_FILENAME = "VBCABLE_Setup_x64.exe"
    
    fun isInstallerAvailable(): Boolean {
        return javaClass.classLoader.getResource(DRIVER_FILENAME) != null
    }

    fun installVBCable() {
        try {
            val resourceStream = javaClass.classLoader.getResourceAsStream(DRIVER_FILENAME)
            if (resourceStream == null) return

            val tempFile = File.createTempFile("VBCABLE_Setup", ".exe")
            tempFile.deleteOnExit()

            Files.copy(resourceStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(tempFile)
            } else {
                 ProcessBuilder(tempFile.absolutePath).start()
            }
        } catch (e: Exception) {
            Logger.e("DriverInstaller", e) { "Failed to run installer" }
        }
    }
}

actual object DriverUtils {
    actual fun isInstallerAvailable(): Boolean = DriverInstaller.isInstallerAvailable()
    actual fun installDriver() = DriverInstaller.installVBCable()
}
