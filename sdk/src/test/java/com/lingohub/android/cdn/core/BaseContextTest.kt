package com.lingohub.android.cdn.core

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.CallSuper
import com.lingohub.android.cdn.utils.createConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
abstract class BaseContextTest {
    val baseContext: Context = mock()
    val packageManger: PackageManager = mock()
    val packageInfo: PackageInfo = mock()
    val baseResources: Resources = mock()
    private var configuration: Configuration = createConfiguration()
    private val sharedPreferences: SharedPreferences = mock()

    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    @CallSuper
    open fun setup() {
        Dispatchers.setMain(testDispatcher)

        whenever(baseContext.packageManager).thenReturn(packageManger)
        whenever(baseContext.packageName).thenReturn("")
        whenever(baseContext.resources).thenReturn(baseResources)
        whenever(baseResources.configuration).thenReturn(configuration)
        whenever(baseContext.getSharedPreferences(any(), any())).thenReturn(sharedPreferences)
        whenever(packageManger.getPackageInfo("", 0)).thenReturn(packageInfo)
        whenever(packageInfo.longVersionCode).thenReturn(0L)
        packageInfo.versionName = ""
    }

    @AfterEach
    @CallSuper
    open fun tearDown() {
        Dispatchers.resetMain()
    }
}
