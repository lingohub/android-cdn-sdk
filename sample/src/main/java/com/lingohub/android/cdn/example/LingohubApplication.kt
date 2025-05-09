package com.lingohub.android.cdn.example

import android.app.Application
import com.lingohub.android.cdn.example.helpers.CacheManager
import com.lingohub.android.cdn.core.Lingohub
import com.lingohub.android.cdn.data.model.Environment
import com.lingohub.android.cdn.utils.LingohubLogLevel

class LingohubApplication : Application() {
    private lateinit var cacheManager: CacheManager

    override fun onCreate() {
        super.onCreate()
        cacheManager = CacheManager(this)

        // Configure Lingohub with your project credentials
        Lingohub.configure(
            context = this,
            apiKey = "YOUR_API_KEY",
            environment = Environment.PRODUCTION,
            logLevel = LingohubLogLevel.NONE
        )

        if (cacheManager.shouldFetchStrings()) {
            Lingohub.update()
            cacheManager.updateLastFetchTime()
        }
    }


}