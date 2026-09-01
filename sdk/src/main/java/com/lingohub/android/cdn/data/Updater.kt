package com.lingohub.android.cdn.data

import com.lingohub.android.cdn.core.Lingohub
import com.lingohub.android.cdn.core.LingohubSDKError
import com.lingohub.android.cdn.core.UpdateManager
import com.lingohub.android.cdn.utils.LingohubLogger

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
                    val error = when (responseCode) {
                        400 -> LingohubSDKError("Error loading Lingohub package: invalid request, check apiKey")
                        401 -> LingohubSDKError("Error loading Lingohub package: Not Authorized")
                        404 -> LingohubSDKError("Error loading Lingohub package: project not found")
                        else -> LingohubSDKError("Error loading Lingohub package: invalid response code $responseCode")
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
}

