package com.lingohub.android.cdn.core

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import com.lingohub.android.cdn.utils.LingoHubLogger
import com.lingohub.android.cdn.utils.ResourcesUtil

internal class LingoHubContextWrapper(base: Context) : ContextWrapper(base) {
    private var wrappedResources: Resources? = null

    override fun getResources(): Resources {
        LingoHubLogger.logger.onDebug("$TAG, getResources called")
        if (wrappedResources == null) {
            LingoHubLogger.logger.onDebug("$TAG, Creating new ResourcesUtil")
            wrappedResources = ResourcesUtil(this, super.getResources())
            LingoHubLogger.logger.onDebug("$TAG, Created ResourcesUtil: ${wrappedResources?.javaClass?.simpleName}")
        }
        return wrappedResources!!
    }

    override fun createConfigurationContext(overrideConfiguration: Configuration): Context {
        LingoHubLogger.logger.onDebug("$TAG, createConfigurationContext called with locale: ${overrideConfiguration.locales[0]}")
        // Clear the wrapped resources to force recreation with new configuration
        wrappedResources = null
        return LingoHubContextWrapper(super.createConfigurationContext(overrideConfiguration))
    }

    fun updateConfiguration(configuration: Configuration, metrics: DisplayMetrics) {
        LingoHubLogger.logger.onDebug("$TAG, updateConfiguration called with locale: ${configuration.locales[0]}")
        // Clear the wrapped resources to force recreation with new configuration
        wrappedResources = null
        super.getResources().updateConfiguration(configuration, metrics)
    }

    companion object {
        private const val TAG = "LingoHubContextWrapper"
    }
}