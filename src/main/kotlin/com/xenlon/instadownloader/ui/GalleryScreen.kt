package com.xenlon.instadownloader.ui

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xenlon.instadownloader.model.GalleryItem

/**
 * User folder for the gallery overview.
 */
private data class UserFolder(
    val username: String,
    val items: List<GalleryItem>,
    val previewItem: GalleryItem
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    galleryItems: List<GalleryItem>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSaveToGallery: (GalleryItem) -> Unit,
    onDeleteItem: (GalleryItem) -> Unit
) {
    var selectedUser by remember { mutableStateOf<String?>(null) }
    var selectedItem by remember { mutableStateOf<GalleryItem?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<GalleryItem?>(null) }

    // Group items by username into folders
    val userFolders = remember(galleryItems) {
        galleryItems
            .groupBy { it.username.ifEmpty { "Andere" } }
            .map { (username, items) ->
                UserFolder(
                    username = username,
                    items = items.sortedByDescending { it.lastModified },
                    previewItem = items.maxByOrNull { it.lastModified } ?: items.first()
                )
            }
            .sortedByDescending { it.items.maxOfOrNull { item -> item.lastModified } ?: 0L }
    }

    // Full-screen viewer
    if (selectedItem != null) {
        GalleryViewer(
            item = selectedItem!!,
            onDismiss = { selectedItem = null },
            onSaveToGallery = { onSaveToGallery(it) },
            onDelete = { showDeleteConfirm = it }
        )

        // Delete confirmation dialog
        if (showDeleteConfirm != null) {
            DeleteConfirmDialog(
                onConfirm = {
                    onDeleteItem(showDeleteConfirm!!)
                    selectedItem = null
                    showDeleteConfirm = null
                },
                onDismiss = { showDeleteConfirm = null }
            )
        }
        return
    }

    // User folder detail view
    if (selectedUser != null) {
        val folder = userFolders.find { it.username == selectedUser }
        if (folder != null) {
            UserFolderScreen(
                folder = folder,
                onBack = { selectedUser = null },
                onItemClick = { selectedItem = it },
                onSaveToGallery = onSaveToGallery,
                onDeleteItem = { item ->
                    showDeleteConfirm = item
                }
            )

            // Delete confirmation dialog
            if (showDeleteConfirm != null) {
                DeleteConfirmDialog(
                    onConfirm = {
                        onDeleteItem(showDeleteConfirm!!)
                        showDeleteConfirm = null
                    },
                    onDismiss = { showDeleteConfirm = null }
                )
            }
            return
        }
    }

    // Main gallery: user folders overview
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
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
                            Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Galerie", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "(${galleryItems.size})",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Zurück", tint = TextPrimary)
                }
            },
            actions = {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, "Aktualisieren", tint = TextSecondary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DarkSurface,
                titleContentColor = TextPrimary
            )
        )

        if (userFolders.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(DarkSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Noch keine Downloads",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Heruntergeladene Bilder und Videos\nerscheinen hier in deiner Galerie.",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // User folder grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(userFolders, key = { it.username }) { folder ->
                    UserFolderCard(
                        folder = folder,
                        onClick = { selectedUser = folder.username }
                    )
                }
            }
        }
    }
}

