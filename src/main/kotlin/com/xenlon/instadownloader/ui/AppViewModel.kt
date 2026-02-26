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

    // Login state
    private val _isLoginLoading = MutableStateFlow(false)
    val isLoginLoading: StateFlow<Boolean> = _isLoginLoading.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    // Profile state
    private val _currentProfile = MutableStateFlow<UserProfile?>(null)
    val currentProfile: StateFlow<UserProfile?> = _currentProfile.asStateFlow()

    // Stories state
    private val _stories = MutableStateFlow<DownloadResult<List<StoryItem>>>(DownloadResult.Loading)
    val stories: StateFlow<DownloadResult<List<StoryItem>>> = _stories.asStateFlow()

    // Highlights state
    private val _highlights = MutableStateFlow<DownloadResult<List<HighlightReel>>>(DownloadResult.Loading)
    val highlights: StateFlow<DownloadResult<List<HighlightReel>>> = _highlights.asStateFlow()

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
     * Searches for a user profile.
     */
    fun searchUser(username: String) {
        scope.launch {
            _currentProfile.value = null
            _stories.value = DownloadResult.Loading
            _highlights.value = DownloadResult.Loading

            when (val result = instagramService.fetchUserProfile(username)) {
                is DownloadResult.Success -> {
                    _currentProfile.value = result.data
                    val userId = result.data.userId

                    if (userId.isNotEmpty()) {
                        // Fetch stories and highlights in parallel
                        launch {
                            _stories.value = instagramService.fetchStories(userId)
                        }
                        launch {
                            _highlights.value = instagramService.fetchHighlights(userId)
                        }
                    }
                }
                is DownloadResult.Error -> {
                    _currentProfile.value = null
                    _stories.value = DownloadResult.Error(result.message)
                    _highlights.value = DownloadResult.Error(result.message)
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
            instagramService.logout()
            _currentScreen.value = Screen.LOGIN
            _currentProfile.value = null
            _stories.value = DownloadResult.Loading
            _highlights.value = DownloadResult.Loading
            _loginError.value = null
        }
    }

    /**
     * Opens the download folder in the file manager.
     */
    fun openDownloadFolder() {
        val dir = downloadManager.getDownloadDir()
        try {
            val os = System.getProperty("os.name").lowercase()
            val command = when {
                os.contains("win") -> arrayOf("explorer.exe", dir)
                os.contains("mac") -> arrayOf("open", dir)
                else -> arrayOf("xdg-open", dir)
            }
            Runtime.getRuntime().exec(command)
        } catch (_: Exception) {
            // Silently ignore if file manager can't be opened
        }
    }

    fun dispose() {
        scope.cancel()
        downloadManager.close()
    }
}
