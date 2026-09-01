package com.lingohub.android.cdn.data

import com.lingohub.android.cdn.data.model.Bundle
import com.lingohub.android.cdn.utils.LingoHubLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream


internal interface IFileHelper {
    suspend fun unzipBundle(inputStream: InputStream)
    suspend fun readBundle(): List<Bundle>?
    suspend fun deleteBundle()
}

internal class FileHelper(private val outputDirectory: File) : IFileHelper {
    private companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }

    override suspend fun unzipBundle(inputStream: InputStream) = withContext(Dispatchers.IO) {
        val zis = ZipInputStream(inputStream)
        zis.use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val file = File(outputDirectory, entry.name)
                val dir: File = if (entry.isDirectory) file else file.parentFile
                if (!dir.isDirectory && !dir.mkdirs()) throw FileNotFoundException("Failed to ensure directory: " + dir.absolutePath)
                if (entry.isDirectory) continue

                FileOutputStream(file).use { fout ->
                    val buffer = ByteArray(8192)
                    var count: Int
                    while (zis.read(buffer).also { count = it } != -1) {
                        fout.write(buffer, 0, count)
                    }
                }
            }
        }
    }

    override suspend fun readBundle(): List<Bundle>? = withContext(Dispatchers.IO) {
        try {
            outputDirectory.listFiles()?.map { it.name }?.let {
                LingoHubLogger.logger.onDebug("found files: $it")
            }

            val files = outputDirectory.listFiles()?.map {
                it.bufferedReader().use { it.readText() }.let { jsonStr ->
                    json.decodeFromString<List<Bundle>>(jsonStr)
                }
            }?.flatten() ?: emptyList()

            val grouped = files.groupBy { it.iso }.map {
                val iso = it.key
                val items = it.value.flatMap { it.items }
                Bundle(iso, items)
            }

            grouped.forEach {
                LingoHubLogger.logger.onDebug("read '${it.iso}' bundle with ${it.items.size} items")
                LingoHubLogger.logger.onDebug(it.items.toString())
            }
            grouped
        } catch (e: Exception) {
            LingoHubLogger.logger.onError("Could not read LingoHub package", e)
            null
        }
    }

    override suspend fun deleteBundle() {
        outputDirectory.listFiles()?.iterator()?.forEach {
            LingoHubLogger.logger.onDebug("deleting: ${it.name}")
            it.deleteRecursively()
        }
    }
}