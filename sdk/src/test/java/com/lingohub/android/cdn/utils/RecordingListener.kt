package com.lingohub.android.cdn.utils

import com.lingohub.android.cdn.core.LingoHubSDKError
import com.lingohub.android.cdn.core.LingoHubUpdateListener

/** Records what `LingoHub.update()` reports to its listeners. */
internal class RecordingListener : LingoHubUpdateListener {
    var updates = 0
    val failures = mutableListOf<LingoHubSDKError>()

    override fun onUpdate() {
        updates++
    }

    override fun onFailure(throwable: Throwable) {
        failures += throwable as LingoHubSDKError
    }
}
