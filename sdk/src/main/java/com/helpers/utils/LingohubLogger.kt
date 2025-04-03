package com.helpers.utils

import android.util.Log
import androidx.annotation.Keep

@Keep
interface ILingohubLogger {
    fun onInfo(message: String)
    fun onError(error: String, cause: Throwable? = null)
    fun onDebug(message: String)
    fun onWarn(message: String, e: Throwable? = null)
}

@Keep
enum class LingohubLogLevel {
    NONE,
    FULL
}

private class LingohubLoggerImpl(private val logLevel: LingohubLogLevel = LingohubLogLevel.NONE) : ILingohubLogger {
    override fun onInfo(message: String) {
        if (logLevel == LingohubLogLevel.FULL) {
        Log.i("Lingohub SDK", message)
        }
    }

    override fun onError(error: String, cause: Throwable?) {
        if (logLevel == LingohubLogLevel.FULL) {
            Log.e("Lingohub SDK", error, cause)
        }
    }
    
    override fun onDebug(message: String) {
        if (logLevel == LingohubLogLevel.FULL) {
            Log.d("Lingohub SDK", message)
        }
    }

    override fun onWarn(message: String, e: Throwable?) {
        if (logLevel == LingohubLogLevel.FULL) {
            Log.w("Lingohub SDK", message)
        }
    }
}

object LingohubLogger {
    lateinit var logger: ILingohubLogger

    fun init(logLevel: LingohubLogLevel) {
        logger = LingohubLoggerImpl(logLevel)
    }
}
