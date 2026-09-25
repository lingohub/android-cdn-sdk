package com.lingohub.android.cdn.core

import java.util.*

internal object LocaleProvider {

    // Null until setLocale() picks a language; until then follow the device.
    // Volatile: lookups run on whichever thread a Service or worker uses.
    @Volatile
    private var selectedLocale: Locale? = null

    var currentLocale: Locale
        get() = selectedLocale ?: Locale.getDefault()
        set(value) {
            selectedLocale = value
        }
}
