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
            val contentType = "application/json".toMediaType()

            return Retrofit.Builder()
                .client(buildHttpClient())
                .baseUrl("https://cdn.lingohub.com/")
                .addConverterFactory(Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                }.asConverterFactory(contentType))
                .build().create(Api::class.java)
        }

        internal fun buildHttpClient(): OkHttpClient {
            val loggingInterceptor =
                HttpLoggingInterceptor { message -> LingoHubLogger.logger.onDebug(sanitizeLogLine(message)) }

            // Only pay the body-buffering cost when logging is actually enabled.
            loggingInterceptor.setLevel(
                if (LingoHubLogger.logLevel == LingoHubLogLevel.FULL) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            )
            // The bearer key must never end up in logcat, even at FULL.
            loggingInterceptor.redactHeader("Authorization")
            return OkHttpClient.Builder()
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
                // Never follow a redirect that changes scheme: the HTTPS
                // requirement on the bundle URL must hold across redirects too.
                .followSslRedirects(false)
                .build()
        }
    }
}

private val urlQueryRegex = Regex("(https?://[^\\s\"'?]+)\\?[^\\s\"']*")

/**
 * Strips query strings from URLs in HTTP log lines. Presigned bundle URLs
 * carry their credentials as query parameters and appear both in the check
 * response body and in the download request line, so header redaction alone
 * is not enough.
 */
internal fun sanitizeLogLine(message: String): String =
    urlQueryRegex.replace(message) { "${it.groupValues[1]}?<redacted>" }