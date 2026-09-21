package com.metrolist.desktop.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import javafx.application.Platform
import javafx.beans.value.ChangeListener
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebView
import netscape.javascript.JSObject
import java.util.Locale

/**
 * Phase 1 Proof-of-Concept Host View:
 * Loads a local HTML test page in native Web View to demonstrate bidirectional Kotlin <-> JavaScript bridge
 * without calling YouTube Data API or extracting audio streams.
 */
@Composable
fun WebView2HostView(
    adapter: WebView2PlayerAdapter,
    modifier: Modifier = Modifier
) {
    val state by adapter.playerState.collectAsState()
    val currentTimeMs by adapter.currentTimeMs.collectAsState()
    val durationMs by adapter.durationMs.collectAsState()
    val volumePercent by adapter.volumePercent.collectAsState()
    val lastErr by adapter.lastError.collectAsState()
    val isAvailable = remember { adapter.isNativeWebView2Available }

    val testHtmlContent = remember {
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8">
          <style>
            html, body {
              margin: 0;
              padding: 0;
              width: 100%;
              height: 100%;
              background-color: #121212;
              color: #ffffff;
              font-family: sans-serif;
              display: flex;
              flex-direction: column;
              align-items: center;
              justify-content: center;
            }
            .title { font-size: 16px; font-weight: bold; color: #FF5252; margin-bottom: 8px; }
            .info { font-size: 13px; color: #cccccc; margin-top: 4px; }
          </style>
        </head>
        <body>
          <div class="title">SARGAM WEBVIEW2 BRIDGE TEST</div>
          <div class="info" id="status">Status: Initializing Test Engine...</div>
          <div class="info" id="currentTrack">Track ID: NONE</div>

          <script>
            var state = 'UNSTARTED';
            var currTime = 0;
            var dur = 180;
            var timer = null;

            function sendToKotlin(msg) {
              try {
                if (window.nativeBridge && window.nativeBridge.onEvent) {
                  window.nativeBridge.onEvent(JSON.stringify(msg));
                }
              } catch (e) {
                console.log("Bridge error: " + e);
              }
            }

            setTimeout(function() {
              document.getElementById('status').innerText = 'Status: Bridge Connected (Ready)';
              sendToKotlin({ type: 'onReady' });
            }, 300);

            function jsLoadVideoById(id) {
              document.getElementById('currentTrack').innerText = 'Track ID: ' + id;
              document.getElementById('status').innerText = 'Status: Loaded Track ' + id;
              state = 'CUED';
              currTime = 0;
              sendToKotlin({ type: 'onStateChange', stateName: 'CUED' });
            }

            function jsPlayVideo() {
              state = 'PLAYING';
              document.getElementById('status').innerText = 'Status: PLAYING';
              sendToKotlin({ type: 'onStateChange', stateName: 'PLAYING' });

              if (!timer) {
                timer = setInterval(function() {
                  if (state === 'PLAYING') {
                    currTime += 0.5;
                    if (currTime >= dur) {
                      state = 'ENDED';
                      currTime = dur;
                      document.getElementById('status').innerText = 'Status: ENDED';
                      sendToKotlin({ type: 'onStateChange', stateName: 'ENDED' });
                      clearInterval(timer);
                      timer = null;
                    }
                    sendToKotlin({
                      type: 'timeUpdate',
                      currentTime: currTime,
                      duration: dur
                    });
                  }
                }, 500);
              }
            }

            function jsPauseVideo() {
              state = 'PAUSED';
              document.getElementById('status').innerText = 'Status: PAUSED';
              sendToKotlin({ type: 'onStateChange', stateName: 'PAUSED' });
            }

            function jsSeekTo(seconds) {
              currTime = seconds;
              document.getElementById('status').innerText = 'Status: Seeked to ' + seconds + 's';
              sendToKotlin({
                type: 'timeUpdate',
                currentTime: currTime,
                duration: dur
              });
            }

            function jsSetVolume(vol) {
              document.getElementById('status').innerText = 'Status: Volume set to ' + vol + '%';
            }
          </script>
        </body>
        </html>
        """.trimIndent()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "PHASE 1 ARCHITECTURE SKELETON & WEBVIEW2 BRIDGE TEST",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            // Visible Native WebView2 Viewport Container (480x270 px)
            Box(
                modifier = Modifier
                    .width(480.dp)
                    .height(270.dp)
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    .background(Color.Black)
            ) {
                SwingPanel(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        JFXPanel().apply {
                            Platform.runLater {
                                val webView = WebView()
                                val engine = webView.engine
                                engine.isJavaScriptEnabled = true
                                adapter.attachEngine(engine, this)

                                engine.loadWorker.stateProperty().addListener(
                                    ChangeListener<Worker.State> { _, _, newState ->
                                        if (newState == Worker.State.SUCCEEDED) {
                                            runCatching {
                                                val win = engine.executeScript("window") as JSObject
                                                win.setMember("nativeBridge", adapter.NativeBridge())
                                            }.onFailure {
                                                println("Failed to attach nativeBridge: ${it.message}")
                                            }
                                        }
                                    }
                                )

                                engine.loadContent(testHtmlContent)
                                val scene = Scene(webView)
                                this.scene = scene
                            }
                        }
                    }
                )
            }

            Spacer(Modifier.width(16.dp))

            // Debug & Verification Panel
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .height(270.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "BRIDGE MONITOR",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(4.dp))
                Text("WebView2 Runtime Available: $isAvailable", style = MaterialTheme.typography.bodySmall)
                Text("Player State: $state", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Text(
                    "Time: ${String.format(Locale.US, "%.1f", currentTimeMs / 1000.0)}s / ${String.format(Locale.US, "%.1f", durationMs / 1000.0)}s",
                    style = MaterialTheme.typography.bodySmall
                )
                Text("Volume: $volumePercent%", style = MaterialTheme.typography.bodySmall)
                if (lastErr != null) {
                    Text("Error: $lastErr", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.weight(1f))

                Text("TEST COMMANDS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { adapter.play() },
                        modifier = Modifier.weight(1f).padding(end = 4.dp)
                    ) {
                        Text("▶ Play")
                    }
                    Button(
                        onClick = { adapter.pause() },
                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                    ) {
                        Text("⏸ Pause")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { adapter.seekTo(30000L) },
                        modifier = Modifier.weight(1f).padding(end = 4.dp)
                    ) {
                        Text("Seek 30s")
                    }
                    Button(
                        onClick = { adapter.setVolume(75) },
                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                    ) {
                        Text("Vol 75%")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Button(
                        onClick = { adapter.loadVideo("TEST_TRACK_101") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Load Test Track")
                    }
                }
            }
        }
    }
}
