package com.example.stocksignal.data.stooq.di

import com.example.stocksignal.data.stooq.network.StooqApi
import com.example.stocksignal.data.stooq.network.StooqBlockInterceptor
import com.example.stocksignal.data.stooq.repository.StooqRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt dependency injection module for Stooq data layer.
 * Provides instances of API, Repository, and network components.
 */
@Module
@InstallIn(SingletonComponent::class)
object StooqModule {

    /**
     * Singleton interceptor that ensures realistic browser headers on all requests.
     */
    @Provides
    @Singleton
    fun provideHeaderInterceptor(): Interceptor {
        return Interceptor { chain ->
            val originalRequest = chain.request()
            val requestWithHeaders = originalRequest.newBuilder()
                .header("User-Agent", StooqApi.DEFAULT_USER_AGENT)
                .header("Accept", StooqApi.DEFAULT_ACCEPT)
                .header("Accept-Language", StooqApi.DEFAULT_ACCEPT_LANGUAGE)
                .header("Connection", "keep-alive")
                .header("Sec-CH-UA", StooqApi.DEFAULT_SEC_CH_UA)
                .header("Sec-CH-UA-Mobile", StooqApi.DEFAULT_SEC_CH_UA_MOBILE)
                .header("Sec-CH-UA-Platform", StooqApi.DEFAULT_SEC_CH_UA_PLATFORM)
                .header("Sec-Fetch-Dest", StooqApi.DEFAULT_SEC_FETCH_DEST)
                .header("Sec-Fetch-Mode", StooqApi.DEFAULT_SEC_FETCH_MODE)
                .header("Sec-Fetch-Site", StooqApi.DEFAULT_SEC_FETCH_SITE)
                .header("Sec-Fetch-User", StooqApi.DEFAULT_SEC_FETCH_USER)
                .header("Upgrade-Insecure-Requests", "1")
                .build()
            chain.proceed(requestWithHeaders)
        }
    }

    /**
     * Singleton HTTP logging interceptor for debugging network calls.
     */
    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
    }

    /**
     * Singleton OkHttpClient with timeouts and logging.
     *
     * IMPORTANT: `retryOnConnectionFailure` is disabled intentionally. OkHttp's default
     * behaviour silently retries connection-level failures across every DNS-resolved
     * route (IP address) for the host. When Stooq is degraded, `stooq.com` typically
     * resolves to multiple A records, so a single app-level call would fan out to 2-3
     * HTTP attempts — each one consuming the full 120 s connect timeout, each one
     * re-entering [StooqBlockInterceptor] and incrementing the consecutive-timeout
     * counter, and each one ignoring the app's carefully controlled 3-5 s pacing.
     * That amplification pushed us past the 5-timeout block threshold in a single
     * user-visible request and also kept [BackgroundStooqExecutionGate] held for
     * 6-14 minutes while one ticker burned through every route. The app has its own
     * retry layers that operate with correct pacing, circuit breakers and terminal-
     * failure detection (see [NotificationWindowRunner.fetchLiveSeriesWithRetries]
     * and [StooqRepository.BatchErrorTracker]); OkHttp-level retries are redundant
     * and harmful. See `docs/CURRENT_DESIGN.md` §14.9.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        stooqBlockInterceptor: StooqBlockInterceptor,
        headerInterceptor: Interceptor,
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .addInterceptor(stooqBlockInterceptor)
            .addInterceptor(headerInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    /**
     * Singleton Retrofit instance configured for Stooq API.
     */
    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(StooqApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
    }

    /**
     * Singleton StooqApi interface implementation.
     */
    @Provides
    @Singleton
    fun provideStooqApi(retrofit: Retrofit): StooqApi {
        return retrofit.create(StooqApi::class.java)
    }

    /**
     * Singleton StooqRepository.
     */
    @Provides
    @Singleton
    fun provideStooqRepository(api: StooqApi): StooqRepository {
        return StooqRepository(api)
    }
}
