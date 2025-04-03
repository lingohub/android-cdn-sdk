package com.helpers.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.helpers.core.Lingohub
import com.helpers.data.model.BundleInfo
import com.helpers.utils.LingohubLogger
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
                    HttpLoggingInterceptor { message -> LingohubLogger.logger.onDebug(message) }

                loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
                OkHttpClient.Builder()
                    .addInterceptor(Interceptor { chain ->
                        val request = chain.request()
                        val newRequest = if (request.tag(Invocation::class.java)
                                ?.method()?.isAnnotationPresent(Authenticated::class.java) == true) {
                            // Only add Authorization for requests marked with @Authenticated
                            request.newBuilder()
                                .addHeader("Authorization", "Bearer ${requireNotNull(Lingohub.apiKey)}")
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