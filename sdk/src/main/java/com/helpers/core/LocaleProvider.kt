package com.helpers.core

import java.util.*

internal object LocaleProvider {

    var isInitial = true

    var currentLocale: Locale = Locale.getDefault()
        get() {
            if (isInitial) {
                return Locale.getDefault()
            }
            return field
        }
        set(value) {
            field = value
            isInitial = false
        }
}