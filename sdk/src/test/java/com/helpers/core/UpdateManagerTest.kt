package com.helpers.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateManagerTest {
    private lateinit var updateManager: UpdateManager
    private lateinit var listener1: LingohubUpdateListener
    private lateinit var listener2: LingohubUpdateListener
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        updateManager = UpdateManager()
        listener1 = mock()
        listener2 = mock()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `addLoadingStateListener should add listener only once`() = runTest(testDispatcher) {
        updateManager.addLoadingStateListener(listener1)
        updateManager.addLoadingStateListener(listener1) // Adding same listener twice

        updateManager.notifyDataChanged()
        advanceUntilIdle()

        verify(listener1).onUpdate()
        verifyNoMoreInteractions(listener1)
    }

    @Test
    fun `removeLoadingStateListener should remove listener`() = runTest(testDispatcher) {
        updateManager.addLoadingStateListener(listener1)
        updateManager.removeLoadingStateListener(listener1)

        updateManager.notifyDataChanged()
        advanceUntilIdle()

        verifyNoMoreInteractions(listener1)
    }

    @Test
    fun `notifyDataChanged should notify all listeners`() = runTest(testDispatcher) {
        updateManager.addLoadingStateListener(listener1)
        updateManager.addLoadingStateListener(listener2)

        updateManager.notifyDataChanged()
        advanceUntilIdle()

        verify(listener1).onUpdate()
        verify(listener2).onUpdate()
    }

    @Test
    fun `notifyFailure should notify all listeners with throwable`() = runTest(testDispatcher) {
        updateManager.addLoadingStateListener(listener1)
        updateManager.addLoadingStateListener(listener2)
        val throwable = RuntimeException("Test exception")

        updateManager.notifyFailure(throwable)
        advanceUntilIdle()

        verify(listener1).onFailure(throwable)
        verify(listener2).onFailure(throwable)
    }

    @Test
    fun `getInstance should return singleton instance`() {
        val instance1 = UpdateManager.getInstance()
        val instance2 = UpdateManager.getInstance()

        assert(instance1 === instance2) // Check reference equality
    }
}