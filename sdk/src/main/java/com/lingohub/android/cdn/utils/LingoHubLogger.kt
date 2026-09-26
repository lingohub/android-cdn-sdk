package com.lingohub.android.cdn.utils

import android.util.Log
import androidx.annotation.Keep

@Keep
internal interface ILingoHubLogger {
    fun onInfo(message: String)
    fun onError(error: String, cause: Throwable? = null)
    fun onDebug(message: String)
    fun onWarn(message: String, e: Throwable? = null)
}

@Keep
enum class LingoHubLogLevel {
    NONE,
    FULL
}

private class LingoHubLoggerImpl(private val logLevel: LingoHubLogLevel = LingoHubLogLevel.NONE) : ILingoHubLogger {
    override fun onInfo(message: String) {
        if (logLevel == LingoHubLogLevel.FULL) {
        Log.i("LingoHub SDK", message)
        }
    }

    override fun onError(error: String, cause: Throwable?) {
        if (logLevel == LingoHubLogLevel.FULL) {
            Log.e("LingoHub SDK", error, cause)
        }
    }
    
    override fun onDebug(message: String) {
        if (logLevel == LingoHubLogLevel.FULL) {
            Log.d("LingoHub SDK", message)
        }
    }

    override fun onWarn(message: String, e: Throwable?) {
        if (logLevel == LingoHubLogLevel.FULL) {
            Log.w("LingoHub SDK", message)
        }
    }
}

internal object LingoHubLogger {
    lateinit var logger: ILingoHubLogger

    internal var logLevel: LingoHubLogLevel = LingoHubLogLevel.NONE
        private set

    fun init(logLevel: LingoHubLogLevel) {
        this.logLevel = logLevel
        logger = LingoHubLoggerImpl(logLevel)
    }

    // For hot paths such as string lookups: the message is only built when it
    // is logged, so lookups allocate nothing for logging at NONE.
    inline fun debug(message: () -> String) {
        if (logLevel == LingoHubLogLevel.FULL) logger.onDebug(message())
    }

    inline fun warn(e: Throwable?, message: () -> String) {
        if (logLevel == LingoHubLogLevel.FULL) logger.onWarn(message(), e)
    }
}
