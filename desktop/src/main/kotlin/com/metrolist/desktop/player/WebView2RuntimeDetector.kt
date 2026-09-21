package com.metrolist.desktop.player

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.io.File

/**
 * Windows native WebView2 Evergreen runtime detector using JNA.
 */
object WebView2RuntimeDetector {
    private const val WEBVIEW2_REG_KEY =
        """SOFTWARE\WOW6432Node\Microsoft\EdgeUpdate\Clients\{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}"""

    fun isWebView2Available(): Boolean {
        val osName = System.getProperty("os.name").lowercase()
        if (!osName.contains("win")) return false

        // Check Windows Registry for installed WebView2 Evergreen Runtime
        return try {
            val is64BitInstalled = Advapi32Util.registryKeyExists(
                WinReg.HKEY_LOCAL_MACHINE,
                WEBVIEW2_REG_KEY
            )
            val isUserInstalled = Advapi32Util.registryKeyExists(
                WinReg.HKEY_CURRENT_USER,
                WEBVIEW2_REG_KEY
            )
            val loaderSystemFile = File("C:\\Windows\\System32\\WebView2Loader.dll").exists()

            is64BitInstalled || isUserInstalled || loaderSystemFile
        } catch (e: Exception) {
            // Fallback check System32 DLL presence
            File("C:\\Windows\\System32\\WebView2Loader.dll").exists()
        }
    }
}
