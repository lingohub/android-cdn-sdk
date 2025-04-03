package com.helpers.utils

import androidx.annotation.Keep
import okhttp3.internal.toImmutableMap

/**
 * This class is used by the SnapKit SDK
 */
@Keep
internal object SnapKitHelper {
    private val stringsKeyMap = mutableMapOf<String, String>()
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
        return stringsKeyMap.toImmutableMap()
    }

    fun addString(key: String, string: String) {
        if (enabled) {
            stringsKeyMap[string] = key
        }
    }
}