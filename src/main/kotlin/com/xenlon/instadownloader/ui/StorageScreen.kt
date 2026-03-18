package com.xenlon.instadownloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(
    totalSizeBytes: Long,
    perUserSize: Map<String, Long>,
    perCategorySize: Map<String, Long>,
    fileCount: Int,
    onBack: () -> Unit,
    onDeleteUser: (String) -> Unit,
) {
    var confirmDeleteUser by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
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
                                    colors = listOf(WarningOrange, AccentPink)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Storage,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Speicherplatz", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = TextPrimary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DarkSurface,
                titleContentColor = TextPrimary
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Gesamtverbrauch",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        Text(
                            formatSize(totalSizeBytes),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "$fileCount Dateien",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Category breakdown
            if (perCategorySize.isNotEmpty()) {
                item {
                    Text(
                        "Nach Kategorie",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            perCategorySize.entries.sortedByDescending { it.value }.forEach { (cat, size) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        cat.replaceFirstChar { it.uppercase() },
                                        fontSize = 14.sp,
                                        color = TextPrimary
                                    )
                                    Text(
                                        formatSize(size),
                                        fontSize = 14.sp,
                                        color = AccentPurple,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                if (totalSizeBytes > 0) {
                                    LinearProgressIndicator(
                                        progress = { (size.toFloat() / totalSizeBytes).coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = AccentPurple,
                                        trackColor = DarkSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Per-user breakdown
            if (perUserSize.isNotEmpty()) {
                item {
                    Text(
                        "Nach Benutzer",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(perUserSize.entries.toList()) { (username, size) ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "@$username",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary
                                )
                                Text(
                                    formatSize(size),
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                                if (totalSizeBytes > 0) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { (size.toFloat() / totalSizeBytes).coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = WarningOrange,
                                        trackColor = DarkSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            IconButton(onClick = { confirmDeleteUser = username }) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Löschen",
                                    tint = ErrorRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirm delete dialog
    confirmDeleteUser?.let { username ->
        AlertDialog(
            onDismissRequest = { confirmDeleteUser = null },
            title = { Text("Downloads löschen?") },
            text = { Text("Alle Downloads von @$username werden unwiderruflich gelöscht.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteUser(username)
                    confirmDeleteUser = null
                }) {
                    Text("Löschen", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteUser = null }) {
                    Text("Abbrechen")
                }
            },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
        )
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
