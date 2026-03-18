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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xenlon.instadownloader.model.DownloadStats
import com.xenlon.instadownloader.model.UserDownloadStats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    stats: DownloadStats,
    onBack: () -> Unit,
) {
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
                            Icons.Default.BarChart,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Statistiken", fontWeight = FontWeight.Bold, fontSize = 20.sp)
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
            // Overview cards
            item {
                Text(
                    "Übersicht",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        icon = Icons.Default.Download,
                        label = "Downloads",
                        value = "${stats.totalDownloads}",
                        color = AccentPink,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        icon = Icons.Default.Storage,
                        label = "Speicher",
                        value = formatSize(stats.totalSizeBytes),
                        color = AccentPurple,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        icon = Icons.Default.Image,
                        label = "Bilder",
                        value = "${stats.imageCount}",
                        color = SuccessGreen,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        icon = Icons.Default.Videocam,
                        label = "Videos",
                        value = "${stats.videoCount}",
                        color = WarningOrange,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        icon = Icons.Default.People,
                        label = "Profile",
                        value = "${stats.perUserStats.size}",
                        color = InstagramPurple,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        icon = Icons.Default.Today,
                        label = "Heute",
                        value = "${stats.dailyDownloads.values.lastOrNull() ?: 0}",
                        color = InstagramOrange,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Top users
            if (stats.perUserStats.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Top Profile",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                val topUsers = stats.perUserStats.values
                    .sortedByDescending { it.downloadCount }
                    .take(15)

                items(topUsers) { userStats ->
                    UserStatRow(userStats)
                }
            }

            // Daily activity
            if (stats.dailyDownloads.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Letzte 7 Tage",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                val recentDays = stats.dailyDownloads.entries
                    .sortedByDescending { it.key }
                    .take(7)

                items(recentDays) { (date, count) ->
                    DailyStatRow(date = date, count = count, maxCount = recentDays.maxOf { it.value })
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                value,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = TextPrimary
            )
            Text(
                label,
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun UserStatRow(userStats: UserDownloadStats) {
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
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "@",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = AccentPink
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    userStats.username,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    userStats.categories.entries.joinToString(", ") { "${it.value} ${it.key}" },
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${userStats.downloadCount}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = AccentPink
                )
                Text(
                    formatSize(userStats.totalSizeBytes),
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun DailyStatRow(date: String, count: Int, maxCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            date,
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.width(80.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(DarkSurfaceVariant)
        ) {
            val fraction = if (maxCount > 0) count.toFloat() / maxCount else 0f
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(InstagramPurple, AccentPink)
                        )
                    )
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "$count",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.width(36.dp)
        )
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> String.format("%.1f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
    bytes >= 1_024 -> String.format("%.0f KB", bytes / 1_024.0)
    else -> "$bytes B"
}
