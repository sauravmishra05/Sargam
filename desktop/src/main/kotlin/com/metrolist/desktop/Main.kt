package com.metrolist.desktop

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.metrolist.desktop.ui.DesktopApp
import com.metrolist.desktop.viewmodels.DesktopPlayerViewModel
import com.metrolist.innertube.NewPipeUtils
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeLocale
import java.util.Locale

fun main() {
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        System.err.println("Uncaught exception in thread ${thread.name}: ${throwable.message}")
        throwable.printStackTrace()
    }

    if (System.getProperty("skiko.renderApi").isNullOrBlank()) {
        System.setProperty("skiko.renderApi", "DIRECT3D,SOFTWARE")
    }

    val locale = Locale.getDefault()
    val country = locale.country.takeIf { it.isNotBlank() } ?: "US"
    val language = locale.language.takeIf { it.isNotBlank() } ?: "en"
    YouTube.locale = YouTubeLocale(gl = country, hl = language)

    // Initialize NewPipeExtractor using the project's existing NewPipeDownloaderImpl
    NewPipeUtils

    application {
        val windowState = rememberWindowState(width = 1200.dp, height = 800.dp)
        val viewModel = DesktopPlayerViewModel()

        Window(
            onCloseRequest = ::exitApplication,
            title = "Sargam",
            icon = painterResource("ic_launcher-playstore.png"),
            state = windowState
        ) {
            DesktopApp(viewModel = viewModel)
        }
    }
}
