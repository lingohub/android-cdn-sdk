package com.lingohub.android.cdn.utils


import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import com.lingohub.android.cdn.core.LingoHub
import com.lingohub.android.cdn.data.IRepository
import com.lingohub.android.cdn.data.model.Environment
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

import java.util.*

fun createConfiguration(locale: Locale = Locale.ENGLISH): Configuration = mock<Configuration>().apply {
    this.locale = locale
    whenever(this.locales).thenReturn(LocaleList(locale))
}

fun configureResourceGetText(resources: Resources, id: Int, nameId: String, text: CharSequence) {
    whenever(resources.getResourceEntryName(id)).thenReturn(nameId)
    whenever(resources.getText(id)).thenReturn(text)
}

fun clearLingoHub(context: Context) {
    LingoHub.configure(context, "", Environment.TEST)
}

fun configureLingoHub(context: Context) {
    LingoHub.configure(context, "", Environment.TEST)
}

fun configureRepository(repository: IRepository, locale: Locale = Locale.ENGLISH) {
    LingoHub.addRepository(locale, repository)
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
