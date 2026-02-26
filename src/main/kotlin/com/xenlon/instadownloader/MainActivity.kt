package com.xenlon.instadownloader

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.xenlon.instadownloader.ui.*
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: AppViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel = AppViewModel()

        setContent {
            InstaDownloaderTheme {
                val currentScreen by viewModel.currentScreen.collectAsState()
                val isLoginLoading by viewModel.isLoginLoading.collectAsState()
                val loginError by viewModel.loginError.collectAsState()
                val currentProfile by viewModel.currentProfile.collectAsState()
                val stories by viewModel.stories.collectAsState()
                val highlights by viewModel.highlights.collectAsState()
                val feedPosts by viewModel.feedPosts.collectAsState()
                val archivedPosts by viewModel.archivedPosts.collectAsState()
                val downloadProgress by viewModel.downloadProgress.collectAsState()
                val isAnonymousMode by viewModel.isAnonymousMode.collectAsState()
                val isOwnProfile by viewModel.isOwnProfile.collectAsState()

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
                            archivedPosts = archivedPosts,
                            downloadProgress = downloadProgress,
                            isAnonymousMode = isAnonymousMode,
                            isOwnProfile = isOwnProfile,
                            onSearchUser = { viewModel.searchUser(it) },
                            onDownloadProfilePic = { viewModel.downloadProfilePicture() },
                            onDownloadStories = { viewModel.downloadStories() },
                            onDownloadHighlight = { viewModel.downloadHighlight(it) },
                            onDownloadAllHighlights = { viewModel.downloadAllHighlights() },
                            onDownloadFeedPosts = { viewModel.downloadFeedPosts() },
                            onDownloadArchivedPosts = { viewModel.downloadArchivedPosts() },
                            onLogout = { viewModel.logout() },
                            onOpenDownloadFolder = { openDownloadFolder() }
                        )
                    }
                }
            }
        }
    }

    private fun openDownloadFolder() {
        val downloadsDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "InstaDownloader"
        )
        downloadsDir.mkdirs()

        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    android.net.Uri.parse("content://com.android.externalstorage.documents/document/primary:Download%2FInstaDownloader"),
                    "vnd.android.document/directory"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "Downloads gespeichert in: Downloads/InstaDownloader",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::viewModel.isInitialized) {
            viewModel.dispose()
        }
    }
}
