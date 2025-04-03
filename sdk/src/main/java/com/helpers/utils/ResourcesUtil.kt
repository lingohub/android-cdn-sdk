package com.helpers.utils

import android.content.Context
import android.content.res.Resources
import android.icu.text.PluralRules
import androidx.core.os.ConfigurationCompat
import com.helpers.core.Lingohub
import com.helpers.core.LocaleProvider
import com.helpers.data.IRepository
import java.util.*

internal class ResourcesUtil(
    private val context: Context,
    baseResources: Resources
) : Resources(baseResources.assets, baseResources.displayMetrics, baseResources.configuration) {
    private val repository: IRepository
        get() {
            val locale = currentLocale()
            LingohubLogger.logger.onDebug("$TAG, Getting repository for locale: $locale")
            return Lingohub.getRepository(locale)
        }

    private fun getResourceKey(id: Int): String {
        val name = getResourceEntryName(id)
        val type = getResourceTypeName(id)
        val pkg = getResourcePackageName(id)

        val key = if (pkg == context.packageName) name else "${pkg}_$name"
        LingohubLogger.logger.onDebug("$TAG, Resource key for id $id: $key (name=$name, type=$type, pkg=$pkg)")
        return key
    }

    @Throws(NotFoundException::class)
    override fun getText(id: Int): CharSequence {
        val resourceKey = try {
            getResourceKey(id)
        } catch (e: NotFoundException) {
            LingohubLogger.logger.onWarn("$TAG, Resource not found for id: $id", e)
            return super.getText(id)
        }

        val text = repository.getText(resourceKey)
        LingohubLogger.logger.onDebug("$TAG, getText: key=$resourceKey, translation=$text")

        val result = text ?: super.getText(id)
        Lingohub.stringRequested(resourceKey, result.toString())
        return result
    }

    @Throws(NotFoundException::class)
    override fun getString(id: Int): String {
        LingohubLogger.logger.onDebug("$TAG, getString: Getting string for id: $id")
        return getText(id).toString()
    }

    @Throws(NotFoundException::class)
    override fun getString(id: Int, vararg formatArgs: Any): String {
        val resourceKey = try {
            getResourceKey(id)
        } catch (e: NotFoundException) {
            LingohubLogger.logger.onWarn("$TAG, Resource not found for id: $id", e)
            return super.getString(id, *formatArgs)
        }

        val string = repository.getText(resourceKey)?.toString()
        LingohubLogger.logger.onDebug(
            "$TAG, getString: key=$resourceKey, translation=$string, args=${formatArgs.joinToString()}"
        )

        val baseString = string ?: super.getString(id, *formatArgs)
        val result = String.format(currentLocale(), baseString, *formatArgs)
        Lingohub.stringRequested(resourceKey, result)
        return result
    }

    @Throws(NotFoundException::class)
    override fun getQuantityText(id: Int, quantity: Int): CharSequence {
        val resourceKey = try {
            getResourceKey(id)
        } catch (e: NotFoundException) {
            LingohubLogger.logger.onWarn("$TAG, Resource not found for id: $id", e)
            return super.getQuantityText(id, quantity)
        }

        val pluralKey = quantity.toPluralKeyword()
        val string = repository.getPlural(resourceKey, pluralKey)
        LingohubLogger.logger.onDebug("$TAG, getQuantityText: key=$resourceKey, plural=$pluralKey, translation=$string")

        val result = string ?: super.getQuantityText(id, quantity)
        Lingohub.stringRequested(resourceKey, result.toString())
        return result
    }

    @Throws(NotFoundException::class)
    override fun getQuantityString(id: Int, quantity: Int): String {
        LingohubLogger.logger.onDebug("$TAG, getQuantityString: Getting string for id: $id, quantity: $quantity")
        return getQuantityText(id, quantity).toString()
    }

    @Throws(NotFoundException::class)
    override fun getQuantityString(id: Int, quantity: Int, vararg formatArgs: Any): String {
        val baseString = getQuantityString(id, quantity)
        val result = String.format(currentLocale(), baseString, *formatArgs)
        LingohubLogger.logger.onDebug("$TAG, getQuantityString: formatted=$result, args=${formatArgs.joinToString()}")
        return result
    }

    @Throws(NotFoundException::class)
    override fun getStringArray(id: Int): Array<String> {
        LingohubLogger.logger.onDebug("$TAG, getStringArray: Getting array for id: $id")
        return getTextArray(id).map { it.toString() }.toTypedArray()
    }

    @Throws(NotFoundException::class)
    override fun getTextArray(id: Int): Array<CharSequence> {
        val resourceKey = try {
            getResourceKey(id)
        } catch (e: NotFoundException) {
            LingohubLogger.logger.onWarn("$TAG, Resource not found for id: $id", e)
            return super.getTextArray(id)
        }

        val array = repository.getTextArray(resourceKey)
        LingohubLogger.logger.onDebug("$TAG, getTextArray: key=$resourceKey, translation=${array?.joinToString()}")
        return array ?: super.getTextArray(id)
    }

    private fun Int.toPluralKeyword(): String =
        PluralRules.forLocale(currentLocale()).select(this.toDouble())

    private fun currentLocale(): Locale {
        val locale = LocaleProvider.currentLocale
        LingohubLogger.logger.onDebug("$TAG, Current locale from LocaleProvider: $locale")
        return locale
    }

    companion object {
        private const val TAG = "ResourcesUtil"
    }
}

