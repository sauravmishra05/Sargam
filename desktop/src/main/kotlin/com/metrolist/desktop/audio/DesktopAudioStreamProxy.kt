package com.metrolist.desktop.audio

import com.sun.net.httpserver.HttpServer
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * Local HTTP Audio Stream Proxy for Windows Desktop.
 * Relays googlevideo.com streams to JavaFX MediaPlayer with proper User-Agent, Referer, and byte-range headers,
 * bypassing 403 Forbidden rejections on desktop.
 */
object DesktopAudioStreamProxy {
    private var server: HttpServer? = null
    private var localPort: Int = 0
    private val okHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun start() {
        if (server != null) return
        try {
            val address = InetSocketAddress("127.0.0.1", 0)
            server = HttpServer.create(address, 0).apply {
                executor = Executors.newCachedThreadPool()
                createContext("/audio") { exchange ->
                    try {
                        val query = exchange.requestURI.rawQuery.orEmpty()
                        val targetUrl = query.substringAfter("url=").takeIf { it.isNotBlank() }?.let {
                            URLDecoder.decode(it, "UTF-8")
                        }

                        if (targetUrl == null) {
                            exchange.sendResponseHeaders(400, -1)
                            exchange.close()
                            return@createContext
                        }

                        val rangeHeader = exchange.requestHeaders.getFirst("Range")
                        val reqBuilder = Request.Builder()
                            .url(targetUrl)
                            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .addHeader("Referer", "https://www.youtube.com/")

                        if (!rangeHeader.isNullOrEmpty()) {
                            reqBuilder.addHeader("Range", rangeHeader)
                        }

                        val response = okHttpClient.newCall(reqBuilder.build()).execute()
                        val code = response.code
                        val body = response.body

                        val contentType = response.header("Content-Type") ?: "audio/mp4"
                        val contentLength = body?.contentLength() ?: -1L

                        exchange.responseHeaders.add("Content-Type", contentType)
                        exchange.responseHeaders.add("Accept-Ranges", "bytes")
                        response.header("Content-Range")?.let {
                            exchange.responseHeaders.add("Content-Range", it)
                        }

                        if (contentLength > 0) {
                            exchange.sendResponseHeaders(code, contentLength)
                        } else {
                            exchange.sendResponseHeaders(code, 0)
                        }

                        body?.byteStream()?.use { input ->
                            exchange.responseBody.use { output ->
                                input.copyTo(output)
                            }
                        } ?: run {
                            exchange.responseBody.close()
                        }
                    } catch (e: Exception) {
                        println("[STREAM_PROXY_ERROR] ${e.message}")
                        runCatching { exchange.sendResponseHeaders(500, -1) }
                        runCatching { exchange.close() }
                    }
                }
                start()
                localPort = address.port
                println("Desktop Audio Stream Proxy running at http://127.0.0.1:$localPort/audio")
            }
        } catch (e: Exception) {
            println("Failed to start Desktop Audio Stream Proxy: ${e.message}")
        }
    }

    fun getProxiedUrl(targetUrl: String): String {
        start()
        val encoded = URLEncoder.encode(targetUrl, "UTF-8")
        return "http://127.0.0.1:$localPort/audio?url=$encoded"
    }
}
