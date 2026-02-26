package com.xenlon.instadownloader

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.xenlon.instadownloader.ui.*

fun main() = application {
    val viewModel = remember { AppViewModel() }

    Window(
        onCloseRequest = {
            viewModel.dispose()
            exitApplication()
        },
        title = "InstaDownloader - Anonymous Instagram Downloader",
        state = WindowState(
            position = WindowPosition(Alignment.Center),
            size = DpSize(900.dp, 700.dp)
        ),
        resizable = true
    ) {
        InstaDownloaderTheme {
            val currentScreen by viewModel.currentScreen.collectAsState()
            val isLoginLoading by viewModel.isLoginLoading.collectAsState()
            val loginError by viewModel.loginError.collectAsState()
            val currentProfile by viewModel.currentProfile.collectAsState()
            val stories by viewModel.stories.collectAsState()
            val highlights by viewModel.highlights.collectAsState()
            val feedPosts by viewModel.feedPosts.collectAsState()
            val downloadProgress by viewModel.downloadProgress.collectAsState()
            val isAnonymousMode by viewModel.isAnonymousMode.collectAsState()

            when (currentScreen) {
                AppViewModel.Screen.LOGIN -> {
                    LoginScreen(
                        onLogin = { username, password -> viewModel.login(username, password) },
                        onAnonymousMode = { viewModel.enterAnonymousMode() },
                        isLoading = isLoginLoading,
                        errorMessage = loginError
                    )
                }

                AppViewModel.Screen.MAIN -> {
                    MainScreen(
                        profile = currentProfile,
                        stories = stories,
                        highlights = highlights,
                        feedPosts = feedPosts,
                        downloadProgress = downloadProgress,
                        isAnonymousMode = isAnonymousMode,
                        onSearchUser = { viewModel.searchUser(it) },
                        onDownloadProfilePic = { viewModel.downloadProfilePicture() },
                        onDownloadStories = { viewModel.downloadStories() },
                        onDownloadHighlight = { viewModel.downloadHighlight(it) },
                        onDownloadAllHighlights = { viewModel.downloadAllHighlights() },
                        onDownloadFeedPosts = { viewModel.downloadFeedPosts() },
                        onLogout = { viewModel.logout() },
                        onOpenDownloadFolder = { viewModel.openDownloadFolder() }
                    )
                }
            }
        }
    }
}
