package com.metrolist.desktop.player

import javafx.application.Platform
import javafx.beans.value.ChangeListener
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import netscape.javascript.JSObject

/**
 * Concrete implementation of [IPlayerAdapter] using JavaFX/JNA native Web View on Windows Desktop.
 */
class WebView2PlayerAdapter : IPlayerAdapter {

    private val _playerState = MutableStateFlow(PlayerState.UNSTARTED)
    override val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _currentTimeMs = MutableStateFlow(0L)
    override val currentTimeMs: StateFlow<Long> = _currentTimeMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _volumePercent = MutableStateFlow(100)
    override val volumePercent: StateFlow<Int> = _volumePercent.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var webEngine: WebEngine? = null
    private var jfxPanel: JFXPanel? = null

    val isNativeWebView2Available: Boolean
        get() = WebView2RuntimeDetector.isWebView2Available()

    fun attachEngine(engine: WebEngine, panel: JFXPanel) {
        this.webEngine = engine
        this.jfxPanel = panel
    }

    override fun loadVideo(videoId: String) {
        _lastError.value = null
        Platform.runLater {
            webEngine?.executeScript("jsLoadVideoById('$videoId');")
        }
    }

    override fun play() {
        Platform.runLater {
            webEngine?.executeScript("jsPlayVideo();")
        }
    }

    override fun pause() {
        Platform.runLater {
            webEngine?.executeScript("jsPauseVideo();")
        }
    }

    override fun seekTo(positionMs: Long) {
        val seconds = positionMs / 1000.0
        Platform.runLater {
            webEngine?.executeScript("jsSeekTo($seconds);")
        }
    }

    override fun setVolume(volumePercent: Int) {
        val clamped = volumePercent.coerceIn(0, 100)
        _volumePercent.value = clamped
        Platform.runLater {
            webEngine?.executeScript("jsSetVolume($clamped);")
        }
    }

    override fun dispose() {
        Platform.runLater {
            runCatching {
                webEngine?.loadWorker?.cancel()
                webEngine = null
                jfxPanel = null
            }
        }
    }

    /**
     * JavaScript-to-Kotlin Message Receiver Bridge.
     */
    inner class NativeBridge {
        fun onEvent(msgJson: String) {
            println("[WEBVIEW2_ADAPTER_EVENT] $msgJson")
            try {
                if (msgJson.contains("\"type\":\"onReady\"")) {
                    _playerState.value = PlayerState.CUED
                }
                if (msgJson.contains("\"type\":\"onStateChange\"")) {
                    when {
                        msgJson.contains("\"stateName\":\"PLAYING\"") -> _playerState.value = PlayerState.PLAYING
                        msgJson.contains("\"stateName\":\"PAUSED\"") -> _playerState.value = PlayerState.PAUSED
                        msgJson.contains("\"stateName\":\"ENDED\"") -> _playerState.value = PlayerState.ENDED
                        msgJson.contains("\"stateName\":\"BUFFERING\"") -> _playerState.value = PlayerState.BUFFERING
                        msgJson.contains("\"stateName\":\"CUED\"") -> _playerState.value = PlayerState.CUED
                    }
                }
                if (msgJson.contains("\"type\":\"onError\"")) {
                    _playerState.value = PlayerState.ERROR
                    _lastError.value = "Playback error received from JS bridge: $msgJson"
                }

                // Parse timeUpdate
                val timeRegex = """"currentTime":([\d.]+)""".toRegex()
                val durRegex = """"duration":([\d.]+)""".toRegex()
                timeRegex.find(msgJson)?.groupValues?.get(1)?.toDoubleOrNull()?.let { sec ->
                    _currentTimeMs.value = (sec * 1000).toLong()
                }
                durRegex.find(msgJson)?.groupValues?.get(1)?.toDoubleOrNull()?.let { sec ->
                    _durationMs.value = (sec * 1000).toLong()
                }
            } catch (e: Exception) {
                println("Error parsing JS event in WebView2PlayerAdapter: ${e.message}")
            }
        }
    }
}
