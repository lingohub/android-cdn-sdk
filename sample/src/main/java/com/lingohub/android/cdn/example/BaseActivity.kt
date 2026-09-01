package com.lingohub.android.cdn.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.lingohub.android.cdn.core.LingoHub
import com.lingohub.android.cdn.core.LingoHubUpdateListener

abstract class BaseActivity : AppCompatActivity(), LingoHubUpdateListener {

    private val lingoHubDelegate: AppCompatDelegate by lazy {
        LingoHub.getAppCompatDelegate(this, AppCompatDelegate.create(this, null))
    }

    override fun getDelegate(): AppCompatDelegate {
        return lingoHubDelegate
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LingoHub.addUpdateListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        LingoHub.removeUpdateListener(this)
    }

    override fun onUpdate() {
        // Possible solution (Not recommended)
        // Recreate the activity to reload all resources with new translations
        // State needs to be saved
        runOnUiThread {
            recreate()
        }
    }

    override fun onFailure(throwable: Throwable) {
        // Handle failure if needed
    }
}