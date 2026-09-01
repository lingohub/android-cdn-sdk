package com.lingohub.android.cdn.core

import androidx.annotation.Keep

@Keep
interface LingoHubUpdateListener {
    /**
     * Called when new data is successfully loaded and applied
     */
    fun onUpdate()

    /**
     * Called when data loading fails
     */
    fun onFailure(throwable: Throwable)
}