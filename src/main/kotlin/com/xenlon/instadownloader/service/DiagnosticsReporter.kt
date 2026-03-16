package com.xenlon.instadownloader.service

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticsReporter {
    private val crashlytics: FirebaseCrashlytics by lazy { FirebaseCrashlytics.getInstance() }
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
        crashlytics.setCustomKey("queue_label", label)
        crashlytics.setCustomKey("queue_added_count", queuedCount)
        crashlytics.setCustomKey("queue_postponed_count", postponedCount)
        crashlytics.setCustomKey("queue_download_dir", downloadDir)
        crashlytics.log("Queue enqueue: $label, queued=$queuedCount, postponed=$postponedCount")
        appendLocalLog("queue enqueue label=$label queued=$queuedCount postponed=$postponedCount dir=$downloadDir")
    }

    fun logWorkerStart(label: String, outputPath: String, sourceUrl: String) {
        crashlytics.setCustomKey("worker_label", label)
        crashlytics.setCustomKey("worker_output_path", outputPath)
        crashlytics.setCustomKey("worker_source_host", sourceUrl.substringBefore('/').substringAfter("://"))
        crashlytics.log("Worker start: $label -> $outputPath")
        appendLocalLog("worker start label=$label path=$outputPath host=${sourceUrl.substringBefore('/').substringAfter("://")}")
    }

    fun logWorkerSuccess(label: String, outputPath: String) {
        val file = File(outputPath)
        crashlytics.setCustomKey("worker_file_exists", file.exists())
        crashlytics.setCustomKey("worker_file_size", file.takeIf { it.exists() }?.length() ?: -1L)
        crashlytics.log("Worker success: $label -> $outputPath")
        appendLocalLog("worker success label=$label path=$outputPath exists=${file.exists()} size=${file.takeIf { it.exists() }?.length() ?: -1L}")
    }

    fun logWorkerFailure(label: String, outputPath: String, reason: String, throwable: Throwable? = null) {
        crashlytics.setCustomKey("worker_failure_label", label)
        crashlytics.setCustomKey("worker_failure_path", outputPath)
        crashlytics.setCustomKey("worker_failure_reason", reason)
        crashlytics.log("Worker failure: $label -> $reason")
        crashlytics.recordException(
            throwable ?: IllegalStateException("Download worker failure: $label ($reason)"),
        )
        appendLocalLog("worker failure label=$label path=$outputPath reason=$reason throwable=${throwable?.javaClass?.simpleName ?: "-"}")
    }

    fun logMissingDownloadedFile(label: String, outputPath: String) {
        crashlytics.setCustomKey("worker_missing_file_label", label)
        crashlytics.setCustomKey("worker_missing_file_path", outputPath)
        crashlytics.log("Worker reported success but file missing: $label")
        crashlytics.recordException(
            IllegalStateException("Downloaded file missing after success: $label -> $outputPath"),
        )
        appendLocalLog("worker missing-file label=$label path=$outputPath")
    }

    fun logGalleryScan(directory: String, itemCount: Int) {
        crashlytics.setCustomKey("gallery_scan_dir", directory)
        crashlytics.setCustomKey("gallery_scan_count", itemCount)
        crashlytics.log("Gallery scan: $itemCount items in $directory")
        appendLocalLog("gallery scan dir=$directory count=$itemCount")
    }

    fun logFatalAppCrash(threadName: String, throwable: Throwable) {
        crashlytics.setCustomKey("fatal_thread", threadName)
        crashlytics.log("Unhandled exception on $threadName: ${throwable.javaClass.simpleName}")
        appendLocalLog("fatal crash thread=$threadName throwable=${throwable.javaClass.name}: ${throwable.message}")
    }

    private fun appendLocalLog(message: String) {
        val context = appContext ?: return
        runCatching {
            val logDir = File(context.filesDir, "diagnostics")
            logDir.mkdirs()
            val logFile = File(logDir, "instadown.log")
            val timestamp = timestampFormat.format(Date())
            logFile.appendText("[$timestamp] $message\n")
        }
    }
}
