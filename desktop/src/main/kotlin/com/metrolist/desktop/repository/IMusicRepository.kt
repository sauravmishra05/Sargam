package com.metrolist.desktop.repository

import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.AlbumPage
import com.metrolist.innertube.pages.ArtistPage
import com.metrolist.innertube.pages.HomePage
import com.metrolist.innertube.pages.PlaylistPage
import com.metrolist.innertube.pages.SearchSummaryPage

/**
 * Clean domain repository interface for Sargam Desktop music catalog and metadata.
 */
interface IMusicRepository {
    suspend fun getHomeCatalog(): Result<HomePage>
    suspend fun searchSummary(query: String): Result<SearchSummaryPage>
    suspend fun getAlbumDetails(browseId: String): Result<AlbumPage>
    suspend fun getArtistDetails(artistId: String): Result<ArtistPage>
    suspend fun getPlaylistDetails(playlistId: String): Result<PlaylistPage>
    suspend fun getVideoMetadata(videoId: String): Result<SongItem>
}
