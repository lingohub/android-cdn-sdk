package com.lingohub.android.cdn.core

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.ViewPumpAppCompatDelegate
import androidx.core.os.ConfigurationCompat
import com.lingohub.android.cdn.data.model.BundleInfo
import com.lingohub.android.cdn.data.model.BundleMetadata
import com.lingohub.android.cdn.data.model.Environment
import com.lingohub.android.cdn.ui.InflationInterceptor
import com.lingohub.android.cdn.utils.BundleHelper
import com.lingohub.android.cdn.utils.LingoHubLogLevel
import com.lingohub.android.cdn.utils.LingoHubLogger
import com.lingohub.android.cdn.utils.SnapKitHelper
import com.lingohub.android.cdn.data.Api
import com.lingohub.android.cdn.data.FileHelper
import com.lingohub.android.cdn.data.IFileHelper
import com.lingohub.android.cdn.data.IPreferences
import com.lingohub.android.cdn.data.IRepository
import com.lingohub.android.cdn.data.LingoHubScope
import com.lingohub.android.cdn.data.Preferences
import com.lingohub.android.cdn.data.Repository
import com.lingohub.android.cdn.data.Updater
import dev.b3nedikt.viewpump.ViewPump
import java.io.File
import java.util.*

@Keep
object LingoHub {

    internal var apiKey: String? = null
    internal lateinit var appVersionCode: String
    internal lateinit var packageName: String
    internal lateinit var api: Api
    internal lateinit var updater: Updater
    internal lateinit var preferences: IPreferences
    internal lateinit var languages: String
    internal lateinit var deviceId: String
    internal lateinit var fileHelper: IFileHelper
    internal lateinit var environment: Environment
    private lateinit var outputDirectory: File
    private lateinit var bundleHelper: BundleHelper

    private val repositoryMap = mutableMapOf<Locale, IRepository>()
    private val emptyRepository: IRepository = object : IRepository {}

    // Add UpdateManager instance
    private val updateManager by lazy { UpdateManager.getInstance() }

    @SuppressLint("HardwareIds")
    @Keep
    @JvmStatic
    fun configure(
        context: Context,
        apiKey: String,
        environment: Environment? = Environment.PRODUCTION,
        logLevel: LingoHubLogLevel = LingoHubLogLevel.NONE
    ) {
        LingoHubLogger.init(logLevel)
        SnapKitHelper.enableIfTest()
        this.environment = environment ?: Environment.PRODUCTION
        this.apiKey = apiKey
        this.deviceId =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        packageName = context.packageName
        val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
        appVersionCode = packageInfo.versionName.toString()
        readDeviceLocales(context)

        outputDirectory = File(context.filesDir, "lingohub").apply {
            mkdirs()
        }

        fileHelper = FileHelper(outputDirectory)

        this.api = Api.Companion.build()
        this.preferences = Preferences(context)
        this.updater = Updater(LingoHubScope())

        ViewPump.init(InflationInterceptor)

        checkIfUpdated()


        bundleHelper = BundleHelper().also {
            it.refresh()
        }
    }

    @Keep
    @JvmStatic
    fun getAppCompatDelegate(
        activity: ComponentActivity,
        baseDelegate: AppCompatDelegate
    ): AppCompatDelegate {
        return ViewPumpAppCompatDelegate(
            baseDelegate = baseDelegate,
            baseContext = activity,
            wrapContext = { baseContext -> LingoHubContextWrapper(baseContext) }
        )
    }

    @Keep
    @JvmStatic
    fun update() {
        ensureInit()
        LingoHubLogger.logger.onInfo("checking for bundle update (${environment.name})")
        updater.update()
    }

    @Keep
    @JvmStatic
    fun setLocale(locale: Locale) {
        LocaleProvider.currentLocale = locale
    }

    /**
     * The locale currently used to resolve translations. Falls back to the
     * device locale until [setLocale] is called. Use it to restore UI state
     * after an Activity recreation.
     */
    @Keep
    @JvmStatic
    fun getCurrentLocale(): Locale {
        return LocaleProvider.currentLocale
    }

    internal fun stringRequested(key: String, string: String) {
        SnapKitHelper.addString(key, string)
    }

    private fun ensureInit() {
        if (apiKey == null) {
            throw LingoHubSDKError("The apiKey is missing.")
        }
    }

    internal fun onBundleUpdated(bundleInfo: BundleInfo) {
        bundleHelper.refresh()
        clearRepositories()

        val metaData = BundleMetadata(bundleInfo.id, appVersionCode)
        LingoHubLogger.logger.onDebug("saving bundle meta: $metaData")
        preferences.saveBundleMetadata(metaData)
        LingoHubLogger.logger.onInfo("downloaded new bundle with id: ${bundleInfo.id}")

        // Notify listeners that data has changed
        updateManager.notifyDataChanged()
    }

    internal fun checkIfUpdated() {
        val savedMetadata = preferences.getBundleMetadata()
        val bundleAppVersion = savedMetadata?.appVersion?.toString()
        val currentAppVersion = appVersionCode.toString()

        LingoHubLogger
            .logger.onInfo("checking metadata $savedMetadata")
        if (bundleAppVersion != null && bundleAppVersion != currentAppVersion) {
            LingoHubLogger.logger.onInfo("bundle update required due to app version change $bundleAppVersion to $currentAppVersion")
            LingoHubLogger.logger.onInfo("app has been updated to $currentAppVersion, clearing local bundle (for app version $bundleAppVersion)")
            preferences.clearBundleMetadata()
            updater.scope.launch { fileHelper.deleteBundle() }
        }
    }

    private fun readDeviceLocales(context: Context) {
        languages = ConfigurationCompat.getLocales(context.resources.configuration).toLanguageTags()
    }

    internal fun getRepository(locale: Locale): IRepository {
        return repositoryMap[locale] ?: buildRepository(locale)?.also { repositoryMap[locale] = it }
        ?: emptyRepository
    }

    internal fun addRepository(locale: Locale, repository: IRepository) =
        repositoryMap.put(locale, repository)

    private fun clearRepositories() {
        repositoryMap.clear()
        LingoHubLogger.logger.onDebug("cleared repositories")
    }

    private fun buildRepository(locale: Locale): IRepository? {
        return bundleHelper.bundleForLocale(locale)?.let { Repository(it) }
    }

    @Keep
    @JvmStatic
    fun addUpdateListener(listener: LingoHubUpdateListener) {
        updateManager.addLoadingStateListener(listener)
    }

    @Keep
    @JvmStatic
    fun removeUpdateListener(listener: LingoHubUpdateListener) {
        updateManager.removeLoadingStateListener(listener)
    }

}