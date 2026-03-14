package com.xenlon.instadownloader.service

import com.xenlon.instadownloader.model.DownloadResult
import com.xenlon.instadownloader.model.DownloadedMediaMetadata
import com.xenlon.instadownloader.model.FeedPost
import com.xenlon.instadownloader.model.HighlightReel
import com.xenlon.instadownloader.model.MediaType
import com.xenlon.instadownloader.model.StoryItem
import com.xenlon.instadownloader.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages downloading of Instagram content (stories, highlights, profile pictures).
 */
class DownloadManager(
    private val instagramService: InstagramService = InstagramService(),
    private val appContext: android.content.Context? = null,
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
    private val metadataJson = Json { ignoreUnknownKeys = true }

    /**
     * Gets the download directory using app-specific external storage.
     * This avoids EACCES permission errors on Android 10+ (Scoped Storage).
     * Falls back to internal cache if external storage is unavailable.
     */
    fun getDownloadDir(): String {
        val dir = appContext?.getExternalFilesDir(null)?.let { File(it, "InstaDownloader") }
            ?: appContext?.cacheDir?.let { File(it, "InstaDownloader") }
            ?: File(
                android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS,
                ),
                "InstaDownloader",
            )
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
    suspend fun downloadProfilePicture(
        profile: UserProfile,
        downloadDir: String = getDownloadDir(),
    ): DownloadResult<String> {
        _downloadProgress.value = DownloadProgress.Downloading(0, 1, "Profilbild")

        val hdUrl = profile.profilePicUrlHD
        val sdUrl = profile.profilePicUrl
        val url = hdUrl.ifEmpty { sdUrl }

        if (url.isEmpty()) {
            _downloadProgress.value = DownloadProgress.Error("Kein Profilbild verfugbar")
            return DownloadResult.Error("Kein Profilbild verfugbar")
        }

        val extension = DownloadFilePlanner.mediaExtension(url, treatAsVideo = false)
        val filename = DownloadFilePlanner.buildProfilePictureFileName(profile.username, extension)
        val outputPath = File(downloadDir, "${profile.username}/$filename").absolutePath

        var result = instagramService.downloadFile(url, outputPath)

        if (result is DownloadResult.Error && hdUrl.isNotEmpty() && sdUrl.isNotEmpty() && hdUrl != sdUrl) {
            result = instagramService.downloadFile(sdUrl, outputPath)
        }

        if (result is DownloadResult.Success) {
            writeMetadata(
                filePath = result.data,
                metadata = DownloadedMediaMetadata(
                    username = profile.username,
                    category = "profile",
                    isVideo = false,
                ),
            )
        }

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
        downloadDir: String = getDownloadDir(),
    ): DownloadResult<List<String>> {
        if (storyItems.isEmpty()) {
            _downloadProgress.value = DownloadProgress.Error("Keine Stories zum Download verfugbar")
            return DownloadResult.Error("Keine Stories verfugbar")
        }

        val downloadedFiles = mutableListOf<String>()
        val total = storyItems.size

        storyItems.forEachIndexed { index, story ->
            _downloadProgress.value = DownloadProgress.Downloading(index, total, "Story ${index + 1}/$total")

            val extension = DownloadFilePlanner.mediaExtension(
                story.mediaUrl,
                treatAsVideo = story.type == MediaType.VIDEO,
            )
            val timestamp = dateFormat.format(Date(story.timestamp * 1000))
            val filename = "${profile.username}_story_${timestamp}_${story.id}.$extension"
            val outputPath = File(downloadDir, "${profile.username}/stories/$filename").absolutePath

            when (val result = instagramService.downloadFile(story.mediaUrl, outputPath)) {
                is DownloadResult.Success -> {
                    downloadedFiles.add(result.data)
                    writeMetadata(
                        filePath = result.data,
                        metadata = DownloadedMediaMetadata(
                            username = profile.username,
                            category = "stories",
                            sourceTimestamp = story.timestamp * 1000L,
                            sourceId = story.id,
                            isVideo = story.type == MediaType.VIDEO,
                        ),
                    )
                }
                is DownloadResult.Error -> {}
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
        downloadDir: String = getDownloadDir(),
    ): DownloadResult<List<String>> {
        _downloadProgress.value = DownloadProgress.Downloading(0, 1, "Highlight: ${highlight.title}")

        val itemsResult = instagramService.fetchHighlightItems(highlight.id)
        if (itemsResult is DownloadResult.Error) {
            _downloadProgress.value = DownloadProgress.Error(itemsResult.message)
            return DownloadResult.Error(itemsResult.message)
        }

        val items = (itemsResult as DownloadResult.Success).data
        if (items.isEmpty()) {
            _downloadProgress.value = DownloadProgress.Error("Keine Highlight-Inhalte verfugbar")
            return DownloadResult.Error("Keine Highlight-Inhalte")
        }

        val downloadedFiles = mutableListOf<String>()
        val total = items.size
        val safeTitle = DownloadFilePlanner.sanitizePathSegment(highlight.title)

        items.forEachIndexed { index, item ->
            _downloadProgress.value = DownloadProgress.Downloading(
                index,
                total,
                "Highlight '${highlight.title}' ${index + 1}/$total",
            )

            val extension = DownloadFilePlanner.mediaExtension(
                item.mediaUrl,
                treatAsVideo = item.type == MediaType.VIDEO,
            )
            val filename = "${profile.username}_highlight_${safeTitle}_${item.id}.$extension"
            val outputPath = File(downloadDir, "${profile.username}/highlights/$safeTitle/$filename").absolutePath

            when (val result = instagramService.downloadFile(item.mediaUrl, outputPath)) {
                is DownloadResult.Success -> {
                    downloadedFiles.add(result.data)
                    writeMetadata(
                        filePath = result.data,
                        metadata = DownloadedMediaMetadata(
                            username = profile.username,
                            category = "highlights",
                            sourceTimestamp = item.timestamp * 1000L,
                            sourceId = item.id,
                            highlightTitle = highlight.title,
                            isVideo = item.type == MediaType.VIDEO,
                        ),
                    )
                }
                is DownloadResult.Error -> {}
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
        downloadDir: String = getDownloadDir(),
    ): DownloadResult<Int> {
        var totalDownloaded = 0
        highlightReels.forEach { highlight ->
            val result = downloadHighlight(profile, highlight, downloadDir)
            if (result is DownloadResult.Success) {
                totalDownloaded += result.data.size
            }
        }
        _downloadProgress.value = DownloadProgress.Complete(totalDownloaded, "Alle Highlights")
        return DownloadResult.Success(totalDownloaded)
    }

    /**
     * Downloads all feed posts (posted images/videos) for a user.
     */
    suspend fun downloadFeedPosts(
        profile: UserProfile,
        posts: List<FeedPost>,
        downloadDir: String = getDownloadDir(),
    ): DownloadResult<List<String>> {
        return downloadPostCollection(
            posts = posts,
            emptyProgressMessage = "Keine Posts zum Download verfugbar",
            emptyResultMessage = "Keine Posts verfugbar",
            progressLabel = { current, total -> "Post $current/$total" },
            completeLabel = "Posts",
            outputPath = { post, mediaIndex, extension, timestamp ->
                val filename = DownloadFilePlanner.buildPostFileName(
                    prefix = "${profile.username}_post",
                    timestamp = timestamp,
                    shortcode = post.shortcode,
                    mediaIndex = mediaIndex,
                    isCarousel = post.isCarousel,
                    extension = extension,
                )
                File(downloadDir, "${profile.username}/posts/$filename").absolutePath
            },
            metadataBuilder = { post, _, url ->
                DownloadedMediaMetadata(
                    username = profile.username,
                    category = "posts",
                    sourceTimestamp = post.timestamp * 1000L,
                    caption = post.caption,
                    shortcode = post.shortcode,
                    sourceId = post.id,
                    isVideo = post.type == MediaType.VIDEO || url.contains(".mp4", ignoreCase = true),
                )
            },
        )
    }

    /**
     * Downloads archived posts for the logged-in user.
     */
    suspend fun downloadArchivedPosts(
        username: String,
        posts: List<FeedPost>,
        downloadDir: String = getDownloadDir(),
    ): DownloadResult<List<String>> {
        return downloadPostCollection(
            posts = posts,
            emptyProgressMessage = "Keine archivierten Posts zum Download verfugbar",
            emptyResultMessage = "Keine archivierten Posts verfugbar",
            progressLabel = { current, total -> "Archiv-Post $current/$total" },
            completeLabel = "Archiv-Posts",
            outputPath = { post, mediaIndex, extension, timestamp ->
                val filename = DownloadFilePlanner.buildPostFileName(
                    prefix = "${username}_archive",
                    timestamp = timestamp,
                    shortcode = post.shortcode,
                    mediaIndex = mediaIndex,
                    isCarousel = post.isCarousel,
                    extension = extension,
                )
                File(downloadDir, "$username/archive/$filename").absolutePath
            },
            metadataBuilder = { post, _, url ->
                DownloadedMediaMetadata(
                    username = username,
                    category = "archive",
                    sourceTimestamp = post.timestamp * 1000L,
                    caption = post.caption,
                    shortcode = post.shortcode,
                    sourceId = post.id,
                    isVideo = post.type == MediaType.VIDEO || url.contains(".mp4", ignoreCase = true),
                )
            },
        )
    }

    /**
     * Downloads all reels for a user.
     */
    suspend fun downloadReels(
        profile: UserProfile,
        reels: List<FeedPost>,
        downloadDir: String = getDownloadDir(),
    ): DownloadResult<List<String>> {
        return downloadPostCollection(
            posts = reels,
            emptyProgressMessage = "Keine Reels zum Download verfugbar",
            emptyResultMessage = "Keine Reels verfugbar",
            progressLabel = { current, total -> "Reel $current/$total" },
            completeLabel = "Reels",
            outputPath = { post, mediaIndex, extension, timestamp ->
                val filename = DownloadFilePlanner.buildPostFileName(
                    prefix = "${profile.username}_reel",
                    timestamp = timestamp,
                    shortcode = post.shortcode,
                    mediaIndex = mediaIndex,
                    isCarousel = post.isCarousel,
                    extension = extension,
                )
                File(downloadDir, "${profile.username}/reels/$filename").absolutePath
            },
            metadataBuilder = { post, _, _ ->
                DownloadedMediaMetadata(
                    username = profile.username,
                    category = "reels",
                    sourceTimestamp = post.timestamp * 1000L,
                    caption = post.caption,
                    shortcode = post.shortcode,
                    sourceId = post.id,
                    isVideo = true,
                )
            },
        )
    }

    /**
     * Downloads saved/bookmarked posts.
     */
    suspend fun downloadSavedPosts(
        posts: List<FeedPost>,
        downloadDir: String = getDownloadDir(),
    ): DownloadResult<List<String>> {
        return downloadPostCollection(
            posts = posts,
            emptyProgressMessage = "Keine gespeicherten Posts zum Download verfugbar",
            emptyResultMessage = "Keine gespeicherten Posts verfugbar",
            progressLabel = { current, total -> "Gespeicherter Post $current/$total" },
            completeLabel = "Gespeicherte Posts",
            outputPath = { post, mediaIndex, extension, timestamp ->
                val filename = DownloadFilePlanner.buildPostFileName(
                    prefix = "saved",
                    timestamp = timestamp,
                    shortcode = post.shortcode,
                    mediaIndex = mediaIndex,
                    isCarousel = post.isCarousel,
                    extension = extension,
                )
                File(downloadDir, "saved/$filename").absolutePath
            },
            metadataBuilder = { post, _, url ->
                DownloadedMediaMetadata(
                    category = "saved",
                    sourceTimestamp = post.timestamp * 1000L,
                    caption = post.caption,
                    shortcode = post.shortcode,
                    sourceId = post.id,
                    isVideo = post.type == MediaType.VIDEO || url.contains(".mp4", ignoreCase = true),
                )
            },
        )
    }

    /**
     * Downloads tagged posts for a user.
     */
    suspend fun downloadTaggedPosts(
        profile: UserProfile,
        posts: List<FeedPost>,
        downloadDir: String = getDownloadDir(),
    ): DownloadResult<List<String>> {
        return downloadPostCollection(
            posts = posts,
            emptyProgressMessage = "Keine markierten Posts zum Download verfugbar",
            emptyResultMessage = "Keine markierten Posts verfugbar",
            progressLabel = { current, total -> "Markierter Post $current/$total" },
            completeLabel = "Markierte Posts",
            outputPath = { post, mediaIndex, extension, timestamp ->
                val filename = DownloadFilePlanner.buildPostFileName(
                    prefix = "${profile.username}_tagged",
                    timestamp = timestamp,
                    shortcode = post.shortcode,
                    mediaIndex = mediaIndex,
                    isCarousel = post.isCarousel,
                    extension = extension,
                )
                File(downloadDir, "${profile.username}/tagged/$filename").absolutePath
            },
            metadataBuilder = { post, _, url ->
                DownloadedMediaMetadata(
                    username = profile.username,
                    category = "tagged",
                    sourceTimestamp = post.timestamp * 1000L,
                    caption = post.caption,
                    shortcode = post.shortcode,
                    sourceId = post.id,
                    isVideo = post.type == MediaType.VIDEO || url.contains(".mp4", ignoreCase = true),
                )
            },
        )
    }

    /**
     * Downloads a single shared post.
     */
    suspend fun downloadSharedPost(
        post: FeedPost,
        downloadDir: String = getDownloadDir(),
    ): DownloadResult<List<String>> {
        return downloadPostCollection(
            posts = listOf(post),
            emptyProgressMessage = "Kein geteilter Post zum Download verfugbar",
            emptyResultMessage = "Kein geteilter Post verfugbar",
            progressLabel = { current, total -> "Geteilter Post $current/$total" },
            completeLabel = "Geteilter Post",
            outputPath = { currentPost, mediaIndex, extension, timestamp ->
                val filename = DownloadFilePlanner.buildPostFileName(
                    prefix = "shared",
                    timestamp = timestamp,
                    shortcode = currentPost.shortcode,
                    mediaIndex = mediaIndex,
                    isCarousel = currentPost.isCarousel,
                    extension = extension,
                )
                File(downloadDir, "shared/$filename").absolutePath
            },
            metadataBuilder = { currentPost, _, url ->
                DownloadedMediaMetadata(
                    category = "shared",
                    sourceTimestamp = currentPost.timestamp * 1000L,
                    caption = currentPost.caption,
                    shortcode = currentPost.shortcode,
                    sourceId = currentPost.id,
                    isVideo = currentPost.type == MediaType.VIDEO || url.contains(".mp4", ignoreCase = true),
                )
            },
        )
    }

    fun resetProgress() {
        _downloadProgress.value = DownloadProgress.Idle
    }

    fun close() {
        scope.cancel()
        instagramService.close()
    }

    private suspend fun downloadPostCollection(
        posts: List<FeedPost>,
        emptyProgressMessage: String,
        emptyResultMessage: String,
        progressLabel: (current: Int, total: Int) -> String,
        completeLabel: String,
        outputPath: (post: FeedPost, mediaIndex: Int, extension: String, timestamp: String) -> String,
        metadataBuilder: (post: FeedPost, mediaIndex: Int, url: String) -> DownloadedMediaMetadata,
    ): DownloadResult<List<String>> {
        if (posts.isEmpty()) {
            _downloadProgress.value = DownloadProgress.Error(emptyProgressMessage)
            return DownloadResult.Error(emptyResultMessage)
        }

        val downloadedFiles = mutableListOf<String>()
        val totalMedia = posts.sumOf { it.mediaUrls.size }
        var currentItem = 0

        posts.forEach { post ->
            val timestamp = dateFormat.format(Date(post.timestamp * 1000))

            post.mediaUrls.forEachIndexed { mediaIndex, url ->
                currentItem++
                _downloadProgress.value = DownloadProgress.Downloading(
                    currentItem,
                    totalMedia,
                    progressLabel(currentItem, totalMedia),
                )

                val treatAsVideo = post.type == MediaType.VIDEO && post.mediaUrls.size == 1
                val extension = DownloadFilePlanner.mediaExtension(url, treatAsVideo)
                val plannedPath = outputPath(post, mediaIndex, extension, timestamp)

                when (val result = instagramService.downloadFile(url, plannedPath)) {
                    is DownloadResult.Success -> {
                        downloadedFiles.add(result.data)
                        writeMetadata(
                            filePath = result.data,
                            metadata = metadataBuilder(post, mediaIndex, url),
                        )
                    }
                    is DownloadResult.Error -> {}
                    else -> {}
                }
            }
        }

        _downloadProgress.value = DownloadProgress.Complete(downloadedFiles.size, completeLabel)
        return DownloadResult.Success(downloadedFiles)
    }

    private fun writeMetadata(filePath: String, metadata: DownloadedMediaMetadata) {
        runCatching {
            val metadataFile = File("$filePath.meta.json")
            metadataFile.parentFile?.mkdirs()
            metadataFile.writeText(metadataJson.encodeToString(metadata))
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
