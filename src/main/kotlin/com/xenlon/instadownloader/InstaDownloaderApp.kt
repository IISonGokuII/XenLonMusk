package com.xenlon.instadownloader

import android.app.Application
import android.os.Build
import com.google.firebase.crashlytics.FirebaseCrashlytics

class InstaDownloaderApp : Application() {
    override fun onCreate() {
        super.onCreate()

        FirebaseCrashlytics.getInstance().apply {
            setCrashlyticsCollectionEnabled(true)
            setCustomKey("app_package", applicationContext.packageName)
            setCustomKey("device_sdk", Build.VERSION.SDK_INT)
            log("InstaDownloader gestartet")
        }
    }
}
