package com.lingohub.android.cdn.data

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.lingohub.android.cdn.data.model.BundleInfo
import com.lingohub.android.cdn.utils.LingoHubLogLevel
import com.lingohub.android.cdn.utils.LingoHubLogger
import kotlinx.serialization.json.Json
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.BufferedSink
import retrofit2.Converter
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.*
import java.lang.reflect.Type

/**
 * Marks a call whose request OkHttp must send at most once. OkHttp otherwise sends a request again on its
 * own: as the follow-up to a 408 or to a 503 with `Retry-After: 0`, and to recover from a connection failure
 * once sending started. The check is metered and paced by [UpdatePolicy], which decides every request.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
internal annotation class SentOnce

internal interface Api {

    /** [authorization] and [body] carry the configuration of the update cycle that sends the check. */
    @SentOnce
    @POST("v1/distributions/check")
    suspend fun getBundleInfo(
        @Header("Authorization") authorization: String,
        @Body body: PackageRequest,
    ): Response<BundleInfo>

    @GET
    suspend fun downloadBundle(@Url url: String): ResponseBody

    companion object {
        private const val CDN_BASE_URL = "https://cdn.lingohub.com/"

        fun build(baseUrl: String = CDN_BASE_URL): Api {
            val contentType = "application/json".toMediaType()

            return Retrofit.Builder()
                .client(buildHttpClient())
                .baseUrl(baseUrl)
                // Before the JSON converter, which it wraps
                .addConverterFactory(SentOnceConverterFactory)
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
                .addInterceptor(loggingInterceptor)
                // Never follow a redirect that changes scheme: the HTTPS
                // requirement on the bundle URL must hold across redirects too.
                .followSslRedirects(false)
                .build()
        }
    }
}

/**
 * Makes the request bodies of [SentOnce] calls one-shot: OkHttp never sends a one-shot body a second time,
 * neither as a follow-up nor to recover from a failure after sending started.
 */
private object SentOnceConverterFactory : Converter.Factory() {
    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<out Annotation>,
        methodAnnotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<*, RequestBody>? {
        if (methodAnnotations.none { it is SentOnce }) return null
        val delegate = retrofit.nextRequestBodyConverter<Any>(this, type, parameterAnnotations, methodAnnotations)
        return Converter<Any, RequestBody> { value -> OneShotRequestBody(requireNotNull(delegate.convert(value))) }
    }
}

private class OneShotRequestBody(private val delegate: RequestBody) : RequestBody() {
    override fun contentType(): MediaType? = delegate.contentType()
    override fun contentLength(): Long = delegate.contentLength()
    override fun isOneShot(): Boolean = true
    override fun writeTo(sink: BufferedSink) = delegate.writeTo(sink)
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
