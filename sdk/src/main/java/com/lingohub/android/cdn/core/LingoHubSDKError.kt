package com.lingohub.android.cdn.core

import androidx.annotation.Keep

@Keep
class LingoHubSDKError(message: String) : IllegalStateException(message)