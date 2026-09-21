package com.metrolist.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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

@Composable
fun HomeScreen(
    viewModel: DesktopPlayerViewModel,
    modifier: Modifier = Modifier
) {
    val homePage by viewModel.homePage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isError by viewModel.isError.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val sections = homePage?.sections ?: emptyList()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Category Pills Header
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryPill("All", isSelected = true)
                CategoryPill("Music", isSelected = false)
                CategoryPill("Podcasts", isSelected = false)
            }
        }

        // Top Quick Banner Tiles (Liked Songs, Favorites, etc.)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickBannerCard("Liked Songs", "💙", modifier = Modifier.weight(1f))
                QuickBannerCard("Bhagwan ji", "🙏", modifier = Modifier.weight(1f))
                QuickBannerCard("Bollywood Mix", "🎬", modifier = Modifier.weight(1f))
                QuickBannerCard("Top Hits", "🔥", modifier = Modifier.weight(1f))
            }
        }

        if (isLoading && homePage == null) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Color(0xFF1DB954))
                    Spacer(Modifier.height(16.dp))
                    Text("Loading Sargam Catalog…", color = Color.White)
                }
            }
        } else if ((isError || sections.isEmpty()) && homePage == null) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF181818))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("📡", style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Unable to connect to online music catalog",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "Please check your internet connection and try again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB3B3B3)
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { viewModel.loadHome() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1DB954),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Retry Connection", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            items(
                sections,
                key = { section -> "desktop_section_${section.title}" },
                contentType = { "desktop_section" }
            ) { section ->
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = section.title ?: "Recommended",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Text(
                            text = "Show all",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB3B3B3),
                            modifier = Modifier.clickable { }
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(
                            section.items,
                            key = { item -> "desktop_home_card_${item.id}" },
                            contentType = { "desktop_home_card" }
                        ) { item ->
                            SpotifyItemCard(item = item, viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryPill(text: String, isSelected: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) Color.White else Color(0xFF282828))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) Color.Black else Color.White
        )
    }
}

@Composable
fun QuickBannerCard(title: String, icon: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF282828))
            .clickable { }
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(Color(0xFF3E3E3E)),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun YTItemCard(
    item: YTItem,
    viewModel: DesktopPlayerViewModel
) {
    SpotifyItemCard(item = item, viewModel = viewModel)
}

@Composable
fun SpotifyItemCard(
    item: YTItem,
    viewModel: DesktopPlayerViewModel
) {
    Column(
        modifier = Modifier
            .width(160.dp)
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
            .padding(12.dp)
    ) {
        AsyncImage(
            model = item.thumbnail,
            contentDescription = item.title,
            modifier = Modifier
                .size(136.dp)
                .clip(RoundedCornerShape(6.dp))
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(4.dp))

        val subtitle = when (item) {
            is SongItem -> item.artists.joinToString(", ") { it.name }
            is AlbumItem -> item.artists?.joinToString(", ") { it.name } ?: "Album"
            is ArtistItem -> "Artist"
            is PlaylistItem -> item.author?.name ?: "Playlist"
            else -> ""
        }

        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB3B3B3),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
