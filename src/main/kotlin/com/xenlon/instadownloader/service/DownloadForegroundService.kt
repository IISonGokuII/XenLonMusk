package com.xenlon.instadownloader.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.xenlon.instadownloader.R

class DownloadForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel(this)

        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_START, ACTION_UPDATE -> {
                val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "InstaDownloader" }
                val text = intent.getStringExtra(EXTRA_TEXT).orEmpty().ifBlank { "Download laeuft" }
                val indeterminate = intent.getBooleanExtra(EXTRA_INDETERMINATE, true)
                val current = intent.getIntExtra(EXTRA_CURRENT, 0)
                val total = intent.getIntExtra(EXTRA_TOTAL, 0)
                val notification = buildNotification(title, text, indeterminate, current, total)

                if (intent.action == ACTION_START) {
                    startForeground(NOTIFICATION_ID, notification)
                } else {
                    val manager = getSystemService(NotificationManager::class.java)
                    manager.notify(NOTIFICATION_ID, notification)
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun buildNotification(
        title: String,
        text: String,
        indeterminate: Boolean,
        current: Int,
        total: Int,
    ): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(total.coerceAtLeast(0), current.coerceAtLeast(0), indeterminate || total <= 0)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "insta_download_foreground"
        private const val NOTIFICATION_ID = 2101
        private const val ACTION_START = "com.xenlon.instadownloader.action.START_DOWNLOAD_FOREGROUND"
        private const val ACTION_UPDATE = "com.xenlon.instadownloader.action.UPDATE_DOWNLOAD_FOREGROUND"
        private const val ACTION_STOP = "com.xenlon.instadownloader.action.STOP_DOWNLOAD_FOREGROUND"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_CURRENT = "current"
        private const val EXTRA_TOTAL = "total"
        private const val EXTRA_INDETERMINATE = "indeterminate"

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Aktive Downloads",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Haelt InstaDownloader-Downloads im Hintergrund aktiv"
                    setShowBadge(false)
                }
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(channel)
            }
        }

        fun startOrUpdate(context: Context, progress: DownloadProgress) {
            createChannel(context)
            val (title, text, current, total, indeterminate) = when (progress) {
                is DownloadProgress.Downloading -> listOf(
                    "InstaDownloader",
                    progress.label,
                    progress.current,
                    progress.total,
                    false,
                )
                is DownloadProgress.Complete -> listOf(
                    "Download abgeschlossen",
                    "${progress.count} ${progress.label}",
                    0,
                    0,
                    true,
                )
                is DownloadProgress.Error -> listOf(
                    "Download unterbrochen",
                    progress.message,
                    0,
                    0,
                    true,
                )
                DownloadProgress.Idle -> listOf(
                    "InstaDownloader",
                    "Wartet auf Downloads",
                    0,
                    0,
                    true,
                )
            }

            val action = if (progress is DownloadProgress.Downloading) ACTION_START else ACTION_UPDATE
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                this.action = action
                putExtra(EXTRA_TITLE, title as String)
                putExtra(EXTRA_TEXT, text as String)
                putExtra(EXTRA_CURRENT, current as Int)
                putExtra(EXTRA_TOTAL, total as Int)
                putExtra(EXTRA_INDETERMINATE, indeterminate as Boolean)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
