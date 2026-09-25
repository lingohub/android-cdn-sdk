package com.lingohub.android.cdn.core

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.res.Resources
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.lingohub.android.cdn.test.R

class TranslatedService : Service() {
    private val lingoHubContext by lazy { LingoHub.wrap(baseContext) }

    override fun getResources(): Resources = lingoHubContext.resources

    /** Built the way a push service builds one; never posted. */
    fun buildNotification(messageCount: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.lh_test_greeting))
            .setContentText(resources.getQuantityString(R.plurals.lh_test_messages, messageCount, messageCount))
            .build()

    override fun onBind(intent: Intent): IBinder = LocalBinder()

    inner class LocalBinder : Binder() {
        val service: TranslatedService get() = this@TranslatedService
    }

    private companion object {
        const val CHANNEL_ID = "lingohub-test"
    }
}
