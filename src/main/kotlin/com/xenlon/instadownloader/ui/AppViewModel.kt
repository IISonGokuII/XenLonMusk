package com.xenlon.instadownloader.ui

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.provider.MediaStore
import com.xenlon.instadownloader.model.*
import com.xenlon.instadownloader.service.DownloadManager
import com.xenlon.instadownloader.service.DownloadProgress
import com.xenlon.instadownloader.service.InstagramService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * ViewModel managing the application state and coordinating between UI and services.
 */
class AppViewModel {

    private val instagramService = InstagramService()
    private val downloadManager = DownloadManager(instagramService)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Screen state
    enum class Screen { LOGIN, MAIN, GALLERY }

    private val _currentScreen = MutableStateFlow(Screen.LOGIN)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Anonymous mode
    private val _isAnonymousMode = MutableStateFlow(false)
    val isAnonymousMode: StateFlow<Boolean> = _isAnonymousMode.asStateFlow()

    // Login state
    private val _isLoginLoading = MutableStateFlow(false)
    val isLoginLoading: StateFlow<Boolean> = _isLoginLoading.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    // Logged-in username (for own-account detection)
    private var loggedInUsername: String = ""

    // Profile state
    private val _currentProfile = MutableStateFlow<UserProfile?>(null)
    val currentProfile: StateFlow<UserProfile?> = _currentProfile.asStateFlow()

    // Stories state
    private val _stories = MutableStateFlow<DownloadResult<List<StoryItem>>>(DownloadResult.Loading)
    val stories: StateFlow<DownloadResult<List<StoryItem>>> = _stories.asStateFlow()

    // Highlights state
    private val _highlights = MutableStateFlow<DownloadResult<List<HighlightReel>>>(DownloadResult.Loading)
    val highlights: StateFlow<DownloadResult<List<HighlightReel>>> = _highlights.asStateFlow()

    // Feed posts state
    private val _feedPosts = MutableStateFlow<DownloadResult<List<FeedPost>>>(DownloadResult.Loading)
    val feedPosts: StateFlow<DownloadResult<List<FeedPost>>> = _feedPosts.asStateFlow()

    // Archived posts state (only available for own account)
    private val _archivedPosts = MutableStateFlow<DownloadResult<List<FeedPost>>?>(null)
    val archivedPosts: StateFlow<DownloadResult<List<FeedPost>>?> = _archivedPosts.asStateFlow()

    // Whether the currently viewed profile is the logged-in user's own profile
    private val _isOwnProfile = MutableStateFlow(false)
    val isOwnProfile: StateFlow<Boolean> = _isOwnProfile.asStateFlow()

    // Gallery state
    private val _galleryItems = MutableStateFlow<List<GalleryItem>>(emptyList())
    val galleryItems: StateFlow<List<GalleryItem>> = _galleryItems.asStateFlow()

    // Download progress
    val downloadProgress: StateFlow<DownloadProgress> = downloadManager.downloadProgress

    /**
     * Attempts to log in with the given credentials.
     */
    fun login(username: String, password: String) {
        scope.launch {
            _isLoginLoading.value = true
            _loginError.value = null

            when (val result = instagramService.login(username, password)) {
                is DownloadResult.Success -> {
                    loggedInUsername = username
                    _currentScreen.value = Screen.MAIN
                    _loginError.value = null
                }
                is DownloadResult.Error -> {
                    _loginError.value = result.message
                }
                else -> {}
            }

            _isLoginLoading.value = false
        }
    }

    /**
     * Enters anonymous mode (no login required, limited to public profiles).
     */
    fun enterAnonymousMode() {
        _isAnonymousMode.value = true
        _currentScreen.value = Screen.MAIN
    }

    /**
     * Searches for a user profile.
     */
    fun searchUser(username: String) {
        scope.launch {
            _currentProfile.value = null
            _stories.value = DownloadResult.Loading
            _highlights.value = DownloadResult.Loading
            _feedPosts.value = DownloadResult.Loading
            _archivedPosts.value = null
            _isOwnProfile.value = false

            when (val result = instagramService.fetchUserProfile(username)) {
                is DownloadResult.Success -> {
                    _currentProfile.value = result.data
                    val userId = result.data.userId

                    // Check if this is the logged-in user's own profile
                    val isOwn = !_isAnonymousMode.value &&
                        instagramService.isAuthenticated() &&
                        (username.equals(loggedInUsername, ignoreCase = true) ||
                            userId == instagramService.getSessionUserId())
                    _isOwnProfile.value = isOwn

                    // Always fetch feed posts (works anonymously for public profiles)
                    launch {
                        _feedPosts.value = instagramService.fetchFeedPosts(username)
                    }

                    if (userId.isNotEmpty() && !_isAnonymousMode.value) {
                        // Fetch stories and highlights in parallel (requires login)
                        launch {
                            _stories.value = instagramService.fetchStories(userId)
                        }
                        launch {
                            _highlights.value = instagramService.fetchHighlights(userId)
                        }

                        // Fetch archived posts only for own profile
                        if (isOwn) {
                            launch {
                                _archivedPosts.value = DownloadResult.Loading
                                _archivedPosts.value = instagramService.fetchArchivedPosts()
                            }
                        }
                    }
                }
                is DownloadResult.Error -> {
                    _currentProfile.value = null
                    _stories.value = DownloadResult.Error(result.message)
                    _highlights.value = DownloadResult.Error(result.message)
                    _feedPosts.value = DownloadResult.Error(result.message)
                }
                else -> {}
            }
        }
    }

