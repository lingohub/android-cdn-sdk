package com.lingohub.android.cdn.data

import com.lingohub.android.cdn.core.Lingohub
import com.lingohub.android.cdn.core.LingohubSDKError
import com.lingohub.android.cdn.core.UpdateManager
import com.lingohub.android.cdn.data.model.CdnErrorResponse
import com.lingohub.android.cdn.utils.LingohubLogger
import kotlinx.serialization.json.Json

internal class Updater(val scope: ICoroutineScope = LingohubScope()) {
    private val api = Lingohub.api
    private val updateManager = UpdateManager.Companion.getInstance()

    fun update() {
        scope.launch {
            try {
                val bundleInfoResponse = api.getBundleInfo()
                val responseCode = bundleInfoResponse.code()

                // 204 is the CDN's regular "already up to date" answer, not an error.
                if (responseCode == 204) {
                    LingohubLogger.logger.onInfo("translations are up to date, no new bundle available")
                    return@launch
                }

                if (responseCode != 200) {
                    val problem = decodeProblem(bundleInfoResponse.errorBody()?.string())
                    val codes = problem?.codes().orEmpty()

                    // 404 DISTRIBUTION_NOT_FOUND: nothing has been published for this
                    // environment/type yet - a regular no-update state, not a failure.
                    if (responseCode == 404 && CODE_DISTRIBUTION_NOT_FOUND in codes) {
                        LingohubLogger.logger.onInfo(
                            "no distribution release available for ${Lingohub.environment.name} yet"
                        )
                        return@launch
                    }

                    val details = codes.takeIf { it.isNotEmpty() }?.joinToString()
                        ?: problem?.detail
                        ?: "no details"
                    val error = when (responseCode) {
                        400 -> LingohubSDKError("Error loading Lingohub package: invalid request ($details)")
                        401 -> LingohubSDKError("Error loading Lingohub package: not authorized ($details), check apiKey")
                        404 -> LingohubSDKError("Error loading Lingohub package: not found ($details)")
                        429 -> LingohubSDKError("Lingohub usage limit exceeded, translation updates are paused ($details)")
                        else -> LingohubSDKError("Error loading Lingohub package: invalid response code $responseCode ($details)")
                    }

                    LingohubLogger.logger.onError(error.message ?: "Unknown error")
                    updateManager.notifyFailure(error)
                    return@launch
                }

                val bundleInfo = bundleInfoResponse.body()!!
                LingohubLogger.logger.onDebug("got bundleInfo: $bundleInfo")
                val bundle = api.downloadBundle(bundleInfo.filesUrl)
                LingohubLogger.logger.onDebug("loaded bundle: $bundle")
                Lingohub.fileHelper.deleteBundle()
                Lingohub.fileHelper.unzipBundle(bundle.byteStream())
                Lingohub.onBundleUpdated(bundleInfo)
                LingohubLogger.logger.onDebug("finished")
            } catch (t: Throwable) {
                val errorMessage = "Unknown Error while updating Lingohub package"
                LingohubLogger.logger.onError(errorMessage, t)
                val error = LingohubSDKError("$errorMessage: ${t.message}")
                updateManager.notifyFailure(error)
            }
        }
    }

    private fun decodeProblem(raw: String?): CdnErrorResponse? {
        if (raw.isNullOrBlank()) return null
        return try {
            problemJson.decodeFromString<CdnErrorResponse>(raw)
        } catch (e: Exception) {
            LingohubLogger.logger.onDebug("could not decode error body: ${e.message}")
            null
        }
    }

    private companion object {
        private const val CODE_DISTRIBUTION_NOT_FOUND = "DISTRIBUTION_NOT_FOUND"
        private val problemJson = Json { ignoreUnknownKeys = true }
    }
}
