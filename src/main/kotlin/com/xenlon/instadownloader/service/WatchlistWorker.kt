package com.xenlon.instadownloader.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.xenlon.instadownloader.model.DownloadResult
import com.xenlon.instadownloader.model.WatchlistCheckResult
import com.xenlon.instadownloader.model.WatchlistEntry
import com.xenlon.instadownloader.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * Periodic WorkManager worker that checks watched profiles for new content.
 *
 * Battery-efficient:
 * - Runs every 30-60 min (WorkManager schedules flexibly)
 * - Only when connected to network
 * - Only when battery is not low
 * - Each check is a single lightweight API call per profile
 * - Does NOT auto-download, only notifies
 */
class WatchlistWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val UNIQUE_WORK_NAME = "watchlist_check"
        private const val CHANNEL_ID = "watchlist_channel"
        private const val NOTIFICATION_ID_BASE = 3000
        private const val PREFS_NAME = "watchlist"
        private const val KEY_ENTRIES = "entries"
        private val json = Json { ignoreUnknownKeys = true }

        fun schedule(context: Context, intervalMinutes: Long = 30) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<WatchlistWorker>(
                intervalMinutes, TimeUnit.MINUTES,
                15, TimeUnit.MINUTES // flex interval
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }

        fun loadWatchlist(context: Context): List<WatchlistEntry> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
            return runCatching { json.decodeFromString<List<WatchlistEntry>>(raw) }
                .getOrDefault(emptyList())
        }

        fun saveWatchlist(context: Context, entries: List<WatchlistEntry>) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ENTRIES, json.encodeToString(entries))
                .apply()
        }

        fun addToWatchlist(context: Context, entry: WatchlistEntry) {
            val current = loadWatchlist(context).toMutableList()
            current.removeAll { it.username.equals(entry.username, ignoreCase = true) }
            current.add(0, entry)
            saveWatchlist(context, current)
        }

        fun removeFromWatchlist(context: Context, username: String) {
            val updated = loadWatchlist(context)
                .filterNot { it.username.equals(username, ignoreCase = true) }
            saveWatchlist(context, updated)
        }

        fun isOnWatchlist(context: Context, username: String): Boolean {
            return loadWatchlist(context).any {
                it.username.equals(username, ignoreCase = true)
            }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val entries = loadWatchlist(applicationContext).filter { it.enabled }
        if (entries.isEmpty()) return@withContext Result.success()

        val service = InstagramService(applicationContext)
        try {
            service.awaitSessionReady()
            if (!service.isAuthenticated()) return@withContext Result.success()

            val results = mutableListOf<WatchlistCheckResult>()
            val updatedEntries = loadWatchlist(applicationContext).toMutableList()

            for (entry in entries) {
                val checkResult = checkProfile(service, entry)
                if (checkResult != null && (checkResult.newPostCount > 0 || checkResult.newStoryCount > 0)) {
                    results.add(checkResult)
                }

                // Update last checked timestamp
                val idx = updatedEntries.indexOfFirst {
                    it.username.equals(entry.username, ignoreCase = true)
                }
                if (idx >= 0) {
                    updatedEntries[idx] = updatedEntries[idx].copy(
                        lastCheckedTimestamp = System.currentTimeMillis()
                    )
                }
            }

            saveWatchlist(applicationContext, updatedEntries)

            // Send notification for profiles with new content
            if (results.isNotEmpty()) {
                sendNotification(results)
            }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        } finally {
            service.close()
        }
    }

    private suspend fun checkProfile(
        service: InstagramService,
        entry: WatchlistEntry
    ): WatchlistCheckResult? {
        return try {
            var newPosts = 0
            var newStories = 0

            if (entry.checkPosts && entry.userId.isNotEmpty()) {
                val postsResult = service.fetchFeedPosts(entry.username, entry.userId)
                if (postsResult is DownloadResult.Success) {
                    val currentCount = postsResult.data.size
                    if (entry.lastKnownPostCount > 0 && currentCount > entry.lastKnownPostCount) {
                        newPosts = currentCount - entry.lastKnownPostCount
                    }
                    // Update known count
                    updateKnownCounts(entry.username, postCount = currentCount)
                }
            }

            if (entry.checkStories && entry.userId.isNotEmpty()) {
                val storiesResult = service.fetchStories(entry.userId)
                if (storiesResult is DownloadResult.Success) {
                    val currentCount = storiesResult.data.size
                    if (currentCount > entry.lastKnownStoryCount) {
                        newStories = currentCount - entry.lastKnownStoryCount
                    }
                    updateKnownCounts(entry.username, storyCount = currentCount)
                }
            }

            WatchlistCheckResult(
                username = entry.username,
                newPostCount = newPosts,
                newStoryCount = newStories,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun updateKnownCounts(
        username: String,
        postCount: Int? = null,
        storyCount: Int? = null
    ) {
        val entries = loadWatchlist(applicationContext).toMutableList()
        val idx = entries.indexOfFirst { it.username.equals(username, ignoreCase = true) }
        if (idx >= 0) {
            entries[idx] = entries[idx].copy(
                lastKnownPostCount = postCount ?: entries[idx].lastKnownPostCount,
                lastKnownStoryCount = storyCount ?: entries[idx].lastKnownStoryCount,
            )
            saveWatchlist(applicationContext, entries)
        }
    }

    private fun sendNotification(results: List<WatchlistCheckResult>) {
        createNotificationChannel()

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = results.joinToString("\n") { result ->
            buildString {
                append("@${result.username}: ")
                val parts = mutableListOf<String>()
                if (result.newPostCount > 0) parts.add("${result.newPostCount} neue Posts")
                if (result.newStoryCount > 0) parts.add("${result.newStoryCount} neue Stories")
                append(parts.joinToString(", "))
            }
        }

        val title = if (results.size == 1) {
            "Neuer Inhalt von @${results.first().username}"
        } else {
            "Neue Inhalte von ${results.size} Profilen"
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(if (results.size == 1) text else "${results.size} Profile haben neue Inhalte")
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_BASE, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Watchlist-Benachrichtigungen",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Benachrichtigungen wenn beobachtete Profile neue Inhalte haben"
            }
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
