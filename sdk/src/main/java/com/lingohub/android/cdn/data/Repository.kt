package com.lingohub.android.cdn.data

import com.lingohub.android.cdn.data.model.Bundle
import com.lingohub.android.cdn.data.model.isArray
import com.lingohub.android.cdn.data.model.isPlural
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
    // Blank values mean "not translated" and are skipped, so lookups fall
    // back to the resources packaged in the app instead of rendering "".
    private val textByKey: Map<String, String> = buildMap {
        bundle.items.forEach { item ->
            if (item.isText()) item.value?.takeIf { it.isNotEmpty() }?.let { putIfAbsent(item.key, it) }
        }
    }

    // CDN bundles deliver plural quantities as separate PLURAL-typed items
    // named "<key>_<quantity>" (e.g. "trips_members_one").
    private val pluralByKey: Map<String, String> = buildMap {
        bundle.items.forEach { item ->
            if (item.isPlural()) item.value?.takeIf { it.isNotEmpty() }?.let { putIfAbsent(item.key, it) }
        }
    }

    private val arrayByKey: Map<String, List<String>> = buildMap {
        bundle.items.forEach { item ->
            if (item.isArray()) item.valueArray?.takeIf { it.isNotEmpty() }?.let { putIfAbsent(item.key, it) }
        }
    }

    override fun getText(key: String): CharSequence? {
        LingoHubLogger.logger.onDebug("loading string: '$key'")
        return textByKey[key]
    }

    override fun getPlural(key: String, quantityString: String): CharSequence? {
        val pluralKey = "${key}_$quantityString"
        LingoHubLogger.logger.onDebug("loading plural '$pluralKey'")
        // TEXT fallback keeps compatibility with bundles that shipped plural
        // quantities as plain TEXT items.
        return pluralByKey[pluralKey] ?: textByKey[pluralKey]
    }

    override fun getTextArray(key: String): Array<CharSequence>? =
        arrayByKey[key]?.toTypedArray()
}
