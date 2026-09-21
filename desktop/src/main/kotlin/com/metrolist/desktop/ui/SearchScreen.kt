package com.metrolist.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.desktop.viewmodels.DesktopPlayerViewModel
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.pages.SearchSummary

@Composable
fun SearchScreen(
    viewModel: DesktopPlayerViewModel,
    modifier: Modifier = Modifier
) {
    val searchSummaryPage by viewModel.searchSummaryPage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val query by viewModel.searchQuery.collectAsState()

    if (query.isBlank()) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🔍", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(12.dp))
            Text("Type a query above to search songs, albums, artists, playlists…", color = Color(0xFFB3B3B3))
        }
        return
    }

    if (isLoading) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = Color(0xFF1DB954))
            Spacer(Modifier.height(16.dp))
            Text("Searching Sargam catalog for '$query'…", color = Color.White)
        }
        return
    }

    val summaries: List<SearchSummary> = searchSummaryPage?.summaries ?: emptyList()

    if (summaries.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("No results found for '$query'", color = Color(0xFFB3B3B3))
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Search results for '$query'",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        summaries.forEach { summary ->
            item(key = "desktop_summary_title_${summary.title}", contentType = "navigation_title") {
                Text(
                    text = summary.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1DB954),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(
                summary.items,
                key = { item -> "desktop_search_${summary.title}_${item.id}" },
                contentType = { "desktop_search_item" }
            ) { item ->
                SearchResultRow(item = item, viewModel = viewModel)
            }
        }
    }
}

@Composable
fun SearchResultRow(
    item: YTItem,
    viewModel: DesktopPlayerViewModel
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF181818))
            .clickable {
                when (item) {
                    is SongItem -> viewModel.playSong(item)
                    is AlbumItem -> viewModel.openAlbum(item.browseId)
                    is ArtistItem -> viewModel.openArtist(item.id)
                    is PlaylistItem -> viewModel.openPlaylist(item.id)
                    else -> {}
                }
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.thumbnail,
            contentDescription = item.title,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(6.dp))
        )

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val subtitle = when (item) {
                is SongItem -> "Song • " + item.artists.joinToString(", ") { it.name }
                is AlbumItem -> "Album • " + (item.artists?.joinToString(", ") { it.name } ?: "")
                is ArtistItem -> "Artist"
                is PlaylistItem -> "Playlist • " + (item.author?.name ?: "")
                else -> ""
            }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB3B3B3),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
