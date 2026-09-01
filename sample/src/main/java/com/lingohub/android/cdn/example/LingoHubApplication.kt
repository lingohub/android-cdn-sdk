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

        // Configure LingoHub with your project credentials
        LingoHub.configure(
            context = this,
            apiKey = "YOUR_API_KEY",
            environment = Environment.PRODUCTION,
            logLevel = LingoHubLogLevel.NONE
        )

        if (cacheManager.shouldFetchStrings()) {
            LingoHub.update()
            cacheManager.updateLastFetchTime()
        }
    }


}