@Composable
private fun UserFolderCard(
    folder: UserFolder,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            // Preview image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(DarkSurfaceVariant)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(folder.previewItem.file)
                        .crossfade(true)
                        .build(),
                    contentDescription = folder.username,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Item count badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "${folder.items.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Video indicator if preview is video
                if (folder.previewItem.isVideo) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Username label
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "@${folder.username}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val videoCount = folder.items.count { it.isVideo }
                val imageCount = folder.items.size - videoCount
                val categories = folder.items.map { it.category }.distinct()

                Text(
                    buildString {
                        append("$imageCount Bilder")
                        if (videoCount > 0) append(", $videoCount Videos")
                    },
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserFolderScreen(
    folder: UserFolder,
    onBack: () -> Unit,
    onItemClick: (GalleryItem) -> Unit,
    onSaveToGallery: (GalleryItem) -> Unit,
    onDeleteItem: (GalleryItem) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("Alle") }

    val categories = remember(folder.items) {
        listOf("Alle") + folder.items.map { it.category }.distinct().sorted()
    }

    val filteredItems = remember(folder.items, selectedCategory) {
        if (selectedCategory == "Alle") folder.items
        else folder.items.filter { it.category == selectedCategory }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        "@${folder.username}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        "${folder.items.size} Dateien",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Zurück", tint = TextPrimary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DarkSurface,
                titleContentColor = TextPrimary
            )
        )

        // Category filter chips
        if (categories.size > 2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = {
                            Text(
                                getCategoryLabel(category),
                                fontSize = 13.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentPink,
                            selectedLabelColor = Color.White,
                            containerColor = DarkSurfaceVariant,
                            labelColor = TextSecondary
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
        }

        // Gallery grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(filteredItems, key = { it.file.absolutePath }) { item ->
                GalleryThumbnail(
                    item = item,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

@Composable
private fun GalleryThumbnail(
    item: GalleryItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceVariant)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.file)
                .crossfade(true)
                .build(),
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Video indicator
        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f)),
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

        // Category badge
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                getCategoryLabel(item.category),
                fontSize = 9.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryViewer(
    item: GalleryItem,
    onDismiss: () -> Unit,
    onSaveToGallery: (GalleryItem) -> Unit,
    onDelete: (GalleryItem) -> Unit
) {
    val context = LocalContext.current
    var saved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Top bar
        TopAppBar(
            title = {
                Column {
                    Text(
                        item.name,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${getCategoryLabel(item.category)} · ${formatFileSize(item.sizeBytes)}",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ArrowBack, "Zurück", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Black.copy(alpha = 0.8f),
                titleContentColor = Color.White
            )
        )

        // Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (item.isVideo) {
                // Video player with ExoPlayer
                VideoPlayer(
                    file = item.file,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Image viewer
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.file)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }

        // Bottom action bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.8f))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Save to gallery button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable {
                    if (!saved) {
                        onSaveToGallery(item)
                        saved = true
                    }
                }
            ) {
                Icon(
                    if (saved) Icons.Default.CheckCircle else Icons.Default.SaveAlt,
                    contentDescription = "In Galerie speichern",
                    tint = if (saved) SuccessGreen else Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (saved) "Gespeichert" else "In Galerie",
                    fontSize = 11.sp,
                    color = if (saved) SuccessGreen else Color.White
                )
            }

            // Delete button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onDelete(item) }
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Löschen",
                    tint = ErrorRed,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Löschen",
                    fontSize = 11.sp,
                    color = ErrorRed
                )
            }
        }
    }
}

@Composable
private fun VideoPlayer(
    file: java.io.File,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            }
        },
        modifier = modifier
    )
}

@Composable
private fun DeleteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Löschen?", color = TextPrimary) },
        text = {
            Text(
                "Möchtest du diese Datei unwiderruflich löschen?",
                color = TextSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Löschen", color = ErrorRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen", color = TextSecondary)
            }
        },
        containerColor = DarkSurface,
        shape = RoundedCornerShape(20.dp)
    )
}

private fun getCategoryLabel(category: String): String = when (category) {
    "Alle" -> "Alle"
    "stories" -> "Stories"
    "highlights" -> "Highlights"
    "posts" -> "Posts"
    "archive" -> "Archiv"
    "profile" -> "Profil"
    "reels" -> "Reels"
    "saved" -> "Gespeichert"
    "tagged" -> "Markiert"
    "shared" -> "Geteilt"
    else -> category.replaceFirstChar { it.uppercase() }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
    bytes >= 1_024 -> String.format("%.0f KB", bytes / 1_024.0)
    else -> "$bytes B"
}
