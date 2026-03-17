package com.xenlon.instadownloader.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xenlon.instadownloader.model.GalleryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * User folder for the gallery overview.
 */
private data class UserFolder(
    val username: String,
    val items: List<GalleryItem>,
    val previewItem: GalleryItem
)

private enum class MediaFilter(val label: String) {
    ALL("Alle"),
    IMAGES("Bilder"),
    VIDEOS("Videos")
}

private enum class GridSize(val columns: Int, val icon: @Composable () -> Unit) {
    SMALL(4, { Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(18.dp)) }),
    MEDIUM(3, { Icon(Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(18.dp)) }),
    LARGE(2, { Icon(Icons.Default.ViewModule, contentDescription = null, modifier = Modifier.size(18.dp)) });

    fun next(): GridSize = entries[(ordinal + 1) % entries.size]
}

/**
 * Extracts a scaled-down video thumbnail bitmap. Full-size frames from
 * MediaMetadataRetriever can be 8+ MB each (1920x1080 ARGB_8888).
 * Scaling to 256px keeps thumbnails under 100KB each.
 */
@Composable
private fun rememberVideoThumbnail(file: java.io.File): Bitmap? {
    var thumbnail by remember(file.absolutePath) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(file.absolutePath) {
        thumbnail = withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(file.absolutePath)
                val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                retriever.release()
                // Scale down to thumbnail size to save RAM
                if (frame != null && (frame.width > 256 || frame.height > 256)) {
                    val scale = 256f / maxOf(frame.width, frame.height)
                    val scaled = Bitmap.createScaledBitmap(
                        frame,
                        (frame.width * scale).toInt(),
                        (frame.height * scale).toInt(),
                        true
                    )
                    if (scaled !== frame) frame.recycle()
                    scaled
                } else {
                    frame
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    return thumbnail
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    galleryItems: List<GalleryItem>,
    lastGalleryVisit: Long = 0L,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSaveToGallery: (GalleryItem) -> Unit,
    onDeleteItem: (GalleryItem) -> Unit,
    onSaveMultiple: (List<GalleryItem>) -> Unit = {},
    onDeleteMultiple: (List<GalleryItem>) -> Unit = {}
) {
    var selectedUser by remember { mutableStateOf<String?>(null) }
    var viewerStartIndex by remember { mutableIntStateOf(-1) }
    var viewerItems by remember { mutableStateOf<List<GalleryItem>>(emptyList()) }
    var showDeleteConfirm by remember { mutableStateOf<GalleryItem?>(null) }
    var rootSearchQuery by remember { mutableStateOf("") }

    // Multi-select state
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    // Group items by username into folders
    val userFolders = remember(galleryItems, rootSearchQuery) {
        val normalizedQuery = rootSearchQuery.trim().lowercase()
        galleryItems
            .filter { item ->
                normalizedQuery.isBlank() ||
                    item.username.lowercase().contains(normalizedQuery) ||
                    item.category.lowercase().contains(normalizedQuery) ||
                    item.caption.lowercase().contains(normalizedQuery) ||
                    item.highlightTitle.lowercase().contains(normalizedQuery) ||
                    item.shortcode.lowercase().contains(normalizedQuery) ||
                    item.name.lowercase().contains(normalizedQuery)
            }
            .groupBy { it.username.ifEmpty { "Andere" } }
            .map { (username, items) ->
                UserFolder(
                    username = username,
                    items = items.sortedByDescending { it.sourceTimestamp.takeIf { ts -> ts > 0L } ?: it.lastModified },
                    previewItem = items.maxByOrNull { it.sourceTimestamp.takeIf { ts -> ts > 0L } ?: it.lastModified } ?: items.first()
                )
            }
            .sortedByDescending { it.items.maxOfOrNull { item -> item.sourceTimestamp.takeIf { ts -> ts > 0L } ?: item.lastModified } ?: 0L }
    }

    // Full-screen pager viewer
    if (viewerStartIndex >= 0 && viewerItems.isNotEmpty()) {
        GalleryPagerViewer(
            items = viewerItems,
            startIndex = viewerStartIndex,
            onDismiss = { viewerStartIndex = -1; viewerItems = emptyList() },
            onSaveToGallery = onSaveToGallery,
            onDelete = { showDeleteConfirm = it }
        )

        if (showDeleteConfirm != null) {
            DeleteConfirmDialog(
                onConfirm = {
                    onDeleteItem(showDeleteConfirm!!)
                    showDeleteConfirm = null
                    if (viewerItems.size <= 1) {
                        viewerStartIndex = -1
                        viewerItems = emptyList()
                    }
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
                lastGalleryVisit = lastGalleryVisit,
                isMultiSelectMode = isMultiSelectMode,
                selectedItems = selectedItems,
                onBack = {
                    selectedUser = null
                    isMultiSelectMode = false
                    selectedItems = emptySet()
                },
                onToggleItem = { item ->
                    if (isMultiSelectMode) {
                        selectedItems = if (selectedItems.contains(item.file.absolutePath)) {
                            selectedItems - item.file.absolutePath
                        } else {
                            selectedItems + item.file.absolutePath
                        }
                    }
                },
                onOpenViewer = { visibleItems, item ->
                    val idx = visibleItems.indexOf(item)
                    viewerItems = visibleItems
                    viewerStartIndex = if (idx >= 0) idx else 0
                },
                onToggleMultiSelect = {
                    isMultiSelectMode = !isMultiSelectMode
                    if (!isMultiSelectMode) selectedItems = emptySet()
                },
                onSelectAll = {
                    selectedItems = folder.items.map { it.file.absolutePath }.toSet()
                },
                onSaveSelected = {
                    val items = folder.items.filter { selectedItems.contains(it.file.absolutePath) }
                    onSaveMultiple(items)
                    isMultiSelectMode = false
                    selectedItems = emptySet()
                },
                onDeleteSelected = { showBatchDeleteConfirm = true }
            )

            if (showDeleteConfirm != null) {
                DeleteConfirmDialog(
                    onConfirm = {
                        onDeleteItem(showDeleteConfirm!!)
                        showDeleteConfirm = null
                    },
                    onDismiss = { showDeleteConfirm = null }
                )
            }

            if (showBatchDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showBatchDeleteConfirm = false },
                    title = { Text("${selectedItems.size} Dateien löschen?", color = TextPrimary) },
                    text = {
                        Text(
                            "Möchtest du ${selectedItems.size} Dateien unwiderruflich löschen?",
                            color = TextSecondary
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val items = folder.items.filter { selectedItems.contains(it.file.absolutePath) }
                            onDeleteMultiple(items)
                            showBatchDeleteConfirm = false
                            isMultiSelectMode = false
                            selectedItems = emptySet()
                        }) {
                            Text("Löschen", color = ErrorRed)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBatchDeleteConfirm = false }) {
                            Text("Abbrechen", color = TextSecondary)
                        }
                    },
                    containerColor = DarkSurface,
                    shape = RoundedCornerShape(20.dp)
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = TextPrimary)
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
            OutlinedTextField(
                value = rootSearchQuery,
                onValueChange = { rootSearchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                placeholder = { Text("Profile, Kategorien oder Captions durchsuchen") },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary)
                },
                trailingIcon = {
                    if (rootSearchQuery.isNotBlank()) {
                        IconButton(onClick = { rootSearchQuery = "" }) {
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
                    val newCount = if (lastGalleryVisit > 0L) {
                        folder.items.count { it.lastModified > lastGalleryVisit }
                    } else 0
                    UserFolderCard(
                        folder = folder,
                        newItemCount = newCount,
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
    newItemCount: Int = 0,
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        buildString {
                            append("$imageCount Bilder")
                            if (videoCount > 0) append(", $videoCount Videos")
                        },
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1
                    )
                    if (newItemCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(InstagramPurple, InstagramPink)
                                    )
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                "+$newItemCount",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun UserFolderScreen(
    folder: UserFolder,
    lastGalleryVisit: Long = 0L,
    isMultiSelectMode: Boolean,
    selectedItems: Set<String>,
    onBack: () -> Unit,
    onToggleItem: (GalleryItem) -> Unit,
    onOpenViewer: (List<GalleryItem>, GalleryItem) -> Unit,
    onToggleMultiSelect: () -> Unit,
    onSelectAll: () -> Unit,
    onSaveSelected: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf("Alle") }
    var selectedMediaFilter by remember { mutableStateOf(MediaFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(SortMode.DATE_NEWEST) }
    var showSortMenu by remember { mutableStateOf(false) }
    var gridSize by remember { mutableStateOf(GridSize.MEDIUM) }

    val categories = remember(folder.items) {
        listOf("Alle") + folder.items.map { it.category }.distinct().sorted()
    }

    val filteredItems = remember(folder.items, selectedCategory, selectedMediaFilter, searchQuery, sortMode) {
        val normalizedQuery = searchQuery.trim().lowercase()
        val filteredByCategory = if (selectedCategory == "Alle") folder.items
        else folder.items.filter { it.category == selectedCategory }

        val filteredByType = when (selectedMediaFilter) {
            MediaFilter.ALL -> filteredByCategory
            MediaFilter.IMAGES -> filteredByCategory.filterNot { it.isVideo }
            MediaFilter.VIDEOS -> filteredByCategory.filter { it.isVideo }
        }

        val filtered = filteredByType.filter { item ->
            normalizedQuery.isBlank() ||
                item.name.lowercase().contains(normalizedQuery) ||
                item.caption.lowercase().contains(normalizedQuery) ||
                item.highlightTitle.lowercase().contains(normalizedQuery) ||
                item.shortcode.lowercase().contains(normalizedQuery) ||
                item.category.lowercase().contains(normalizedQuery)
        }

        when (sortMode) {
            SortMode.DATE_NEWEST -> filtered.sortedByDescending { it.sourceTimestamp.takeIf { ts -> ts > 0L } ?: it.lastModified }
            SortMode.DATE_OLDEST -> filtered.sortedBy { it.sourceTimestamp.takeIf { ts -> ts > 0L } ?: it.lastModified }
            SortMode.NAME_AZ -> filtered.sortedBy { it.name.lowercase() }
            SortMode.NAME_ZA -> filtered.sortedByDescending { it.name.lowercase() }
            SortMode.SIZE_LARGEST -> filtered.sortedByDescending { it.sizeBytes }
            SortMode.SIZE_SMALLEST -> filtered.sortedBy { it.sizeBytes }
            SortMode.CATEGORY -> filtered.sortedWith(
                compareBy<GalleryItem> { it.category }.thenByDescending {
                    it.sourceTimestamp.takeIf { ts -> ts > 0L } ?: it.lastModified
                }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        TopAppBar(
            title = {
                if (isMultiSelectMode) {
                    Text(
                        "${selectedItems.size} ausgewählt",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                } else {
                    Column {
                        Text(
                            "@${folder.username}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            "${filteredItems.size} von ${folder.items.size} Dateien",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = TextPrimary)
                }
            },
            actions = {
                if (isMultiSelectMode) {
                    IconButton(onClick = onSelectAll) {
                        Icon(Icons.Default.SelectAll, "Alle auswählen", tint = TextSecondary)
                    }
                    IconButton(onClick = onSaveSelected, enabled = selectedItems.isNotEmpty()) {
                        Icon(Icons.Default.SaveAlt, "Speichern", tint = SuccessGreen)
                    }
                    IconButton(onClick = onDeleteSelected, enabled = selectedItems.isNotEmpty()) {
                        Icon(Icons.Default.Delete, "Löschen", tint = ErrorRed)
                    }
                }
                // Sort button
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, "Sortieren", tint = TextSecondary)
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        SortMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (sortMode == mode) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = AccentPink,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.size(18.dp))
                                        }
                                        Text(
                                            mode.label,
                                            color = if (sortMode == mode) AccentPink else TextPrimary,
                                            fontSize = 14.sp
                                        )
                                    }
                                },
                                onClick = {
                                    sortMode = mode
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
                // Grid size toggle
                IconButton(onClick = { gridSize = gridSize.next() }) {
                    gridSize.icon()
                }
                IconButton(onClick = onToggleMultiSelect) {
                    Icon(
                        if (isMultiSelectMode) Icons.Default.Close else Icons.Default.DoneAll,
                        "Mehrfachauswahl",
                        tint = if (isMultiSelectMode) AccentPink else TextSecondary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DarkSurface,
                titleContentColor = TextPrimary
            )
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Dateiname, Caption, Highlight oder Shortcode suchen") },
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary)
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }) {
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MediaFilter.entries.forEach { filter ->
                FilterChip(
                    selected = selectedMediaFilter == filter,
                    onClick = { selectedMediaFilter = filter },
                    label = { Text(filter.label, fontSize = 13.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentPurple,
                        selectedLabelColor = Color.White,
                        containerColor = DarkSurfaceVariant,
                        labelColor = TextSecondary
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }

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
            columns = GridCells.Fixed(gridSize.columns),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(filteredItems, key = { it.file.absolutePath }) { item ->
                val isNew = lastGalleryVisit > 0L && item.lastModified > lastGalleryVisit
                GalleryThumbnail(
                    item = item,
                    isSelected = selectedItems.contains(item.file.absolutePath),
                    isMultiSelectMode = isMultiSelectMode,
                    isNew = isNew,
                    onClick = {
                        if (isMultiSelectMode) onToggleItem(item)
                        else onOpenViewer(filteredItems, item)
                    },
                    onLongClick = {
                        if (!isMultiSelectMode) onToggleMultiSelect()
                        onToggleItem(item)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryThumbnail(
    item: GalleryItem,
    isSelected: Boolean = false,
    isMultiSelectMode: Boolean = false,
    isNew: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceVariant)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .then(
                if (isSelected) Modifier.border(3.dp, AccentPink, RoundedCornerShape(8.dp))
                else Modifier
            )
    ) {
        if (item.isVideo) {
            // Extract and show video thumbnail
            val thumbnail = rememberVideoThumbnail(item.file)
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Fallback while loading
                Box(
                    modifier = Modifier.fillMaxSize().background(DarkSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Videocam,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(item.file)
                    .crossfade(true)
                    .size(512)
                    .build(),
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Video indicator overlay
        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Selection checkbox
        if (isMultiSelectMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) AccentPink else Color.Black.copy(alpha = 0.5f))
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
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

        // "Neu" badge for recently downloaded items
        if (isNew) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(InstagramPurple, InstagramPink)
                        )
                    )
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    "NEU",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

/**
 * Full-screen viewer with horizontal pager (swipe between images) and pinch-to-zoom.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun GalleryPagerViewer(
    items: List<GalleryItem>,
    startIndex: Int,
    onDismiss: () -> Unit,
    onSaveToGallery: (GalleryItem) -> Unit,
    onDelete: (GalleryItem) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = startIndex) { items.size }
    val currentItem = items.getOrNull(pagerState.currentPage) ?: return

    var saved by remember { mutableStateOf(false) }

    // Reset saved state when page changes
    LaunchedEffect(pagerState.currentPage) {
        saved = false
    }

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
                        currentItem.name,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${getCategoryLabel(currentItem.category)} · ${formatFileSize(currentItem.sizeBytes)} · ${pagerState.currentPage + 1}/${items.size}",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Black.copy(alpha = 0.8f),
                titleContentColor = Color.White
            )
        )

        // Swipeable pager content. beyondViewportPageCount=0 means only the
        // current page is composed - critical for videos since each page creates
        // an ExoPlayer instance that buffers video into RAM.
        HorizontalPager(
            state = pagerState,
            beyondBoundsPageCount = 0,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            val item = items[page]
            val isCurrentPage = pagerState.currentPage == page
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (item.isVideo) {
                    PagerVideoPlayer(
                        file = item.file,
                        isActive = isCurrentPage,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    ZoomableImage(
                        item = item,
                        modifier = Modifier.fillMaxSize()
                    )
                }
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable {
                    if (!saved) {
                        onSaveToGallery(currentItem)
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

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onDelete(currentItem) }
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

/**
 * Zoomable image with pinch-to-zoom and pan gestures.
 */
@Composable
private fun ZoomableImage(
    item: GalleryItem,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()

                        if (event.changes.size >= 2) {
                            // Multi-touch: handle zoom and pan
                            val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                            scale = newScale
                            if (newScale > 1f) {
                                offsetX += panChange.x
                                offsetY += panChange.y
                                val maxX = (newScale - 1f) * size.width / 2f
                                val maxY = (newScale - 1f) * size.height / 2f
                                offsetX = offsetX.coerceIn(-maxX, maxX)
                                offsetY = offsetY.coerceIn(-maxY, maxY)
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                            event.changes.forEach { it.consume() }
                        } else if (scale > 1f) {
                            // Single touch while zoomed: allow panning
                            offsetX += panChange.x
                            offsetY += panChange.y
                            val maxX = (scale - 1f) * size.width / 2f
                            val maxY = (scale - 1f) * size.height / 2f
                            offsetX = offsetX.coerceIn(-maxX, maxX)
                            offsetY = offsetY.coerceIn(-maxY, maxY)
                            event.changes.forEach { it.consume() }
                        }
                        // Single touch while not zoomed: don't consume, let HorizontalPager handle swipe
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.file)
                .crossfade(true)
                .build(),
            contentDescription = item.name,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                ),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun VideoPlayer(
    file: java.io.File,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true // handleAudioFocus
            )
            .build().apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        hasError = true
                        errorMessage = error.localizedMessage ?: "Wiedergabefehler"
                    }
                })
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    if (hasError) {
        Box(
            modifier = modifier.background(DarkSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = ErrorRed,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Video kann nicht abgespielt werden",
                    color = TextPrimary,
                    fontSize = 14.sp
                )
                Text(
                    errorMessage,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    } else {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                }
            },
            modifier = modifier
        )
    }
}

/**
 * Video player for the pager viewer that auto-plays when active and pauses when swiped away.
 */
@Composable
private fun PagerVideoPlayer(
    file: java.io.File,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .build().apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        hasError = true
                        errorMessage = error.localizedMessage ?: "Wiedergabefehler"
                    }
                })
                prepare()
            }
    }

    // Auto-play/pause based on page visibility
    LaunchedEffect(isActive) {
        exoPlayer.playWhenReady = isActive
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    if (hasError) {
        Box(
            modifier = modifier.background(DarkSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = ErrorRed,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Video kann nicht abgespielt werden", color = TextPrimary, fontSize = 14.sp)
                Text(errorMessage, color = TextSecondary, fontSize = 12.sp)
            }
        }
    } else {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                }
            },
            modifier = modifier
        )
    }
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

/**
 * Sort modes for the gallery user folder view.
 */
private enum class SortMode(val label: String) {
    DATE_NEWEST("Neueste zuerst"),
    DATE_OLDEST("Älteste zuerst"),
    NAME_AZ("Name A-Z"),
    NAME_ZA("Name Z-A"),
    SIZE_LARGEST("Größte zuerst"),
    SIZE_SMALLEST("Kleinste zuerst"),
    CATEGORY("Nach Kategorie")
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
    bytes >= 1_024 -> String.format("%.0f KB", bytes / 1_024.0)
    else -> "$bytes B"
}
