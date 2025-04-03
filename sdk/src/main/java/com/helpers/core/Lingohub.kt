package com.helpers.core

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.ViewPumpAppCompatDelegate
import androidx.core.os.ConfigurationCompat
import com.helpers.data.*
import com.helpers.data.model.BundleInfo
import com.helpers.data.model.BundleMetadata
import com.helpers.data.model.Environment
import com.helpers.ui.InflationInterceptor
import com.helpers.utils.BundleHelper
import com.helpers.utils.LingohubLogLevel
import com.helpers.utils.ILingohubLogger
import com.helpers.utils.LingohubLogger
import com.helpers.utils.SnapKitHelper
import dev.b3nedikt.viewpump.ViewPump
import java.io.File
import java.util.*

@Keep
object Lingohub {

    internal var apiKey: String? = null
    internal lateinit var appVersionCode: String
    internal lateinit var packageName: String
    internal lateinit var api: Api
    internal lateinit var updater: Updater
    internal lateinit var preferences: IPreferences
    internal lateinit var appLanguage: String
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
        logLevel: LingohubLogLevel = LingohubLogLevel.NONE
    ) {
        LingohubLogger.init(logLevel)
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

        this.api = Api.build()
        this.preferences = Preferences(context)
        this.updater = Updater(LingohubScope())

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
            wrapContext = { baseContext -> LingohubContextWrapper(baseContext) }
        )
    }

    @Keep
    @JvmStatic
    fun update() {
        ensureInit()
        LingohubLogger.logger.onInfo("checking for bundle update (${environment.name})")
        updater.update()
    }

    @Keep
    @JvmStatic
    fun setLocale(locale: Locale) {
        LocaleProvider.currentLocale = locale
    }

    internal fun stringRequested(key: String, string: String) {
        SnapKitHelper.addString(key, string)
    }

    private fun ensureInit() {
        if (apiKey == null) {
            throw LingohubSDKError("The apiKey is missing.")
        }
    }

    internal fun onBundleUpdated(bundleInfo: BundleInfo) {
        bundleHelper.refresh()
        clearRepositories()

        val metaData = BundleMetadata(bundleInfo.id, appVersionCode)
        LingohubLogger.logger.onDebug("saving bundle meta: $metaData")
        preferences.saveBundleMetadata(metaData)
        LingohubLogger.logger.onInfo("downloaded new bundle with id: ${bundleInfo.id}")

        // Notify listeners that data has changed
        updateManager.notifyDataChanged()
    }

    internal fun checkIfUpdated() {
        val savedMetadata = preferences.getBundleMetadata()
        val bundleAppVersion = savedMetadata?.appVersion?.toString()
        val currentAppVersion = appVersionCode.toString()

        LingohubLogger
            .logger.onInfo("checking metadata $savedMetadata")
        if (bundleAppVersion != null && bundleAppVersion != currentAppVersion) {
            LingohubLogger.logger.onInfo("bundle update required due to app version change $bundleAppVersion to $currentAppVersion")
            LingohubLogger.logger.onInfo("app has been updated to $currentAppVersion, clearing local bundle (for app version $bundleAppVersion)")
            preferences.clearBundleMetadata()
            updater.scope.launch { fileHelper.deleteBundle() }
        }
    }

    private fun readDeviceLocales(context: Context) {
        languages = ConfigurationCompat.getLocales(context.resources.configuration).toLanguageTags()
        appLanguage = LocaleProvider.currentLocale.language
    }

    internal fun getRepository(locale: Locale): IRepository {
        return repositoryMap[locale] ?: buildRepository(locale)?.also { repositoryMap[locale] = it }
        ?: emptyRepository
    }

    internal fun addRepository(locale: Locale, repository: IRepository) =
        repositoryMap.put(locale, repository)

    private fun clearRepositories() {
        repositoryMap.clear()
        LingohubLogger.logger.onDebug("cleared repositories")
    }

    private fun buildRepository(locale: Locale): IRepository? {
        return bundleHelper.bundleForLocale(locale)?.let { Repository(it) }
    }

    @Keep
    @JvmStatic
    fun addUpdateListener(listener: LingohubUpdateListener) {
        updateManager.addLoadingStateListener(listener)
    }

    @Keep
    @JvmStatic
    fun removeUpdateListener(listener: LingohubUpdateListener) {
        updateManager.removeLoadingStateListener(listener)
    }

}