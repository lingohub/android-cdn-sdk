package com.lingohub.android.cdn.core

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

            // A view whose text/hint is a hard-coded literal (or absent) has no
            // resource id here; calling setText/setHint with an invalid id throws
            // Resources.NotFoundException.
            if (textResId != NO_RESOURCE_ID && textResId != 0) setText(textResId)
            if (hintResId != NO_RESOURCE_ID && hintResId != 0) setHint(hintResId)
        }

        return view
    }

    private const val NO_RESOURCE_ID = -1
}