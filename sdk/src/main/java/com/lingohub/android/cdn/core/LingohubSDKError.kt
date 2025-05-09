package com.lingohub.android.cdn.core

import androidx.annotation.Keep

@Keep
class LingohubSDKError(message: String) : IllegalStateException(message)