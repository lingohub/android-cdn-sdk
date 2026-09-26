package com.lingohub.android.cdn.core

import com.lingohub.android.cdn.data.model.Bundle
import com.lingohub.android.cdn.data.model.BundleInfo
import com.lingohub.android.cdn.data.model.BundleMetadata
import com.lingohub.android.cdn.data.model.Item
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal fun text(key: String, value: String) = Item(key = key, type = "TEXT", value = value)

internal fun plural(key: String, quantity: String, value: String) =
    Item(key = "${key}_$quantity", type = "PLURAL", value = value)

/**
 * Installs a release through the same steps the updater runs after a
 * download, so lookups resolve it exactly like a published release.
 */
internal fun installRelease(language: String, vararg items: Item) = runBlocking {
    val archive = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("strings.json"))
            zip.write(Json.encodeToString(listOf(Bundle(language, items.toList()))).toByteArray())
            zip.closeEntry()
        }
    }.toByteArray()
    val release = BundleInfo(
        id = "release-${UUID.randomUUID()}",
        name = "instrumented test release",
        filesUrl = "https://cdn.lingohub.com/test-release.zip",
        createdAt = "2026-09-25T00:00:00Z"
    )

    LingoHub.bundleTransitionLock.withLock {
        LingoHub.fileHelper.stageBundle(archive.inputStream())
        val activated = LingoHub.runIfConfigured(LingoHub.configurationGeneration) {
            LingoHub.fileHelper.activateStagedBundle()
            LingoHub.preferences.saveBundleMetadata(BundleMetadata(release.id, LingoHub.appVersionName))
        }
        check(activated) { "the SDK was configured again while the release was installed" }
        LingoHub.onBundleUpdated(release)
    }
}
