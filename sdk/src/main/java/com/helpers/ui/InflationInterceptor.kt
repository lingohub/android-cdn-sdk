package com.helpers.ui

import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import com.helpers.core.TextViewTransformer
import dev.b3nedikt.viewpump.InflateResult
import dev.b3nedikt.viewpump.Interceptor

object InflationInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): InflateResult {
        return chain.proceed(chain.request()).let { inflateResult ->
            inflateResult.copy(view = retextView(inflateResult.view, inflateResult.attrs))
        }
    }

    private fun retextView(view: View?, attributeSet: AttributeSet?): View? {
        if (view == null || attributeSet == null) return view
        return when (view) {
            is TextView -> TextViewTransformer.retext(view, attributeSet)
            else -> view
        }
    }
}