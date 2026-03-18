package com.xenlon.instadownloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.xenlon.instadownloader.model.DownloadQueueItem
import com.xenlon.instadownloader.model.DownloadQueueStatus

private enum class QueueViewFilter(val label: String) {
    ALL("Alle"),
    ACTIVE("Aktiv"),
    FAILED("Fehler"),
    COMPLETED("Fertig"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    queueItems: List<DownloadQueueItem>,
    onBack: () -> Unit,
    onRetryItem: (DownloadQueueItem) -> Unit,
    onCancelItem: (DownloadQueueItem) -> Unit,
    onRetryFailedItems: () -> Unit,
    onCancelActiveItems: () -> Unit,
    onClearFinishedItems: () -> Unit,
) {
    var selectedFilter by remember { mutableStateOf(QueueViewFilter.ALL) }
    val waiting = queueItems.count { it.status == DownloadQueueStatus.WAITING }
    val running = queueItems.count { it.status == DownloadQueueStatus.RUNNING }
    val completed = queueItems.count { it.status == DownloadQueueStatus.COMPLETED }
    val failed = queueItems.count { it.status == DownloadQueueStatus.FAILED || it.status == DownloadQueueStatus.CANCELLED }

    val filteredItems = queueItems.filter { item ->
        when (selectedFilter) {
            QueueViewFilter.ALL -> true
            QueueViewFilter.ACTIVE -> item.status == DownloadQueueStatus.WAITING || item.status == DownloadQueueStatus.RUNNING
            QueueViewFilter.FAILED -> item.status == DownloadQueueStatus.FAILED || item.status == DownloadQueueStatus.CANCELLED
            QueueViewFilter.COMPLETED -> item.status == DownloadQueueStatus.COMPLETED
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Download-Queue") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurueck")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary
                )
            )
        },
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QueueScreenSummaryChip("Aktiv", running + waiting, AccentPink)
                            QueueScreenSummaryChip("Fehler", failed, WarningOrange)
                            QueueScreenSummaryChip("Fertig", completed, SuccessGreen)
                        }

                        // Throughput indicator
                        AnimatedVisibility(
                            visible = running > 0,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Speed,
                                        contentDescription = null,
                                        tint = AccentPurple,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        "$running aktive Downloads, ${waiting} in Warteschlange",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QueueViewFilter.entries.forEach { filter ->
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
                            if (failed > 0) {
                                FilledTonalButton(onClick = onRetryFailedItems) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text("Fehler erneut")
                                }
                            }
                            if (running + waiting > 0) {
                                OutlinedButton(onClick = onCancelActiveItems) {
                                    Icon(Icons.Default.StopCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text("Aktive stoppen")
                                }
                            }
                            if (completed > 0) {
                                OutlinedButton(onClick = onClearFinishedItems) {
                                    Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text("Fertige aufraeumen")
                                }
                            }
                        }
                    }
                }
            }

            if (filteredItems.isEmpty()) {
                item {
                    Text(
                        "Keine Eintraege fuer diesen Filter.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            items(filteredItems, key = { it.workId }) { item ->
                QueueListRow(
                    item = item,
                    onRetryItem = onRetryItem,
                    onCancelItem = onCancelItem
                )
            }
        }
    }
}

@Composable
private fun QueueScreenSummaryChip(
    label: String,
    count: Int,
    accent: Color
) {
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
private fun QueueListRow(
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = accent)
                    Column {
                        Text(item.label, color = TextPrimary)
                        Text(statusLabel, color = accent, fontSize = 12.sp)
                    }
                }
                when (item.status) {
                    DownloadQueueStatus.FAILED, DownloadQueueStatus.CANCELLED -> {
                        FilledTonalButton(onClick = { onRetryItem(item) }) {
                            Text("Erneut")
                        }
                    }
                    DownloadQueueStatus.WAITING, DownloadQueueStatus.RUNNING -> {
                        OutlinedButton(onClick = { onCancelItem(item) }) {
                            Text("Abbrechen")
                        }
                    }
                    DownloadQueueStatus.COMPLETED -> {
                        Text("OK", color = SuccessGreen, fontSize = 12.sp)
                    }
                }
            }

            Text(
                item.outputPath.substringAfterLast('\\').substringAfterLast('/'),
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 2
            )
        }
    }
}
