package com.helpers.data.model

import androidx.annotation.Keep
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@Keep
@OptIn(InternalSerializationApi::class)
@Serializable
internal data class Bundle(
    val iso: String,
    val items: List<Item>
)