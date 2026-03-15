package com.xenlon.instadownloader.ui

import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.media.MediaScannerConnection
import android.os.Environment
import android.provider.MediaStore
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.xenlon.instadownloader.model.*
import com.xenlon.instadownloader.service.DownloadManager
import com.xenlon.instadownloader.service.DownloadProgress
import com.xenlon.instadownloader.service.DownloadWorker
import com.xenlon.instadownloader.service.InstagramService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.json.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

class AppViewModel(private val appContext: Context) {
    private val maxVisibleQueueItems = 180

    private val instagramService = InstagramService(appContext)
    private val downloadManager = DownloadManager(instagramService, appContext)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("insta_downloader", Context.MODE_PRIVATE)
    private val fileNameTimestampFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())

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

    // Reels state
    private val _reels = MutableStateFlow<DownloadResult<List<FeedPost>>?>(null)
    val reels: StateFlow<DownloadResult<List<FeedPost>>?> = _reels.asStateFlow()

    // Saved posts state (own account only)
    private val _savedPosts = MutableStateFlow<DownloadResult<List<FeedPost>>?>(null)
    val savedPosts: StateFlow<DownloadResult<List<FeedPost>>?> = _savedPosts.asStateFlow()

    // Tagged posts state
    private val _taggedPosts = MutableStateFlow<DownloadResult<List<FeedPost>>?>(null)
    val taggedPosts: StateFlow<DownloadResult<List<FeedPost>>?> = _taggedPosts.asStateFlow()

    // Shared post (from intent)
    private val _sharedPost = MutableStateFlow<DownloadResult<FeedPost>?>(null)
    val sharedPost: StateFlow<DownloadResult<FeedPost>?> = _sharedPost.asStateFlow()

    // Whether the currently viewed profile is the logged-in user's own profile
    private val _isOwnProfile = MutableStateFlow(false)
    val isOwnProfile: StateFlow<Boolean> = _isOwnProfile.asStateFlow()

    // 2FA state
    private val _twoFactorInfo = MutableStateFlow<TwoFactorInfo?>(null)
    val twoFactorInfo: StateFlow<TwoFactorInfo?> = _twoFactorInfo.asStateFlow()

    private val _isTwoFactorPending = MutableStateFlow(false)
    val isTwoFactorPending: StateFlow<Boolean> = _isTwoFactorPending.asStateFlow()

    // Whether to use SMS (true) or TOTP (false) for 2FA
    private val _useSms2FA = MutableStateFlow(false)
    val useSms2FA: StateFlow<Boolean> = _useSms2FA.asStateFlow()

    // Gallery state
    private val _galleryItems = MutableStateFlow<List<GalleryItem>>(emptyList())
    val galleryItems: StateFlow<List<GalleryItem>> = _galleryItems.asStateFlow()

    // Search history
    private val _searchHistory = MutableStateFlow<List<SearchHistoryEntry>>(emptyList())
    val searchHistory: StateFlow<List<SearchHistoryEntry>> = _searchHistory.asStateFlow()

    // Download progress
    val downloadProgress: StateFlow<DownloadProgress> = downloadManager.downloadProgress
    val requestHealth: StateFlow<RequestHealthState> = instagramService.requestHealth

    private val workManager = WorkManager.getInstance(appContext)
    private val _downloadQueue = MutableStateFlow<List<DownloadQueueItem>>(emptyList())
    val downloadQueue: StateFlow<List<DownloadQueueItem>> = _downloadQueue.asStateFlow()

    // Quality selection
    private val _downloadQuality = MutableStateFlow(DownloadQuality.HD)
    val downloadQuality: StateFlow<DownloadQuality> = _downloadQuality.asStateFlow()

    private val _downloadOnlyNew = MutableStateFlow(true)
    val downloadOnlyNew: StateFlow<Boolean> = _downloadOnlyNew.asStateFlow()

    // Search loading state
    private val _isSearchLoading = MutableStateFlow(false)
    val isSearchLoading: StateFlow<Boolean> = _isSearchLoading.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    // Clipboard monitoring
    private val _clipboardUrl = MutableStateFlow<String?>(null)
    val clipboardUrl: StateFlow<String?> = _clipboardUrl.asStateFlow()
    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null

    init {
        loadSearchHistory()
        loadQualityPreference()
        loadDownloadPreferences()
        startClipboardMonitoring()
        if (instagramService.isAuthenticated()) {
            _currentScreen.value = Screen.MAIN
        }
        observeDownloadQueue()
    }

    // --- Login ---

    fun login(username: String, password: String) {
        scope.launch {
            _isLoginLoading.value = true
            _loginError.value = null

            when (val result = instagramService.login(username, password)) {
                is DownloadResult.Success -> {
                    loggedInUsername = username
                    _currentScreen.value = Screen.MAIN
                    _loginError.value = null
                    _isTwoFactorPending.value = false
                    _twoFactorInfo.value = null
                }
                is DownloadResult.TwoFactorRequired -> {
                    loggedInUsername = username
                    _twoFactorInfo.value = result.twoFactorInfo
                    _isTwoFactorPending.value = true
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

    fun verifyTwoFactor(code: String) {
        val info = _twoFactorInfo.value ?: return
        scope.launch {
            _isLoginLoading.value = true
            _loginError.value = null

            val useTOTP = !_useSms2FA.value && info.totpEnabled

            when (val result = instagramService.verifyTwoFactor(
                code = code,
                identifier = info.identifier,
                username = info.username,
                useTOTP = useTOTP
            )) {
                is DownloadResult.Success -> {
                    loggedInUsername = info.username
                    _currentScreen.value = Screen.MAIN
                    _loginError.value = null
                    _isTwoFactorPending.value = false
                    _twoFactorInfo.value = null
                }
                is DownloadResult.Error -> {
                    _loginError.value = result.message
                }
                else -> {}
            }

            _isLoginLoading.value = false
        }
    }

    fun cancelTwoFactor() {
        _isTwoFactorPending.value = false
        _twoFactorInfo.value = null
        _loginError.value = null
        _useSms2FA.value = false
    }

    fun switchToSms2FA() {
        val info = _twoFactorInfo.value ?: return
        _useSms2FA.value = true
        _loginError.value = null
        // Request SMS code
        scope.launch {
            _isLoginLoading.value = true
            when (val result = instagramService.requestSmsCode(info.username, info.identifier)) {
                is DownloadResult.Success -> {
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

    fun switchToTotp2FA() {
        _useSms2FA.value = false
        _loginError.value = null
    }

    fun enterAnonymousMode() {
        _isAnonymousMode.value = true
        _currentScreen.value = Screen.MAIN
    }

    // --- Profile Search ---

    fun searchUser(username: String) {
        scope.launch {
            _isSearchLoading.value = true
            _searchError.value = null
            _currentProfile.value = null
            _stories.value = DownloadResult.Loading
            _highlights.value = DownloadResult.Loading
            _feedPosts.value = DownloadResult.Loading
            _archivedPosts.value = null
            _reels.value = null
            _savedPosts.value = null
            _taggedPosts.value = null
            _isOwnProfile.value = false
            _sharedPost.value = null

            when (val result = instagramService.fetchUserProfile(username)) {
                is DownloadResult.Success -> {
                    _currentProfile.value = result.data
                    _isSearchLoading.value = false
                    val userId = result.data.userId

                    // Add to search history
                    addToSearchHistory(result.data)

                    // Check if this is the logged-in user's own profile
                    val isOwn = !_isAnonymousMode.value &&
                        instagramService.isAuthenticated() &&
                        (username.equals(loggedInUsername, ignoreCase = true) ||
                            userId == instagramService.getSessionUserId())
                    _isOwnProfile.value = isOwn

                    _feedPosts.value = instagramService.fetchFeedPosts(username, userId)

                    if (userId.isNotEmpty() && !_isAnonymousMode.value) {
                        _stories.value = instagramService.fetchStories(userId)
                        _highlights.value = instagramService.fetchHighlights(userId)
                        _reels.value = DownloadResult.Loading
                        _reels.value = instagramService.fetchReels(userId)
                        _taggedPosts.value = DownloadResult.Loading
                        _taggedPosts.value = instagramService.fetchTaggedPosts(userId)

                        // Own-profile-only data
                        if (isOwn) {
                            _archivedPosts.value = DownloadResult.Loading
                            _archivedPosts.value = instagramService.fetchArchivedPosts()
                            _savedPosts.value = DownloadResult.Loading
                            _savedPosts.value = instagramService.fetchSavedPosts()
                        }
                    }
                }
                is DownloadResult.Error -> {
                    _currentProfile.value = null
                    _isSearchLoading.value = false
                    _searchError.value = result.message
                    _stories.value = DownloadResult.Error(result.message)
                    _highlights.value = DownloadResult.Error(result.message)
                    _feedPosts.value = DownloadResult.Error(result.message)
                }
                else -> {
                    _isSearchLoading.value = false
                }
            }
        }
    }

    // --- Downloads ---

    fun downloadProfilePicture() {
        val profile = _currentProfile.value ?: return
        scope.launch { downloadManager.downloadProfilePicture(profile) }
    }

    fun downloadStories() {
        val profile = _currentProfile.value ?: return
        val storiesResult = _stories.value
        if (storiesResult !is DownloadResult.Success) return
        scope.launch { downloadManager.downloadStories(profile, storiesResult.data) }
    }

    fun downloadHighlight(highlight: HighlightReel) {
        val profile = _currentProfile.value ?: return
        scope.launch { downloadManager.downloadHighlight(profile, highlight) }
    }

    fun downloadAllHighlights() {
        val profile = _currentProfile.value ?: return
        val highlightsResult = _highlights.value
        if (highlightsResult !is DownloadResult.Success) return
        scope.launch { downloadManager.downloadAllHighlights(profile, highlightsResult.data) }
    }

    fun downloadFeedPosts() {
        val profile = _currentProfile.value ?: return
        val postsResult = _feedPosts.value
        if (postsResult !is DownloadResult.Success) return
        scope.launch { downloadManager.downloadFeedPosts(profile, postsResult.data) }
    }

    fun downloadArchivedPosts() {
        val profile = _currentProfile.value ?: return
        val archiveResult = _archivedPosts.value
        if (archiveResult !is DownloadResult.Success) return
        scope.launch { downloadManager.downloadArchivedPosts(profile.username, archiveResult.data) }
    }

    fun downloadReels() {
        val profile = _currentProfile.value ?: return
        val reelsResult = _reels.value
        if (reelsResult !is DownloadResult.Success) return
        scope.launch { downloadManager.downloadReels(profile, reelsResult.data) }
    }

    fun downloadSavedPosts() {
        val savedResult = _savedPosts.value
        if (savedResult !is DownloadResult.Success) return
        scope.launch { downloadManager.downloadSavedPosts(savedResult.data) }
    }

    fun downloadTaggedPosts() {
        val profile = _currentProfile.value ?: return
        val taggedResult = _taggedPosts.value
        if (taggedResult !is DownloadResult.Success) return
        scope.launch { downloadManager.downloadTaggedPosts(profile, taggedResult.data) }
    }

    fun downloadSharedPost() {
        val postResult = _sharedPost.value
        if (postResult !is DownloadResult.Success) return
        scope.launch { downloadManager.downloadSharedPost(postResult.data) }
    }

    fun downloadPreviewItem(category: String, sourceId: String) {
        val profile = _currentProfile.value ?: return
        scope.launch {
            when (category.lowercase()) {
                "stories" -> {
                    val story = (_stories.value as? DownloadResult.Success)?.data
                        ?.firstOrNull { it.id == sourceId }
                        ?: return@launch
                    downloadManager.downloadStories(profile, listOf(story))
                }
                "highlights" -> {
                    val highlight = (_highlights.value as? DownloadResult.Success)?.data
                        ?.firstOrNull { it.id == sourceId }
                        ?: return@launch
                    downloadManager.downloadHighlight(profile, highlight)
                }
                "reels" -> {
                    val post = (_reels.value as? DownloadResult.Success)?.data
                        ?.firstOrNull { it.id == sourceId }
                        ?: return@launch
                    downloadManager.downloadReels(profile, listOf(post))
                }
                "markiert" -> {
                    val post = (_taggedPosts.value as? DownloadResult.Success)?.data
                        ?.firstOrNull { it.id == sourceId }
                        ?: return@launch
                    downloadManager.downloadTaggedPosts(profile, listOf(post))
                }
                "archiv" -> {
                    val post = (_archivedPosts.value as? DownloadResult.Success)?.data
                        ?.firstOrNull { it.id == sourceId }
                        ?: return@launch
                    downloadManager.downloadArchivedPosts(profile.username, listOf(post))
                }
                "gespeichert" -> {
                    val post = (_savedPosts.value as? DownloadResult.Success)?.data
                        ?.firstOrNull { it.id == sourceId }
                        ?: return@launch
                    downloadManager.downloadSavedPosts(listOf(post))
                }
                else -> {
                    val post = (_feedPosts.value as? DownloadResult.Success)?.data
                        ?.firstOrNull { it.id == sourceId }
                        ?: return@launch
                    downloadManager.downloadFeedPosts(profile, listOf(post))
                }
            }
        }
    }

    fun dismissSharedPost() {
        _sharedPost.value = null
    }

    // --- Share Intent ---

    fun handleShareIntent(text: String) {
        scope.launch {
            // Try to extract shortcode from URL
            val shortcode = instagramService.extractShortcodeFromUrl(text)
            if (shortcode != null) {
                _sharedPost.value = DownloadResult.Loading
                _sharedPost.value = instagramService.fetchPostByShortcode(shortcode)

                // Switch to main screen if not already there
                if (_currentScreen.value == Screen.LOGIN && instagramService.isAuthenticated()) {
                    _currentScreen.value = Screen.MAIN
                }
                return@launch
            }

            // Try to extract username
            val username = instagramService.extractUsernameFromUrl(text)
            if (username != null) {
                if (_currentScreen.value == Screen.LOGIN && instagramService.isAuthenticated()) {
                    _currentScreen.value = Screen.MAIN
                }
                searchUser(username)
            }
        }
    }

    // --- Quality Selection ---

    fun toggleQuality() {
        val newQuality = if (_downloadQuality.value == DownloadQuality.HD) DownloadQuality.SD else DownloadQuality.HD
        _downloadQuality.value = newQuality
        instagramService.preferHD = newQuality == DownloadQuality.HD
        prefs.edit().putString("download_quality", newQuality.name).apply()
    }

    fun toggleDownloadOnlyNew() {
        val newValue = !_downloadOnlyNew.value
        _downloadOnlyNew.value = newValue
        downloadManager.onlyNewDownloads = newValue
        prefs.edit().putBoolean("download_only_new", newValue).apply()
    }

    private fun loadQualityPreference() {
        val saved = prefs.getString("download_quality", "HD") ?: "HD"
        val quality = try { DownloadQuality.valueOf(saved) } catch (_: Exception) { DownloadQuality.HD }
        _downloadQuality.value = quality
        instagramService.preferHD = quality == DownloadQuality.HD
    }

    private fun loadDownloadPreferences() {
        val onlyNew = prefs.getBoolean("download_only_new", true)
        _downloadOnlyNew.value = onlyNew
        downloadManager.onlyNewDownloads = onlyNew
    }

    // --- Clipboard Monitoring ---

    private fun startClipboardMonitoring() {
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: return@OnPrimaryClipChangedListener
                if (text.contains("instagram.com/")) {
                    _clipboardUrl.value = text
                }
            }
        }
        clipboard.addPrimaryClipChangedListener(clipboardListener)
    }

    fun handleClipboardUrl() {
        val url = _clipboardUrl.value ?: return
        _clipboardUrl.value = null
        handleShareIntent(url)
    }

    fun dismissClipboardUrl() {
        _clipboardUrl.value = null
    }

    // --- Batch Gallery Operations ---

    fun saveMultipleToGallery(context: Context, items: List<GalleryItem>) {
        items.forEach { saveToSystemGallery(context, it) }
    }

    fun deleteMultipleItems(items: List<GalleryItem>) {
        scope.launch(Dispatchers.IO) {
            items.forEach { item ->
                if (item.file.exists()) item.file.delete()
                metadataFileFor(item.file).delete()
            }
            _galleryItems.value = _galleryItems.value.filter { existing ->
                items.none { it.file.absolutePath == existing.file.absolutePath }
            }
        }
    }

    // --- Gallery ---

    fun openGallery() {
        loadGalleryItems()
        _currentScreen.value = Screen.GALLERY
    }

    fun closeGallery() {
        _currentScreen.value = Screen.MAIN
    }

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
                    val metadata = readMetadata(file)
                    val username = metadata?.username?.takeIf { it.isNotBlank() }
                        ?: if (parts.size >= 2) parts[0] else ""
                    val category = metadata?.category?.takeIf { it.isNotBlank() }
                        ?: if (parts.size >= 3) parts[1] else
                            if (file.name.contains("profile_pic")) "profile" else "posts"
                    val sourceTimestamp = metadata?.sourceTimestamp?.takeIf { it > 0L }
                        ?: deriveSourceTimestamp(file)

                    GalleryItem(
                        file = file,
                        name = file.name,
                        isVideo = metadata?.isVideo ?: (file.extension.lowercase() in listOf("mp4", "mov")),
                        sizeBytes = file.length(),
                        lastModified = file.lastModified(),
                        sourceTimestamp = sourceTimestamp,
                        username = username,
                        category = category,
                        caption = metadata?.caption.orEmpty(),
                        shortcode = metadata?.shortcode.orEmpty(),
                        sourceId = metadata?.sourceId.orEmpty(),
                        highlightTitle = metadata?.highlightTitle.orEmpty(),
                    )
                }
                .sortedByDescending { it.sourceTimestamp.takeIf { ts -> ts > 0L } ?: it.lastModified }
                .toList()

            _galleryItems.value = items
        }
    }

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

                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(uri.path ?: ""),
                        arrayOf(mimeType),
                        null
                    )
                }
            } catch (_: Exception) {
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

    fun deleteGalleryItem(item: GalleryItem) {
        scope.launch(Dispatchers.IO) {
            if (item.file.exists()) {
                item.file.delete()
            }
            metadataFileFor(item.file).delete()
            _galleryItems.value = _galleryItems.value.filter { it.file.absolutePath != item.file.absolutePath }
        }
    }

    fun retryQueueItem(item: DownloadQueueItem) {
        scope.launch(Dispatchers.IO) {
            DownloadWorker.enqueueDownload(
                context = appContext,
                url = item.sourceUrl,
                outputPath = item.outputPath,
                label = item.label,
                metadata = item.metadataJson.takeIf { it.isNotBlank() }?.let {
                    runCatching { json.decodeFromString<DownloadedMediaMetadata>(it) }.getOrNull()
                },
            )
        }
    }

    fun cancelQueueItem(item: DownloadQueueItem) {
        scope.launch(Dispatchers.IO) {
            runCatching { workManager.cancelWorkById(UUID.fromString(item.workId)) }
        }
    }

    fun retryFailedQueueItems() {
        val retryableItems = _downloadQueue.value.filter {
            it.status == DownloadQueueStatus.FAILED || it.status == DownloadQueueStatus.CANCELLED
        }
        retryableItems.forEach { retryQueueItem(it) }
    }

    fun cancelActiveQueueItems() {
        val activeItems = _downloadQueue.value.filter {
            it.status == DownloadQueueStatus.WAITING || it.status == DownloadQueueStatus.RUNNING
        }
        activeItems.forEach { cancelQueueItem(it) }
    }

    fun clearFinishedQueueItems() {
        val finishedIds = _downloadQueue.value
            .filter { it.status == DownloadQueueStatus.COMPLETED }
            .map { it.workId }

        scope.launch(Dispatchers.IO) {
            runCatching { workManager.pruneWork() }
            finishedIds.forEach { workId ->
                DownloadWorker.removeQueuePayload(appContext, workId)
            }
        }
    }

    private fun readMetadata(file: File): DownloadedMediaMetadata? {
        val metadataFile = metadataFileFor(file)
        if (!metadataFile.exists()) return null
        return runCatching {
            json.decodeFromString<DownloadedMediaMetadata>(metadataFile.readText())
        }.getOrNull()
    }

    private fun metadataFileFor(file: File): File = File("${file.absolutePath}.meta.json")

    private fun deriveSourceTimestamp(file: File): Long {
        val rawTimestamp = Regex("(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2})")
            .find(file.name)
            ?.groupValues
            ?.getOrNull(1)
            ?: return 0L
        return runCatching { fileNameTimestampFormat.parse(rawTimestamp)?.time ?: 0L }.getOrDefault(0L)
    }

    // --- Search History ---

    private fun loadSearchHistory() {
        val historyJson = prefs.getString("search_history", "[]") ?: "[]"
        try {
            val jsonArray = json.decodeFromString<JsonArray>(historyJson)
            val entries = jsonArray.mapNotNull { element ->
                val obj = element.jsonObject
                SearchHistoryEntry(
                    username = obj["username"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    fullName = obj["fullName"]?.jsonPrimitive?.content ?: "",
                    profilePicUrl = obj["profilePicUrl"]?.jsonPrimitive?.content ?: "",
                    isFavorite = obj["isFavorite"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
                    lastSearched = obj["lastSearched"]?.jsonPrimitive?.longOrNull ?: 0
                )
            }
            _searchHistory.value = entries.sortedWith(
                compareByDescending<SearchHistoryEntry> { it.isFavorite }
                    .thenByDescending { it.lastSearched }
            )
        } catch (_: Exception) {
            _searchHistory.value = emptyList()
        }
    }

    private fun observeDownloadQueue() {
        scope.launch {
            workManager.getWorkInfosByTagFlow("insta_download_queue").collectLatest { workInfos ->
                _downloadQueue.value = workInfos
                    .sortedWith(
                        compareBy<WorkInfo> { it.state.sortPriority() }
                            .thenByDescending { it.runAttemptCount }
                    )
                    .take(maxVisibleQueueItems)
                    .map { info -> info.toQueueItem() }
            }
        }
    }

    private fun WorkInfo.State.sortPriority(): Int = when (this) {
        WorkInfo.State.RUNNING -> 0
        WorkInfo.State.ENQUEUED -> 1
        WorkInfo.State.BLOCKED -> 2
        WorkInfo.State.FAILED -> 3
        WorkInfo.State.CANCELLED -> 4
        WorkInfo.State.SUCCEEDED -> 5
    }

    private fun WorkInfo.toQueueItem(): DownloadQueueItem {
        val status = when (state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadQueueStatus.WAITING
            WorkInfo.State.RUNNING -> DownloadQueueStatus.RUNNING
            WorkInfo.State.SUCCEEDED -> DownloadQueueStatus.COMPLETED
            WorkInfo.State.FAILED -> DownloadQueueStatus.FAILED
            WorkInfo.State.CANCELLED -> DownloadQueueStatus.CANCELLED
        }
        val payload = DownloadWorker.getQueuePayload(appContext, id.toString())

        return DownloadQueueItem(
            workId = id.toString(),
            label = progress.getString(DownloadWorker.KEY_LABEL)
                ?: outputData.getString(DownloadWorker.KEY_LABEL)
                ?: payload?.label
                ?: "Download",
            outputPath = payload?.outputPath.orEmpty(),
            sourceUrl = payload?.sourceUrl.orEmpty(),
            status = status,
            metadataJson = payload?.metadataJson.orEmpty(),
        )
    }

    private fun saveSearchHistory() {
        val jsonArray = buildJsonArray {
            _searchHistory.value.forEach { entry ->
                add(buildJsonObject {
                    put("username", entry.username)
                    put("fullName", entry.fullName)
                    put("profilePicUrl", entry.profilePicUrl)
                    put("isFavorite", entry.isFavorite)
                    put("lastSearched", entry.lastSearched)
                })
            }
        }
        prefs.edit().putString("search_history", jsonArray.toString()).apply()
    }

    private fun addToSearchHistory(profile: UserProfile) {
        val existing = _searchHistory.value.toMutableList()
        val existingIndex = existing.indexOfFirst { it.username.equals(profile.username, ignoreCase = true) }

        val entry = SearchHistoryEntry(
            username = profile.username,
            fullName = profile.fullName,
            profilePicUrl = profile.profilePicUrl,
            isFavorite = if (existingIndex >= 0) existing[existingIndex].isFavorite else false,
            lastSearched = System.currentTimeMillis()
        )

        if (existingIndex >= 0) {
            existing[existingIndex] = entry
        } else {
            existing.add(0, entry)
        }

        // Keep max 50 entries
        if (existing.size > 50) {
            val nonFavorites = existing.filter { !it.isFavorite }.sortedByDescending { it.lastSearched }
            val favorites = existing.filter { it.isFavorite }
            _searchHistory.value = (favorites + nonFavorites).take(50)
        } else {
            _searchHistory.value = existing.sortedWith(
                compareByDescending<SearchHistoryEntry> { it.isFavorite }
                    .thenByDescending { it.lastSearched }
            )
        }

        saveSearchHistory()
    }

    fun toggleFavorite(entry: SearchHistoryEntry) {
        val updated = _searchHistory.value.map {
            if (it.username == entry.username) it.copy(isFavorite = !it.isFavorite) else it
        }.sortedWith(
            compareByDescending<SearchHistoryEntry> { it.isFavorite }
                .thenByDescending { it.lastSearched }
        )
        _searchHistory.value = updated
        saveSearchHistory()
    }

    fun removeFromHistory(entry: SearchHistoryEntry) {
        _searchHistory.value = _searchHistory.value.filter { it.username != entry.username }
        saveSearchHistory()
    }

    // --- Logout ---

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
            _reels.value = null
            _savedPosts.value = null
            _taggedPosts.value = null
            _sharedPost.value = null
            _isOwnProfile.value = false
            _loginError.value = null
            _isAnonymousMode.value = false
            _isTwoFactorPending.value = false
            _twoFactorInfo.value = null
            _useSms2FA.value = false
            loggedInUsername = ""
        }
    }

    fun getDownloadDir(): String = downloadManager.getDownloadDir()

    fun dispose() {
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboardListener?.let { clipboard?.removePrimaryClipChangedListener(it) }
        scope.cancel()
        downloadManager.close()
    }
}
