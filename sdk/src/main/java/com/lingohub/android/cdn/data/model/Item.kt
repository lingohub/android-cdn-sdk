package com.lingohub.android.cdn.data.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
internal data class Item(
    val key: String,
    val type: String,
    val value: String? = null,
    val valueArray: List<String>? = null
)



internal fun Item.isText() = type == "TEXT"
internal fun Item.isPlural() = type == "PLURAL"
internal fun Item.isArray() = type == "ARRAY"