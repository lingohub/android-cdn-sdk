package com.lingohub.android.cdn.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * Regression tests for the double-format bug: OTA templates need exactly one
 * String.format pass, while the Resources.getString(id, *args) fallback is
 * already formatted and must be returned untouched. Formatting the fallback a
 * second time crashed on literal '%' characters (e.g. a resource using the
 * documented "%%" escape, or an argument value like "50% off").
 */
class ResourcesUtilFormatTest {

    @Test
    fun `OTA template is formatted exactly once`() {
        val result = formatTranslation(Locale.US, "Loading: %1\$d%%", arrayOf(80)) {
            error("fallback must not be used when a translation exists")
        }
        assertEquals("Loading: 80%", result)
    }

    @Test
    fun `fallback with a literal percent from the double-escape is returned untouched`() {
        // What Resources.getString(id, 80) produces for the template "Loading: %1$d%%".
        // The old code re-formatted this and threw UnknownFormatConversionException.
        val result = formatTranslation(Locale.US, null, arrayOf(80)) { "Loading: 80%" }
        assertEquals("Loading: 80%", result)
    }

    @Test
    fun `fallback with a percent-carrying argument is returned untouched`() {
        // The old code re-formatted this and threw IllegalFormatConversionException.
        val result = formatTranslation(Locale.US, null, arrayOf("50% off")) { "Deal: 50% off" }
        assertEquals("Deal: 50% off", result)
    }

    @Test
    fun `OTA template is formatted with the given locale`() {
        val result = formatTranslation(Locale.GERMANY, "Preis: %,.2f", arrayOf(1234.5)) {
            error("fallback must not be used when a translation exists")
        }
        assertEquals("Preis: 1.234,50", result)
    }
}
