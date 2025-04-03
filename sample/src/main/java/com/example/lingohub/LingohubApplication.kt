package com.example.lingohub

import android.app.Application
import com.example.lingohub.helpers.CacheManager
import com.helpers.core.Lingohub
import com.helpers.data.model.Environment
import com.helpers.utils.LingohubLogLevel

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