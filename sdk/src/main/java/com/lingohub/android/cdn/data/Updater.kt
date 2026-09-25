package com.lingohub.android.cdn.data

import com.lingohub.android.cdn.core.LingoHub
import com.lingohub.android.cdn.core.LingoHubSDKError
import com.lingohub.android.cdn.core.UpdateManager
import com.lingohub.android.cdn.data.model.BundleInfo
import com.lingohub.android.cdn.data.model.BundleMetadata
import com.lingohub.android.cdn.data.model.CdnErrorResponse
import com.lingohub.android.cdn.data.model.Environment
import com.lingohub.android.cdn.utils.LingoHubLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.ResponseBody
import retrofit2.HttpException
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs update cycles (check, download, install), paced by the persisted [UpdateSchedule] and the
 * [UpdatePolicy] shared with the iOS SDK.
 *
 * @param clock the time update checks are paced by; tests move it instead of waiting.
 * @param waitBeforeRetry suspends a cycle before its single retry after a 5xx; tests record the delay.
 * @param ioDispatcher runs the activation of a release, which blocks on the disk; tests keep it on their scheduler.
 */
internal class Updater(
    val scope: ICoroutineScope = LingoHubScope(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val waitBeforeRetry: suspend (Long) -> Unit = { delay(it) },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val api = LingoHub.api
    private val updateManager = UpdateManager.Companion.getInstance()

    // Single-flight guard: concurrent update() calls would otherwise race
    // through deletion and extraction of the same bundle directory.
    private val updateInFlight = AtomicBoolean(false)

    fun update() {
        if (!updateInFlight.compareAndSet(false, true)) {
            LingoHubLogger.logger.onInfo("bundle update already in progress, skipping")
            return
        }
        scope.launch {
            try {
                runUpdate()
            } catch (e: CancellationException) {
                throw e
            } catch (e: CycleSuperseded) {
                LingoHubLogger.logger.onInfo("the SDK was configured again during the update check, which stopped without changes")
            } catch (e: LingoHubSDKError) {
                failUpdate(e)
            } catch (e: HttpException) {
                failUpdate(LingoHubSDKError("Error downloading LingoHub bundle (HTTP ${e.code()})", statusCode = e.code(), cause = e))
            } catch (e: Exception) {
                val errorMessage = "Unknown Error while updating LingoHub package"
                LingoHubLogger.logger.onError(errorMessage, e)
                updateManager.notifyFailure(
                    LingoHubSDKError("$errorMessage: ${e.message}", cause = e)
                )
            } finally {
                updateInFlight.set(false)
            }
        }
    }

    private suspend fun runUpdate() {
        val context = CycleContext.current()
        val schedule = LingoHub.preferences.getUpdateSchedule(context.scope)
        when (val decision = schedule.decide(clock(), LingoHub.minimumCheckIntervalMs)) {
            is UpdateSchedule.Decision.Paused -> {
                LingoHubLogger.logger.onInfo("update checks are paused until ${Date(decision.cooldown.untilMs)}, skipping the check")
                throw decision.cooldown.toError()
            }
            is UpdateSchedule.Decision.Skip -> {
                LingoHubLogger.logger.onInfo("last successful update is recent, skipping the check until ${Date(decision.untilMs)}")
                return
            }
            UpdateSchedule.Decision.Check -> Unit
        }

        val cycle = Cycle(context, schedule)
        try {
            cycle.run()
            cycle.schedule = cycle.schedule.recordSuccess(clock())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keeps what the cycle recorded: a pause, or the end of a series of 5xx
            cycle.save()
            throw e
        }
        cycle.save()
    }

    /**
     * The configuration an update cycle runs with, captured when it starts: every request of the cycle is
     * sent with it, and its answers are recorded under its scope. The cycle belongs to the [generation] of
     * the configuration: once the app calls `configure()` again, the cycle stops without changes, see
     * [CycleSuperseded].
     */
    private data class CycleContext(
        val generation: Long,
        val apiKey: String,
        val environment: Environment,
        val appVersion: String,
    ) {
        val scope: UpdateSchedule.Scope
            get() = UpdateSchedule.Scope.of(appVersion, environment, apiKey)

        fun ensureCurrent() {
            if (generation != LingoHub.configurationGeneration) throw CycleSuperseded()
        }

        companion object {
            // The generation first: should configure() run meanwhile, the cycle is superseded from the start
            fun current() = CycleContext(
                LingoHub.configurationGeneration,
                LingoHub.apiKey.orEmpty(),
                LingoHub.environment,
                LingoHub.appVersionName,
            )
        }
    }

    /**
     * Ends a cycle that finds the SDK configured again: it sends no further request, installs nothing,
     * records nothing and reports nothing. Whatever it ended with belongs to a configuration no longer in use;
     * the app's next update() call checks with the new one.
     */
    private class CycleSuperseded : Exception()

    /** One update cycle. Its checks record the CDN's answers into [schedule]. */
    private inner class Cycle(private val context: CycleContext, var schedule: UpdateSchedule) {

        /**
         * Checks for a release, downloads and installs it. A download the storage refuses (an expired
         * URL, a 5xx) gets one fresh check for a new URL; every other failure ends the cycle, and the next
         * update() call is the retry.
         */
        suspend fun run() {
            var bundleInfo = check(retryingServerErrors = true) ?: return
            context.ensureCurrent()
            val bundle = try {
                download(bundleInfo)
            } catch (e: HttpException) {
                context.ensureCurrent()
                LingoHubLogger.logger.onInfo("bundle download failed (HTTP ${e.code()}), checking again for a fresh download URL")
                bundleInfo = check(retryingServerErrors = false) ?: return
                context.ensureCurrent()
                download(bundleInfo)
            }
            // The download stays outside the lock; the disk transition (install,
            // refresh, metadata) must not interleave with the startup purge/refresh.
            LingoHub.bundleTransitionLock.withLock {
                LingoHub.fileHelper.stageBundle(bundle.byteStream())
                activate(bundleInfo)
                LingoHub.onBundleUpdated(bundleInfo)
            }
            LingoHubLogger.logger.onDebug("finished")
        }

        /**
         * Makes the staged release the live one and records it as installed, both while configure() waits:
         * a release must not go live once the app has configured the SDK again, and a check with the new
         * configuration must find the metadata of the bundle that is live.
         */
        private suspend fun activate(bundleInfo: BundleInfo) {
            val activated = withContext(ioDispatcher) {
                LingoHub.runIfConfigured(context.generation) {
                    LingoHub.fileHelper.activateStagedBundle()
                    val metadata = BundleMetadata(bundleInfo.id, context.appVersion)
                    LingoHubLogger.logger.onDebug("saving bundle meta: $metadata")
                    LingoHub.preferences.saveBundleMetadata(metadata)
                }
            }
            if (!activated) {
                LingoHub.fileHelper.discardStagedBundle()
                throw CycleSuperseded()
            }
        }

        /**
         * Saves what the cycle recorded, unless the app configured the SDK again meanwhile: the schedule
         * then belongs to a configuration no longer in use, and saving it would replace the one the current
         * configuration records. The cycle's outcome is void then, and [CycleSuperseded] replaces it.
         */
        fun save() {
            if (!LingoHub.runIfConfigured(context.generation) { LingoHub.preferences.saveUpdateSchedule(schedule) }) {
                throw CycleSuperseded()
            }
        }

        /**
         * Sends one check request and applies the policy to the answer: returns the release to install,
         * or null when there is nothing new. A 5xx is retried once when [retryingServerErrors]; a 5xx that
         * persists, or a 429, pauses update checks.
         */
        suspend fun check(retryingServerErrors: Boolean): BundleInfo? {
            val response = api.getBundleInfo(
                authorization = "Bearer ${context.apiKey}",
                body = PackageRequest(distributionEnvironment = context.environment.name, clientVersion = context.appVersion),
            )
            val code = response.code()

            // 204 is the CDN's regular "already up to date" answer, not an error.
            if (code == 204) {
                schedule = schedule.recordAnswer()
                LingoHubLogger.logger.onInfo("translations are up to date, no new bundle available")
                return null
            }
            if (code == 200) {
                schedule = schedule.recordAnswer()
                val bundleInfo = response.body()
                    ?: throw LingoHubSDKError("Error loading LingoHub package: response body missing", statusCode = code)
                LingoHubLogger.logger.onDebug("got bundleInfo: id=${bundleInfo.id}, name=${bundleInfo.name}")
                return bundleInfo
            }

            val problem = decodeProblem(response.errorBody()?.string())
            val codes = problem?.codes().orEmpty()
            val retryAfterMs = UpdatePolicy.retryAfterMs(response.headers()["Retry-After"], clock())
            when {
                // 404 DISTRIBUTION_NOT_FOUND: nothing has been published for this
                // environment/type yet - a regular no-update state, not a failure.
                code == 404 && CODE_DISTRIBUTION_NOT_FOUND in codes -> {
                    schedule = schedule.recordAnswer()
                    LingoHubLogger.logger.onInfo(
                        "no distribution release available for ${context.environment.name} yet"
                    )
                    return null
                }
                code == 429 -> schedule = schedule.recordUsageLimit(codes, retryAfterMs, clock())
                code in 500..599 -> {
                    val delayMs = if (retryingServerErrors) UpdatePolicy.serverErrorRetryDelayMs(retryAfterMs) else null
                    if (delayMs != null) {
                        LingoHubLogger.logger.onInfo("server error (HTTP $code), retrying once in $delayMs ms")
                        waitBeforeRetry(delayMs)
                        context.ensureCurrent()
                        return check(retryingServerErrors = false)
                    }
                    schedule = schedule.recordServerError(code, codes, retryAfterMs, clock())
                }
                // No retry: the next update() call checks again
                else -> schedule = schedule.recordAnswer()
            }
            throw checkError(code, codes, problem)
        }
    }

    /** Downloads a release archive, over HTTPS only. */
    private suspend fun download(bundleInfo: BundleInfo): ResponseBody {
        val downloadUrl = bundleInfo.filesUrl.toHttpUrlOrNull()
        if (downloadUrl == null || downloadUrl.scheme != "https") {
            throw LingoHubSDKError("Error loading LingoHub package: refusing bundle download from non-HTTPS URL")
        }
        return api.downloadBundle(downloadUrl.toString())
    }

    private fun checkError(code: Int, codes: List<String>, problem: CdnErrorResponse?): LingoHubSDKError {
        val details = codes.takeIf { it.isNotEmpty() }?.joinToString()
            ?: problem?.detail
            ?: "no details"
        val message = when (code) {
            400 -> "Error loading LingoHub package: HTTP 400, invalid request ($details)"
            401 -> "Error loading LingoHub package: HTTP 401, not authorized ($details), check apiKey"
            404 -> "Error loading LingoHub package: HTTP 404, not found ($details)"
            429 -> "LingoHub usage limit exceeded, translation updates are paused (HTTP 429, $details)"
            in 500..599 -> "LingoHub CDN unavailable, translation updates are paused (HTTP $code, $details)"
            else -> "Error loading LingoHub package: unexpected response (HTTP $code, $details)"
        }
        return LingoHubSDKError(message, statusCode = code, errorCodes = codes)
    }

    /**
     * Reports a failed update. A client error (400, 401, a 404 other than DISTRIBUTION_NOT_FOUND, …) comes
     * back on every check until the app or its configuration changes, so each one is logged once per process,
     * across `configure()` calls too.
     */
    private fun failUpdate(error: LingoHubSDKError) {
        val statusCode = error.statusCode
        val repeatedClientError = statusCode != null && statusCode in 400..499 && statusCode != 429 &&
            !loggedClientErrors.add("$statusCode ${error.errorCodes.joinToString(",")}")
        if (!repeatedClientError) {
            LingoHubLogger.logger.onError(error.message ?: "Unknown error", error.cause)
        }
        updateManager.notifyFailure(error)
    }

    private fun decodeProblem(raw: String?): CdnErrorResponse? {
        if (raw.isNullOrBlank()) return null
        return try {
            problemJson.decodeFromString<CdnErrorResponse>(raw)
        } catch (e: Exception) {
            LingoHubLogger.logger.onDebug("could not decode error body: ${e.message}")
            null
        }
    }

    private companion object {
        private const val CODE_DISTRIBUTION_NOT_FOUND = "DISTRIBUTION_NOT_FOUND"
        private val problemJson = Json { ignoreUnknownKeys = true }

        // Process-wide: configure() creates a new Updater.
        private val loggedClientErrors: MutableSet<String> = ConcurrentHashMap.newKeySet()
    }
}
