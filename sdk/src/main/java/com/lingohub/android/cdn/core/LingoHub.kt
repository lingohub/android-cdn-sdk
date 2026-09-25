package com.lingohub.android.cdn.core

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.activity.ComponentActivity
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.ViewPumpAppCompatDelegate
import com.lingohub.android.cdn.data.model.BundleInfo
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
import com.lingohub.android.cdn.data.UpdatePolicy
import com.lingohub.android.cdn.data.Updater
import dev.b3nedikt.viewpump.ViewPump
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import java.util.concurrent.TimeUnit

@Keep
object LingoHub {

    internal var apiKey: String? = null
    internal lateinit var appVersionName: String
    internal lateinit var packageName: String
    internal lateinit var api: Api
    internal lateinit var updater: Updater
    internal lateinit var preferences: IPreferences

    // Random per-install identifier sent as clientUser for usage metering.
    // Deliberately not a hardware identifier: it is app-scoped, cannot be
    // correlated across apps, and resets on uninstall/clear-data.
    internal lateinit var clientId: String
    internal lateinit var fileHelper: IFileHelper
    internal lateinit var environment: Environment

    // Created up front: wrapped contexts look up strings before configure()
    // runs and then simply see no release until the first refresh.
    private val bundleHelper = BundleHelper()
    private val emptyRepository: IRepository = object : IRepository {}

    // Serializes every bundle state transition (app-version purge, initial
    // refresh, install + refresh after an update) so their disk operations
    // cannot interleave and an older snapshot can never be published last.
    internal val bundleTransitionLock = Mutex()

    // Counts configure() calls. An update cycle belongs to the configuration it started with: once the
    // app configures the SDK again, the cycle stops without changes (see Updater and runIfConfigured).
    private val configurationLock = Any()

    @Volatile
    internal var configurationGeneration = 0L
        private set

    // Add UpdateManager instance
    private val updateManager by lazy { UpdateManager.getInstance() }

    // The minimum time between update checks: the app's choice (setMinimumCheckInterval), otherwise
    // 15 minutes, and none in debuggable builds so that every update() call checks during development.
    @Volatile
    internal var minimumCheckIntervalOverrideMs: Long? = null

    @Volatile
    private var debuggable = false

    internal val minimumCheckIntervalMs: Long
        get() = minimumCheckIntervalOverrideMs
            ?: if (debuggable) 0 else UpdatePolicy.RELEASE_MINIMUM_CHECK_INTERVAL_MS

    @Keep
    @JvmStatic
    fun configure(
        context: Context,
        apiKey: String,
        environment: Environment? = Environment.PRODUCTION,
        logLevel: LingoHubLogLevel = LingoHubLogLevel.NONE
    ) {
        // Supersedes running update cycles before anything changes; one that is activating its
        // release right now finishes that first (see runIfConfigured)
        synchronized(configurationLock) { configurationGeneration++ }
        LingoHubLogger.init(logLevel)
        SnapKitHelper.enableIfTest()
        this.environment = environment ?: Environment.PRODUCTION
        this.apiKey = apiKey
        this.preferences = Preferences(context)
        this.clientId = preferences.getClientId()
            ?: UUID.randomUUID().toString().also { preferences.saveClientId(it) }
        packageName = context.packageName
        val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
        appVersionName = packageInfo.versionName.toString()
        debuggable = ((context.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        fileHelper = FileHelper(context.filesDir)

        this.api = Api.Companion.build()
        this.updater = Updater(LingoHubScope())

        ViewPump.init(InflationInterceptor)

        updater.scope.launch {
            bundleTransitionLock.withLock {
                purgeBundleOnAppUpdate()
                bundleHelper.refresh()
            }
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

    /**
     * Returns a context whose string lookups (`getString`, `getText`,
     * `getQuantityString`, `getStringArray`) serve the downloaded translations
     * and fall back to the strings packaged in the app. Use it where the
     * Activity delegate does not reach: the `Application`, `Service`s and
     * `BroadcastReceiver`s.
     *
     * In an `Application` or `Service`, create it once and return its
     * resources from `getResources()`. Do not pass it to
     * `Application.attachBaseContext()`: the app then crashes when Android
     * delivers a broadcast to a receiver declared in the manifest. See the
     * README for the full pattern.
     *
     * Safe to call before [configure] (lookups return the packaged strings
     * until a release is loaded) and from any thread. Wrapping a wrapped
     * context returns it unchanged.
     */
    @Keep
    @JvmStatic
    fun wrap(base: Context): Context =
        base as? LingoHubContextWrapper ?: LingoHubContextWrapper(base)

    /**
     * Checks the CDN for a newer release and installs it; [LingoHubUpdateListener]s hear about the
     * outcome. Call it whenever your app starts or comes to the foreground: within the minimum interval
     * after the last successful update (see [setMinimumCheckInterval]) it returns without contacting the
     * CDN, and while a failure has paused update checks, it reports that failure to
     * [LingoHubUpdateListener.onFailure] the same way (see "Failures and retries" in the README).
     */
    @Keep
    @JvmStatic
    fun update() {
        ensureInit()
        LingoHubLogger.logger.onInfo("checking for bundle update (${environment.name})")
        updater.update()
    }

    /**
     * Sets the minimum time between update checks. For this long after the last successful update,
     * [update] returns without contacting the CDN. Defaults to 15 minutes, and to 0 in debuggable
     * builds so that every call checks while you develop. Pauses after failed checks apply regardless
     * of it. Negative values count as 0.
     */
    @Keep
    @JvmStatic
    fun setMinimumCheckInterval(interval: Long, unit: TimeUnit) {
        minimumCheckIntervalOverrideMs = unit.toMillis(interval.coerceAtLeast(0))
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

    /**
     * Runs [block] if the SDK is still configured as in [generation] and returns whether it did. configure()
     * waits for [block] meanwhile, so what [block] changes, such as the live bundle, changes entirely before
     * the app configures the SDK again, or not at all. Keep [block] brief: configure() usually runs on the main thread.
     */
    internal fun runIfConfigured(generation: Long, block: () -> Unit): Boolean = synchronized(configurationLock) {
        if (generation != configurationGeneration) return false
        block()
        true
    }

    /** Serves a release an update cycle just activated (see Updater) and tells the listeners. */
    internal suspend fun onBundleUpdated(bundleInfo: BundleInfo) {
        // Await the disk read before notifying listeners, so lookups they
        // trigger already resolve against the new release.
        bundleHelper.refresh()
        LingoHubLogger.logger.onInfo("downloaded new bundle with id: ${bundleInfo.id}")

        updateManager.notifyDataChanged()
    }

    internal suspend fun purgeBundleOnAppUpdate() {
        val savedMetadata = preferences.getBundleMetadata()
        val bundleAppVersion = savedMetadata?.appVersion
        val currentAppVersion = appVersionName

        LingoHubLogger
            .logger.onInfo("checking metadata $savedMetadata")
        if (bundleAppVersion != null && bundleAppVersion != currentAppVersion) {
            LingoHubLogger.logger.onInfo("bundle update required due to app version change $bundleAppVersion to $currentAppVersion")
            LingoHubLogger.logger.onInfo("app has been updated to $currentAppVersion, clearing local bundle (for app version $bundleAppVersion)")
            // Clear the metadata before the first suspension point so an
            // update started right after configure() reports no stale
            // clientRelease to the server.
            preferences.clearBundleMetadata()
            fileHelper.deleteBundle()
        }
    }

    internal fun getRepository(locale: Locale): IRepository =
        bundleHelper.repositoryForLocale(locale) ?: emptyRepository

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