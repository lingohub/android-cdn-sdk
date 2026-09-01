package com.lingohub.android.cdn.data

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.lingohub.android.cdn.core.LingoHub
import com.lingohub.android.cdn.data.model.BundleInfo
import com.lingohub.android.cdn.utils.LingoHubLogLevel
import com.lingohub.android.cdn.utils.LingoHubLogger
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Invocation
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.*

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
internal annotation class Authenticated

internal interface Api {

    @Authenticated
    @POST("v1/distributions/check")
    suspend fun getBundleInfo(
        @Body body: PackageRequest = PackageRequest()
    ): Response<BundleInfo>

    @GET
    suspend fun downloadBundle(@Url url: String): ResponseBody

    companion object {
        fun build(): Api {
            val client by lazy {
                val loggingInterceptor =
                    HttpLoggingInterceptor { message -> LingoHubLogger.logger.onDebug(message) }

                // Only pay the body-buffering cost when logging is actually enabled.
                loggingInterceptor.setLevel(
                    if (LingoHubLogger.logLevel == LingoHubLogLevel.FULL) {
                        HttpLoggingInterceptor.Level.BODY
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                )
                OkHttpClient.Builder()
                    .addInterceptor(Interceptor { chain ->
                        val request = chain.request()
                        val newRequest = if (request.tag(Invocation::class.java)
                                ?.method()?.isAnnotationPresent(Authenticated::class.java) == true) {
                            // Only add Authorization for requests marked with @Authenticated
                            request.newBuilder()
                                .addHeader("Authorization", "Bearer ${requireNotNull(LingoHub.apiKey)}")
                                .build()
                        } else {
                            request
                        }
                        chain.proceed(newRequest)
                    })
                    .addInterceptor(loggingInterceptor)
                    .build()
            }

            val contentType = "application/json".toMediaType()

            return Retrofit.Builder()
                .client(client)
                .baseUrl("https://cdn.lingohub.com/")
                .addConverterFactory(Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                }.asConverterFactory(contentType))
                .build().create(Api::class.java)
        }
    }
}