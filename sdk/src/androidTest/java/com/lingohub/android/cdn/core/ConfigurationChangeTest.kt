package com.lingohub.android.cdn.core

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * The wrapped application context copies the application's resources. These
 * tests change the configuration of the running process and check that the
 * copy follows, which is what Android and AppCompat rely on when they read
 * the configuration back through the application context.
 */
@RunWith(AndroidJUnit4::class)
class ConfigurationChangeTest {

    private val application = ApplicationProvider.getApplicationContext<TestApplication>()

    // Unwrapped: the resources Android itself updates on a configuration change.
    private val base: Context = application.baseContext

    // Uses the font scale: unlike dark mode (the uimode shell command needs API 26),
    // a test can change it on every supported API level.
    @Test
    fun applicationResourcesFollowAConfigurationChange() {
        val originalSetting = shell("settings get system font_scale").trim()
        val originalScale = base.resources.configuration.fontScale
        val targetScale = if (originalScale == 1.3f) 1.15f else 1.3f
        application.resources // copied before the change

        try {
            shell("settings put system font_scale $targetScale")
            waitUntil("font scale $targetScale") { base.resources.configuration.fontScale == targetScale }

            assertEquals(targetScale, application.resources.configuration.fontScale)
            assertEquals(base.resources.configuration, application.resources.configuration)
        } finally {
            shell(
                if (originalSetting == "null") "settings delete system font_scale"
                else "settings put system font_scale $originalSetting"
            )
            waitUntil("font scale restored") { base.resources.configuration.fontScale == originalScale }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    fun processDefaultLocaleFollowsAnAppLanguageChange() {
        val localeManager = application.getSystemService(LocaleManager::class.java)
        val originalAppLocales = localeManager.applicationLocales
        val originalDefault = Locale.getDefault()
        val target = if (originalDefault.language == "de") "fr" else "de"
        application.resources // copied before the change

        try {
            localeManager.applicationLocales = LocaleList.forLanguageTags(target)

            // Android derives the process default locale from the application's
            // resources right after updating them: a stale copy pins the old language.
            waitUntil("default locale $target") { Locale.getDefault().language == target }
            assertEquals(target, application.resources.configuration.locales[0].language)
        } finally {
            localeManager.applicationLocales = originalAppLocales
            waitUntil("default locale restored") { Locale.getDefault() == originalDefault }
        }
    }

    private fun shell(command: String): String {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        // Reading to the end waits for the command to finish.
        return ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes().decodeToString() }
    }

    private fun waitUntil(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (!condition()) {
            if (SystemClock.uptimeMillis() > deadline) fail("timed out waiting for $description")
            SystemClock.sleep(50)
        }
    }
}
