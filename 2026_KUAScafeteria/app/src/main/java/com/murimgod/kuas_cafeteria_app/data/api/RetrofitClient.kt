package com.murimgod.kuas_cafeteria_app.data.api

import android.content.Context
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val BASE_URL = "https://kuas-cafeteria-api.murimgod.ru/v1/"

    // Serve identical GETs from the on-disk cache for this long before hitting
    // the server again — stops the app from hammering the API on every screen.
    private const val CACHE_MAX_AGE_SECONDS = 5 * 60
    private const val CACHE_SIZE_BYTES = 5L * 1024 * 1024  // 5 MiB

    private var cache: Cache? = null

    /** Call once from App.onCreate() to enable the HTTP disk cache. */
    fun init(context: Context) {
        if (cache == null) {
            cache = Cache(context.cacheDir.resolve("http_cache"), CACHE_SIZE_BYTES)
        }
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    // Force a 5-min client cache window regardless of what the server sends, so
    // repeated requests within the window are answered from disk (no network).
    private val cacheControlInterceptor = okhttp3.Interceptor { chain ->
        val response = chain.proceed(chain.request())
        response.newBuilder()
            .header(
                "Cache-Control",
                CacheControl.Builder()
                    .maxAge(CACHE_MAX_AGE_SECONDS, TimeUnit.SECONDS)
                    .build()
                    .toString(),
            )
            .removeHeader("Pragma")  // legacy header can defeat caching
            .build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .apply { cache?.let { cache(it) } }
            .addNetworkInterceptor(cacheControlInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
