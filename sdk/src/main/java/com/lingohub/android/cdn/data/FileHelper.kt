package com.lingohub.android.cdn.data

import com.lingohub.android.cdn.data.model.Bundle
import com.lingohub.android.cdn.utils.LingoHubLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream


internal interface IFileHelper {
    suspend fun installBundle(inputStream: InputStream)
    suspend fun readBundle(): List<Bundle>?
    suspend fun deleteBundle()
}

/**
 * Owns the on-disk bundle layout below [baseDir]:
 * - `lingohub/` is the live bundle that [readBundle] serves;
 * - `lingohub-staging/` receives a new bundle while it is extracted and validated;
 * - `lingohub-old/` briefly holds the previous bundle during the swap so a
 *   failed or interrupted install can roll back to the last known-good state.
 */
internal class FileHelper(
    baseDir: File,
    private val maxEntries: Int = MAX_ENTRIES,
    private val maxTotalUncompressedBytes: Long = MAX_TOTAL_UNCOMPRESSED_BYTES
) : IFileHelper {
    private val bundleDir = File(baseDir, BUNDLE_DIR_NAME)
    private val stagingDir = File(baseDir, "$BUNDLE_DIR_NAME-staging")
    private val backupDir = File(baseDir, "$BUNDLE_DIR_NAME-old")

    private companion object {
        // Must stay "lingohub": existing installs keep their downloaded bundle here.
        private const val BUNDLE_DIR_NAME = "lingohub"
        private const val MAX_ENTRIES = 1_000
        private const val MAX_TOTAL_UNCOMPRESSED_BYTES = 100L * 1024 * 1024
        private val json = Json { ignoreUnknownKeys = true }
    }

    override suspend fun installBundle(inputStream: InputStream): Unit = withContext(Dispatchers.IO) {
        stagingDir.deleteRecursively()
        if (!stagingDir.mkdirs()) {
            throw IOException("Failed to create staging directory: ${stagingDir.absolutePath}")
        }
        try {
            extractInto(stagingDir, inputStream)
            // Decode the staged bundle before touching the live directory, so
            // a release with malformed content can never destroy the last
            // known-good bundle.
            try {
                parseBundleDir(stagingDir)
            } catch (e: Exception) {
                throw IOException("Bundle archive rejected: content failed to parse", e)
            }
            activateStagedBundle()
        } catch (e: Exception) {
            stagingDir.deleteRecursively()
            throw e
        }
    }

    private fun extractInto(targetDir: File, inputStream: InputStream) {
        val targetPrefix = targetDir.canonicalPath + File.separator
        val seenPaths = mutableSetOf<String>()
        var entryCount = 0
        var totalBytes = 0L

        ZipInputStream(inputStream).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break

                if (++entryCount > maxEntries) {
                    throw IOException("Bundle archive rejected: more than $maxEntries entries")
                }

                val file = File(targetDir, entry.name)
                val canonicalPath = file.canonicalPath
                if (!canonicalPath.startsWith(targetPrefix)) {
                    throw IOException("Bundle archive rejected: entry escapes target directory: ${entry.name}")
                }
                if (!seenPaths.add(canonicalPath)) {
                    throw IOException("Bundle archive rejected: duplicate entry: ${entry.name}")
                }

                if (entry.isDirectory) {
                    if (!file.isDirectory && !file.mkdirs()) {
                        throw IOException("Failed to create directory: ${file.absolutePath}")
                    }
                    continue
                }

                if (!entry.name.endsWith(".json")) {
                    throw IOException("Bundle archive rejected: unexpected non-JSON entry: ${entry.name}")
                }

                val parent = file.parentFile
                if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
                    throw IOException("Failed to create directory: ${parent.absolutePath}")
                }

                file.outputStream().use { out ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = zis.read(buffer)
                        if (count == -1) break
                        totalBytes += count
                        if (totalBytes > maxTotalUncompressedBytes) {
                            throw IOException("Bundle archive rejected: uncompressed size exceeds $maxTotalUncompressedBytes bytes")
                        }
                        out.write(buffer, 0, count)
                    }
                }
            }
        }

        // A response that is not a ZIP archive (or an empty one) yields no entries.
        if (entryCount == 0) {
            throw IOException("Bundle archive rejected: no entries found")
        }
    }

    private fun activateStagedBundle() {
        backupDir.deleteRecursively()
        if (bundleDir.exists() && !bundleDir.renameTo(backupDir)) {
            throw IOException("Failed to move current bundle aside: ${bundleDir.absolutePath}")
        }
        if (!stagingDir.renameTo(bundleDir)) {
            backupDir.renameTo(bundleDir)
            throw IOException("Failed to activate new bundle: ${bundleDir.absolutePath}")
        }
        backupDir.deleteRecursively()
    }

    override suspend fun readBundle(): List<Bundle>? = withContext(Dispatchers.IO) {
        recoverInterruptedInstall()
        try {
            val grouped = parseBundleDir(bundleDir)
            grouped.forEach {
                LingoHubLogger.logger.onDebug("read '${it.iso}' bundle with ${it.items.size} items")
            }
            grouped
        } catch (e: Exception) {
            LingoHubLogger.logger.onError("Could not read LingoHub package", e)
            null
        }
    }

    private fun parseBundleDir(dir: File): List<Bundle> {
        val bundleFiles = dir.listFiles()?.filter { it.isFile }.orEmpty()
        LingoHubLogger.logger.onDebug("found files: ${bundleFiles.map { it.name }}")

        val files = bundleFiles.map {
            it.bufferedReader().use { reader -> reader.readText() }.let { jsonStr ->
                json.decodeFromString<List<Bundle>>(jsonStr)
            }
        }.flatten()

        return files.groupBy { it.iso }.map {
            val iso = it.key
            val items = it.value.flatMap { bundle -> bundle.items }
            Bundle(iso, items)
        }
    }

    override suspend fun deleteBundle(): Unit = withContext(Dispatchers.IO) {
        LingoHubLogger.logger.onDebug("deleting bundle directories")
        // The recovery directories must go too: otherwise readBundle() could
        // resurrect a purged bundle from lingohub-old after an app update.
        stagingDir.deleteRecursively()
        backupDir.deleteRecursively()
        bundleDir.deleteRecursively()
    }

    /**
     * If the process died between the two renames of [activateStagedBundle],
     * the live directory is gone but the previous bundle still sits in the
     * backup directory - restore it instead of serving nothing.
     */
    private fun recoverInterruptedInstall() {
        if (!bundleDir.exists() && backupDir.exists()) {
            LingoHubLogger.logger.onDebug("restoring bundle from interrupted install")
            backupDir.renameTo(bundleDir)
        }
    }
}
