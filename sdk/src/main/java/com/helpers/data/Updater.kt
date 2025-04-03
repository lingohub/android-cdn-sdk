package com.helpers.data

import com.helpers.core.Lingohub
import com.helpers.core.LingohubSDKError
import com.helpers.core.UpdateManager
import com.helpers.utils.LingohubLogger

internal class Updater(val scope: ICoroutineScope = LingohubScope()) {
    private val api = Lingohub.api
    private val updateManager = UpdateManager.getInstance()

    fun update() {
        scope.launch {
            try {
                val bundleInfoResponse = api.getBundleInfo()
                val responseCode = bundleInfoResponse.code()

                if (responseCode != 200) {
                    val error = when (responseCode) {
                        204 -> LingohubSDKError("No new Lingohub package available")
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

