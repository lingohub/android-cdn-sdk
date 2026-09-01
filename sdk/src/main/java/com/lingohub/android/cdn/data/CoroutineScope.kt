package com.lingohub.android.cdn.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

internal interface ICoroutineScope : CoroutineScope {
    fun launch(block: suspend CoroutineScope.() -> Unit): Job
}

internal class LingoHubScope : ICoroutineScope {
    override val coroutineContext: CoroutineContext = Dispatchers.Main
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun launch(block: suspend CoroutineScope.() -> Unit): Job {
        return scope.launch { block.invoke(this) }
    }
}