package com.lingohub.android.cdn.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.lingohub.android.cdn.core.Lingohub
import com.lingohub.android.cdn.core.LingohubUpdateListener

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