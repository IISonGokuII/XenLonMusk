package com.xenlon.instadownloader.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xenlon.instadownloader.model.*
import com.xenlon.instadownloader.service.DownloadProgress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    profile: UserProfile?,
    stories: DownloadResult<List<StoryItem>>,
    highlights: DownloadResult<List<HighlightReel>>,
    downloadProgress: DownloadProgress,
    isAnonymousMode: Boolean = false,
    onSearchUser: (String) -> Unit,
    onDownloadProfilePic: () -> Unit,
    onDownloadStories: () -> Unit,
    onDownloadHighlight: (HighlightReel) -> Unit,
    onDownloadAllHighlights: () -> Unit,
    onLogout: () -> Unit,
    onOpenDownloadFolder: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

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
                IconButton(onClick = onOpenDownloadFolder) {
                    Icon(Icons.Default.FolderOpen, "Downloads öffnen", tint = TextSecondary)
                }
                IconButton(onClick = onLogout) {
                    Icon(Icons.Default.Logout, "Abmelden", tint = TextSecondary)
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
                            "Anonymer Modus - Profilsuche & Profilbilder ohne Login. " +
                                "Für Stories & Highlights melde dich an.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.weight(1f)
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
                        isSearching = true
                        onSearchUser(searchQuery.trim().removePrefix("@"))
                    }
                },
                isSearching = isSearching && profile == null
            )

            // Download progress
            AnimatedVisibility(visible = downloadProgress !is DownloadProgress.Idle) {
                DownloadProgressCard(downloadProgress)
            }

            // Profile section
            if (profile != null) {
                isSearching = false
                ProfileCard(
                    profile = profile,
                    onDownloadProfilePic = onDownloadProfilePic
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
            } else if (!isSearching) {
                // Welcome card
                WelcomeCard()
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
private fun ProfileCard(
    profile: UserProfile,
    onDownloadProfilePic: () -> Unit
) {
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
                // Profile picture placeholder with gradient border
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
                    Text(
                        text = profile.username.firstOrNull()?.uppercase() ?: "?",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentPink
                    )
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

                        // Horizontal scrollable highlight list
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
            // Highlight circle with gradient border
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
                "Gib einen Instagram-Benutzernamen ein, um\nStories, Highlights und Profilbilder herunterzuladen.",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                FeatureChip(Icons.Default.AutoStories, "Stories")
                FeatureChip(Icons.Default.Stars, "Highlights")
                FeatureChip(Icons.Default.AccountCircle, "Profilbilder")
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

private fun formatCount(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
