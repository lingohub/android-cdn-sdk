package com.lingohub.android.cdn.example

import android.app.Application
import com.lingohub.android.cdn.example.helpers.CacheManager
import com.lingohub.android.cdn.core.LingoHub
import com.lingohub.android.cdn.data.model.Environment
import com.lingohub.android.cdn.utils.LingoHubLogLevel

class LingoHubApplication : Application() {
    private lateinit var cacheManager: CacheManager

    override fun onCreate() {
        super.onCreate()
        cacheManager = CacheManager(this)

        // Configure LingoHub with your project credentials.
        // The demo distribution ("Wanderly CDN Demo") lives in the DEVELOPMENT
        // environment - the environment must match the published release.
        LingoHub.configure(
            context = this,
            apiKey = "YOUR_API_KEY",
            environment = Environment.DEVELOPMENT,
            logLevel = if (BuildConfig.DEBUG) LingoHubLogLevel.FULL else LingoHubLogLevel.NONE
        )

        if (cacheManager.shouldFetchStrings()) {
            LingoHub.update()
            cacheManager.updateLastFetchTime()
        }
    }


}