package com.lingohub.android.cdn.utils

import androidx.annotation.Keep
import java.util.concurrent.ConcurrentHashMap

/**
 * This class is used by the SnapKit SDK
 */
@Keep
internal object SnapKitHelper {
    // Written by string lookups, which may run on any thread.
    private val stringsKeyMap = ConcurrentHashMap<String, String>()

    @Volatile
    private var enabled = false

    fun enableIfTest() {
        enabled = try {
            Class.forName("com.lingohub.snap.Snap")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    fun getStringsKeyMap(): Map<String, String> {
        return stringsKeyMap.toMap()
    }

    fun addString(key: String, string: String) {
        if (enabled) {
            stringsKeyMap[string] = key
        }
    }
}