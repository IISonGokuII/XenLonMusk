package com.xenlon.instadownloader.service

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticsReporter {
    private const val TAG = "InstaDownloader"
    @Volatile
    private var appContext: Context? = null
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun initialize(context: Context) {
        appContext = context.applicationContext
        appendLocalLog("Diagnostics initialized")
    }

    fun logQueueEnqueued(
        label: String,
        queuedCount: Int,
        postponedCount: Int,
        downloadDir: String,
    ) {
        Log.d(TAG, "Queue enqueue: $label, queued=$queuedCount, postponed=$postponedCount")
        appendLocalLog("queue enqueue label=$label queued=$queuedCount postponed=$postponedCount dir=$downloadDir")
    }

    fun logWorkerStart(label: String, outputPath: String, sourceUrl: String) {
        Log.d(TAG, "Worker start: $label -> $outputPath")
        appendLocalLog("worker start label=$label path=$outputPath host=${sourceUrl.substringBefore('/').substringAfter("://")}")
    }

    fun logWorkerSuccess(label: String, outputPath: String) {
        val file = File(outputPath)
        Log.d(TAG, "Worker success: $label -> $outputPath (exists=${file.exists()}, size=${file.takeIf { it.exists() }?.length() ?: -1L})")
        appendLocalLog("worker success label=$label path=$outputPath exists=${file.exists()} size=${file.takeIf { it.exists() }?.length() ?: -1L}")
    }

    fun logWorkerFailure(label: String, outputPath: String, reason: String, throwable: Throwable? = null) {
        Log.e(TAG, "Worker failure: $label -> $reason", throwable)
        appendLocalLog("worker failure label=$label path=$outputPath reason=$reason throwable=${throwable?.javaClass?.simpleName ?: "-"}")
    }

    fun logMissingDownloadedFile(label: String, outputPath: String) {
        Log.w(TAG, "Worker reported success but file missing: $label -> $outputPath")
        appendLocalLog("worker missing-file label=$label path=$outputPath")
    }

    fun logGalleryScan(directory: String, itemCount: Int) {
        Log.d(TAG, "Gallery scan: $itemCount items in $directory")
        appendLocalLog("gallery scan dir=$directory count=$itemCount")
    }

    fun logFatalAppCrash(threadName: String, throwable: Throwable) {
        Log.e(TAG, "Unhandled exception on $threadName", throwable)
        appendLocalLog("fatal crash thread=$threadName throwable=${throwable.javaClass.name}: ${throwable.message}")
    }

    private fun appendLocalLog(message: String) {
        val context = appContext ?: return
        runCatching {
            val logDir = File(context.filesDir, "diagnostics")
            logDir.mkdirs()
            val logFile = File(logDir, "instadown.log")
            // Rotate log if > 500KB
            if (logFile.exists() && logFile.length() > 512_000) {
                val oldLog = File(logDir, "instadown.old.log")
                oldLog.delete()
                logFile.renameTo(oldLog)
            }
            val timestamp = timestampFormat.format(Date())
            logFile.appendText("[$timestamp] $message\n")
        }
    }
}
