package com.lingohub.android.cdn.data

import com.lingohub.android.cdn.data.model.Bundle
import com.lingohub.android.cdn.data.model.isArray
import com.lingohub.android.cdn.data.model.isText
import com.lingohub.android.cdn.utils.LingoHubLogger

internal interface IRepository {
    fun getText(key: String): CharSequence? = null
    fun getPlural(key: String, quantityString: String): CharSequence? = null
    fun getTextArray(key: String): Array<CharSequence>? = null
}

internal class Repository(bundle: Bundle) : IRepository {
    // Lookups run during view inflation and rendering, so index the bundle
    // once instead of scanning the item list on every resource access.
    // First occurrence of a key wins, matching the previous find() behavior.
    private val textByKey: Map<String, String> = buildMap {
        bundle.items.forEach { item ->
            if (item.isText()) item.value?.let { putIfAbsent(item.key, it) }
        }
    }
    private val arrayByKey: Map<String, List<String>> = buildMap {
        bundle.items.forEach { item ->
            if (item.isArray()) item.valueArray?.let { putIfAbsent(item.key, it) }
        }
    }

    override fun getText(key: String): CharSequence? {
        LingoHubLogger.logger.onDebug("loading string: '$key'")
        return textByKey[key]
    }

    override fun getPlural(key: String, quantityString: String): CharSequence? {
        val pluralKey = "${key}_$quantityString"
        LingoHubLogger.logger.onDebug("loading plural '$pluralKey'")
        return textByKey[pluralKey]
    }

    override fun getTextArray(key: String): Array<CharSequence>? =
        arrayByKey[key]?.toTypedArray()
}
