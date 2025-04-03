package com.helpers.data

import com.helpers.data.model.BundleInfo
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import retrofit2.Response

class ApiTest {
    private lateinit var mockApi: Api
    
    @BeforeEach
    fun setup() {
        mockApi = mock()
    }

    @Test
    fun `test getBundleInfo success`() = runTest {
        val testBundleInfo = BundleInfo(
            id = "123",
            name = "Test Bundle",
            filesUrl = "https://test.com/files",
            createdAt = "2024-03-25T10:00:00Z"
        )
        val mockResponse = Response.success(testBundleInfo)
        
        whenever(mockApi.getBundleInfo(any())).thenReturn(mockResponse)

        val result = mockApi.getBundleInfo(PackageRequest())
        
        assert(result.isSuccessful)
        assert(result.body() == testBundleInfo)
    }

    @Test
    fun `test getBundleInfo failure`() = runTest {
        val errorBody = mock<ResponseBody>()
        val errorResponse = Response.error<BundleInfo>(404, errorBody)
        
        whenever(mockApi.getBundleInfo(any())).thenReturn(errorResponse)

        val result = mockApi.getBundleInfo(PackageRequest())
        
        assert(!result.isSuccessful)
        assert(result.code() == 404)
    }

    @Test
    fun `test downloadBundle success`() = runTest {
        val mockResponseBody = mock<ResponseBody>()
        
        whenever(mockApi.downloadBundle(any())).thenReturn(mockResponseBody)

        val result = mockApi.downloadBundle("https://test.com/bundle")
        
        assert(result == mockResponseBody)
    }
}