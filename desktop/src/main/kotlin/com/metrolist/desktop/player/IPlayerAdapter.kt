package com.metrolist.desktop.player

import kotlinx.coroutines.flow.StateFlow

enum class PlayerState {
    UNSTARTED,
    ENDED,
    PLAYING,
    PAUSED,
    BUFFERING,
    CUED,
    ERROR
}

sealed class PlaybackEvent {
    object Ready : PlaybackEvent()
    data class StateChanged(val newState: PlayerState) : PlaybackEvent()
    data class TimeUpdated(val currentTimeMs: Long, val durationMs: Long) : PlaybackEvent()
    data class ErrorReceived(val errorCode: Int, val message: String) : PlaybackEvent()
}

/**
 * Clean player adapter interface for controlling media playback and observing state events.
 */
interface IPlayerAdapter {
    val playerState: StateFlow<PlayerState>
    val currentTimeMs: StateFlow<Long>
    val durationMs: StateFlow<Long>
    val volumePercent: StateFlow<Int> // 0 to 100
    val lastError: StateFlow<String?>

    fun loadVideo(videoId: String)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setVolume(volumePercent: Int)
    fun dispose()
}
