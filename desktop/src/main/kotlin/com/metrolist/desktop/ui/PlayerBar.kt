package com.metrolist.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import java.util.Locale

@Composable
fun PlayerBar(
    viewModel: DesktopPlayerViewModel,
    modifier: Modifier = Modifier
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.audioPlayer.isPlaying.collectAsState()
    val currentPositionMs by viewModel.audioPlayer.currentPositionMs.collectAsState()
    val durationMs by viewModel.audioPlayer.durationMs.collectAsState()
    val volume by viewModel.audioPlayer.volume.collectAsState()

    val song = currentSong

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .background(Color.Black)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Song Artwork, Title, Artist
        Row(
            modifier = Modifier.width(280.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (song != null) {
                AsyncImage(
                    model = song.thumbnail,
                    contentDescription = song.title,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(6.dp))
                )

                Spacer(Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artists.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB3B3B3),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(8.dp))

                Text("💚", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.weight(1f))

        // Center: Spotify Controls & Seek Progress Bar
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(480.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("🔀", style = MaterialTheme.typography.bodyMedium)

                IconButton(onClick = { /* Previous */ }, modifier = Modifier.size(32.dp)) {
                    Text("⏮", style = MaterialTheme.typography.titleLarge, color = Color.White)
                }

                // White Circular Play/Pause Button (Spotify Style 1:1)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable {
                            if (isPlaying) viewModel.audioPlayer.pause()
                            else viewModel.audioPlayer.resume()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isPlaying) "⏸" else "▶",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Black
                    )
                }

                IconButton(onClick = { /* Next */ }, modifier = Modifier.size(32.dp)) {
                    Text("⏭", style = MaterialTheme.typography.titleLarge, color = Color.White)
                }

                Text("🔁", style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val posSec = (currentPositionMs / 1000).toInt()
                val durSec = (durationMs / 1000).toInt()
                Text(
                    text = String.format(Locale.US, "%d:%02d", posSec / 60, posSec % 60),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFB3B3B3)
                )
                Slider(
                    value = if (durationMs > 0) currentPositionMs.toFloat() / durationMs else 0f,
                    onValueChange = { percent ->
                        viewModel.audioPlayer.seekTo((percent * durationMs).toLong())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color(0xFF1DB954),
                        inactiveTrackColor = Color(0xFF4D4D4D)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                Text(
                    text = String.format(Locale.US, "%d:%02d", durSec / 60, durSec % 60),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFB3B3B3)
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Right: Lyrics, Queue, Volume, Fullscreen Controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.width(220.dp)
        ) {
            Text("🎤", style = MaterialTheme.typography.bodyMedium)
            Text("≡", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text("🔊", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = volume,
                onValueChange = { viewModel.audioPlayer.setVolume(it) },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color(0xFF4D4D4D)
                ),
                modifier = Modifier.weight(1f)
            )
            Text("⤢", style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
    }
}
