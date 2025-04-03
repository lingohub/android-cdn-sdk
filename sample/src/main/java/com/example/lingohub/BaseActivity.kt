package com.example.lingohub

import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import com.helpers.core.Lingohub
import com.helpers.core.LingohubUpdateListener
import android.os.Bundle

abstract class BaseActivity : AppCompatActivity(), LingohubUpdateListener {

    private val lingohubDelegate: AppCompatDelegate by lazy {
        Lingohub.getAppCompatDelegate(this, AppCompatDelegate.create(this, null))
    }

    override fun getDelegate(): AppCompatDelegate {
        return lingohubDelegate
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Lingohub.addUpdateListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        Lingohub.removeUpdateListener(this)
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
