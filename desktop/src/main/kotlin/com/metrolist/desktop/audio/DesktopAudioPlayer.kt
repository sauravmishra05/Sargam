package com.metrolist.desktop.audio

import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DesktopAudioPlayer(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var positionJob: Job? = null

    init {
        runCatching {
            JFXPanel()
        }
    }

    fun play(url: String, durationMs: Long = 0L) {
        stop()
        _durationMs.value = durationMs

        Platform.runLater {
            try {
                println("Initializing JavaFX MediaPlayer for URL: ${url.take(100)}...")
                val media = Media(url)
                val player = MediaPlayer(media).apply {
                    volume = _volume.value.toDouble()
                    setOnPlaying {
                        println("MediaPlayer state: PLAYING")
                        _isPlaying.value = true
                    }
                    setOnPaused {
                        println("MediaPlayer state: PAUSED")
                        _isPlaying.value = false
                    }
                    setOnStopped {
                        println("MediaPlayer state: STOPPED")
                        _isPlaying.value = false
                    }
                    setOnEndOfMedia {
                        println("MediaPlayer state: END_OF_MEDIA")
                        _isPlaying.value = false
                        _currentPositionMs.value = 0L
                    }
                    setOnError {
                        println("MediaPlayer error: ${error?.message}")
                        _isPlaying.value = false
                    }
                    currentTimeProperty().addListener { _, _, newValue ->
                        _currentPositionMs.value = newValue.toMillis().toLong()
                    }
                    setOnReady {
                        println("MediaPlayer status: READY. Media duration: ${media.duration}")
                        if (media.duration != null && !media.duration.isUnknown) {
                            _durationMs.value = media.duration.toMillis().toLong()
                        }
                        play()
                        _isPlaying.value = true
                    }
                }
                mediaPlayer = player
                startPositionTracker()
            } catch (e: Exception) {
                println("MediaPlayer init error: ${e.message}")
                e.printStackTrace()
                _isPlaying.value = false
            }
        }
    }

    fun pause() {
        Platform.runLater {
            mediaPlayer?.pause()
            _isPlaying.value = false
        }
    }

    fun resume() {
        Platform.runLater {
            mediaPlayer?.play()
            _isPlaying.value = true
        }
    }

    fun stop() {
        _isPlaying.value = false
        positionJob?.cancel()
        Platform.runLater {
            runCatching {
                mediaPlayer?.stop()
                mediaPlayer?.dispose()
            }
            mediaPlayer = null
        }
        _currentPositionMs.value = 0L
    }

    fun seekTo(positionMs: Long) {
        _currentPositionMs.value = positionMs
        Platform.runLater {
            mediaPlayer?.seek(Duration.millis(positionMs.toDouble()))
        }
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0.0f, 1.0f)
        _volume.value = clamped
        Platform.runLater {
            mediaPlayer?.volume = clamped.toDouble()
        }
    }

    private fun startPositionTracker() {
        positionJob?.cancel()
        positionJob = scope.launch(Dispatchers.IO) {
            while (true) {
                delay(250)
                mediaPlayer?.let { player ->
                    withContext(Dispatchers.Main) {
                        if (player.status == MediaPlayer.Status.PLAYING) {
                            _isPlaying.value = true
                            _currentPositionMs.value = player.currentTime.toMillis().toLong()
                        }
                    }
                }
            }
        }
    }
}
