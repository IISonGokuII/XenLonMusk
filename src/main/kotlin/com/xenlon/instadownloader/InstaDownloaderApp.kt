package com.xenlon.instadownloader

import android.app.Application
import com.xenlon.instadownloader.service.DiagnosticsReporter

class InstaDownloaderApp : Application() {
    override fun onCreate() {
        super.onCreate()

        DiagnosticsReporter.initialize(this)

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            DiagnosticsReporter.logFatalAppCrash(thread.name, throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }
    }
}
