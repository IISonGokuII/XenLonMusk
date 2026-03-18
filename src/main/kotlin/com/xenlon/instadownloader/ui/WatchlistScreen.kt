package com.xenlon.instadownloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xenlon.instadownloader.model.WatchlistEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    entries: List<WatchlistEntry>,
    onBack: () -> Unit,
    onRemove: (String) -> Unit,
    onToggleEnabled: (String) -> Unit,
    onToggleAutoDownload: (String) -> Unit = {},
    onOpenProfile: (String) -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY) }

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
                                    colors = listOf(AccentPurple, AccentPink)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Visibility,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Watchlist", fontWeight = FontWeight.Bold, fontSize = 20.sp)
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

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Keine Profile auf der Watchlist",
                        color = TextSecondary,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Füge Profile über den Stern-Button hinzu",
                        color = TextSecondary.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            WatchlistStatChip("Gesamt", entries.size, AccentPurple)
                            WatchlistStatChip(
                                "Aktiv",
                                entries.count { it.enabled },
                                SuccessGreen
                            )
                            WatchlistStatChip(
                                "Pausiert",
                                entries.count { !it.enabled },
                                WarningOrange
                            )
                        }
                    }
                }

                items(entries, key = { it.username }) { entry ->
                    WatchlistEntryCard(
                        entry = entry,
                        dateFormat = dateFormat,
                        onRemove = { onRemove(entry.username) },
                        onToggleEnabled = { onToggleEnabled(entry.username) },
                        onToggleAutoDownload = { onToggleAutoDownload(entry.username) },
                        onOpenProfile = { onOpenProfile(entry.username) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchlistStatChip(label: String, count: Int, accent: Color) {
    Card(
        shape = RoundedCornerShape(999.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.14f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent)
            )
            Text("$label $count", color = TextPrimary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun WatchlistEntryCard(
    entry: WatchlistEntry,
    dateFormat: SimpleDateFormat,
    onRemove: () -> Unit,
    onToggleEnabled: () -> Unit,
    onToggleAutoDownload: () -> Unit = {},
    onOpenProfile: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.enabled) DarkSurface else DarkSurface.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Profile pic
                if (entry.profilePicUrl.isNotEmpty()) {
                    AsyncImage(
                        model = entry.profilePicUrl,
                        contentDescription = entry.username,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("@", fontWeight = FontWeight.Bold, color = AccentPink)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "@${entry.username}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (entry.enabled) TextPrimary else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (entry.fullName.isNotEmpty()) {
                        Text(
                            entry.fullName,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Switch(
                    checked = entry.enabled,
                    onCheckedChange = { onToggleEnabled() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = SuccessGreen
                    )
                )
            }

            // Monitoring info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (entry.checkPosts) {
                    MonitoringChip("Posts", Icons.Default.GridOn)
                }
                if (entry.checkStories) {
                    MonitoringChip("Stories", Icons.Default.AutoStories)
                }
                if (entry.checkReels) {
                    MonitoringChip("Reels", Icons.Default.VideoLibrary)
                }
            }

            // Auto-download toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = if (entry.autoDownload) SuccessGreen else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            "Auto-Download",
                            fontSize = 13.sp,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            if (entry.autoDownload) "Neue Inhalte automatisch herunterladen"
                            else "Nur benachrichtigen",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
                Switch(
                    checked = entry.autoDownload,
                    onCheckedChange = { onToggleAutoDownload() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = SuccessGreen
                    )
                )
            }

            // Last check & known counts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Letzter Check",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Text(
                        if (entry.lastCheckedTimestamp > 0) {
                            dateFormat.format(Date(entry.lastCheckedTimestamp))
                        } else {
                            "Noch nicht geprüft"
                        },
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Bekannte Inhalte",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Text(
                        "${entry.lastKnownPostCount} Posts, ${entry.lastKnownStoryCount} Stories",
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onOpenProfile,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Profil öffnen")
                }
                OutlinedButton(
                    onClick = onRemove
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MonitoringChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(14.dp))
            Text(label, fontSize = 11.sp, color = TextSecondary)
        }
    }
}
