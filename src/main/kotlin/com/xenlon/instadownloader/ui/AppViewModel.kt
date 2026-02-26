package com.xenlon.instadownloader.ui

import com.xenlon.instadownloader.model.*
import com.xenlon.instadownloader.service.DownloadManager
import com.xenlon.instadownloader.service.DownloadProgress
import com.xenlon.instadownloader.service.InstagramService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel managing the application state and coordinating between UI and services.
 */
class AppViewModel {

    private val instagramService = InstagramService()
    private val downloadManager = DownloadManager(instagramService)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Screen state
    enum class Screen { LOGIN, MAIN }

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
     * Opening the folder on Android is handled by the Activity.
     */
    fun getDownloadDir(): String = downloadManager.getDownloadDir()

    fun dispose() {
        scope.cancel()
        downloadManager.close()
    }
}
