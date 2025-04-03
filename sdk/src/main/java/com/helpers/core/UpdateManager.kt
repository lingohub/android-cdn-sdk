package com.helpers.core

import androidx.annotation.Keep
import kotlinx.coroutines.*

@Keep
internal class UpdateManager {
    private val lingohubUpdateListeners = mutableListOf<LingohubUpdateListener>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun addLoadingStateListener(listener: LingohubUpdateListener) {
        if (!lingohubUpdateListeners.contains(listener)) {
            lingohubUpdateListeners.add(listener)
        }
    }

    fun removeLoadingStateListener(listener: LingohubUpdateListener) {
        lingohubUpdateListeners.remove(listener)
    }

    internal fun notifyDataChanged() {
        scope.launch {
            lingohubUpdateListeners.forEach { it.onUpdate() }
        }
    }

    internal fun notifyFailure(throwable: Throwable) {
        scope.launch {
            lingohubUpdateListeners.forEach { it.onFailure(throwable) }
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