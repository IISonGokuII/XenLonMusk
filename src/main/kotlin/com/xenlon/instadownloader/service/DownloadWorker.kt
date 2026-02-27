package com.xenlon.instadownloader.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
            label: String
        ): java.util.UUID {
            createNotificationChannel(context)

            val data = workDataOf(
                KEY_DOWNLOAD_URL to url,
                KEY_OUTPUT_PATH to outputPath,
                KEY_LABEL to label
            )

            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueue(request)
            return request.id
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(KEY_DOWNLOAD_URL) ?: return@withContext Result.failure()
        val outputPath = inputData.getString(KEY_OUTPUT_PATH) ?: return@withContext Result.failure()
        val label = inputData.getString(KEY_LABEL) ?: "Download"

        try {
            // Show progress notification
            setForeground(createForegroundInfo(label, 0))

            val service = InstagramService()
            val result = service.downloadFile(url, outputPath)
            service.close()

            when (result) {
                is com.xenlon.instadownloader.model.DownloadResult.Success -> {
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
}
