package com.helpers.data

import com.helpers.data.model.Bundle
import com.helpers.data.model.isArray
import com.helpers.data.model.isText
import com.helpers.utils.LingohubLogger

interface IRepository {
    fun getText(key: String): CharSequence? = null
    fun getPlural(key: String, quantityString: String): CharSequence? = null
    fun getTextArray(key: String): Array<CharSequence>? = null
}

internal class Repository(private val bundle: Bundle) : IRepository {
    override fun getText(key: String): CharSequence? {
        LingohubLogger.logger.onDebug("loading string: '$key'")
        return findString(key)
    }

    override fun getPlural(key: String, quantityString: String): CharSequence? {
        val pluralKey = "${key}_$quantityString"
        LingohubLogger.logger.onDebug("loading plural '$pluralKey'")
        return findString(pluralKey)
    }

    override fun getTextArray(key: String): Array<CharSequence>? {
        return findArray(key)
    }

    private fun findString(key: String): String? = bundle.items.filter { it.isText() }.find { it.key == key }?.value

    private fun findArray(key: String): Array<CharSequence>? = bundle.items.filter { it.isArray() }.find { it.key == key }?.valueArray?.toTypedArray()
}