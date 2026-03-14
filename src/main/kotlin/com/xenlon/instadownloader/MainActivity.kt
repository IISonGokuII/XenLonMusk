package com.xenlon.instadownloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.xenlon.instadownloader.ui.*

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: AppViewModel

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op, just request */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel = AppViewModel(applicationContext)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

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
                val downloadQuality by viewModel.downloadQuality.collectAsState()
                val clipboardUrl by viewModel.clipboardUrl.collectAsState()
                val requestHealth by viewModel.requestHealth.collectAsState()

                when (currentScreen) {
                    AppViewModel.Screen.LOGIN -> {
                        val useSms2FA by viewModel.useSms2FA.collectAsState()
                        LoginScreen(
                            onLogin = { username, password -> viewModel.login(username, password) },
                            onAnonymousMode = { viewModel.enterAnonymousMode() },
                            onVerifyTwoFactor = { code -> viewModel.verifyTwoFactor(code) },
                            onCancelTwoFactor = { viewModel.cancelTwoFactor() },
                            isLoading = isLoginLoading,
                            errorMessage = loginError,
                            isTwoFactorPending = isTwoFactorPending,
                            twoFactorInfo = twoFactorInfo,
                            useSms = useSms2FA,
                            onSwitchToSms = { viewModel.switchToSms2FA() },
                            onSwitchToTotp = { viewModel.switchToTotp2FA() }
                        )
                    }

                    AppViewModel.Screen.MAIN -> {
                        val isSearchLoading by viewModel.isSearchLoading.collectAsState()
                        val searchError by viewModel.searchError.collectAsState()
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
                            downloadQuality = downloadQuality,
                            clipboardUrl = clipboardUrl,
                            requestHealth = requestHealth,
                            isSearchLoading = isSearchLoading,
                            searchError = searchError,
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
                            onToggleQuality = { viewModel.toggleQuality() },
                            onHandleClipboardUrl = { viewModel.handleClipboardUrl() },
                            onDismissClipboardUrl = { viewModel.dismissClipboardUrl() },
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
                            onDeleteItem = { viewModel.deleteGalleryItem(it) },
                            onSaveMultiple = { items ->
                                viewModel.saveMultipleToGallery(context, items)
                                Toast.makeText(
                                    context,
                                    "${items.size} Dateien in Galerie gespeichert",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onDeleteMultiple = { items ->
                                viewModel.deleteMultipleItems(items)
                                Toast.makeText(
                                    context,
                                    "${items.size} Dateien gelöscht",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
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
