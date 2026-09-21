package com.metrolist.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.metrolist.desktop.viewmodels.DesktopNavSection
import com.metrolist.desktop.viewmodels.DesktopPlayerViewModel
import com.metrolist.innertube.models.SongItem

val SpotifyDarkScheme = darkColorScheme(
    primary = Color(0xFF1DB954), // Spotify Green
    onPrimary = Color.Black,
    surface = Color(0xFF000000), // Pure Black Background
    onSurface = Color.White,
    surfaceContainer = Color(0xFF121212), // Dark Container
    surfaceContainerHigh = Color(0xFF181818), // Card Container
    surfaceContainerHighest = Color(0xFF282828)
)

@Composable
fun DesktopApp(
    viewModel: DesktopPlayerViewModel
) {
    val currentSection by viewModel.currentSection.collectAsState()
    var searchInput by remember { mutableStateOf("") }
    val currentSong by viewModel.currentSong.collectAsState()

    MaterialTheme(
        colorScheme = SpotifyDarkScheme
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Spotify Header Bar
                TopSpotifyHeaderBar(
                    searchInput = searchInput,
                    onSearchInputChange = { text ->
                        searchInput = text
                        if (text.isNotBlank()) {
                            viewModel.performSearch(text)
                        }
                    },
                    onPerformSearch = { viewModel.performSearch(searchInput) },
                    onHomeClick = { viewModel.navigateTo(DesktopNavSection.HOME) }
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    // Left Spotify Library Sidebar
                    LeftSpotifySidebar(
                        currentSection = currentSection,
                        onNavigate = { viewModel.navigateTo(it) }
                    )

                    Spacer(Modifier.width(8.dp))

                    // Center Canvas (Main Content)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF121212))
                    ) {
                        when (currentSection) {
                            DesktopNavSection.HOME -> HomeScreen(viewModel, Modifier.fillMaxSize())
                            DesktopNavSection.SEARCH -> SearchScreen(viewModel, Modifier.fillMaxSize())
                            DesktopNavSection.ALBUM_DETAIL -> AlbumScreen(viewModel, Modifier.fillMaxSize())
                            DesktopNavSection.ARTIST_DETAIL -> ArtistScreen(viewModel, Modifier.fillMaxSize())
                            DesktopNavSection.PLAYLIST_DETAIL -> PlaylistScreen(viewModel, Modifier.fillMaxSize())
                        }
                    }

                    // Right Side "Now Playing / Track Credits" Panel (Spotify Style)
                    val activeSong = currentSong
                    if (activeSong != null) {
                        Spacer(Modifier.width(8.dp))
                        RightSpotifyTrackPanel(
                            song = activeSong,
                            viewModel = viewModel,
                            modifier = Modifier
                                .width(280.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF121212))
                                .padding(16.dp)
                        )
                    }
                }

                // Bottom Spotify Player Bar
                PlayerBar(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun TopSpotifyHeaderBar(
    searchInput: String,
    onSearchInputChange: (String) -> Unit,
    onPerformSearch: () -> Unit,
    onHomeClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Color.Black)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Title / Logo
        Text(
            text = "Sargam",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1DB954),
            modifier = Modifier.padding(end = 24.dp)
        )

        // Center Search & Home Button
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1F1F1F))
                    .clickable(onClick = onHomeClick),
                contentAlignment = Alignment.Center
            ) {
                Text("🏠", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.width(12.dp))

            OutlinedTextField(
                value = searchInput,
                onValueChange = onSearchInputChange,
                placeholder = { Text("What do you want to play?", color = Color(0xFFB3B3B3)) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onPerformSearch() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF2A2A2A),
                    unfocusedContainerColor = Color(0xFF1F1F1F),
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                leadingIcon = {
                    Text(
                        "🔍",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.clickable(onClick = onPerformSearch)
                    )
                },
                modifier = Modifier
                    .width(420.dp)
                    .height(48.dp)
            )

            Spacer(Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1F1F1F))
                    .clickable(onClick = onPerformSearch),
                contentAlignment = Alignment.Center
            ) {
                Text("➔", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
        }

        // Right Profiles & Controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("🔔", style = MaterialTheme.typography.titleMedium)
            Text("👥", style = MaterialTheme.typography.titleMedium)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1DB954)),
                contentAlignment = Alignment.Center
            ) {
                Text("👤", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun LeftSpotifySidebar(
    currentSection: DesktopNavSection,
    onNavigate: (DesktopNavSection) -> Unit
) {
    Column(
        modifier = Modifier
            .width(64.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF121212))
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (currentSection == DesktopNavSection.HOME) Color(0xFF282828) else Color.Transparent)
                .clickable { onNavigate(DesktopNavSection.HOME) },
            contentAlignment = Alignment.Center
        ) {
            Text("|||", fontWeight = FontWeight.Bold, color = Color.White)
        }

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (currentSection == DesktopNavSection.SEARCH) Color(0xFF282828) else Color.Transparent)
                .clickable { onNavigate(DesktopNavSection.SEARCH) },
            contentAlignment = Alignment.Center
        ) {
            Text("🔍", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF1F1F1F)),
            contentAlignment = Alignment.Center
        ) {
            Text("➕", style = MaterialTheme.typography.titleSmall, color = Color.White)
        }

        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1DB954).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text("💚", style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
fun RightSpotifyTrackPanel(
    song: SongItem,
    viewModel: DesktopPlayerViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        Text(
            text = song.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(16.dp))

        AsyncImage(
            model = song.thumbnail,
            contentDescription = song.title,
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = song.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = song.artists.joinToString(", ") { it.name },
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFB3B3B3),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Credits",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(Modifier.height(12.dp))

        song.artists.forEach { artist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(artist.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text("Main Artist", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB3B3B3))
                }

                Button(
                    onClick = { },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.border(1.dp, Color.White, RoundedCornerShape(16.dp))
                ) {
                    Text("Follow", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
