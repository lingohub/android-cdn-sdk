package com.lingohub.android.cdn.utils

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.icu.text.PluralRules
import android.util.DisplayMetrics
import com.lingohub.android.cdn.core.LingoHub
import com.lingohub.android.cdn.core.LocaleProvider
import com.lingohub.android.cdn.data.IRepository
import java.util.*

internal class ResourcesUtil(
    private val context: Context,
    // The resources this is a copy of. Android keeps them current, so the
    // strings packaged in the app are read through them.
    private val base: Resources,
    assets: AssetManager,
    metrics: DisplayMetrics,
    configuration: Configuration
) : Resources(assets, metrics, configuration) {
    private val repository: IRepository
        get() {
            val locale = currentLocale()
            LingoHubLogger.debug { "$TAG, Getting repository for locale: $locale" }
            return LingoHub.getRepository(locale)
        }

    private fun getResourceKey(id: Int): String {
        val name = getResourceEntryName(id)
        val pkg = getResourcePackageName(id)

        val key = if (pkg == context.packageName) name else "${pkg}_$name"
        // The type name is only looked up for the log message.
        LingoHubLogger.debug {
            "$TAG, Resource key for id $id: $key (name=$name, type=${getResourceTypeName(id)}, pkg=$pkg)"
        }
        return key
    }

    @Throws(NotFoundException::class)
    override fun getText(id: Int): CharSequence {
        val resourceKey = try {
            getResourceKey(id)
        } catch (e: NotFoundException) {
            LingoHubLogger.warn(e) { "$TAG, Resource not found for id: $id" }
            return base.getText(id)
        }

        val text = repository.getText(resourceKey)
        LingoHubLogger.debug { "$TAG, getText: key=$resourceKey, translation=$text" }

        val result = text ?: base.getText(id)
        LingoHub.stringRequested(resourceKey, result.toString())
        return result
    }

    // Like Android's, returns def for id 0 or a missing resource instead of throwing.
    override fun getText(id: Int, def: CharSequence?): CharSequence? {
        val resourceKey = try {
            getResourceKey(id)
        } catch (e: NotFoundException) {
            return base.getText(id, def)
        }

        val text = repository.getText(resourceKey)
        LingoHubLogger.debug { "$TAG, getText: key=$resourceKey, translation=$text" }

        val result = text ?: base.getText(id, def)
        result?.let { LingoHub.stringRequested(resourceKey, it.toString()) }
        return result
    }

    @Throws(NotFoundException::class)
    override fun getString(id: Int): String {
        LingoHubLogger.debug { "$TAG, getString: Getting string for id: $id" }
        return getText(id).toString()
    }

    @Throws(NotFoundException::class)
    override fun getString(id: Int, vararg formatArgs: Any): String {
        val resourceKey = try {
            getResourceKey(id)
        } catch (e: NotFoundException) {
            LingoHubLogger.warn(e) { "$TAG, Resource not found for id: $id" }
            return base.getString(id, *formatArgs)
        }

        val template = repository.getText(resourceKey)?.toString()
        LingoHubLogger.debug {
            "$TAG, getString: key=$resourceKey, translation=$template, args=${formatArgs.joinToString()}"
        }

        val result = formatTranslation(currentLocale(), template, formatArgs) {
            base.getString(id, *formatArgs)
        }
        LingoHub.stringRequested(resourceKey, result)
        return result
    }

    @Throws(NotFoundException::class)
    override fun getQuantityText(id: Int, quantity: Int): CharSequence {
        val resourceKey = try {
            getResourceKey(id)
        } catch (e: NotFoundException) {
            LingoHubLogger.warn(e) { "$TAG, Resource not found for id: $id" }
            return base.getQuantityText(id, quantity)
        }

        val pluralKey = quantity.toPluralKeyword()
        val string = repository.getPlural(resourceKey, pluralKey)
        LingoHubLogger.debug { "$TAG, getQuantityText: key=$resourceKey, plural=$pluralKey, translation=$string" }

        val result = string ?: base.getQuantityText(id, quantity)
        LingoHub.stringRequested(resourceKey, result.toString())
        return result
    }

    @Throws(NotFoundException::class)
    override fun getQuantityString(id: Int, quantity: Int): String {
        LingoHubLogger.debug { "$TAG, getQuantityString: Getting string for id: $id, quantity: $quantity" }
        return getQuantityText(id, quantity).toString()
    }

    @Throws(NotFoundException::class)
    override fun getQuantityString(id: Int, quantity: Int, vararg formatArgs: Any): String {
        val baseString = getQuantityString(id, quantity)
        val result = String.format(currentLocale(), baseString, *formatArgs)
        LingoHubLogger.debug { "$TAG, getQuantityString: formatted=$result, args=${formatArgs.joinToString()}" }
        return result
    }

    @Throws(NotFoundException::class)
    override fun getStringArray(id: Int): Array<String> {
        LingoHubLogger.debug { "$TAG, getStringArray: Getting array for id: $id" }
        return getTextArray(id).map { it.toString() }.toTypedArray()
    }

    @Throws(NotFoundException::class)
    override fun getTextArray(id: Int): Array<CharSequence> {
        val resourceKey = try {
            getResourceKey(id)
        } catch (e: NotFoundException) {
            LingoHubLogger.warn(e) { "$TAG, Resource not found for id: $id" }
            return base.getTextArray(id)
        }

        val array = repository.getTextArray(resourceKey)
        LingoHubLogger.debug { "$TAG, getTextArray: key=$resourceKey, translation=${array?.joinToString()}" }
        return array ?: base.getTextArray(id)
    }

    private fun Int.toPluralKeyword(): String =
        PluralRules.forLocale(currentLocale()).select(this.toDouble())

    private fun currentLocale(): Locale {
        val locale = LocaleProvider.currentLocale
        LingoHubLogger.debug { "$TAG, Current locale from LocaleProvider: $locale" }
        return locale
    }

    companion object {
        private const val TAG = "ResourcesUtil"
    }
}

/**
 * OTA translations are raw format templates and need exactly one
 * String.format pass. Android's Resources.getString(id, *args) fallback is
 * already formatted, so it must be returned untouched: formatting it a second
 * time corrupts the output and crashes on literal '%' characters (e.g. a
 * resource using the documented "%%" escape, or an argument like "50% off").
 */
internal inline fun formatTranslation(
    locale: Locale,
    template: String?,
    args: Array<out Any>,
    alreadyFormattedFallback: () -> String
): String {
    return if (template != null) {
        String.format(locale, template, *args)
    } else {
        alreadyFormattedFallback()
    }
}

