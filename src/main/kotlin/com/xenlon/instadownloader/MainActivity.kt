package com.xenlon.instadownloader

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.xenlon.instadownloader.ui.*

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: AppViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel = AppViewModel(applicationContext)

        // Handle share intent on launch
        handleIntent(intent)

        setContent {
            InstaDownloaderTheme {
                val currentScreen by viewModel.currentScreen.collectAsState()
                val isLoginLoading by viewModel.isLoginLoading.collectAsState()
                val loginError by viewModel.loginError.collectAsState()
                val isTwoFactorPending by viewModel.isTwoFactorPending.collectAsState()
                val twoFactorInfo by viewModel.twoFactorInfo.collectAsState()
                val currentProfile by viewModel.currentProfile.collectAsState()
                val stories by viewModel.stories.collectAsState()
                val highlights by viewModel.highlights.collectAsState()
                val feedPosts by viewModel.feedPosts.collectAsState()
                val archivedPosts by viewModel.archivedPosts.collectAsState()
                val reels by viewModel.reels.collectAsState()
                val savedPosts by viewModel.savedPosts.collectAsState()
                val taggedPosts by viewModel.taggedPosts.collectAsState()
                val sharedPost by viewModel.sharedPost.collectAsState()
                val downloadProgress by viewModel.downloadProgress.collectAsState()
                val isAnonymousMode by viewModel.isAnonymousMode.collectAsState()
                val isOwnProfile by viewModel.isOwnProfile.collectAsState()
                val galleryItems by viewModel.galleryItems.collectAsState()
                val searchHistory by viewModel.searchHistory.collectAsState()

                when (currentScreen) {
                    AppViewModel.Screen.LOGIN -> {
                        LoginScreen(
                            onLogin = { username, password -> viewModel.login(username, password) },
                            onAnonymousMode = { viewModel.enterAnonymousMode() },
                            onVerifyTwoFactor = { code -> viewModel.verifyTwoFactor(code) },
                            onCancelTwoFactor = { viewModel.cancelTwoFactor() },
                            isLoading = isLoginLoading,
                            errorMessage = loginError,
                            isTwoFactorPending = isTwoFactorPending,
                            twoFactorInfo = twoFactorInfo
                        )
                    }

                    AppViewModel.Screen.MAIN -> {
                        MainScreen(
                            profile = currentProfile,
                            stories = stories,
                            highlights = highlights,
                            feedPosts = feedPosts,
                            archivedPosts = archivedPosts,
                            reels = reels,
                            savedPosts = savedPosts,
                            taggedPosts = taggedPosts,
                            sharedPost = sharedPost,
                            downloadProgress = downloadProgress,
                            isAnonymousMode = isAnonymousMode,
                            isOwnProfile = isOwnProfile,
                            searchHistory = searchHistory,
                            onSearchUser = { viewModel.searchUser(it) },
                            onDownloadProfilePic = { viewModel.downloadProfilePicture() },
                            onDownloadStories = { viewModel.downloadStories() },
                            onDownloadHighlight = { viewModel.downloadHighlight(it) },
                            onDownloadAllHighlights = { viewModel.downloadAllHighlights() },
                            onDownloadFeedPosts = { viewModel.downloadFeedPosts() },
                            onDownloadArchivedPosts = { viewModel.downloadArchivedPosts() },
                            onDownloadReels = { viewModel.downloadReels() },
                            onDownloadSavedPosts = { viewModel.downloadSavedPosts() },
                            onDownloadTaggedPosts = { viewModel.downloadTaggedPosts() },
                            onDownloadSharedPost = { viewModel.downloadSharedPost() },
                            onDismissSharedPost = { viewModel.dismissSharedPost() },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onRemoveFromHistory = { viewModel.removeFromHistory(it) },
                            onLogout = { viewModel.logout() },
                            onOpenGallery = { viewModel.openGallery() }
                        )
                    }

                    AppViewModel.Screen.GALLERY -> {
                        val context = this@MainActivity
                        GalleryScreen(
                            galleryItems = galleryItems,
                            onBack = { viewModel.closeGallery() },
                            onRefresh = { viewModel.loadGalleryItems() },
                            onSaveToGallery = { item ->
                                viewModel.saveToSystemGallery(context, item)
                                Toast.makeText(
                                    context,
                                    "In Galerie gespeichert",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onDeleteItem = { viewModel.deleteGalleryItem(it) }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (sharedText != null && sharedText.contains("instagram.com")) {
                        viewModel.handleShareIntent(sharedText)
                        Toast.makeText(this, "Instagram-Link erkannt", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            Intent.ACTION_VIEW -> {
                val uri = intent.data
                if (uri != null && uri.host?.contains("instagram.com") == true) {
                    viewModel.handleShareIntent(uri.toString())
                    Toast.makeText(this, "Instagram-Link erkannt", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::viewModel.isInitialized) {
            viewModel.dispose()
        }
    }
}
