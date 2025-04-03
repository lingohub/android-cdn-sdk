package com.helpers.data

import com.helpers.data.model.Bundle
import com.helpers.data.model.Item
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileHelperTest {
    @TempDir
    lateinit var tempDir: File
    private lateinit var fileHelper: FileHelper
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @BeforeEach
    fun setup() {
        fileHelper = FileHelper(tempDir)
    }

    @Test
    fun `test unzipBundle extracts files correctly`() = runTest {
        // Create test bundles
        val enBundle = Bundle(
            iso = "en",
            items = listOf(Item(key = "test", type = "TEXT", value = "Test"))
        )
        val deBundle = Bundle(
            iso = "de",
            items = listOf(Item(key = "test", type = "TEXT", value = "Testen"))
        )

        // Create ZIP file with bundles
        val zipBytes = createTestZip(mapOf(
            "en.json" to json.encodeToString(listOf(enBundle)),
            "de.json" to json.encodeToString(listOf(deBundle))
        ))

        // Test unzipping
        fileHelper.unzipBundle(ByteArrayInputStream(zipBytes))

        // Verify files were extracted correctly
        val extractedFiles = tempDir.listFiles()?.sortedBy { it.name } ?: emptyList()
        assertEquals(2, extractedFiles.size)
        assertEquals("de.json", extractedFiles[0].name)
        assertEquals("en.json", extractedFiles[1].name)
    }

    @Test
    fun `test readBundle reads and groups bundles correctly`() = runTest {
        // Create test files
        val enBundle1 = Bundle(
            iso = "en",
            items = listOf(Item(key = "key1", type = "TEXT", value = "Value 1"))
        )
        val enBundle2 = Bundle(
            iso = "en",
            items = listOf(Item(key = "key2", type = "TEXT", value = "Value 2"))
        )
        val deBundle1 = Bundle(
            iso = "de",
            items = listOf(Item(key = "key1", type = "TEXT", value = "Wert 1"))
        )

        File(tempDir, "en1.json").writeText(json.encodeToString(listOf(enBundle1)))
        File(tempDir, "en2.json").writeText(json.encodeToString(listOf(enBundle2)))
        File(tempDir, "de.json").writeText(json.encodeToString(listOf(deBundle1)))

        // Read bundles
        val result = fileHelper.readBundle()
        assertNotNull(result, "readBundle should not return null")

        // Find bundles by language
        val bundles = result?.groupBy { it.iso }
        val enBundles = bundles?.get("en")
        val deBundles = bundles?.get("de")

        // Verify English bundles
        assertNotNull(enBundles, "English bundles should exist")
        assertEquals(1, enBundles?.size, "Should have one merged English bundle")
        assertEquals(2, enBundles?.first()?.items?.size, "English bundle should have 2 items")

        // Verify German bundles
        assertNotNull(deBundles, "German bundles should exist")
        assertEquals(1, deBundles?.size, "Should have one German bundle")
        assertEquals(1, deBundles?.first()?.items?.size, "German bundle should have 1 item")

        // Verify item content
        val enItems = enBundles?.first()?.items
        val deItems = deBundles?.first()?.items
        assertEquals(listOf("key1", "key2"), enItems?.map { it.key }?.sorted())
        assertEquals(listOf("key1"), deItems?.map { it.key })
    }

    @Test
    fun `test readBundle returns null on invalid JSON`() = runTest {
        File(tempDir, "invalid.json").writeText("invalid json")
        val result = fileHelper.readBundle()
        assertNull(result, "readBundle should return null for invalid JSON")
    }

    @Test
    fun `test deleteBundle removes all files`() = runTest {
        // Create test files
        File(tempDir, "test1.json").writeText("test1")
        File(tempDir, "test2.json").writeText("test2")
        File(tempDir, "subdir").mkdir()
        File(tempDir, "subdir/test3.json").writeText("test3")

        fileHelper.deleteBundle()

        val remainingFiles = tempDir.listFiles()
        assertNotNull(remainingFiles, "listFiles should not return null")
        assertEquals(0, remainingFiles?.size, "All files should be deleted")
    }

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