    /**
     * Downloads the profile picture of the current user.
     */
    fun downloadProfilePicture() {
        val profile = _currentProfile.value ?: return
        scope.launch {
            downloadManager.downloadProfilePicture(profile)
        }
    }

    /**
     * Downloads all stories of the current user.
     */
    fun downloadStories() {
        val profile = _currentProfile.value ?: return
        val storiesResult = _stories.value
        if (storiesResult !is DownloadResult.Success) return

        scope.launch {
            downloadManager.downloadStories(profile, storiesResult.data)
        }
    }

    /**
     * Downloads a specific highlight reel.
     */
    fun downloadHighlight(highlight: HighlightReel) {
        val profile = _currentProfile.value ?: return
        scope.launch {
            downloadManager.downloadHighlight(profile, highlight)
        }
    }

    /**
     * Downloads all feed posts of the current user.
     */
    fun downloadFeedPosts() {
        val profile = _currentProfile.value ?: return
        val postsResult = _feedPosts.value
        if (postsResult !is DownloadResult.Success) return

        scope.launch {
            downloadManager.downloadFeedPosts(profile, postsResult.data)
        }
    }

    /**
     * Downloads all archived posts (own account only).
     */
    fun downloadArchivedPosts() {
        val profile = _currentProfile.value ?: return
        val archiveResult = _archivedPosts.value
        if (archiveResult !is DownloadResult.Success) return

        scope.launch {
            downloadManager.downloadArchivedPosts(profile.username, archiveResult.data)
        }
    }

    /**
     * Downloads all highlights.
     */
    fun downloadAllHighlights() {
        val profile = _currentProfile.value ?: return
        val highlightsResult = _highlights.value
        if (highlightsResult !is DownloadResult.Success) return

        scope.launch {
            downloadManager.downloadAllHighlights(profile, highlightsResult.data)
        }
    }

    // --- Gallery functions ---

    /**
     * Opens the in-app gallery.
     */
    fun openGallery() {
        loadGalleryItems()
        _currentScreen.value = Screen.GALLERY
    }

    /**
     * Returns from gallery to main screen.
     */
    fun closeGallery() {
        _currentScreen.value = Screen.MAIN
    }

    /**
     * Scans the download directory and loads all media files into the gallery.
     */
    fun loadGalleryItems() {
        scope.launch(Dispatchers.IO) {
            val dir = File(downloadManager.getDownloadDir())
            if (!dir.exists()) {
                _galleryItems.value = emptyList()
                return@launch
            }

            val items = dir.walkTopDown()
                .filter { it.isFile && !it.name.startsWith(".") }
                .filter { file ->
                    val ext = file.extension.lowercase()
                    ext in listOf("jpg", "jpeg", "png", "webp", "mp4", "mov")
                }
                .map { file ->
                    val relativePath = file.relativeTo(dir).path
                    val parts = relativePath.split(File.separator)
                    val username = if (parts.size >= 2) parts[0] else ""
                    val category = if (parts.size >= 3) parts[1] else
                        if (file.name.contains("profile_pic")) "profile" else "posts"

                    GalleryItem(
                        file = file,
                        name = file.name,
                        isVideo = file.extension.lowercase() in listOf("mp4", "mov"),
                        sizeBytes = file.length(),
                        lastModified = file.lastModified(),
                        username = username,
                        category = category
                    )
                }
                .sortedByDescending { it.lastModified }
                .toList()

            _galleryItems.value = items
        }
    }

    /**
     * Saves a gallery item to the system gallery (DCIM/InstaDownloader).
     * This makes it visible in the phone's normal gallery app.
     */
    fun saveToSystemGallery(context: Context, item: GalleryItem) {
        scope.launch(Dispatchers.IO) {
            try {
                val mimeType = if (item.isVideo) "video/mp4" else "image/jpeg"
                val collection = if (item.isVideo) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DCIM}/InstaDownloader"
                    )
                }

                val uri = context.contentResolver.insert(collection, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        item.file.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    // Notify MediaScanner
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(uri.path ?: ""),
                        arrayOf(mimeType),
                        null
                    )
                }
            } catch (_: Exception) {
                // Fallback: copy directly
                try {
                    val dcimDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                        "InstaDownloader"
                    )
                    dcimDir.mkdirs()
                    val dest = File(dcimDir, item.name)
                    item.file.copyTo(dest, overwrite = true)

                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(dest.absolutePath),
                        null,
                        null
                    )
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Deletes a gallery item from the download directory.
     */
    fun deleteGalleryItem(item: GalleryItem) {
        scope.launch(Dispatchers.IO) {
            if (item.file.exists()) {
                item.file.delete()
            }
            _galleryItems.value = _galleryItems.value.filter { it.file.absolutePath != item.file.absolutePath }
        }
    }

    /**
     * Logs out and returns to the login screen.
     */
    fun logout() {
        scope.launch {
            if (!_isAnonymousMode.value) {
                instagramService.logout()
            }
            _currentScreen.value = Screen.LOGIN
            _currentProfile.value = null
            _stories.value = DownloadResult.Loading
            _highlights.value = DownloadResult.Loading
            _feedPosts.value = DownloadResult.Loading
            _archivedPosts.value = null
            _isOwnProfile.value = false
            _loginError.value = null
            _isAnonymousMode.value = false
            loggedInUsername = ""
        }
    }

    /**
     * Returns the download directory path.
     */
    fun getDownloadDir(): String = downloadManager.getDownloadDir()

    fun dispose() {
        scope.cancel()
        downloadManager.close()
    }
}
