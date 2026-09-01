package com.lingohub.android.cdn.data

import com.lingohub.android.cdn.data.model.Bundle
import com.lingohub.android.cdn.data.model.Item
import com.lingohub.android.cdn.utils.LingoHubLogLevel
import com.lingohub.android.cdn.utils.LingoHubLogger
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileHelperTest {
    @TempDir
    lateinit var tempDir: File
    private lateinit var fileHelper: FileHelper
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val bundleDir get() = File(tempDir, "lingohub")
    private val stagingDir get() = File(tempDir, "lingohub-staging")
    private val backupDir get() = File(tempDir, "lingohub-old")

    @BeforeEach
    fun setup() {
        LingoHubLogger.init(LingoHubLogLevel.NONE)
        fileHelper = FileHelper(tempDir)
    }

    @Test
    fun `test installBundle extracts files correctly`() = runTest {
        val zipBytes = createTestZip(mapOf(
            "en.json" to json.encodeToString(listOf(bundle("en", "test", "Test"))),
            "de.json" to json.encodeToString(listOf(bundle("de", "test", "Testen")))
        ))

        fileHelper.installBundle(ByteArrayInputStream(zipBytes))

        val extractedFiles = bundleDir.listFiles()?.sortedBy { it.name } ?: emptyList()
        assertEquals(2, extractedFiles.size)
        assertEquals("de.json", extractedFiles[0].name)
        assertEquals("en.json", extractedFiles[1].name)
        assertFalse(stagingDir.exists(), "staging directory should be gone after install")
        assertFalse(backupDir.exists(), "backup directory should be gone after install")
    }

    @Test
    fun `test installBundle replaces the previous bundle`() = runTest {
        fileHelper.installBundle(zipWithBundle("en", "greeting", "Hello"))
        fileHelper.installBundle(zipWithBundle("en", "greeting", "Hi there"))

        val result = fileHelper.readBundle()
        assertEquals("Hi there", result?.single()?.items?.single()?.value)
    }

    @Test
    fun `installBundle rejects entries escaping the target directory`() = runTest {
        val zipBytes = createTestZip(mapOf("../evil.json" to "[]"))

        assertInstallFails(zipBytes)
        assertFalse(File(tempDir, "evil.json").exists(), "traversal entry must not be written outside the bundle directory")
    }

    @Test
    fun `installBundle rejects non-JSON entries`() = runTest {
        val zipBytes = createTestZip(mapOf(
            "en.json" to "[]",
            "payload.dex" to "junk"
        ))

        assertInstallFails(zipBytes)
    }

    @Test
    fun `installBundle rejects entries resolving to the same file`() = runTest {
        // ZipOutputStream refuses literal duplicates, but distinct entry names
        // can still collapse onto the same canonical path.
        val zipBytes = createTestZip(linkedMapOf(
            "en.json" to "[]",
            "./en.json" to "[]"
        ))

        assertInstallFails(zipBytes)
    }

    @Test
    fun `installBundle rejects archives with too many entries`() = runTest {
        fileHelper = FileHelper(tempDir, maxEntries = 3)
        val zipBytes = createTestZip((1..4).associate { "file$it.json" to "[]" })

        assertInstallFails(zipBytes)
    }

    @Test
    fun `installBundle rejects archives exceeding the uncompressed size limit`() = runTest {
        fileHelper = FileHelper(tempDir, maxTotalUncompressedBytes = 64)
        val zipBytes = createTestZip(mapOf("en.json" to "x".repeat(1024)))

        assertInstallFails(zipBytes)
    }

    @Test
    fun `installBundle rejects content that is not a ZIP archive`() = runTest {
        assertInstallFails("<html>gateway error</html>".toByteArray())
    }

    @Test
    fun `failed install preserves the previous bundle`() = runTest {
        fileHelper.installBundle(zipWithBundle("en", "greeting", "Hello"))

        // Fails after the first entry was already extracted into staging.
        val badZip = createTestZip(mapOf(
            "en.json" to json.encodeToString(listOf(bundle("en", "greeting", "REPLACED"))),
            "payload.bin" to "junk"
        ))
        assertInstallFails(badZip)

        val result = fileHelper.readBundle()
        assertEquals("Hello", result?.single()?.items?.single()?.value, "last known-good bundle must survive a failed install")
        assertFalse(stagingDir.exists(), "staging leftovers must be cleaned up")
    }

    @Test
    fun `readBundle recovers a bundle from an interrupted install`() = runTest {
        fileHelper.installBundle(zipWithBundle("en", "greeting", "Hello"))

        // Simulate a process death between the two renames of the swap:
        // the live directory is gone, the old bundle still sits in backup.
        assertTrue(bundleDir.renameTo(backupDir))

        val result = fileHelper.readBundle()
        assertEquals("Hello", result?.single()?.items?.single()?.value)
        assertTrue(bundleDir.exists(), "recovered bundle should be live again")
        assertFalse(backupDir.exists())
    }

    @Test
    fun `test readBundle reads and groups bundles correctly`() = runTest {
        bundleDir.mkdirs()
        File(bundleDir, "en1.json").writeText(json.encodeToString(listOf(bundle("en", "key1", "Value 1"))))
        File(bundleDir, "en2.json").writeText(json.encodeToString(listOf(bundle("en", "key2", "Value 2"))))
        File(bundleDir, "de.json").writeText(json.encodeToString(listOf(bundle("de", "key1", "Wert 1"))))

        val result = fileHelper.readBundle()
        assertNotNull(result, "readBundle should not return null")

        val bundles = result?.groupBy { it.iso }
        val enBundles = bundles?.get("en")
        val deBundles = bundles?.get("de")

        assertNotNull(enBundles, "English bundles should exist")
        assertEquals(1, enBundles?.size, "Should have one merged English bundle")
        assertEquals(2, enBundles?.first()?.items?.size, "English bundle should have 2 items")

        assertNotNull(deBundles, "German bundles should exist")
        assertEquals(1, deBundles?.size, "Should have one German bundle")
        assertEquals(1, deBundles?.first()?.items?.size, "German bundle should have 1 item")

        assertEquals(listOf("key1", "key2"), enBundles?.first()?.items?.map { it.key }?.sorted())
        assertEquals(listOf("key1"), deBundles?.first()?.items?.map { it.key })
    }

    @Test
    fun `test readBundle returns null on invalid JSON`() = runTest {
        bundleDir.mkdirs()
        File(bundleDir, "invalid.json").writeText("invalid json")
        val result = fileHelper.readBundle()
        assertNull(result, "readBundle should return null for invalid JSON")
    }

    @Test
    fun `test readBundle returns empty list when no bundle exists`() = runTest {
        val result = fileHelper.readBundle()
        assertEquals(emptyList<Bundle>(), result)
    }

    @Test
    fun `test deleteBundle removes all files`() = runTest {
        bundleDir.mkdirs()
        File(bundleDir, "test1.json").writeText("test1")
        File(bundleDir, "subdir").mkdir()
        File(bundleDir, "subdir/test3.json").writeText("test3")

        fileHelper.deleteBundle()

        assertFalse(bundleDir.exists(), "bundle directory should be deleted")
    }

    private suspend fun assertInstallFails(zipBytes: ByteArray) {
        try {
            fileHelper.installBundle(ByteArrayInputStream(zipBytes))
            fail<Unit>("expected installBundle to fail")
        } catch (expected: IOException) {
            // expected
        }
    }

    private fun bundle(iso: String, key: String, value: String) =
        Bundle(iso = iso, items = listOf(Item(key = key, type = "TEXT", value = value)))

    private fun zipWithBundle(iso: String, key: String, value: String): ByteArrayInputStream =
        ByteArrayInputStream(createTestZip(mapOf("$iso.json" to json.encodeToString(listOf(bundle(iso, key, value))))))

    private fun createTestZip(files: Map<String, String>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            files.forEach { (name, content) ->
                val entry = ZipEntry(name)
                zos.putNextEntry(entry)
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }
}
