package com.xenlon.instadownloader.service

import android.content.Context
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticsReporter {
    private const val TAG = "InstaDownloader"
    private const val LOG_FILE_NAME = "instadown.log"
    private const val LOG_ARCHIVE_FILE_NAME = "instadown.old.log"
    private const val PUBLIC_LOG_SUBDIR = "InstaDownloader"
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
            val timestamp = timestampFormat.format(Date())
            val line = "[$timestamp] $message\n"

            // Internal app log (always available)
            val internalLogDir = File(context.filesDir, "diagnostics")
            internalLogDir.mkdirs()
            val internalLogFile = File(internalLogDir, LOG_FILE_NAME)
            rotateIfNeeded(internalLogDir, internalLogFile)
            internalLogFile.appendText(line)

            // Mirror into app download folder on the device:
            // /Android/data/com.instadown.app/files/InstaDownloader/instadown.log
            val publicAppDir = context.getExternalFilesDir(null)?.let {
                File(it, "InstaDownloader")
            }
            if (publicAppDir != null) {
                publicAppDir.mkdirs()
                val downloadLogFile = File(publicAppDir, LOG_FILE_NAME)
                rotateIfNeeded(publicAppDir, downloadLogFile)
                downloadLogFile.appendText(line)
            }

            // Write into regular Downloads folder so the user can access it easily.
            appendToPublicDownloadsLog(context, line)
        }
    }

    private fun rotateIfNeeded(logDir: File, logFile: File) {
        if (!logFile.exists() || logFile.length() <= 512_000) return
        val oldLog = File(logDir, LOG_ARCHIVE_FILE_NAME)
        oldLog.delete()
        logFile.renameTo(oldLog)
    }

    private fun appendToPublicDownloadsLog(context: Context, line: String) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$PUBLIC_LOG_SUBDIR/"
                val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val existingUri = findDownloadsLogUri(resolver, collection, relativePath)
                val targetUri = existingUri ?: resolver.insert(
                    collection,
                    ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, LOG_FILE_NAME)
                        put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                        put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                    }
                )
                targetUri?.let { uri ->
                    resolver.openOutputStream(uri, "wa")?.use { out ->
                        out.write(line.toByteArray())
                    }
                }
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    PUBLIC_LOG_SUBDIR
                )
                dir.mkdirs()
                File(dir, LOG_FILE_NAME).appendText(line)
            }
        }.onFailure { throwable ->
            Log.w(TAG, "Public downloads log write failed: ${throwable.message}")
        }
    }

    private fun findDownloadsLogUri(
        resolver: android.content.ContentResolver,
        collection: Uri,
        relativePath: String,
    ): Uri? {
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection =
            "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?"
        val selectionArgs = arrayOf(LOG_FILE_NAME, relativePath)
        resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                return Uri.withAppendedPath(collection, id.toString())
            }
        }
        return null
    }
}
