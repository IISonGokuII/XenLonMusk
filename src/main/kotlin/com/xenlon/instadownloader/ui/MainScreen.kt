package com.xenlon.instadownloader.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xenlon.instadownloader.model.*
import com.xenlon.instadownloader.service.DownloadProgress

private data class ProfilePreviewItem(
    val id: String,
    val sourceId: String,
    val imageUrl: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val isVideo: Boolean = false,
    val caption: String = "",
    val timestampMillis: Long = 0L,
    val mediaCount: Int = 1,
    val likeCount: Long = 0L,
    val commentCount: Long = 0L,
    val actionLabel: String = "Jetzt herunterladen",
)

private data class ProfilePreviewTab(
    val label: String,
    val count: Int,
)

private data class ContentSectionTab(
    val id: String,
    val label: String,
)

private enum class ContentSortMode(val label: String) {
    NEWEST("Neueste"),
    OLDEST("Aelteste"),
    MOST_LIKED("Top")
}

private enum class QueueFilterTab(val label: String) {
    ALL("Alle"),
    ACTIVE("Aktiv"),
    FAILED("Fehler"),
    COMPLETED("Fertig")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    profile: UserProfile?,
    stories: DownloadResult<List<StoryItem>>,
    highlights: DownloadResult<List<HighlightReel>>,
    feedPosts: DownloadResult<List<FeedPost>>,
    archivedPosts: DownloadResult<List<FeedPost>>?,
    reels: DownloadResult<List<FeedPost>>?,
    savedPosts: DownloadResult<List<FeedPost>>?,
    taggedPosts: DownloadResult<List<FeedPost>>?,
    sharedPost: DownloadResult<FeedPost>?,
    downloadProgress: DownloadProgress,
    downloadQueue: List<DownloadQueueItem>,
    isAnonymousMode: Boolean = false,
    isOwnProfile: Boolean = false,
    searchHistory: List<SearchHistoryEntry> = emptyList(),
    downloadQuality: DownloadQuality = DownloadQuality.HD,
    downloadOnlyNew: Boolean = true,
    clipboardUrl: String? = null,
    requestHealth: RequestHealthState = RequestHealthState(),
    isSearchLoading: Boolean = false,
    searchError: String? = null,
    onSearchUser: (String) -> Unit,
    onDownloadProfilePic: () -> Unit,
    onDownloadStories: () -> Unit,
    onDownloadHighlight: (HighlightReel) -> Unit,
    onDownloadAllHighlights: () -> Unit,
    onDownloadFeedPosts: () -> Unit,
    onDownloadArchivedPosts: () -> Unit,
    onDownloadReels: () -> Unit,
    onDownloadSavedPosts: () -> Unit,
    onDownloadTaggedPosts: () -> Unit,
    onDownloadSharedPost: () -> Unit,
    onDismissSharedPost: () -> Unit,
    onToggleFavorite: (SearchHistoryEntry) -> Unit,
    onRemoveFromHistory: (SearchHistoryEntry) -> Unit,
    onToggleQuality: () -> Unit = {},
    onToggleDownloadOnlyNew: () -> Unit = {},
    onDownloadPreviewItem: (category: String, sourceId: String) -> Unit = { _, _ -> },
    onRetryQueueItem: (DownloadQueueItem) -> Unit = {},
    onCancelQueueItem: (DownloadQueueItem) -> Unit = {},
    onRetryFailedQueueItems: () -> Unit = {},
    onCancelActiveQueueItems: () -> Unit = {},
    onClearFinishedQueueItems: () -> Unit = {},
    onHandleClipboardUrl: () -> Unit = {},
    onDismissClipboardUrl: () -> Unit = {},
    onLogout: () -> Unit,
    onOpenQueue: () -> Unit = {},
    onOpenGallery: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var profileSearchQuery by remember(profile?.username) { mutableStateOf("") }
    var selectedSectionTab by remember(profile?.username) { mutableStateOf("posts") }
    var contentSortMode by remember(profile?.username) { mutableStateOf(ContentSortMode.NEWEST) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Top bar
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(InstagramPurple, InstagramPink, InstagramOrange)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "InstaDownloader",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            },
            actions = {
                IconButton(onClick = onOpenGallery) {
                    Icon(Icons.Default.PhotoLibrary, "Galerie", tint = TextSecondary)
                }
                IconButton(onClick = onOpenQueue) {
                    Icon(Icons.Default.PendingActions, "Queue", tint = TextSecondary)
                }
                IconButton(onClick = onLogout) {
                    Icon(Icons.AutoMirrored.Filled.Logout, "Abmelden", tint = TextSecondary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DarkSurface,
                titleContentColor = TextPrimary
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Anonymous mode banner
            if (isAnonymousMode) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2A3D))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = AccentPurple,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Anonymer Modus - Profilsuche, Profilbilder & gepostete Bilder ohne Login. " +
                                "Für Stories, Highlights, Reels & mehr melde dich an.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Clipboard link banner
            if (clipboardUrl != null) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2A3D))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentPaste,
                            contentDescription = null,
                            tint = AccentPurple,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Instagram-Link in Zwischenablage erkannt",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onHandleClipboardUrl) {
                            Text("Öffnen", color = AccentPink, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = onDismissClipboardUrl, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (requestHealth.level != RequestHealthLevel.IDLE && requestHealth.message.isNotBlank()) {
                RequestHealthBanner(requestHealth = requestHealth)
            }

            // Shared post banner (from Intent)
            if (sharedPost != null) {
                SharedPostBanner(
                    sharedPost = sharedPost,
                    onDownload = onDownloadSharedPost,
                    onDismiss = onDismissSharedPost
                )
            }

            // Quality toggle
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleQuality() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.HighQuality,
                            contentDescription = null,
                            tint = if (downloadQuality == DownloadQuality.HD) SuccessGreen else WarningOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Download-Qualität",
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            downloadQuality.label,
                            color = if (downloadQuality == DownloadQuality.HD) SuccessGreen else WarningOrange,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Update,
                        contentDescription = null,
                        tint = if (downloadOnlyNew) SuccessGreen else WarningOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Nur neue Inhalte herunterladen",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (downloadOnlyNew) {
                                "Bereits bekannte Dateien werden anhand von Pfad und Metadaten uebersprungen."
                            } else {
                                "Auch vorhandene Inhalte koennen erneut geladen werden."
                            },
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                    Switch(
                        checked = downloadOnlyNew,
                        onCheckedChange = { onToggleDownloadOnlyNew() }
                    )
                }
            }

            // Search bar
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = {
                    if (searchQuery.isNotBlank()) {
                        onSearchUser(searchQuery.trim().removePrefix("@"))
                    }
                },
                isSearching = isSearchLoading
            )

            // Search error message
            if (searchError != null) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3D1111)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = searchError,
                        color = Color(0xFFFF6B6B),
                        modifier = Modifier.padding(16.dp),
                        fontSize = 14.sp
                    )
                }
            }

            // Search history (show when no profile is loaded)
            if (profile == null && !isSearchLoading && searchHistory.isNotEmpty()) {
                SearchHistorySection(
                    history = searchHistory,
                    onSelect = { entry ->
                        searchQuery = entry.username
                        onSearchUser(entry.username)
                    },
                    onToggleFavorite = onToggleFavorite,
                    onRemove = onRemoveFromHistory
                )
            }

            // Download progress
            AnimatedVisibility(visible = downloadProgress !is DownloadProgress.Idle) {
                DownloadProgressCard(downloadProgress)
            }

            if (downloadQueue.isNotEmpty()) {
                DownloadQueueSummaryCard(
                    queueItems = downloadQueue,
                    onOpenQueue = onOpenQueue
                )
            }

            // Profile section
            if (profile != null) {
                ProfileCard(
                    profile = profile,
                    onDownloadProfilePic = onDownloadProfilePic
                )

                ProfileExplorerCard(
                    profile = profile,
                    stories = stories.sortStoriesResult(contentSortMode),
                    highlights = highlights.sortHighlightsResult(contentSortMode),
                    feedPosts = feedPosts.sortFeedPostsResult(contentSortMode),
                    reels = reels.sortNullableFeedPostsResult(contentSortMode),
                    taggedPosts = taggedPosts.sortNullableFeedPostsResult(contentSortMode),
                    archivedPosts = archivedPosts.sortNullableFeedPostsResult(contentSortMode),
                    savedPosts = savedPosts.sortNullableFeedPostsResult(contentSortMode),
                    query = profileSearchQuery,
                    onQueryChange = { profileSearchQuery = it },
                    onDownloadItem = onDownloadPreviewItem
                )

                ContentOverviewCard(
                    stories = stories,
                    highlights = highlights,
                    feedPosts = feedPosts,
                    reels = reels,
                    taggedPosts = taggedPosts,
                    archivedPosts = archivedPosts,
                    savedPosts = savedPosts
                )

                val contentTabs = buildList {
                    add(ContentSectionTab("posts", "Posts ${feedPosts.feedResultCount()}"))
                    add(ContentSectionTab("reels", "Reels ${reels.feedResultCount()}"))
                    add(ContentSectionTab("stories", "Stories ${stories.storyResultCount()}"))
                    add(ContentSectionTab("highlights", "Highlights ${highlights.highlightResultCount()}"))
                    if (!isAnonymousMode && taggedPosts != null) add(ContentSectionTab("tagged", "Markiert ${taggedPosts.feedResultCount()}"))
                    if (isOwnProfile && archivedPosts != null) add(ContentSectionTab("archive", "Archiv ${archivedPosts.feedResultCount()}"))
                    if (isOwnProfile && savedPosts != null) add(ContentSectionTab("saved", "Gespeichert ${savedPosts.feedResultCount()}"))
                }

                if (contentTabs.none { it.id == selectedSectionTab }) {
                    selectedSectionTab = contentTabs.firstOrNull()?.id ?: "posts"
                }

                ProfileSectionsTabBar(
                    tabs = contentTabs,
                    selectedTab = selectedSectionTab,
                    onSelect = { selectedSectionTab = it }
                )

                ContentSortBar(
                    selectedMode = contentSortMode,
                    onSelect = { contentSortMode = it }
                )

                when (selectedSectionTab) {
                    "stories" -> {
                        if (isAnonymousMode) {
                            AnonymousLimitCard(
                                title = "Stories",
                                icon = Icons.Default.AutoStories,
                                gradientColors = listOf(InstagramPink, InstagramOrange)
                            )
                        } else {
                            StoriesSection(
                                stories = stories.sortStoriesResult(contentSortMode),
                                onDownloadStories = onDownloadStories
                            )
                        }
                    }
                    "highlights" -> {
                        if (isAnonymousMode) {
                            AnonymousLimitCard(
                                title = "Highlights",
                                icon = Icons.Default.Stars,
                                gradientColors = listOf(InstagramPurple, InstagramPink)
                            )
                        } else {
                            HighlightsSection(
                                highlights = highlights.sortHighlightsResult(contentSortMode),
                                onDownloadHighlight = onDownloadHighlight,
                                onDownloadAllHighlights = onDownloadAllHighlights
                            )
                        }
                    }
                    "reels" -> {
                        if (!isAnonymousMode && reels != null) {
                            ReelsSection(
                                reels = reels.sortFeedPostsResult(contentSortMode),
                                onDownloadReels = onDownloadReels
                            )
                        } else {
                            AnonymousLimitCard(
                                title = "Reels",
                                icon = Icons.Default.VideoLibrary,
                                gradientColors = listOf(Color(0xFFE040FB), Color(0xFFFF4081))
                            )
                        }
                    }
                    "tagged" -> {
                        if (!isAnonymousMode && taggedPosts != null) {
                            TaggedPostsSection(
                                taggedPosts = taggedPosts.sortFeedPostsResult(contentSortMode),
                                onDownloadTagged = onDownloadTaggedPosts
                            )
                        }
                    }
                    "archive" -> {
                        if (isOwnProfile && archivedPosts != null) {
                            ArchivedPostsSection(
                                archivedPosts = archivedPosts.sortFeedPostsResult(contentSortMode),
                                onDownloadArchive = onDownloadArchivedPosts
                            )
                        }
                    }
                    "saved" -> {
                        if (isOwnProfile && savedPosts != null) {
                            SavedPostsSection(
                                savedPosts = savedPosts.sortFeedPostsResult(contentSortMode),
                                onDownloadSaved = onDownloadSavedPosts
                            )
                        }
                    }
                    else -> {
                        FeedPostsSection(
                            feedPosts = feedPosts.sortFeedPostsResult(contentSortMode),
                            onDownloadPosts = onDownloadFeedPosts
                        )
                    }
                }
            } else if (!isSearchLoading && searchError == null) {
                // Welcome card
                WelcomeCard()
            }
        }
    }
}

