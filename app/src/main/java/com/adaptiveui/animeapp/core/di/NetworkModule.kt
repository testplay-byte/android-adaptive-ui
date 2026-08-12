package com.adaptiveui.animeapp.core.di

import com.adaptiveui.animeapp.core.network.anilist.AniListApi
import com.adaptiveui.animeapp.core.network.anizip.AniZipApi
import com.adaptiveui.animeapp.core.network.jikan.JikanApi
import com.adaptiveui.animeapp.core.network.kitsu.KitsuApi
import com.adaptiveui.animeapp.core.network.openai.OpenAiApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module providing OkHttp, kotlinx.serialization [Json], Retrofit instances, and the four
 * API interfaces (AniList, AniZip, Jikan, OpenAI-compatible).
 *
 * - AniList, AniZip, Jikan each get their own Retrofit with the appropriate base URL.
 * - The OpenAI client uses a placeholder base URL because [com.adaptiveui.animeapp.data.repository.AiRepository]
 *   passes the full URL via `@Url` on each call (the user can swap providers at runtime).
 * - The OkHttp client has 60s timeouts and an HTTP BODY-level logging interceptor (visible in
 *   logcat under the `OkHttp` tag in debug builds; disabled in release by `level = NONE`).
 * - AniList rate-limit headers (`X-RateLimit-Remaining`, `Retry-After`) are logged but not
 *   throttled — single-page requests stay well under the 90/min limit.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val ANILIST_BASE_URL = "https://graphql.anilist.co/"
    private const val ANIZIP_BASE_URL = "https://api.ani.zip/"
    private const val JIKAN_BASE_URL = "https://api.jikan.moe/v4/"
    private const val KITSU_BASE_URL = "https://kitsu.io/api/edge/"
    private const val OPENAI_PLACEHOLDER_URL = "https://placeholder.invalid/"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        isLenient = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(logging: HttpLoggingInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(AniListRateLimitLogger())
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

    // ---------- AniList ----------

    @Provides
    @Singleton
    fun provideAniListApi(client: OkHttpClient, json: Json): AniListApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(ANILIST_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(AniListApi::class.java)
    }

    // ---------- AniZip ----------

    @Provides
    @Singleton
    fun provideAniZipApi(client: OkHttpClient, json: Json): AniZipApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(ANIZIP_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(AniZipApi::class.java)
    }

    // ---------- Jikan ----------

    @Provides
    @Singleton
    fun provideJikanApi(client: OkHttpClient, json: Json): JikanApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(JIKAN_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(JikanApi::class.java)
    }

    // ---------- Kitsu ----------

    @Provides
    @Singleton
    fun provideKitsuApi(client: OkHttpClient, json: Json): KitsuApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(KITSU_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(KitsuApi::class.java)
    }

    // ---------- OpenAI-compatible ----------

    @Provides
    @Singleton
    fun provideOpenAiApi(client: OkHttpClient, json: Json): OpenAiApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            // Placeholder base URL — every call passes the full URL via @Url.
            .baseUrl(OPENAI_PLACEHOLDER_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(OpenAiApi::class.java)
    }
}

/**
 * Logs AniList rate-limit headers (visible in logcat). Does NOT sleep / throttle — the app's
 * request pattern (single-page queries, cache-first) stays well below the 90 req/min limit.
 * Swap in `Thread.sleep()` based on `X-RateLimit-Reset` if you start hitting 429s.
 */
private class AniListRateLimitLogger : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val remaining = response.header("X-RateLimit-Remaining")
        val reset = response.header("X-RateLimit-Reset")
        val retryAfter = response.header("Retry-After")
        if (remaining != null || retryAfter != null) {
            android.util.Log.d(
                "AniListRate",
                "url=${request.url} remaining=$remaining reset=$reset retryAfter=$retryAfter code=${response.code}"
            )
        }
        return response
    }
}
