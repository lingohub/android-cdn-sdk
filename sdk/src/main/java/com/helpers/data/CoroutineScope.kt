package com.helpers.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

interface ICoroutineScope : CoroutineScope {
    fun launch(block: suspend CoroutineScope.() -> Unit): Job
}

class LingohubScope : ICoroutineScope {
    override val coroutineContext: CoroutineContext = Dispatchers.Main
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun launch(block: suspend CoroutineScope.() -> Unit): Job {
        return scope.launch { block.invoke(this) }
    }
}