package com.lingohub.android.cdn.data

import com.lingohub.android.cdn.core.LingoHub
import com.lingohub.android.cdn.core.LingoHubSDKError
import com.lingohub.android.cdn.core.UpdateManager
import com.lingohub.android.cdn.data.model.CdnErrorResponse
import com.lingohub.android.cdn.utils.LingoHubLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.HttpException
import java.util.concurrent.atomic.AtomicBoolean

internal class Updater(val scope: ICoroutineScope = LingoHubScope()) {
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
            } catch (e: HttpException) {
                val message = "Error downloading LingoHub bundle (HTTP ${e.code()})"
                LingoHubLogger.logger.onError(message, e)
                updateManager.notifyFailure(
                    LingoHubSDKError(message, statusCode = e.code(), cause = e)
                )
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
        val bundleInfoResponse = api.getBundleInfo()
        val responseCode = bundleInfoResponse.code()

        // 204 is the CDN's regular "already up to date" answer, not an error.
        if (responseCode == 204) {
            LingoHubLogger.logger.onInfo("translations are up to date, no new bundle available")
            return
        }

        if (responseCode != 200) {
            val problem = decodeProblem(bundleInfoResponse.errorBody()?.string())
            val codes = problem?.codes().orEmpty()

            // 404 DISTRIBUTION_NOT_FOUND: nothing has been published for this
            // environment/type yet - a regular no-update state, not a failure.
            if (responseCode == 404 && CODE_DISTRIBUTION_NOT_FOUND in codes) {
                LingoHubLogger.logger.onInfo(
                    "no distribution release available for ${LingoHub.environment.name} yet"
                )
                return
            }

            val details = codes.takeIf { it.isNotEmpty() }?.joinToString()
                ?: problem?.detail
                ?: "no details"
            val message = when (responseCode) {
                400 -> "Error loading LingoHub package: HTTP 400, invalid request ($details)"
                401 -> "Error loading LingoHub package: HTTP 401, not authorized ($details), check apiKey"
                404 -> "Error loading LingoHub package: HTTP 404, not found ($details)"
                429 -> "LingoHub usage limit exceeded, translation updates are paused (HTTP 429, $details)"
                else -> "Error loading LingoHub package: unexpected response (HTTP $responseCode, $details)"
            }
            failUpdate(LingoHubSDKError(message, statusCode = responseCode, errorCodes = codes))
            return
        }

        val bundleInfo = bundleInfoResponse.body()
        if (bundleInfo == null) {
            failUpdate(LingoHubSDKError("Error loading LingoHub package: response body missing", statusCode = responseCode))
            return
        }
        LingoHubLogger.logger.onDebug("got bundleInfo: id=${bundleInfo.id}, name=${bundleInfo.name}")

        val downloadUrl = bundleInfo.filesUrl.toHttpUrlOrNull()
        if (downloadUrl == null || downloadUrl.scheme != "https") {
            failUpdate(LingoHubSDKError("Error loading LingoHub package: refusing bundle download from non-HTTPS URL"))
            return
        }

        val bundle = api.downloadBundle(downloadUrl.toString())
        // The download stays outside the lock; the disk transition (install,
        // refresh, metadata) must not interleave with the startup purge/refresh.
        LingoHub.bundleTransitionLock.withLock {
            LingoHub.fileHelper.installBundle(bundle.byteStream())
            LingoHub.onBundleUpdated(bundleInfo)
        }
        LingoHubLogger.logger.onDebug("finished")
    }

    private fun failUpdate(error: LingoHubSDKError) {
        LingoHubLogger.logger.onError(error.message ?: "Unknown error")
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
    }
}
