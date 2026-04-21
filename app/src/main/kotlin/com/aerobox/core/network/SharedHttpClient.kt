package com.aerobox.core.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Single shared [OkHttpClient] instance for the entire app.
 *
 * Callers that need custom timeouts should use [base]`.newBuilder()` so they
 * reuse the same connection pool and dispatcher instead of creating isolated
 * instances.
 */
object SharedHttpClient {

    val base: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