@Composable
private fun RequestHealthBanner(requestHealth: RequestHealthState) {
    val now = System.currentTimeMillis()
    val cooldownSeconds = ((requestHealth.cooldownUntilMillis - now).coerceAtLeast(0L) / 1000L)
    val background = when (requestHealth.level) {
        RequestHealthLevel.COOLDOWN -> Color(0xFF3B2A12)
        RequestHealthLevel.WARNING -> Color(0xFF3D1111)
        RequestHealthLevel.ACTIVE -> Color(0xFF102A24)
        RequestHealthLevel.IDLE -> DarkSurface
    }
    val accent = when (requestHealth.level) {
        RequestHealthLevel.COOLDOWN -> WarningOrange
        RequestHealthLevel.WARNING -> ErrorRed
        RequestHealthLevel.ACTIVE -> SuccessGreen
        RequestHealthLevel.IDLE -> TextSecondary
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = when (requestHealth.level) {
                    RequestHealthLevel.COOLDOWN -> Icons.Default.Schedule
                    RequestHealthLevel.WARNING -> Icons.Default.Warning
                    else -> Icons.Default.Shield
                },
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = requestHealth.message,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Text(
                    text = if (cooldownSeconds > 0) {
                        "Schonmodus noch ca. ${cooldownSeconds}s, letzte Minute: ${requestHealth.recentRequestCount} Requests"
                    } else {
                        "Letzte Minute: ${requestHealth.recentRequestCount} Requests"
                    },
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun ProfileSectionsTabBar(
    tabs: List<ContentSectionTab>,
    selectedTab: String,
    onSelect: (String) -> Unit
) {
    if (tabs.isEmpty()) return

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        ScrollableTabRow(
            selectedTabIndex = tabs.indexOfFirst { it.id == selectedTab }.coerceAtLeast(0),
            containerColor = Color.Transparent,
            edgePadding = 0.dp,
            divider = {}
        ) {
            tabs.forEach { tab ->
                Tab(
                    selected = selectedTab == tab.id,
                    onClick = { onSelect(tab.id) },
                    text = {
                        Text(
                            tab.label,
                            maxLines = 1,
                            fontWeight = if (selectedTab == tab.id) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ContentSortBar(
    selectedMode: ContentSortMode,
    onSelect: (ContentSortMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ContentSortMode.entries.forEach { mode ->
            FilterChip(
                selected = selectedMode == mode,
                onClick = { onSelect(mode) },
                label = { Text(mode.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentPink,
                    selectedLabelColor = Color.White,
                    containerColor = DarkSurface,
                    labelColor = TextSecondary
                )
            )
        }
    }
}

@Composable
private fun ContentOverviewCard(
    stories: DownloadResult<List<StoryItem>>,
    highlights: DownloadResult<List<HighlightReel>>,
    feedPosts: DownloadResult<List<FeedPost>>,
    reels: DownloadResult<List<FeedPost>>?,
    taggedPosts: DownloadResult<List<FeedPost>>?,
    archivedPosts: DownloadResult<List<FeedPost>>?,
    savedPosts: DownloadResult<List<FeedPost>>?,
) {
    val items = listOf(
        Triple("Posts", feedPosts.feedResultCount(), feedPosts.feedStateLabel()),
        Triple("Reels", reels.feedResultCount(), reels.feedStateLabel()),
        Triple("Stories", stories.storyResultCount(), stories.storyStateLabel()),
        Triple("Highlights", highlights.highlightResultCount(), highlights.highlightStateLabel()),
        Triple("Markiert", taggedPosts.feedResultCount(), taggedPosts.feedStateLabel()),
        Triple("Archiv", archivedPosts.feedResultCount(), archivedPosts.feedStateLabel()),
        Triple("Gespeichert", savedPosts.feedResultCount(), savedPosts.feedStateLabel()),
    ).filterNot { it.first in listOf("Markiert", "Archiv", "Gespeichert") && it.third == "Aus" }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Inhaltsstatus",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            items.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { (label, count, state) ->
                        StatusMetricTile(
                            title = label,
                            value = count.toString(),
                            status = state,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(2 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusMetricTile(
    title: String,
    value: String,
    status: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, color = TextSecondary, fontSize = 12.sp)
            Text(value, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(status, color = AccentPink, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    isSearching: Boolean
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text("Instagram-Benutzernamen eingeben...", color = TextSecondary)
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary)
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = AccentPink
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onSearch,
                enabled = query.isNotBlank() && !isSearching,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentPink,
                    disabledContainerColor = Color(0xFF444458)
                ),
                modifier = Modifier.height(48.dp)
            ) {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Suchen", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SearchHistorySection(
    history: List<SearchHistoryEntry>,
    onSelect: (SearchHistoryEntry) -> Unit,
    onToggleFavorite: (SearchHistoryEntry) -> Unit,
    onRemove: (SearchHistoryEntry) -> Unit
) {
    val context = LocalContext.current

    SectionCard(
        title = "Suchverlauf",
        icon = Icons.Default.History,
        gradientColors = listOf(Color(0xFF607D8B), Color(0xFF455A64))
    ) {
        // Favorites first, then recent
        val favorites = history.filter { it.isFavorite }
        val recent = history.filter { !it.isFavorite }.take(8)

        if (favorites.isNotEmpty()) {
            Text(
                "Favoriten",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AccentPink
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(favorites) { entry ->
                    HistoryChip(
                        entry = entry,
                        context = context,
                        onClick = { onSelect(entry) },
                        onToggleFavorite = { onToggleFavorite(entry) },
                        onRemove = { onRemove(entry) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (recent.isNotEmpty()) {
            Text(
                "Letzte Suchen",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(recent) { entry ->
                    HistoryChip(
                        entry = entry,
                        context = context,
                        onClick = { onSelect(entry) },
                        onToggleFavorite = { onToggleFavorite(entry) },
                        onRemove = { onRemove(entry) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryChip(
    entry: SearchHistoryEntry,
    context: android.content.Context,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRemove: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .width(100.dp)
            .clickable { onClick() }
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = { showMenu = true }
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile pic or initial
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(InstagramPurple, InstagramPink, InstagramOrange)
                        )
                    )
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (entry.profilePicUrl.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(entry.profilePicUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = entry.username,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        entry.username.firstOrNull()?.uppercase() ?: "?",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentPink
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                entry.username,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            if (entry.isFavorite) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = InstagramYellow,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = {
                    Text(if (entry.isFavorite) "Favorit entfernen" else "Als Favorit")
                },
                onClick = { onToggleFavorite(); showMenu = false },
                leadingIcon = {
                    Icon(
                        if (entry.isFavorite) Icons.Default.StarBorder else Icons.Default.Star,
                        contentDescription = null,
                        tint = InstagramYellow
                    )
                }
            )
            DropdownMenuItem(
                text = { Text("Aus Verlauf entfernen") },
                onClick = { onRemove(); showMenu = false },
                leadingIcon = {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed)
                }
            )
        }
    }
}

@Composable
private fun ProfileCard(
    profile: UserProfile,
    onDownloadProfilePic: () -> Unit
) {
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Profile picture with Coil + gradient border
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(InstagramPurple, InstagramPink, InstagramOrange)
                            )
                        )
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(DarkSurface)
                        .clickable { onDownloadProfilePic() },
                    contentAlignment = Alignment.Center
                ) {
                    if (profile.profilePicUrl.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(profile.profilePicUrlHD.ifEmpty { profile.profilePicUrl })
                                .crossfade(true)
                                .build(),
                            contentDescription = "${profile.username} Profilbild",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = profile.username.firstOrNull()?.uppercase() ?: "?",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentPink
                        )
                    }
                }

                Spacer(modifier = Modifier.width(20.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.fullName.ifEmpty { profile.username },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "@${profile.username}",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                    if (profile.isPrivate) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = WarningOrange,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Privates Profil",
                                fontSize = 12.sp,
                                color = WarningOrange
                            )
                        }
                    }
                }

                // Download profile pic button
                FilledTonalButton(
                    onClick = onDownloadProfilePic,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = DarkSurfaceVariant
                    )
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Profilbild herunterladen",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Profilbild", fontSize = 13.sp)
                }
            }

            if (profile.biography.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = profile.biography,
                    fontSize = 14.sp,
                    color = TextSecondary,
                    lineHeight = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("Beiträge", formatCount(profile.postCount))
                StatItem("Follower", formatCount(profile.followerCount))
                StatItem("Folgt", formatCount(profile.followingCount))
            }
        }
    }
}

@Composable
private fun ProfileExplorerCard(
    profile: UserProfile,
    stories: DownloadResult<List<StoryItem>>,
    highlights: DownloadResult<List<HighlightReel>>,
    feedPosts: DownloadResult<List<FeedPost>>,
    reels: DownloadResult<List<FeedPost>>?,
    taggedPosts: DownloadResult<List<FeedPost>>?,
    archivedPosts: DownloadResult<List<FeedPost>>?,
    savedPosts: DownloadResult<List<FeedPost>>?,
    query: String,
    onQueryChange: (String) -> Unit,
    onDownloadItem: (category: String, sourceId: String) -> Unit,
) {
    val normalizedQuery = query.trim().lowercase()
    var selectedTab by remember(profile.username) { mutableStateOf("Alle") }
    var viewerStartIndex by remember(profile.username) { mutableIntStateOf(-1) }
    val previewItems = remember(
        profile.username,
        stories,
        highlights,
        feedPosts,
        reels,
        taggedPosts,
        archivedPosts,
        savedPosts,
        normalizedQuery
    ) {
        buildList {
            if (stories is DownloadResult.Success) {
                stories.data.forEach { story ->
                    add(
                        ProfilePreviewItem(
                            id = "story-${story.id}",
                            sourceId = story.id,
                            imageUrl = story.thumbnailUrl.ifEmpty { story.mediaUrl },
                            title = "Story",
                            subtitle = formatPreviewTimestamp(story.timestamp * 1000L),
                            category = "Stories",
                            isVideo = story.type == MediaType.VIDEO,
                            timestampMillis = story.timestamp * 1000L,
                            actionLabel = "Story herunterladen",
                        )
                    )
                }
            }
            if (highlights is DownloadResult.Success) {
                highlights.data.forEach { highlight ->
                    add(
                        ProfilePreviewItem(
                            id = "highlight-${highlight.id}",
                            sourceId = highlight.id,
                            imageUrl = highlight.coverImageUrl,
                            title = highlight.title,
                            subtitle = "${highlight.items.size} Elemente",
                            category = "Highlights",
                            mediaCount = highlight.items.size.coerceAtLeast(1),
                            actionLabel = "Highlight herunterladen",
                        )
                    )
                }
            }
            addAll(feedPosts.toPreviewItems("Posts"))
            addAll(reels.toPreviewItems("Reels"))
            addAll(taggedPosts.toPreviewItems("Markiert"))
            addAll(archivedPosts.toPreviewItems("Archiv"))
            addAll(savedPosts.toPreviewItems("Gespeichert"))
        }.filter { item ->
            normalizedQuery.isBlank() ||
                item.title.lowercase().contains(normalizedQuery) ||
                item.subtitle.lowercase().contains(normalizedQuery) ||
                item.category.lowercase().contains(normalizedQuery)
        }
    }

    val tabs = remember(previewItems) {
        buildList {
            add(ProfilePreviewTab(label = "Alle", count = previewItems.size))
            addAll(
                previewItems
                    .groupingBy { it.category }
                    .eachCount()
                    .toList()
                    .sortedByDescending { it.second }
                    .map { (category, count) -> ProfilePreviewTab(label = category, count = count) }
            )
        }
    }

    if (tabs.none { it.label == selectedTab }) {
        selectedTab = "Alle"
    }

    val visibleItems = remember(previewItems, selectedTab) {
        if (selectedTab == "Alle") previewItems else previewItems.filter { it.category == selectedTab }
    }

    if (viewerStartIndex >= 0 && visibleItems.isNotEmpty()) {
        ProfilePreviewViewer(
            items = visibleItems,
            startIndex = viewerStartIndex,
            onDismiss = { viewerStartIndex = -1 },
            onDownloadItem = onDownloadItem
        )
    }

    SectionCard(
        title = "Profilvorschau",
        icon = Icons.Default.PersonSearch,
        gradientColors = listOf(Color(0xFF00ACC1), Color(0xFF4DD0E1))
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Stories, Highlights, Posts, Reels oder Archiv durchsuchen") },
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary)
            },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Suche leeren", tint = TextSecondary)
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentPink,
                unfocusedBorderColor = DarkSurfaceVariant,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = AccentPink
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Alles Geladene von @${profile.username} in einer Vorschau: Stories, Highlights, Posts, Reels, Markierungen, Archiv und Gespeichertes.",
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (tabs.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = tabs.indexOfFirst { it.label == selectedTab }.coerceAtLeast(0),
                containerColor = Color.Transparent,
                edgePadding = 0.dp,
                divider = {}
            ) {
                tabs.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab.label,
                        onClick = { selectedTab = tab.label },
                        text = {
                            Text(
                                "${tab.label} ${tab.count}",
                                maxLines = 1
                            )
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (visibleItems.isEmpty()) {
            EmptyMessage("Keine Inhalte zur Vorschau gefunden")
        } else {
            Text(
                text = "Grid-Ansicht wie im Profil: ${visibleItems.size} Elemente im Bereich $selectedTab.",
                color = TextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                visibleItems.take(30).chunked(3).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowItems.forEach { item ->
                            ProfileGridTile(
                                item = item,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewerStartIndex = visibleItems.indexOf(item)
                                }
                            )
                        }
                        repeat(3 - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

        }
    }
}

private fun DownloadResult<List<FeedPost>>?.toPreviewItems(category: String): List<ProfilePreviewItem> {
    val posts = (this as? DownloadResult.Success)?.data ?: return emptyList()
    return posts.map { post ->
        ProfilePreviewItem(
            id = "$category-${post.id}",
            sourceId = post.id,
            imageUrl = post.thumbnailUrl.ifEmpty { post.mediaUrls.firstOrNull().orEmpty() },
            title = post.caption.takeIf { it.isNotBlank() }?.take(48) ?: post.shortcode.ifBlank { "Ohne Caption" },
            subtitle = formatPreviewTimestamp(post.timestamp * 1000L),
            category = category,
            isVideo = post.type == MediaType.VIDEO,
            caption = post.caption,
            timestampMillis = post.timestamp * 1000L,
            mediaCount = post.mediaUrls.size.coerceAtLeast(1),
            likeCount = post.likeCount,
            commentCount = post.commentCount,
            actionLabel = when (category) {
                "Reels" -> "Reel herunterladen"
                "Markiert" -> "Markierten Post herunterladen"
                "Archiv" -> "Archivpost herunterladen"
                "Gespeichert" -> "Gespeicherten Post herunterladen"
                else -> "Post herunterladen"
            }
        )
    }
}

@Composable
private fun ProfilePreviewCard(item: ProfilePreviewItem) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.width(132.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .background(DarkSurface)
            ) {
                if (item.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                if (item.isVideo) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(item.category, color = Color.White, fontSize = 10.sp)
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(item.title, color = TextPrimary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(4.dp))
                Text(item.subtitle, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ProfileGridTile(
    item: ProfilePreviewItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current

    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (item.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(item.category, color = Color.White, fontSize = 10.sp)
            }

            if (item.isVideo) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                        )
                    )
                    .padding(10.dp)
            ) {
                Column {
                    Text(
                        item.title,
                        color = Color.White,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        item.subtitle,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ProfilePreviewViewer(
    items: List<ProfilePreviewItem>,
    startIndex: Int,
    onDismiss: () -> Unit,
    onDownloadItem: (category: String, sourceId: String) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = startIndex) { items.size }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                TopAppBar(
                    title = {
                        val currentItem = items.getOrNull(pagerState.currentPage)
                        Column {
                            Text(
                                currentItem?.title ?: "",
                                color = Color.White,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${pagerState.currentPage + 1}/${items.size} · ${currentItem?.category.orEmpty()}",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 12.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Schliessen", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black,
                        titleContentColor = Color.White
                    )
                )

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { page ->
                    val item = items[page]
                    ProfilePreviewViewerPage(
                        item = item,
                        onDownload = { onDownloadItem(item.category, item.sourceId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfilePreviewViewerPage(
    item: ProfilePreviewItem,
    onDownload: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (item.imageUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(item.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                    )
                )
                .padding(16.dp)
        ) {
            Text(
                item.category,
                color = AccentPink,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                item.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                item.subtitle,
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 13.sp
            )
            if (item.caption.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    item.caption,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ViewerStatChip(
                    icon = if (item.isVideo) Icons.Default.PlayArrow else Icons.Default.Photo,
                    label = "${item.mediaCount} Datei${if (item.mediaCount != 1) "en" else ""}"
                )
                if (item.likeCount > 0) {
                    ViewerStatChip(
                        icon = Icons.Default.Favorite,
                        label = formatCount(item.likeCount)
                    )
                }
                if (item.commentCount > 0) {
                    ViewerStatChip(
                        icon = Icons.Default.ChatBubble,
                        label = formatCount(item.commentCount)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onDownload,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPink)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(item.actionLabel, fontWeight = FontWeight.SemiBold)
            }
        }

        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(42.dp)
                )
            }
        }
    }
}

@Composable
private fun ViewerStatChip(
    icon: ImageVector,
    label: String
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}

@Composable
private fun SharedPostBanner(
    sharedPost: DownloadResult<FeedPost>,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A3D2A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Geteilter Link", fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Schließen", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (sharedPost) {
                is DownloadResult.Loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = SuccessGreen, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Post wird geladen...", color = TextSecondary, fontSize = 14.sp)
                    }
                }
                is DownloadResult.Success -> {
                    val post = sharedPost.data
                    val totalMedia = post.mediaUrls.size
                    Text(
                        "${if (post.type == MediaType.VIDEO) "Video" else "Bild"} ($totalMedia Dateien) - ${post.shortcode}",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Herunterladen", fontWeight = FontWeight.SemiBold, color = Color.Black)
                    }
                }
                is DownloadResult.Error -> {
                    Text(sharedPost.message, color = ErrorRed, fontSize = 13.sp)
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextSecondary
        )
    }
}

@Composable
private fun StoriesSection(
    stories: DownloadResult<List<StoryItem>>,
    onDownloadStories: () -> Unit
) {
    SectionCard(
        title = "Stories",
        icon = Icons.Default.AutoStories,
        gradientColors = listOf(InstagramPink, InstagramOrange)
    ) {
        when (stories) {
            is DownloadResult.Loading -> {
                Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentPink, modifier = Modifier.size(24.dp))
                }
            }
            is DownloadResult.Error -> {
                ErrorMessage(stories.message)
            }
            is DownloadResult.Success -> {
                val items = stories.data
                if (items.isEmpty()) {
                    EmptyMessage("Keine aktiven Stories vorhanden")
                } else {
                    val context = LocalContext.current
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${items.size} Story${if (items.size != 1) "s" else ""} gefunden",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                            val videoCount = items.count { it.type == MediaType.VIDEO }
                            val imageCount = items.size - videoCount
                            Text(
                                "$imageCount Bilder, $videoCount Videos",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Story thumbnail preview row
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(items) { story ->
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(DarkSurfaceVariant)
                                        .border(
                                            2.dp,
                                            Brush.linearGradient(
                                                colors = listOf(InstagramPink, InstagramOrange, InstagramYellow)
                                            ),
                                            RoundedCornerShape(12.dp)
                                        )
                                ) {
                                    val thumbUrl = story.thumbnailUrl.ifEmpty { story.mediaUrl }
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(thumbUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Story",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(2.dp)
                                            .clip(RoundedCornerShape(10.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    if (story.type == MediaType.VIDEO) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(4.dp)
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.7f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onDownloadStories,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPink)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Alle Stories herunterladen",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun HighlightsSection(
    highlights: DownloadResult<List<HighlightReel>>,
    onDownloadHighlight: (HighlightReel) -> Unit,
    onDownloadAllHighlights: () -> Unit
) {
    SectionCard(
        title = "Highlights",
        icon = Icons.Default.Stars,
        gradientColors = listOf(InstagramPurple, InstagramPink)
    ) {
        when (highlights) {
            is DownloadResult.Loading -> {
                Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentPurple, modifier = Modifier.size(24.dp))
                }
            }
            is DownloadResult.Error -> {
                ErrorMessage(highlights.message)
            }
            is DownloadResult.Success -> {
                val reels = highlights.data
                if (reels.isEmpty()) {
                    EmptyMessage("Keine Highlights vorhanden")
                } else {
                    Column {
                        Text(
                            "${reels.size} Highlight${if (reels.size != 1) "s" else ""} gefunden",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(reels) { highlight ->
                                HighlightItem(
                                    highlight = highlight,
                                    onDownload = { onDownloadHighlight(highlight) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = onDownloadAllHighlights,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
                        ) {
                            Icon(Icons.Default.DownloadForOffline, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Alle Highlights herunterladen",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun HighlightItem(
    highlight: HighlightReel,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(120.dp)
            .clickable { onDownload() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(InstagramPurple, InstagramPink, InstagramOrange)
                        )
                    )
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    tint = AccentPink,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = highlight.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Icon(
                Icons.Default.Download,
                contentDescription = "Herunterladen",
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ReelsSection(
    reels: DownloadResult<List<FeedPost>>,
    onDownloadReels: () -> Unit
) {
    SectionCard(
        title = "Reels",
        icon = Icons.Default.VideoLibrary,
        gradientColors = listOf(Color(0xFFE040FB), Color(0xFFFF4081))
    ) {
        when (reels) {
            is DownloadResult.Loading -> {
                Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFE040FB), modifier = Modifier.size(24.dp))
                }
            }
            is DownloadResult.Error -> {
                ErrorMessage(reels.message)
            }
            is DownloadResult.Success -> {
                val items = reels.data
                if (items.isEmpty()) {
                    EmptyMessage("Keine Reels vorhanden")
                } else {
                    Column {
                        val totalMedia = items.sumOf { it.mediaUrls.size }
                        val totalLikes = items.sumOf { it.likeCount }
                        Text(
                            "${items.size} Reel${if (items.size != 1) "s" else ""} gefunden · $totalMedia Dateien · ${formatCount(totalLikes)} Likes",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        PostPreviewStrip(
                            posts = items,
                            accent = Color(0xFFE040FB)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onDownloadReels,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE040FB))
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Alle Reels herunterladen",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun TaggedPostsSection(
    taggedPosts: DownloadResult<List<FeedPost>>,
    onDownloadTagged: () -> Unit
) {
    SectionCard(
        title = "Markierte Posts",
        icon = Icons.Default.PersonPin,
        gradientColors = listOf(Color(0xFF26C6DA), Color(0xFF00ACC1))
    ) {
        when (taggedPosts) {
            is DownloadResult.Loading -> {
                Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF26C6DA), modifier = Modifier.size(24.dp))
                }
            }
            is DownloadResult.Error -> {
                ErrorMessage(taggedPosts.message)
            }
            is DownloadResult.Success -> {
                val posts = taggedPosts.data
                if (posts.isEmpty()) {
                    EmptyMessage("Keine markierten Posts vorhanden")
                } else {
                    Column {
                        val totalMedia = posts.sumOf { it.mediaUrls.size }
                        val totalLikes = posts.sumOf { it.likeCount }
                        Text(
                            "${posts.size} markierte${if (posts.size != 1) " Posts" else "r Post"} ($totalMedia Dateien, ${formatCount(totalLikes)} Likes)",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        PostPreviewStrip(
                            posts = posts,
                            accent = Color(0xFF26C6DA)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onDownloadTagged,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26C6DA))
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Alle markierten Posts herunterladen",
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black
                            )
                        }
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun SavedPostsSection(
    savedPosts: DownloadResult<List<FeedPost>>,
    onDownloadSaved: () -> Unit
) {
    SectionCard(
        title = "Gespeicherte Posts",
        icon = Icons.Default.Bookmark,
        gradientColors = listOf(Color(0xFFFFB300), Color(0xFFFF8F00))
    ) {
        when (savedPosts) {
            is DownloadResult.Loading -> {
                Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFFFB300), modifier = Modifier.size(24.dp))
                }
            }
            is DownloadResult.Error -> {
                ErrorMessage(savedPosts.message)
            }
            is DownloadResult.Success -> {
                val posts = savedPosts.data
                if (posts.isEmpty()) {
                    EmptyMessage("Keine gespeicherten Posts vorhanden")
                } else {
                    Column {
                        val totalMedia = posts.sumOf { it.mediaUrls.size }
                        val totalLikes = posts.sumOf { it.likeCount }
                        Text(
                            "${posts.size} gespeicherte${if (posts.size != 1) " Posts" else "r Post"} ($totalMedia Dateien, ${formatCount(totalLikes)} Likes)",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        PostPreviewStrip(
                            posts = posts,
                            accent = Color(0xFFFFB300)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onDownloadSaved,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300))
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Alle gespeicherten Posts herunterladen",
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Bookmark,
                                contentDescription = null,
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "Nur deine eigenen Lesezeichen - nicht sichtbar für andere.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(gradientColors)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            content()
        }
    }
}

@Composable
private fun DownloadProgressCard(progress: DownloadProgress) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (progress) {
                is DownloadProgress.Queued -> Color(0xFF1A243D)
                is DownloadProgress.Complete -> Color(0xFF1A3D1A)
                is DownloadProgress.Error -> Color(0xFF3D1A1A)
                else -> DarkSurface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (progress) {
                is DownloadProgress.Downloading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = AccentPink,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(progress.label, color = TextPrimary, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { if (progress.total > 0) progress.current.toFloat() / progress.total else 0f },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = AccentPink,
                            trackColor = DarkSurfaceVariant
                        )
                    }
                }
                is DownloadProgress.Queued -> {
                    Icon(Icons.Default.Queue, contentDescription = null, tint = AccentPurple)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "${progress.count} Elemente zur Download-Queue hinzugefuegt. Die Dateien erscheinen, sobald die Worker fertig sind.",
                        color = AccentPurple,
                        fontWeight = FontWeight.Medium
                    )
                }
                is DownloadProgress.Complete -> {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "${progress.count} ${progress.label} erfolgreich heruntergeladen",
                        color = SuccessGreen,
                        fontWeight = FontWeight.Medium
                    )
                }
                is DownloadProgress.Error -> {
                    Icon(Icons.Default.Error, contentDescription = null, tint = ErrorRed)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(progress.message, color = ErrorRed, fontSize = 14.sp)
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun WelcomeCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(InstagramPurple, InstagramPink, InstagramOrange)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "Suche einen Benutzer",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Gib einen Instagram-Benutzernamen ein, um\nStories, Highlights, Reels und mehr herunterzuladen.",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FeatureChip(Icons.Default.AutoStories, "Stories")
                FeatureChip(Icons.Default.Stars, "Highlights")
                FeatureChip(Icons.Default.VideoLibrary, "Reels")
                FeatureChip(Icons.Default.GridView, "Posts")
            }
        }
    }
}

@Composable
private fun FeatureChip(icon: ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(DarkSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = AccentPink, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, color = TextSecondary)
    }
}

@Composable
private fun FeedPostsSection(
    feedPosts: DownloadResult<List<FeedPost>>,
    onDownloadPosts: () -> Unit
) {
    SectionCard(
        title = "Gepostete Bilder",
        icon = Icons.Default.GridView,
        gradientColors = listOf(InstagramOrange, InstagramYellow)
    ) {
        when (feedPosts) {
            is DownloadResult.Loading -> {
                Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = InstagramOrange, modifier = Modifier.size(24.dp))
                }
            }
            is DownloadResult.Error -> {
                ErrorMessage(feedPosts.message)
            }
            is DownloadResult.Success -> {
                val posts = feedPosts.data
                if (posts.isEmpty()) {
                    EmptyMessage("Keine Posts vorhanden")
                } else {
                    Column {
                        val totalMedia = posts.sumOf { it.mediaUrls.size }
                        val videoCount = posts.count { it.type == MediaType.VIDEO }
                        val carouselCount = posts.count { it.isCarousel }
                        val imageCount = posts.size - videoCount - carouselCount

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${posts.size} Post${if (posts.size != 1) "s" else ""} gefunden",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                            Text(
                                buildString {
                                    if (imageCount > 0) append("$imageCount Bilder")
                                    if (videoCount > 0) {
                                        if (isNotEmpty()) append(", ")
                                        append("$videoCount Videos")
                                    }
                                    if (carouselCount > 0) {
                                        if (isNotEmpty()) append(", ")
                                        append("$carouselCount Karussells")
                                    }
                                    append(" ($totalMedia Dateien)")
                                },
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        PostPreviewStrip(
                            posts = posts,
                            accent = InstagramOrange
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onDownloadPosts,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = InstagramOrange)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Alle Posts herunterladen ($totalMedia Dateien)",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun ArchivedPostsSection(
    archivedPosts: DownloadResult<List<FeedPost>>,
    onDownloadArchive: () -> Unit
) {
    SectionCard(
        title = "Archiv",
        icon = Icons.Default.Archive,
        gradientColors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
    ) {
        when (archivedPosts) {
            is DownloadResult.Loading -> {
                Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF8B5CF6), modifier = Modifier.size(24.dp))
                }
            }
            is DownloadResult.Error -> {
                ErrorMessage(archivedPosts.message)
            }
            is DownloadResult.Success -> {
                val posts = archivedPosts.data
                if (posts.isEmpty()) {
                    EmptyMessage("Keine archivierten Posts vorhanden")
                } else {
                    Column {
                        val totalMedia = posts.sumOf { it.mediaUrls.size }
                        val totalLikes = posts.sumOf { it.likeCount }
                        Text(
                            "${posts.size} archivierte${if (posts.size != 1) " Posts" else "r Post"} ($totalMedia Dateien, ${formatCount(totalLikes)} Likes)",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        PostPreviewStrip(
                            posts = posts,
                            accent = Color(0xFF8B5CF6)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onDownloadArchive,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                        ) {
                            Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Archiv herunterladen ($totalMedia Dateien)",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun AnonymousLimitCard(
    title: String,
    icon: ImageVector,
    gradientColors: List<Color>
) {
    SectionCard(
        title = title,
        icon = icon,
        gradientColors = gradientColors
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = WarningOrange,
                modifier = Modifier.size(20.dp)
            )
            Text(
                "Login erforderlich, um $title herunterzuladen.\n" +
                    "Melde dich an, um auf $title zuzugreifen.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Row(
        modifier = Modifier.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(message, color = WarningOrange, fontSize = 14.sp)
    }
}

@Composable
private fun EmptyMessage(message: String) {
    Row(
        modifier = Modifier.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(message, color = TextSecondary, fontSize = 14.sp)
    }
}

private fun formatCount(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

@Composable
private fun DownloadQueueCard(
    queueItems: List<DownloadQueueItem>,
    onRetryItem: (DownloadQueueItem) -> Unit,
    onCancelItem: (DownloadQueueItem) -> Unit,
    onRetryFailedItems: () -> Unit,
    onCancelActiveItems: () -> Unit,
    onClearFinishedItems: () -> Unit,
) {
    var selectedFilter by remember { mutableStateOf(QueueFilterTab.ALL) }
    val waiting = queueItems.count { it.status == DownloadQueueStatus.WAITING }
    val running = queueItems.count { it.status == DownloadQueueStatus.RUNNING }
    val completed = queueItems.count { it.status == DownloadQueueStatus.COMPLETED }
    val failed = queueItems.count { it.status == DownloadQueueStatus.FAILED }
    val cancelled = queueItems.count { it.status == DownloadQueueStatus.CANCELLED }
    val filteredItems = queueItems.filter { item ->
        when (selectedFilter) {
            QueueFilterTab.ALL -> true
            QueueFilterTab.ACTIVE -> item.status == DownloadQueueStatus.WAITING || item.status == DownloadQueueStatus.RUNNING
            QueueFilterTab.FAILED -> item.status == DownloadQueueStatus.FAILED || item.status == DownloadQueueStatus.CANCELLED
            QueueFilterTab.COMPLETED -> item.status == DownloadQueueStatus.COMPLETED
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = AccentPurple)
                    Column {
                        Text("Download-Queue", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(
                            "$running laeuft, $waiting wartet, $failed fehlgeschlagen, $completed fertig",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
                  Text(
                      "${queueItems.size} Jobs",
                      color = AccentPink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                  )
              }

              Row(
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                  QueueSummaryChip("Aktiv", running + waiting, AccentPink)
                  QueueSummaryChip("Fehler", failed + cancelled, WarningOrange)
                  QueueSummaryChip("Fertig", completed, SuccessGreen)
              }

              Row(
                  modifier = Modifier.horizontalScroll(rememberScrollState()),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                  QueueFilterTab.entries.forEach { filter ->
                      FilterChip(
                          selected = selectedFilter == filter,
                          onClick = { selectedFilter = filter },
                          label = { Text(filter.label) }
                      )
                  }
              }

              Row(
                  modifier = Modifier.horizontalScroll(rememberScrollState()),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                  if (failed + cancelled > 0) {
                      FilledTonalButton(onClick = onRetryFailedItems) {
                          Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                          Spacer(modifier = Modifier.width(6.dp))
                          Text("Fehler erneut")
                      }
                  }
                  if (running + waiting > 0) {
                      OutlinedButton(onClick = onCancelActiveItems) {
                          Icon(Icons.Default.StopCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                          Spacer(modifier = Modifier.width(6.dp))
                          Text("Aktive stoppen")
                      }
                  }
                  if (completed > 0) {
                      OutlinedButton(onClick = onClearFinishedItems) {
                          Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                          Spacer(modifier = Modifier.width(6.dp))
                          Text("Fertige aufraeumen")
                      }
                  }
              }

              if (filteredItems.isEmpty()) {
                  Text(
                      "Keine Eintraege fuer diesen Filter",
                      color = TextSecondary,
                      fontSize = 12.sp
                  )
              }

              filteredItems.forEach { item ->
                  DownloadQueueRow(
                      item = item,
                      onRetryItem = onRetryItem,
                    onCancelItem = onCancelItem
                )
            }
        }
      }
  }

@Composable
private fun QueueSummaryChip(
    label: String,
    count: Int,
    accent: Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.14f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text("$label $count", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DownloadQueueSummaryCard(
    queueItems: List<DownloadQueueItem>,
    onOpenQueue: () -> Unit,
) {
    val waiting = queueItems.count { it.status == DownloadQueueStatus.WAITING }
    val running = queueItems.count { it.status == DownloadQueueStatus.RUNNING }
    val completed = queueItems.count { it.status == DownloadQueueStatus.COMPLETED }
    val failed = queueItems.count { it.status == DownloadQueueStatus.FAILED || it.status == DownloadQueueStatus.CANCELLED }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.PendingActions, contentDescription = null, tint = AccentPurple)
                    Column {
                        Text("Download-Queue", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(
                            "$running laeuft, $waiting wartet, $failed Fehler, $completed fertig",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
                AssistChip(
                    onClick = onOpenQueue,
                    label = { Text("Oeffnen") }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QueueSummaryChip("Aktiv", running + waiting, AccentPink)
                QueueSummaryChip("Fehler", failed, WarningOrange)
                QueueSummaryChip("Fertig", completed, SuccessGreen)
            }

            Text(
                "Die komplette Queue liegt jetzt auf einer eigenen Seite, damit die Hauptansicht nicht endlos lang wird.",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun DownloadQueueRow(
    item: DownloadQueueItem,
    onRetryItem: (DownloadQueueItem) -> Unit,
    onCancelItem: (DownloadQueueItem) -> Unit,
) {
    val accent = when (item.status) {
        DownloadQueueStatus.RUNNING -> AccentPink
        DownloadQueueStatus.WAITING -> AccentPurple
        DownloadQueueStatus.COMPLETED -> SuccessGreen
        DownloadQueueStatus.FAILED -> ErrorRed
        DownloadQueueStatus.CANCELLED -> WarningOrange
    }
    val statusLabel = when (item.status) {
        DownloadQueueStatus.RUNNING -> "Laeuft"
        DownloadQueueStatus.WAITING -> "Wartet"
        DownloadQueueStatus.COMPLETED -> "Fertig"
        DownloadQueueStatus.FAILED -> "Fehlgeschlagen"
        DownloadQueueStatus.CANCELLED -> "Abgebrochen"
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
              Column(modifier = Modifier.weight(1f)) {
                  Text(
                      item.label,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(statusLabel, color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                  item.outputPath.takeIf { it.isNotBlank() }?.let { outputPath ->
                      Text(
                          outputPath.substringAfterLast('/').substringAfterLast('\\'),
                          color = TextSecondary,
                          fontSize = 11.sp,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis
                      )
                  }
              }
            when (item.status) {
                DownloadQueueStatus.FAILED, DownloadQueueStatus.CANCELLED -> {
                    TextButton(onClick = { onRetryItem(item) }) {
                        Text("Erneut", color = AccentPink)
                    }
                }
                DownloadQueueStatus.WAITING, DownloadQueueStatus.RUNNING -> {
                    TextButton(onClick = { onCancelItem(item) }) {
                        Text("Abbrechen", color = WarningOrange)
                    }
                }
                DownloadQueueStatus.COMPLETED -> {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun PostPreviewStrip(
    posts: List<FeedPost>,
    accent: Color,
    emptyLabel: String = "Keine Vorschau"
) {
    val context = LocalContext.current
    if (posts.isEmpty()) {
        Text(emptyLabel, color = TextSecondary, fontSize = 12.sp)
        return
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(posts.take(8), key = { it.id }) { post ->
            Card(
                modifier = Modifier.size(92.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(post.thumbnailUrl.ifEmpty { post.mediaUrls.firstOrNull().orEmpty() })
                            .crossfade(true)
                            .build(),
                        contentDescription = post.shortcode,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    if (post.type == MediaType.VIDEO) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }

                    if (post.isCarousel) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.68f))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text("${post.mediaUrls.size}", color = Color.White, fontSize = 10.sp)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                                )
                            )
                            .padding(8.dp)
                    ) {
                        Text(
                            text = post.caption.takeIf { it.isNotBlank() }?.take(28)
                                ?: post.shortcode.ifBlank { formatPreviewTimestamp(post.timestamp * 1000L) },
                            color = Color.White,
                            fontSize = 10.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Text(
            "${posts.take(8).size} Vorschauen sichtbar, sortiert nach aktueller Auswahl",
            color = TextSecondary,
            fontSize = 11.sp
        )
    }
}

private fun formatPreviewTimestamp(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return "Kein Datum"
    val now = System.currentTimeMillis()
    val diffDays = ((now - timestampMillis).coerceAtLeast(0L) / 86_400_000L).toInt()
    return when {
        diffDays == 0 -> "Heute"
        diffDays == 1 -> "Gestern"
        diffDays < 7 -> "Vor $diffDays Tagen"
        else -> java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
            .format(java.util.Date(timestampMillis))
    }
}

private fun DownloadResult<List<FeedPost>>?.feedResultCount(): Int = (this as? DownloadResult.Success)?.data?.size ?: 0

private fun DownloadResult<List<StoryItem>>.storyResultCount(): Int = (this as? DownloadResult.Success)?.data?.size ?: 0

private fun DownloadResult<List<HighlightReel>>.highlightResultCount(): Int = (this as? DownloadResult.Success)?.data?.size ?: 0

private fun DownloadResult<List<FeedPost>>?.feedStateLabel(): String = when (this) {
    null -> "Aus"
    is DownloadResult.Loading -> "Laedt"
    is DownloadResult.Error -> "Fehler"
    is DownloadResult.Success -> if (data.isEmpty()) "Leer" else "Bereit"
    else -> "Aus"
}

private fun DownloadResult<List<StoryItem>>.storyStateLabel(): String = when (this) {
    is DownloadResult.Loading -> "Laedt"
    is DownloadResult.Error -> "Fehler"
    is DownloadResult.Success -> if (data.isEmpty()) "Leer" else "Bereit"
    else -> "Aus"
}

private fun DownloadResult<List<HighlightReel>>.highlightStateLabel(): String = when (this) {
    is DownloadResult.Loading -> "Laedt"
    is DownloadResult.Error -> "Fehler"
    is DownloadResult.Success -> if (data.isEmpty()) "Leer" else "Bereit"
    else -> "Aus"
}

private fun DownloadResult<List<FeedPost>>.sortFeedPostsResult(mode: ContentSortMode): DownloadResult<List<FeedPost>> {
    val data = (this as? DownloadResult.Success)?.data ?: return this
    val sorted = sortFeedPosts(data, mode)
    return DownloadResult.Success(sorted)
}

private fun DownloadResult<List<FeedPost>>?.sortNullableFeedPostsResult(mode: ContentSortMode): DownloadResult<List<FeedPost>>? {
    val data = (this as? DownloadResult.Success)?.data ?: return this
    val sorted = sortFeedPosts(data, mode)
    return DownloadResult.Success(sorted)
}

private fun DownloadResult<List<StoryItem>>.sortStoriesResult(mode: ContentSortMode): DownloadResult<List<StoryItem>> {
    val data = (this as? DownloadResult.Success)?.data ?: return this
    val sorted = when (mode) {
        ContentSortMode.OLDEST -> data.sortedBy { it.timestamp }
        else -> data.sortedByDescending { it.timestamp }
    }
    return DownloadResult.Success(sorted)
}

private fun DownloadResult<List<HighlightReel>>.sortHighlightsResult(mode: ContentSortMode): DownloadResult<List<HighlightReel>> {
    val data = (this as? DownloadResult.Success)?.data ?: return this
    val sorted = when (mode) {
        ContentSortMode.OLDEST -> data.sortedBy { it.title.lowercase() }
        ContentSortMode.MOST_LIKED -> data.sortedByDescending { it.items.size }
        ContentSortMode.NEWEST -> data.sortedByDescending { highlight ->
            highlight.items.maxOfOrNull { it.timestamp } ?: 0L
        }
    }
    return DownloadResult.Success(sorted)
}

private fun sortFeedPosts(posts: List<FeedPost>, mode: ContentSortMode): List<FeedPost> = when (mode) {
    ContentSortMode.NEWEST -> posts.sortedByDescending { it.timestamp }
    ContentSortMode.OLDEST -> posts.sortedBy { it.timestamp }
    ContentSortMode.MOST_LIKED -> posts.sortedWith(
        compareByDescending<FeedPost> { it.likeCount }.thenByDescending { it.timestamp }
    )
}
