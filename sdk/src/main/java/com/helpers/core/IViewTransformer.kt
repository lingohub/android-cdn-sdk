package com.helpers.core

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.AttrRes
import androidx.annotation.StringRes

interface IViewTransformer {

    fun retext(view: View, attributeSet: AttributeSet): View

    @StringRes
    fun Context.getStringResourceId(attributeSet: AttributeSet, @AttrRes attrResId: Int): Int {
        val typedArray = obtainStyledAttributes(attributeSet, intArrayOf(attrResId))
        val stringResId = typedArray.getResourceId(0, -1)
        typedArray.recycle()
        return stringResId
    }
}