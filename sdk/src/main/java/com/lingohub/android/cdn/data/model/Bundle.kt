package com.lingohub.android.cdn.data.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
internal data class Bundle(
    val iso: String,
    val items: List<Item>
)