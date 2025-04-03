package com.helpers.core

import android.R
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes

internal object TextViewTransformer : IViewTransformer {

    override fun retext(view: View, attributeSet: AttributeSet): View {
        (view as? TextView)?.apply {
            @StringRes val textResId = context.getStringResourceId(attributeSet, R.attr.text)
            @StringRes val hintResId = context.getStringResourceId(attributeSet, R.attr.hint)

            setText(textResId)
            setHint(hintResId)
        }

        return view
    }
}