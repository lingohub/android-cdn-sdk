package com.helpers.core

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import com.helpers.utils.LingohubLogger
import com.helpers.utils.ResourcesUtil

internal class LingohubContextWrapper(base: Context) : ContextWrapper(base) {
    private var wrappedResources: Resources? = null

    override fun getResources(): Resources {
        LingohubLogger.logger.onDebug("$TAG, getResources called")
        if (wrappedResources == null) {
            LingohubLogger.logger.onDebug("$TAG, Creating new ResourcesUtil")
            wrappedResources = ResourcesUtil(this, super.getResources())
            LingohubLogger.logger.onDebug("$TAG, Created ResourcesUtil: ${wrappedResources?.javaClass?.simpleName}")
        }
        return wrappedResources!!
    }

    override fun createConfigurationContext(overrideConfiguration: Configuration): Context {
        LingohubLogger.logger.onDebug("$TAG, createConfigurationContext called with locale: ${overrideConfiguration.locales[0]}")
        // Clear the wrapped resources to force recreation with new configuration
        wrappedResources = null
        return LingohubContextWrapper(super.createConfigurationContext(overrideConfiguration))
    }

    fun updateConfiguration(configuration: Configuration, metrics: DisplayMetrics) {
        LingohubLogger.logger.onDebug("$TAG, updateConfiguration called with locale: ${configuration.locales[0]}")
        // Clear the wrapped resources to force recreation with new configuration
        wrappedResources = null
        super.getResources().updateConfiguration(configuration, metrics)
    }

    companion object {
        private const val TAG = "LingohubContextWrapper"
    }
}