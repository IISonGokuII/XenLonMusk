package com.xenlon.instadownloader.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.xenlon.instadownloader.model.DownloadResult
import com.xenlon.instadownloader.model.DownloadedMediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * WorkManager worker for background downloads with notification progress.
 * Allows downloads to continue even when the app is in the background.
 */
class DownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val CHANNEL_ID = "insta_download_channel"
        const val NOTIFICATION_ID = 1001
        const val KEY_DOWNLOAD_URL = "download_url"
        const val KEY_OUTPUT_PATH = "output_path"
        const val KEY_LABEL = "label"
        const val KEY_METADATA = "metadata"
        private const val UNIQUE_QUEUE_NAME = "insta_download_queue"
        private const val QUEUE_PREFS = "download_queue_registry"
        private const val BACKLOG_PREFS = "download_queue_backlog"
        private const val BACKLOG_KEY = "pending_downloads"
        private const val ENQUEUE_CHAIN_CHUNK_SIZE = 25
        private val json = Json { ignoreUnknownKeys = true }
        private val backlogLock = Any()

        @kotlinx.serialization.Serializable
        data class QueuePayload(
            val sourceUrl: String,
            val outputPath: String,
            val label: String,
            val metadataJson: String,
        )

        @kotlinx.serialization.Serializable
        data class PendingDownloadRequest(
            val url: String,
            val outputPath: String,
            val label: String,
            val metadata: DownloadedMediaMetadata? = null,
        )

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Downloads",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Download-Fortschritt für InstaDownloader"
                    setShowBadge(false)
                }
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(channel)
            }
        }

        fun enqueueDownload(
            context: Context,
            url: String,
            outputPath: String,
            label: String,
            metadata: DownloadedMediaMetadata? = null,
        ): java.util.UUID {
            return enqueueDownloads(
                context = context,
                downloads = listOf(PendingDownloadRequest(url, outputPath, label, metadata)),
            ).first()
        }

        fun enqueueDownloads(
            context: Context,
            downloads: List<PendingDownloadRequest>,
        ): List<java.util.UUID> {
            if (downloads.isEmpty()) return emptyList()
            createNotificationChannel(context)

            val requests = downloads.map { download ->
                val metadataJson = download.metadata?.let { json.encodeToString(it) }.orEmpty()
                val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setInputData(
                        workDataOf(
                            KEY_DOWNLOAD_URL to download.url,
                            KEY_OUTPUT_PATH to download.outputPath,
                            KEY_LABEL to download.label,
                            KEY_METADATA to metadataJson,
                        )
                    )
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .addTag(UNIQUE_QUEUE_NAME)
                    .build()

                persistQueuePayload(
                    context = context,
                    workId = request.id.toString(),
                    payload = QueuePayload(
                        sourceUrl = download.url,
                        outputPath = download.outputPath,
                        label = download.label,
                        metadataJson = metadataJson,
                    )
                )
                request
            }

            val workManager = WorkManager.getInstance(context)
            var continuation = workManager.beginUniqueWork(
                UNIQUE_QUEUE_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                requests.first(),
            )
            requests
                .drop(1)
                .chunked(ENQUEUE_CHAIN_CHUNK_SIZE)
                .forEach { chunk ->
                    continuation = continuation.then(chunk)
                }
            continuation.enqueue()

            return requests.map { it.id }
        }

        fun getQueuePayload(context: Context, workId: String): QueuePayload? {
            val raw = context.getSharedPreferences(QUEUE_PREFS, Context.MODE_PRIVATE)
                .getString(workId, null)
                ?: return null
            return runCatching { json.decodeFromString<QueuePayload>(raw) }.getOrNull()
        }

        fun removeQueuePayload(context: Context, workId: String) {
            context.getSharedPreferences(QUEUE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(workId)
                .apply()
        }

        fun appendPendingDownloads(
            context: Context,
            downloads: List<PendingDownloadRequest>,
        ) {
            if (downloads.isEmpty()) return
            synchronized(backlogLock) {
                val prefs = context.getSharedPreferences(BACKLOG_PREFS, Context.MODE_PRIVATE)
                val existing = prefs.getString(BACKLOG_KEY, null)
                    ?.let { raw -> runCatching { json.decodeFromString<List<PendingDownloadRequest>>(raw) }.getOrDefault(emptyList()) }
                    .orEmpty()
                prefs.edit()
                    .putString(BACKLOG_KEY, json.encodeToString(existing + downloads))
                    .apply()
            }
        }

        fun dequeuePendingDownloads(
            context: Context,
            limit: Int,
        ): List<PendingDownloadRequest> {
            if (limit <= 0) return emptyList()
            synchronized(backlogLock) {
                val prefs = context.getSharedPreferences(BACKLOG_PREFS, Context.MODE_PRIVATE)
                val existing = prefs.getString(BACKLOG_KEY, null)
                    ?.let { raw -> runCatching { json.decodeFromString<List<PendingDownloadRequest>>(raw) }.getOrDefault(emptyList()) }
                    .orEmpty()
                if (existing.isEmpty()) return emptyList()

                val selected = existing.take(limit)
                val remaining = existing.drop(limit)
                prefs.edit().apply {
                    if (remaining.isEmpty()) {
                        remove(BACKLOG_KEY)
                    } else {
                        putString(BACKLOG_KEY, json.encodeToString(remaining))
                    }
                }.apply()
                return selected
            }
        }

        fun pendingDownloadCount(context: Context): Int {
            synchronized(backlogLock) {
                val raw = context.getSharedPreferences(BACKLOG_PREFS, Context.MODE_PRIVATE)
                    .getString(BACKLOG_KEY, null)
                    ?: return 0
                return runCatching { json.decodeFromString<List<PendingDownloadRequest>>(raw).size }.getOrDefault(0)
            }
        }

        private fun persistQueuePayload(context: Context, workId: String, payload: QueuePayload) {
            context.getSharedPreferences(QUEUE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(workId, json.encodeToString(payload))
                .apply()
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(KEY_DOWNLOAD_URL) ?: return@withContext Result.failure()
        val outputPath = inputData.getString(KEY_OUTPUT_PATH) ?: return@withContext Result.failure()
        val label = inputData.getString(KEY_LABEL) ?: "Download"
        val metadata = inputData.getString(KEY_METADATA)
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString<DownloadedMediaMetadata>(it) }.getOrNull() }

        try {
            // Show progress notification
            setForeground(createForegroundInfo(label, 0))

            val service = InstagramService(applicationContext)
            val result = service.downloadFile(url, outputPath)
            service.close()

            when (result) {
                is DownloadResult.Success -> {
                    metadata?.let { writeMetadata(outputPath, it) }
                    showCompleteNotification(label)
                    Result.success(workDataOf("output_path" to outputPath))
                }
                else -> {
                    showErrorNotification(label)
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            showErrorNotification(label)
            Result.failure()
        }
    }

    private fun createForegroundInfo(label: String, progress: Int): ForegroundInfo {
        createNotificationChannel(applicationContext)

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("InstaDownloader")
            .setContentText("Lade herunter: $label")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()

        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun showCompleteNotification(label: String) {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Download abgeschlossen")
            .setContentText("$label wurde heruntergeladen")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .build()

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun showErrorNotification(label: String) {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Download fehlgeschlagen")
            .setContentText("$label konnte nicht heruntergeladen werden")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID + 2, notification)
    }

    private fun writeMetadata(filePath: String, metadata: DownloadedMediaMetadata) {
        runCatching {
            java.io.File("$filePath.meta.json").apply {
                parentFile?.mkdirs()
                writeText(json.encodeToString(metadata))
            }
        }
    }
}
