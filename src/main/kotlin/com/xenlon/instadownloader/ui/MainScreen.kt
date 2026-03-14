package com.xenlon.instadownloader.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xenlon.instadownloader.model.*
import com.xenlon.instadownloader.service.DownloadProgress

private data class ProfilePreviewItem(
    val id: String,
    val imageUrl: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val isVideo: Boolean = false,
)

private data class ProfilePreviewTab(
    val label: String,
    val count: Int,
)

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
    isAnonymousMode: Boolean = false,
    isOwnProfile: Boolean = false,
    searchHistory: List<SearchHistoryEntry> = emptyList(),
    downloadQuality: DownloadQuality = DownloadQuality.HD,
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
    onHandleClipboardUrl: () -> Unit = {},
    onDismissClipboardUrl: () -> Unit = {},
    onLogout: () -> Unit,
    onOpenGallery: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var profileSearchQuery by remember(profile?.username) { mutableStateOf("") }

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

            // Profile section
            if (profile != null) {
                ProfileCard(
                    profile = profile,
                    onDownloadProfilePic = onDownloadProfilePic
                )

                ProfileExplorerCard(
                    profile = profile,
                    stories = stories,
                    highlights = highlights,
                    feedPosts = feedPosts,
                    reels = reels,
                    taggedPosts = taggedPosts,
                    archivedPosts = archivedPosts,
                    savedPosts = savedPosts,
                    query = profileSearchQuery,
                    onQueryChange = { profileSearchQuery = it }
                )

                // Stories section
                if (isAnonymousMode) {
                    AnonymousLimitCard(
                        title = "Stories",
                        icon = Icons.Default.AutoStories,
                        gradientColors = listOf(InstagramPink, InstagramOrange)
                    )
                } else {
                    StoriesSection(
                        stories = stories,
                        onDownloadStories = onDownloadStories
                    )
                }

                // Highlights section
                if (isAnonymousMode) {
                    AnonymousLimitCard(
                        title = "Highlights",
                        icon = Icons.Default.Stars,
                        gradientColors = listOf(InstagramPurple, InstagramPink)
                    )
                } else {
                    HighlightsSection(
                        highlights = highlights,
                        onDownloadHighlight = onDownloadHighlight,
                        onDownloadAllHighlights = onDownloadAllHighlights
                    )
                }

                // Reels section
                if (!isAnonymousMode && reels != null) {
                    ReelsSection(
                        reels = reels,
                        onDownloadReels = onDownloadReels
                    )
                } else if (isAnonymousMode) {
                    AnonymousLimitCard(
                        title = "Reels",
                        icon = Icons.Default.VideoLibrary,
                        gradientColors = listOf(Color(0xFFE040FB), Color(0xFFFF4081))
                    )
                }

                // Feed posts section (works in both modes)
                FeedPostsSection(
                    feedPosts = feedPosts,
                    onDownloadPosts = onDownloadFeedPosts
                )

                // Tagged posts section
                if (!isAnonymousMode && taggedPosts != null) {
                    TaggedPostsSection(
                        taggedPosts = taggedPosts,
                        onDownloadTagged = onDownloadTaggedPosts
                    )
                }

                // Archived posts section (only for own profile when logged in)
                if (isOwnProfile && archivedPosts != null) {
                    ArchivedPostsSection(
                        archivedPosts = archivedPosts,
                        onDownloadArchive = onDownloadArchivedPosts
                    )
                }

                // Saved posts section (only for own profile)
                if (isOwnProfile && savedPosts != null) {
                    SavedPostsSection(
                        savedPosts = savedPosts,
                        onDownloadSaved = onDownloadSavedPosts
                    )
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
) {
    val normalizedQuery = query.trim().lowercase()
    var selectedTab by remember(profile.username) { mutableStateOf("Alle") }
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
                            imageUrl = story.thumbnailUrl.ifEmpty { story.mediaUrl },
                            title = "Story",
                            subtitle = formatPreviewTimestamp(story.timestamp * 1000L),
                            category = "Stories",
                            isVideo = story.type == MediaType.VIDEO,
                        )
                    )
                }
            }
            if (highlights is DownloadResult.Success) {
                highlights.data.forEach { highlight ->
                    add(
                        ProfilePreviewItem(
                            id = "highlight-${highlight.id}",
                            imageUrl = highlight.coverImageUrl,
                            title = highlight.title,
                            subtitle = "${highlight.items.size} Elemente",
                            category = "Highlights",
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
                                modifier = Modifier.weight(1f)
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
            imageUrl = post.thumbnailUrl.ifEmpty { post.mediaUrls.firstOrNull().orEmpty() },
            title = post.caption.takeIf { it.isNotBlank() }?.take(48) ?: post.shortcode.ifBlank { "Ohne Caption" },
            subtitle = formatPreviewTimestamp(post.timestamp * 1000L),
            category = category,
            isVideo = post.type == MediaType.VIDEO
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier.aspectRatio(1f),
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
                        Text(
                            "${items.size} Reel${if (items.size != 1) "s" else ""} gefunden",
                            color = TextSecondary,
                            fontSize = 14.sp
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
                        Text(
                            "${posts.size} markierte${if (posts.size != 1) " Posts" else "r Post"} ($totalMedia Dateien)",
                            color = TextSecondary,
                            fontSize = 14.sp
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
                        Text(
                            "${posts.size} gespeicherte${if (posts.size != 1) " Posts" else "r Post"} ($totalMedia Dateien)",
                            color = TextSecondary,
                            fontSize = 14.sp
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
                        Text(
                            "${posts.size} archivierte${if (posts.size != 1) " Posts" else "r Post"} ($totalMedia Dateien)",
                            color = TextSecondary,
                            fontSize = 14.sp
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
