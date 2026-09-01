package com.lingohub.android.cdn.data

import com.lingohub.android.cdn.core.LingoHub
import com.lingohub.android.cdn.core.LingoHubSDKError
import com.lingohub.android.cdn.core.UpdateManager
import com.lingohub.android.cdn.data.model.CdnErrorResponse
import com.lingohub.android.cdn.utils.LingoHubLogger
import kotlinx.serialization.json.Json

internal class Updater(val scope: ICoroutineScope = LingoHubScope()) {
    private val api = LingoHub.api
    private val updateManager = UpdateManager.Companion.getInstance()

    fun update() {
        scope.launch {
            try {
                val bundleInfoResponse = api.getBundleInfo()
                val responseCode = bundleInfoResponse.code()

                // 204 is the CDN's regular "already up to date" answer, not an error.
                if (responseCode == 204) {
                    LingoHubLogger.logger.onInfo("translations are up to date, no new bundle available")
                    return@launch
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
                        return@launch
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
                    val error = LingoHubSDKError(message, statusCode = responseCode, errorCodes = codes)

                    LingoHubLogger.logger.onError(error.message ?: "Unknown error")
                    updateManager.notifyFailure(error)
                    return@launch
                }

                val bundleInfo = bundleInfoResponse.body()!!
                LingoHubLogger.logger.onDebug("got bundleInfo: $bundleInfo")
                val bundle = api.downloadBundle(bundleInfo.filesUrl)
                LingoHubLogger.logger.onDebug("loaded bundle: $bundle")
                LingoHub.fileHelper.deleteBundle()
                LingoHub.fileHelper.unzipBundle(bundle.byteStream())
                LingoHub.onBundleUpdated(bundleInfo)
                LingoHubLogger.logger.onDebug("finished")
            } catch (t: Throwable) {
                val errorMessage = "Unknown Error while updating LingoHub package"
                LingoHubLogger.logger.onError(errorMessage, t)
                val error = LingoHubSDKError("$errorMessage: ${t.message}")
                updateManager.notifyFailure(error)
            }
        }
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
