package com.helpers.core

import androidx.annotation.Keep

@Keep
class LingohubSDKError(message: String) : IllegalStateException(message)