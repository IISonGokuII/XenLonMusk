package com.xenlon.instadownloader.service

import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.io.File

object DiagnosticsReporter {
    private val crashlytics: FirebaseCrashlytics by lazy { FirebaseCrashlytics.getInstance() }

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
    }

    fun logWorkerStart(label: String, outputPath: String, sourceUrl: String) {
        crashlytics.setCustomKey("worker_label", label)
        crashlytics.setCustomKey("worker_output_path", outputPath)
        crashlytics.setCustomKey("worker_source_host", sourceUrl.substringBefore('/').substringAfter("://"))
        crashlytics.log("Worker start: $label -> $outputPath")
    }

    fun logWorkerSuccess(label: String, outputPath: String) {
        val file = File(outputPath)
        crashlytics.setCustomKey("worker_file_exists", file.exists())
        crashlytics.setCustomKey("worker_file_size", file.takeIf { it.exists() }?.length() ?: -1L)
        crashlytics.log("Worker success: $label -> $outputPath")
    }

    fun logWorkerFailure(label: String, outputPath: String, reason: String, throwable: Throwable? = null) {
        crashlytics.setCustomKey("worker_failure_label", label)
        crashlytics.setCustomKey("worker_failure_path", outputPath)
        crashlytics.setCustomKey("worker_failure_reason", reason)
        crashlytics.log("Worker failure: $label -> $reason")
        crashlytics.recordException(
            throwable ?: IllegalStateException("Download worker failure: $label ($reason)"),
        )
    }

    fun logMissingDownloadedFile(label: String, outputPath: String) {
        crashlytics.setCustomKey("worker_missing_file_label", label)
        crashlytics.setCustomKey("worker_missing_file_path", outputPath)
        crashlytics.log("Worker reported success but file missing: $label")
        crashlytics.recordException(
            IllegalStateException("Downloaded file missing after success: $label -> $outputPath"),
        )
    }

    fun logGalleryScan(directory: String, itemCount: Int) {
        crashlytics.setCustomKey("gallery_scan_dir", directory)
        crashlytics.setCustomKey("gallery_scan_count", itemCount)
        crashlytics.log("Gallery scan: $itemCount items in $directory")
    }
}
