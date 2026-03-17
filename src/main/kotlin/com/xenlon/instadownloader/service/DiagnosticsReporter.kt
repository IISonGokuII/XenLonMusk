package com.xenlon.instadownloader.service

import android.content.Context
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticsReporter {
    private const val TAG = "InstaDownloader"
    @Volatile
    private var appContext: Context? = null
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val crashlytics: FirebaseCrashlytics by lazy { FirebaseCrashlytics.getInstance() }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        runCatching {
            crashlytics.setCrashlyticsCollectionEnabled(true)
            crashlytics.setCustomKey("app_package", context.packageName)
            crashlytics.setCustomKey("device_sdk", android.os.Build.VERSION.SDK_INT)
            crashlytics.log("Diagnostics initialized")
        }
        appendLocalLog("Diagnostics initialized")
    }

    fun logQueueEnqueued(
        label: String,
        queuedCount: Int,
        postponedCount: Int,
        downloadDir: String,
    ) {
        Log.d(TAG, "Queue enqueue: $label, queued=$queuedCount, postponed=$postponedCount")
        runCatching {
            crashlytics.setCustomKey("queue_label", label)
            crashlytics.setCustomKey("queue_added_count", queuedCount)
            crashlytics.setCustomKey("queue_postponed_count", postponedCount)
            crashlytics.log("Queue enqueue: $label, queued=$queuedCount, postponed=$postponedCount")
        }
        appendLocalLog("queue enqueue label=$label queued=$queuedCount postponed=$postponedCount dir=$downloadDir")
    }

    fun logWorkerStart(label: String, outputPath: String, sourceUrl: String) {
        Log.d(TAG, "Worker start: $label -> $outputPath")
        runCatching {
            crashlytics.setCustomKey("worker_label", label)
            crashlytics.setCustomKey("worker_output_path", outputPath)
            crashlytics.setCustomKey("worker_source_host", sourceUrl.substringBefore('/').substringAfter("://"))
            crashlytics.log("Worker start: $label -> $outputPath")
        }
        appendLocalLog("worker start label=$label path=$outputPath host=${sourceUrl.substringBefore('/').substringAfter("://")}")
    }

    fun logWorkerSuccess(label: String, outputPath: String) {
        val file = File(outputPath)
        Log.d(TAG, "Worker success: $label -> $outputPath (exists=${file.exists()}, size=${file.takeIf { it.exists() }?.length() ?: -1L})")
        runCatching {
            crashlytics.setCustomKey("worker_file_exists", file.exists())
            crashlytics.setCustomKey("worker_file_size", file.takeIf { it.exists() }?.length() ?: -1L)
            crashlytics.log("Worker success: $label -> $outputPath")
        }
        appendLocalLog("worker success label=$label path=$outputPath exists=${file.exists()} size=${file.takeIf { it.exists() }?.length() ?: -1L}")
    }

    fun logWorkerFailure(label: String, outputPath: String, reason: String, throwable: Throwable? = null) {
        Log.e(TAG, "Worker failure: $label -> $reason", throwable)
        runCatching {
            crashlytics.setCustomKey("worker_failure_label", label)
            crashlytics.setCustomKey("worker_failure_path", outputPath)
            crashlytics.setCustomKey("worker_failure_reason", reason)
            crashlytics.log("Worker failure: $label -> $reason")
            crashlytics.recordException(
                throwable ?: IllegalStateException("Worker failure: $label ($reason)")
            )
        }
        appendLocalLog("worker failure label=$label path=$outputPath reason=$reason throwable=${throwable?.javaClass?.simpleName ?: "-"}")
    }

    fun logMissingDownloadedFile(label: String, outputPath: String) {
        Log.w(TAG, "Worker reported success but file missing: $label -> $outputPath")
        runCatching {
            crashlytics.log("Worker success but missing file: $label -> $outputPath")
            crashlytics.recordException(
                IllegalStateException("Missing file after success: $label -> $outputPath")
            )
        }
        appendLocalLog("worker missing-file label=$label path=$outputPath")
    }

    fun logGalleryScan(directory: String, itemCount: Int) {
        Log.d(TAG, "Gallery scan: $itemCount items in $directory")
        runCatching {
            crashlytics.setCustomKey("gallery_scan_count", itemCount)
            crashlytics.log("Gallery scan: $itemCount items in $directory")
        }
        appendLocalLog("gallery scan dir=$directory count=$itemCount")
    }

    fun logFatalAppCrash(threadName: String, throwable: Throwable) {
        Log.e(TAG, "Unhandled exception on $threadName", throwable)
        runCatching {
            crashlytics.setCustomKey("fatal_thread", threadName)
            crashlytics.log("Unhandled exception on $threadName")
            crashlytics.recordException(throwable)
        }
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
