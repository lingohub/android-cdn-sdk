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
import org.amshove.kluent.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

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
        
        When calling baseContext.packageManager doReturn packageManger
        When calling baseContext.packageName doReturn ""
        When calling baseContext.resources doReturn baseResources
        When calling baseResources.configuration doReturn configuration
        When calling baseContext.getSharedPreferences(any(), any()) doReturn sharedPreferences
        When calling packageManger.getPackageInfo("", 0) doReturn packageInfo
        When calling packageInfo.longVersionCode doReturn 0L
        packageInfo.versionName = ""
    }
    
    @AfterEach
    @CallSuper
    open fun tearDown() {
        Dispatchers.resetMain()
    }
}