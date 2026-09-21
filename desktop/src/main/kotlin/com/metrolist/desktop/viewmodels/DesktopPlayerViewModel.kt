package com.metrolist.desktop.viewmodels

import com.metrolist.desktop.audio.DesktopAudioPlayer
import com.metrolist.desktop.audio.DesktopAudioStreamProxy
import com.metrolist.innertube.NewPipeExtractor
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.pages.AlbumPage
import com.metrolist.innertube.pages.ArtistPage
import com.metrolist.innertube.pages.HomePage
import com.metrolist.innertube.pages.PlaylistPage
import com.metrolist.innertube.pages.SearchSummaryPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DesktopNavSection {
    HOME,
    SEARCH,
    ALBUM_DETAIL,
    ARTIST_DETAIL,
    PLAYLIST_DETAIL
}

class DesktopPlayerViewModel(
    val audioPlayer: DesktopAudioPlayer = DesktopAudioPlayer()
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _currentSection = MutableStateFlow(DesktopNavSection.HOME)
    val currentSection: StateFlow<DesktopNavSection> = _currentSection.asStateFlow()

    private val _homePage = MutableStateFlow<HomePage?>(null)
    val homePage: StateFlow<HomePage?> = _homePage.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchSummaryPage = MutableStateFlow<SearchSummaryPage?>(null)
    val searchSummaryPage: StateFlow<SearchSummaryPage?> = _searchSummaryPage.asStateFlow()

    private val _currentAlbumPage = MutableStateFlow<AlbumPage?>(null)
    val currentAlbumPage: StateFlow<AlbumPage?> = _currentAlbumPage.asStateFlow()

    private val _currentArtistPage = MutableStateFlow<ArtistPage?>(null)
    val currentArtistPage: StateFlow<ArtistPage?> = _currentArtistPage.asStateFlow()

    private val _currentPlaylistPage = MutableStateFlow<PlaylistPage?>(null)
    val currentPlaylistPage: StateFlow<PlaylistPage?> = _currentPlaylistPage.asStateFlow()

    private val _currentSong = MutableStateFlow<SongItem?>(null)
    val currentSong: StateFlow<SongItem?> = _currentSong.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isError = MutableStateFlow(false)
    val isError: StateFlow<Boolean> = _isError.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val albumCache = java.util.concurrent.ConcurrentHashMap<String, AlbumPage>()
    private val artistCache = java.util.concurrent.ConcurrentHashMap<String, ArtistPage>()
    private val searchCache = java.util.concurrent.ConcurrentHashMap<String, SearchSummaryPage>()

    init {
        loadHome()
    }

    fun navigateTo(section: DesktopNavSection) {
        _currentSection.value = section
    }

    fun loadHome(forceRefresh: Boolean = false) {
        if (_homePage.value != null && !forceRefresh) return
        scope.launch {
            _isLoading.value = _homePage.value == null
            _isError.value = false
            _errorMessage.value = null

            if (YouTube.visitorData == null) {
                runCatching {
                    YouTube.visitorData().onSuccess {
                        YouTube.visitorData = it
                    }.onFailure {
                        YouTube.visitorData = "CgtnUkZtdGRaUTBfTSi7i9e2BjIKCgJVUxIEGgAgGA%3D%3D"
                    }
                }
            }
            YouTube.home().onSuccess { page ->
                _homePage.value = page
                _isError.value = false
                _errorMessage.value = null
            }.onFailure { e ->
                println("Failed to load home catalog: ${e.message}")
                if (_homePage.value == null) {
                    _isError.value = true
                    _errorMessage.value = e.message ?: "Unable to connect to Sargam servers."
                }
            }
            _isLoading.value = false
        }
    }

    fun performSearch(query: String) {
        if (query.isBlank()) return
        _searchQuery.value = query
        _currentSection.value = DesktopNavSection.SEARCH

        val cached = searchCache[query]
        if (cached != null) {
            _searchSummaryPage.value = cached
            _isLoading.value = false
            return
        }

        scope.launch {
            _isLoading.value = true
            YouTube.searchSummary(query).onSuccess { page ->
                searchCache[query] = page
                _searchSummaryPage.value = page
            }.onFailure { e ->
                println("Failed to search query: $query, error: ${e.message}")
            }
            _isLoading.value = false
        }
    }

    fun openAlbum(browseId: String) {
        _currentSection.value = DesktopNavSection.ALBUM_DETAIL

        val cached = albumCache[browseId]
        if (cached != null) {
            _currentAlbumPage.value = cached
            _isLoading.value = false
            return
        }

        scope.launch {
            _isLoading.value = true
            YouTube.album(browseId).onSuccess { page ->
                albumCache[browseId] = page
                _currentAlbumPage.value = page
            }.onFailure { e ->
                println("Failed to load album: $browseId, error: ${e.message}")
            }
            _isLoading.value = false
        }
    }

    fun openArtist(artistId: String) {
        _currentSection.value = DesktopNavSection.ARTIST_DETAIL

        val cached = artistCache[artistId]
        if (cached != null) {
            _currentArtistPage.value = cached
            _isLoading.value = false
            return
        }

        scope.launch {
            _isLoading.value = true
            YouTube.artist(artistId).onSuccess { page ->
                artistCache[artistId] = page
                _currentArtistPage.value = page
            }.onFailure { e ->
                println("Failed to load artist: $artistId, error: ${e.message}")
            }
            _isLoading.value = false
        }
    }

    fun openPlaylist(playlistId: String) {
        _currentSection.value = DesktopNavSection.PLAYLIST_DETAIL
        scope.launch {
            _isLoading.value = true
            YouTube.playlist(playlistId).onSuccess { page ->
                _currentPlaylistPage.value = page
            }.onFailure { e ->
                println("Failed to load playlist: $playlistId, error: ${e.message}")
            }
            _isLoading.value = false
        }
    }

    fun playSong(song: SongItem) {
        _currentSong.value = song
        scope.launch {
            println("Resolving stream for song: ${song.title} (${song.id})")
            val clients = listOf(
                YouTubeClient.VISIONOS,
                YouTubeClient.ANDROID_VR_1_65_10,
                YouTubeClient.TVHTML5,
                YouTubeClient.WEB_REMIX
            )

            var resolvedUrl: String? = null
            var resolvedDurationMs: Long = (song.duration ?: 0) * 1000L

            for (client in clients) {
                if (resolvedUrl != null) break
                YouTube.player(videoId = song.id, client = client).onSuccess { playerResponse ->
                    if (resolvedDurationMs == 0L) {
                        val durSec = playerResponse.videoDetails?.lengthSeconds?.toLongOrNull() ?: 0L
                        resolvedDurationMs = durSec * 1000L
                    }

                    val streamingData = playerResponse.streamingData
                    val adaptiveFormats = streamingData?.adaptiveFormats.orEmpty()
                    val progressiveFormats = streamingData?.formats.orEmpty()

                    // Try audio-only mp4/m4a first
                    val audioFormat = adaptiveFormats
                        .filter { it.isAudio && (it.mimeType.contains("mp4", ignoreCase = true) || it.mimeType.contains("m4a", ignoreCase = true)) }
                        .maxByOrNull { it.bitrate ?: 0 }

                    // Or progressive format (itag 18 / 22 MP4)
                    val progFormat = progressiveFormats
                        .firstOrNull { !it.url.isNullOrEmpty() }

                    val chosenFormat = audioFormat ?: progFormat ?: adaptiveFormats.firstOrNull { it.isAudio }

                    if (chosenFormat != null) {
                        resolvedUrl = chosenFormat.url ?: NewPipeExtractor.getStreamUrl(chosenFormat, song.id)
                    }
                }.onFailure { e ->
                    println("Client ${client.clientName} failed for ${song.id}: ${e.message}")
                }
            }

            if (resolvedUrl == null) {
                // Fallback to NewPipeExtractor
                runCatching {
                    val newPipeStreams = NewPipeExtractor.newPipePlayer(song.id)
                    resolvedUrl = newPipeStreams.firstOrNull()?.second
                }
            }

            if (resolvedUrl != null) {
                val proxiedUrl = DesktopAudioStreamProxy.getProxiedUrl(resolvedUrl!!)
                println("SUCCESS: Resolved stream URL for ${song.title}! Proxied URL: $proxiedUrl, DurationMs: $resolvedDurationMs")
                withContext(Dispatchers.Main) {
                    audioPlayer.play(proxiedUrl, resolvedDurationMs)
                }
            } else {
                println("FAILURE: Could not resolve stream URL for ${song.title} (${song.id})")
            }
        }
    }
}
