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
import com.xenlon.instadownloader.model.DownloadHistoryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadHistoryScreen(
    entries: List<DownloadHistoryEntry>,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.GERMANY) }

    // Group by day
    val grouped = remember(entries) {
        entries.groupBy { dateFormat.format(Date(it.timestamp)) }
    }

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
                            Icons.Default.History,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Download-Verlauf", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = TextPrimary)
                }
            },
            actions = {
                if (entries.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.DeleteSweep, "Verlauf löschen", tint = TextSecondary)
                    }
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
                        Icons.Default.HistoryToggleOff,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Noch keine Downloads",
                        color = TextSecondary,
                        fontSize = 16.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                grouped.forEach { (day, dayEntries) ->
                    item {
                        Text(
                            day,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentPurple,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(dayEntries) { entry ->
                        HistoryEntryCard(entry, timeFormat)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryEntryCard(
    entry: DownloadHistoryEntry,
    timeFormat: SimpleDateFormat,
) {
    val icon = when (entry.category.lowercase()) {
        "stories" -> Icons.Default.AutoStories
        "highlights" -> Icons.Default.StarOutline
        "reels" -> Icons.Default.VideoLibrary
        "profile" -> Icons.Default.AccountCircle
        "posts" -> Icons.Default.GridOn
        else -> Icons.Default.Download
    }

    val categoryColor = when (entry.category.lowercase()) {
        "stories" -> AccentPink
        "highlights" -> WarningOrange
        "reels" -> AccentPurple
        "profile" -> SuccessGreen
        else -> TextSecondary
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(categoryColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = categoryColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.label.ifBlank { "${entry.category} - @${entry.username}" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1
                )
                Text(
                    "@${entry.username} • ${entry.itemCount} ${if (entry.itemCount == 1) "Datei" else "Dateien"}",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
            Text(
                timeFormat.format(Date(entry.timestamp)),
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
    }
}
