package com.helpers

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import com.helpers.core.Lingohub
import com.helpers.data.IRepository
import com.helpers.data.model.Environment
import org.mockito.kotlin.mock
import org.amshove.kluent.*
import org.mockito.kotlin.doReturn

import java.util.*

fun createConfiguration(locale: Locale = Locale.ENGLISH): Configuration = mock<Configuration>().apply {
    this.locale = locale
    When calling this.locales doReturn LocaleList(locale)
}

fun configureResourceGetText(resources: Resources, id: Int, nameId: String, text: CharSequence) {
    When calling resources.getResourceEntryName(id) doReturn nameId
    When calling resources.getText(id) doReturn text
}

fun clearLingohub(context: Context) {
    Lingohub.configure(context, "", Environment.TEST)
}

fun configureLingohub(context: Context) {
    Lingohub.configure(context, "", Environment.TEST)
}

fun configureRepository(repository: IRepository, locale: Locale = Locale.ENGLISH) {
    Lingohub.addRepository(locale, repository)
}

fun createRepository(nameId: String, quantity: String? = null, text: CharSequence? = null, textArray: Array<CharSequence>? = null): IRepository {
    return object : IRepository {
        override fun getText(key: String) = text.takeIf { key == nameId }

        override fun getPlural(key: String, quantityString: String): CharSequence? {
            return text.takeIf { key == nameId && quantity == quantity }
        }

        override fun getTextArray(key: String) = textArray.takeIf { key == nameId }
    }
}