package com.lingohub.android.cdn.core

import androidx.annotation.Keep
import kotlinx.coroutines.*

@Keep
internal class UpdateManager {
    private val lingohubUpdateListeners = mutableListOf<LingoHubUpdateListener>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun addLoadingStateListener(listener: LingoHubUpdateListener) {
        if (!lingohubUpdateListeners.contains(listener)) {
            lingohubUpdateListeners.add(listener)
        }
    }

    fun removeLoadingStateListener(listener: LingoHubUpdateListener) {
        lingohubUpdateListeners.remove(listener)
    }

    internal fun notifyDataChanged() {
        scope.launch {
            // Iterate a snapshot so a listener removing itself in its callback
            // does not trigger a ConcurrentModificationException.
            lingohubUpdateListeners.toList().forEach { it.onUpdate() }
        }
    }

    internal fun notifyFailure(throwable: Throwable) {
        scope.launch {
            lingohubUpdateListeners.toList().forEach { it.onFailure(throwable) }
        }
    }

    companion object {
        @Volatile
        private var instance: UpdateManager? = null

        fun getInstance(): UpdateManager {
            return instance ?: synchronized(this) {
                instance ?: UpdateManager().also { instance = it }
            }
        }
    }
}