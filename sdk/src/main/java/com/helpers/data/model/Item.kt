package com.helpers.data.model

import androidx.annotation.Keep
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@Keep
@OptIn(InternalSerializationApi::class)
@Serializable
data class Item(
    val key: String,
    val type: String,
    val value: String? = null,
    val valueArray: List<String>? = null
)



fun Item.isText() = type == "TEXT"
fun Item.isArray() = type == "ARRAY"