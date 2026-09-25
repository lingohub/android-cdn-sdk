package com.lingohub.android.cdn.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lingohub.android.cdn.test.R
import java.util.concurrent.LinkedBlockingQueue

/**
 * Declared in the manifest: Android instantiates it through the application's
 * base context, which fails if that base context is wrapped.
 */
class TranslatedReceiver : BroadcastReceiver() {

    data class Strings(val receiverContext: String, val applicationContext: String, val wrapped: String)

    override fun onReceive(context: Context, intent: Intent) {
        received += Strings(
            receiverContext = context.getString(R.string.lh_test_greeting),
            applicationContext = context.applicationContext.getString(R.string.lh_test_greeting),
            wrapped = LingoHub.wrap(context).getString(R.string.lh_test_greeting)
        )
    }

    companion object {
        val received = LinkedBlockingQueue<Strings>()
    }
}
