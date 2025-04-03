package com.example.lingohub.helpers

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class CacheManager(private val context: Context) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("lingohub_prefs", Context.MODE_PRIVATE)
    }

    fun shouldFetchStrings(): Boolean {
        val lastFetchTime = prefs.getLong("last_fetch_time", 0)
        val oneDayInMillis = 24 * 60 * 60 * 1000L
        return System.currentTimeMillis() - lastFetchTime >= oneDayInMillis
    }

    fun updateLastFetchTime() {
        prefs.edit {
            putLong("last_fetch_time", System.currentTimeMillis())
        }
    }
}