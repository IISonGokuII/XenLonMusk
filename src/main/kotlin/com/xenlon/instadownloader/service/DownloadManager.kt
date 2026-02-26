package com.xenlon.instadownloader.service

import com.xenlon.instadownloader.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages downloading of Instagram content (stories, highlights, profile pictures).
 */
class DownloadManager(
    private val instagramService: InstagramService = InstagramService()
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _downloadProgress = MutableStateFlow<DownloadProgress>(DownloadProgress.Idle)
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress.asStateFlow()

    private val _currentProfile = MutableStateFlow<DownloadResult<UserProfile>>(DownloadResult.Loading)
    val currentProfile: StateFlow<DownloadResult<UserProfile>> = _currentProfile.asStateFlow()

    private val _stories = MutableStateFlow<DownloadResult<List<StoryItem>>>(DownloadResult.Loading)
    val stories: StateFlow<DownloadResult<List<StoryItem>>> = _stories.asStateFlow()

    private val _highlights = MutableStateFlow<DownloadResult<List<HighlightReel>>>(DownloadResult.Loading)
    val highlights: StateFlow<DownloadResult<List<HighlightReel>>> = _highlights.asStateFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())

    /**
     * Gets the default download directory.
     */
    fun getDownloadDir(): String {
        val home = System.getProperty("user.home")
        val dir = File(home, "InstaDownloader")
        dir.mkdirs()
        return dir.absolutePath
    }

    /**
     * Loads the profile of the given username.
     */
    suspend fun loadProfile(username: String): DownloadResult<UserProfile> {
        _currentProfile.value = DownloadResult.Loading
        _stories.value = DownloadResult.Loading
        _highlights.value = DownloadResult.Loading

        val result = instagramService.fetchUserProfile(username)
        _currentProfile.value = result

        // If profile loaded, fetch stories and highlights in parallel
        if (result is DownloadResult.Success) {
            val userId = result.data.userId
            if (userId.isNotEmpty()) {
                scope.launch {
                    _stories.value = instagramService.fetchStories(userId)
                }
                scope.launch {
                    _highlights.value = instagramService.fetchHighlights(userId)
                }
            }
        }

        return result
    }

    /**
     * Downloads the profile picture of the current user.
     */
    suspend fun downloadProfilePicture(profile: UserProfile, downloadDir: String = getDownloadDir()): DownloadResult<String> {
        _downloadProgress.value = DownloadProgress.Downloading(0, 1, "Profilbild")

        val url = profile.profilePicUrlHD.ifEmpty { profile.profilePicUrl }
        if (url.isEmpty()) {
            _downloadProgress.value = DownloadProgress.Error("Kein Profilbild verfügbar")
            return DownloadResult.Error("Kein Profilbild verfügbar")
        }

        val extension = getExtension(url)
        val filename = "${profile.username}_profile_pic.$extension"
        val outputPath = File(downloadDir, "${profile.username}/$filename").absolutePath

        val result = instagramService.downloadFile(url, outputPath)
        _downloadProgress.value = when (result) {
            is DownloadResult.Success -> DownloadProgress.Complete(1, "Profilbild")
            is DownloadResult.Error -> DownloadProgress.Error(result.message)
            else -> DownloadProgress.Idle
        }

        return result
    }

    /**
     * Downloads all stories of the current user.
     */
    suspend fun downloadStories(
        profile: UserProfile,
        storyItems: List<StoryItem>,
        downloadDir: String = getDownloadDir()
    ): DownloadResult<List<String>> {
        if (storyItems.isEmpty()) {
            _downloadProgress.value = DownloadProgress.Error("Keine Stories zum Download verfügbar")
            return DownloadResult.Error("Keine Stories verfügbar")
        }

        val downloadedFiles = mutableListOf<String>()
        val total = storyItems.size

        storyItems.forEachIndexed { index, story ->
            _downloadProgress.value = DownloadProgress.Downloading(index, total, "Story ${index + 1}/$total")

            val extension = if (story.type == MediaType.VIDEO) "mp4" else getExtension(story.mediaUrl)
            val timestamp = dateFormat.format(Date(story.timestamp * 1000))
            val filename = "${profile.username}_story_${timestamp}_${story.id}.$extension"
            val outputPath = File(downloadDir, "${profile.username}/stories/$filename").absolutePath

            when (val result = instagramService.downloadFile(story.mediaUrl, outputPath)) {
                is DownloadResult.Success -> downloadedFiles.add(result.data)
                is DownloadResult.Error -> { /* Skip failed items, continue downloading */ }
                else -> {}
            }
        }

        _downloadProgress.value = DownloadProgress.Complete(downloadedFiles.size, "Stories")
        return DownloadResult.Success(downloadedFiles)
    }

    /**
     * Downloads all items from a highlight reel.
     */
    suspend fun downloadHighlight(
        profile: UserProfile,
        highlight: HighlightReel,
        downloadDir: String = getDownloadDir()
    ): DownloadResult<List<String>> {
        _downloadProgress.value = DownloadProgress.Downloading(0, 1, "Highlight: ${highlight.title}")

        val itemsResult = instagramService.fetchHighlightItems(highlight.id)
        if (itemsResult is DownloadResult.Error) {
            _downloadProgress.value = DownloadProgress.Error(itemsResult.message)
            return DownloadResult.Error(itemsResult.message)
        }

        val items = (itemsResult as DownloadResult.Success).data
        if (items.isEmpty()) {
            _downloadProgress.value = DownloadProgress.Error("Keine Highlight-Inhalte verfügbar")
            return DownloadResult.Error("Keine Highlight-Inhalte")
        }

        val downloadedFiles = mutableListOf<String>()
        val total = items.size
        val safeTitle = highlight.title.replace(Regex("[^a-zA-Z0-9_-]"), "_")

        items.forEachIndexed { index, item ->
            _downloadProgress.value = DownloadProgress.Downloading(
                index, total, "Highlight '${highlight.title}' ${index + 1}/$total"
            )

            val extension = if (item.type == MediaType.VIDEO) "mp4" else getExtension(item.mediaUrl)
            val filename = "${profile.username}_highlight_${safeTitle}_${item.id}.$extension"
            val outputPath = File(downloadDir, "${profile.username}/highlights/$safeTitle/$filename").absolutePath

            when (val result = instagramService.downloadFile(item.mediaUrl, outputPath)) {
                is DownloadResult.Success -> downloadedFiles.add(result.data)
                is DownloadResult.Error -> { /* Skip failed items */ }
                else -> {}
            }
        }

        _downloadProgress.value = DownloadProgress.Complete(downloadedFiles.size, "Highlight '${highlight.title}'")
        return DownloadResult.Success(downloadedFiles)
    }

    /**
     * Downloads all highlights for a user.
     */
    suspend fun downloadAllHighlights(
        profile: UserProfile,
        highlightReels: List<HighlightReel>,
        downloadDir: String = getDownloadDir()
    ): DownloadResult<Int> {
        var totalDownloaded = 0
        highlightReels.forEachIndexed { index, highlight ->
            val result = downloadHighlight(profile, highlight, downloadDir)
            if (result is DownloadResult.Success) {
                totalDownloaded += result.data.size
            }
        }
        _downloadProgress.value = DownloadProgress.Complete(totalDownloaded, "Alle Highlights")
        return DownloadResult.Success(totalDownloaded)
    }

    fun resetProgress() {
        _downloadProgress.value = DownloadProgress.Idle
    }

    fun close() {
        scope.cancel()
        instagramService.close()
    }

    private fun getExtension(url: String): String {
        return when {
            url.contains(".mp4") -> "mp4"
            url.contains(".webp") -> "webp"
            url.contains(".png") -> "png"
            else -> "jpg"
        }
    }
}

/**
 * Represents the current download progress state.
 */
sealed class DownloadProgress {
    data object Idle : DownloadProgress()
    data class Downloading(val current: Int, val total: Int, val label: String) : DownloadProgress()
    data class Complete(val count: Int, val label: String) : DownloadProgress()
    data class Error(val message: String) : DownloadProgress()
}
