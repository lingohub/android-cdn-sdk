package com.lingohub.android.cdn.core

import android.app.Application
import android.content.res.Resources
import com.lingohub.android.cdn.data.model.Environment
import com.lingohub.android.cdn.test.R

class TestApplication : Application() {
    private val lingoHubContext by lazy { LingoHub.wrap(baseContext) }

    /** What getString() returned before LingoHub.configure() ran. */
    lateinit var greetingBeforeConfigure: String
        private set

    override fun getResources(): Resources = lingoHubContext.resources

    override fun onCreate() {
        super.onCreate()
        greetingBeforeConfigure = getString(R.string.lh_test_greeting)
        LingoHub.configure(this, apiKey = "lh-cdn_instrumented-test", environment = Environment.TEST)
    }
}
