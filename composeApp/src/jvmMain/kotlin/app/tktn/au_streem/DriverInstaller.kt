package app.tktn.au_streem

import co.touchlab.kermit.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.awt.Desktop

object DriverInstaller {
    private const val DRIVER_FILENAME = "VBCABLE_Setup_x64.exe"
    
    fun isInstallerAvailable(): Boolean {
        // Check if the installer is bundled in resources
        return javaClass.classLoader.getResource(DRIVER_FILENAME) != null
    }

    fun installVBCable() {
        try {
            val resourceStream = javaClass.classLoader.getResourceAsStream(DRIVER_FILENAME)
            if (resourceStream == null) {
                Logger.e("DriverInstaller") { "Driver installer not found in resources!" }
                return
            }

            // Create a temp file
            val tempFile = File.createTempFile("VBCABLE_Setup", ".exe")
            tempFile.deleteOnExit()

            // Copy resource to temp file
            Files.copy(resourceStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            
            Logger.d("DriverInstaller") { "Extracted installer to: ${tempFile.absolutePath}" }

            // Run the installer with Admin privileges (UAC will prompt)
            // On Windows, 'runas' verb is handled by ShellExecute, but ProcessBuilder doesn't support verbs easily.
            // However, most Setup.exe files have a manifest demanding Admin, so just running it works.
            
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
