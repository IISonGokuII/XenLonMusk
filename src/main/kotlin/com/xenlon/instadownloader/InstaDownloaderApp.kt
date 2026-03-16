package com.xenlon.instadownloader

import android.app.Application
import android.os.Build
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.xenlon.instadownloader.service.DiagnosticsReporter

class InstaDownloaderApp : Application() {
    override fun onCreate() {
        super.onCreate()

        DiagnosticsReporter.initialize(this)

        FirebaseCrashlytics.getInstance().apply {
            setCrashlyticsCollectionEnabled(true)
            setCustomKey("app_package", applicationContext.packageName)
            setCustomKey("device_sdk", Build.VERSION.SDK_INT)
            log("InstaDownloader gestartet")
        }

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            DiagnosticsReporter.logFatalAppCrash(thread.name, throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }
    }
}
