/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import com.metrolist.music.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val description: String,
    val releaseDate: String,
    val assets: List<ReleaseAsset>
) {
    val cleanedDescription: String
        get() = cleanReleaseNotes(description)

    companion object {
        fun cleanReleaseNotes(raw: String): String {
            if (raw.isBlank()) return raw
            val lines = raw.lines().mapNotNull { line ->
                val trimmed = line.trim()
                val lower = trimmed.lowercase()
                if (lower.startsWith("released by") ||
                    lower.startsWith("new contributors:") ||
                    lower.startsWith("contributors:")
                ) {
                    null
                } else {
                    var processed = line.replace(Regex("""\s*\(@[A-Za-z0-9_-]+(?:\s+@[A-Za-z0-9_-]+)*\)"""), "")
                    processed = processed.replace(Regex("""(?<=\s|^)@[A-Za-z0-9_-]+\b"""), "")
                    processed = processed
                        .replace("Metrolist-KMP", "Sargam KMP")
                        .replace("Metrolist KMP", "Sargam KMP")
                        .replace("Metrolist", "Sargam")
                    processed
                }
            }
            return lines.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
        }
    }
}

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long,
    val architecture: String,
    val variant: String // "foss" or "gms"
)

object Updater {
    private val client = HttpClient()
    var lastCheckTime = -1L
        private set
    
    private var cachedReleaseInfo: ReleaseInfo? = null
    private var cachedAllReleases: List<ReleaseInfo> = emptyList()
    
    private const val CHECK_INTERVAL_MILLIS = 2 * 60 * 60 * 1000L // 2 hours
    private const val GITHUB_API_BASE = "https://api.github.com/repos/sauravmishra05/Sargam"
    const val KMP_APK_NAME = "app-universal-with-Google-Cast.apk"

    /**
     * Extracts a clean semantic version string from tag_name or release_name.
     * Example: "v13.6.2" or "Sargam 13.6.2" -> "13.6.2"
     */
    fun extractVersionName(tagName: String, releaseName: String): String {
        val tagCleaned = tagName.trim().removePrefix("v").removePrefix("V")
        if (tagCleaned.matches(Regex("""\d+(\.\d+)+.*"""))) {
            return tagCleaned
        }
        val match = Regex("""\d+(\.\d+)+""").find(releaseName)
        if (match != null) {
            return match.value
        }
        return tagCleaned.ifBlank { releaseName }
    }

    /**
     * Compares two version strings.
     * Returns: 1 if v1 > v2, -1 if v1 < v2, 0 if equal
     */
    fun compareVersions(v1: String, v2: String): Int {
        val v1Parts = v1.removePrefix("v").removePrefix("V").split(".").map { it.takeWhile { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val v2Parts = v2.removePrefix("v").removePrefix("V").split(".").map { it.takeWhile { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val maxLength = maxOf(v1Parts.size, v2Parts.size)
        
        for (i in 0 until maxLength) {
            val part1 = v1Parts.getOrNull(i) ?: 0
            val part2 = v2Parts.getOrNull(i) ?: 0
            when {
                part1 > part2 -> return 1
                part1 < part2 -> return -1
            }
        }
        return 0
    }

    /**
     * Checks if the latest version is newer than the current version.
     * Returns true if an update is available (latestVersion > currentVersion)
     */
    fun isUpdateAvailable(currentVersion: String, latestVersion: String): Boolean {
        return compareVersions(latestVersion, currentVersion) > 0
    }

    /**
     * Get the current app's architecture and variant
     */
    private fun getCurrentAppVariant(): Pair<String, String> {
        val architecture = BuildConfig.ARCHITECTURE
        val variant = if (BuildConfig.CAST_AVAILABLE) "gms" else "foss"
        return architecture to variant
    }

    /**
     * Parse release assets from GitHub API response
     */
    private fun parseAssets(assetsArray: JSONArray): List<ReleaseAsset> {
        val assets = mutableListOf<ReleaseAsset>()
        
        for (i in 0 until assetsArray.length()) {
            val asset = assetsArray.getJSONObject(i)
            val name = asset.getString("name")
            
            // Skip non-APK files
            if (!name.endsWith(".apk")) continue
            
            val downloadUrl = asset.getString("browser_download_url")
            val size = asset.optLong("size", 0L)
            
            // Parse architecture and variant strictly from filename
            val (arch, variant) = when {
                name == "app-universal-with-Google-Cast.apk" || name == "Metrolist-with-Google-Cast.apk" || name == "Sargam-with-Google-Cast.apk" -> "universal" to "gms"
                name == "app-universal-release.apk" || name == "Metrolist.apk" || name == "Sargam.apk" -> "universal" to "foss"
                name.startsWith("app-") && name.contains("Google-Cast") -> {
                    val archStr = name.removePrefix("app-").substringBefore("-with-Google-Cast")
                    archStr to "gms"
                }
                name.startsWith("app-") && name.contains("release") -> {
                    val archStr = name.removePrefix("app-").substringBefore("-release")
                    archStr to "foss"
                }
                else -> null to null
            }
            
            if (arch != null && variant != null) {
                assets.add(ReleaseAsset(name, downloadUrl, size, arch, variant))
            }
        }
        
        return assets
    }

    /**
     * Fetch latest release from GitHub API
     */
    suspend fun getLatestRelease(forceRefresh: Boolean = false): Result<ReleaseInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (cachedReleaseInfo != null && !forceRefresh) {
                    return@runCatching cachedReleaseInfo!!
                }
                
                val response = client.get("$GITHUB_API_BASE/releases/latest")
                    .bodyAsText()
                val json = JSONObject(response)
                
                val tagName = json.getString("tag_name")
                val rawName = json.optString("name", "")
                val parsedVersionName = extractVersionName(tagName, rawName)

                val releaseInfo = ReleaseInfo(
                    tagName = tagName,
                    versionName = parsedVersionName,
                    description = json.optString("body", ""),
                    releaseDate = json.optString("published_at", ""),
                    assets = parseAssets(json.optJSONArray("assets") ?: JSONArray())
                )
                
                cachedReleaseInfo = releaseInfo
                lastCheckTime = System.currentTimeMillis()
                releaseInfo
            }
        }

    /**
     * Fetch all releases from GitHub API (paginated)
     */
    suspend fun getAllReleases(forceRefresh: Boolean = false): Result<List<ReleaseInfo>> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (cachedAllReleases.isNotEmpty() && !forceRefresh) {
                    return@runCatching cachedAllReleases
                }
                
                val releases = mutableListOf<ReleaseInfo>()
                var page = 1
                var hasMore = true
                
                while (hasMore && page <= 10) { // Limit to 10 pages
                    val response = client.get("$GITHUB_API_BASE/releases?page=$page&per_page=30")
                        .bodyAsText()
                    if (response.isBlank() || !response.trim().startsWith("[")) {
                        hasMore = false
                        break
                    }
                    val json = JSONArray(response)
                    
                    if (json.length() == 0) {
                        hasMore = false
                        break
                    }
                    
                    for (i in 0 until json.length()) {
                        val releaseObj = json.getJSONObject(i)
                        val tagName = releaseObj.optString("tag_name", "")
                        val rawName = releaseObj.optString("name", "")
                        val parsedVersionName = extractVersionName(tagName, rawName)

                        releases.add(ReleaseInfo(
                            tagName = tagName,
                            versionName = parsedVersionName,
                            description = releaseObj.optString("body", ""),
                            releaseDate = releaseObj.optString("published_at", ""),
                            assets = parseAssets(releaseObj.optJSONArray("assets") ?: JSONArray())
                        ))
                    }
                    
                    page++
                }
                
                cachedAllReleases = releases
                releases
            }
        }

