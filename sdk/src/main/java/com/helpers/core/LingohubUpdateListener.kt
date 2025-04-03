package com.helpers.core

import androidx.annotation.Keep

@Keep
interface LingohubUpdateListener {
    /**
     * Called when new data is successfully loaded and applied
     */
    fun onUpdate()

    /**
     * Called when data loading fails
     */
    fun onFailure(throwable: Throwable)
}