    /**
     * Disabled KMP migration lookup.
     */
    suspend fun getLatestKmpRelease(): Result<ReleaseInfo?> =
        withContext(Dispatchers.IO) {
            runCatching { null }
        }

    /**
     * Get the download URL for the current app variant (gms or foss).
     * Returns null if no matching variant asset exists in the release.
     */
    fun getDownloadUrlForCurrentVariant(releaseInfo: ReleaseInfo): String? {
        val (currentArch, currentVariant) = getCurrentAppVariant()
        
        // Strict variant match only — DO NOT fall back to mismatched variant
        return releaseInfo.assets
            .find { it.architecture == currentArch && it.variant == currentVariant }
            ?.downloadUrl
    }

    /**
     * Get all available download URLs for a release
     */
    fun getAllDownloadUrls(releaseInfo: ReleaseInfo): Map<String, String> {
        return releaseInfo.assets.associate { "${it.architecture}-${it.variant}" to it.downloadUrl }
    }

    /**
     * Check if update is needed (respects 2-hour cache)
     */
    suspend fun checkForUpdate(forceRefresh: Boolean = false): Result<Pair<ReleaseInfo?, Boolean>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val shouldFetch = forceRefresh || 
                    (System.currentTimeMillis() - lastCheckTime) > CHECK_INTERVAL_MILLIS
                
                if (!shouldFetch && cachedReleaseInfo != null) {
                    val hasUpdate = isUpdateAvailable(
                        BuildConfig.VERSION_NAME,
                        cachedReleaseInfo!!.versionName
                    )
                    return@runCatching cachedReleaseInfo!! to hasUpdate
                }
                
                val result = getLatestRelease(forceRefresh = true)
                if (result.isSuccess) {
                    val releaseInfo = result.getOrThrow()
                    val hasUpdate = isUpdateAvailable(
                        BuildConfig.VERSION_NAME,
                        releaseInfo.versionName
                    )
                    releaseInfo to hasUpdate
                } else {
                    throw result.exceptionOrNull() ?: Exception("Unknown error")
                }
            }
        }

    /**
     * Get the download URL for the correct app variant
     * Returns null if no matching asset is found
     */
    fun getLatestDownloadUrl(): String? {
        return cachedReleaseInfo?.let { getDownloadUrlForCurrentVariant(it) }
    }
    
    /**
     * Get the latest release info (cached)
     */
    fun getCachedLatestRelease(): ReleaseInfo? = cachedReleaseInfo
}
