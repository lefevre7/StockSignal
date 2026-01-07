package com.example.stocksignal.data.stooq.di

import com.example.stocksignal.data.stooq.network.StooqApi
import com.example.stocksignal.data.stooq.repository.StooqRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Koin dependency injection module for Stooq data layer.
 * Provides instances of API, Repository, and network components.
 */
val stooqModule = module {

    /**
     * Singleton interceptor that ensures realistic browser headers on all requests.
     */
    single {
        okhttp3.Interceptor { chain ->
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
     * Singleton HTTP logging interceptor for debugging network calls
     */
    single {
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
    }

    /**
     * Singleton OkHttpClient with timeouts and logging
     */
    single {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(get<okhttp3.Interceptor>())
            .addInterceptor(get<HttpLoggingInterceptor>())
            .build()
    }

    /**
     * Singleton Retrofit instance configured for Stooq API
     */
    single {
        Retrofit.Builder()
            .baseUrl(StooqApi.BASE_URL)
            .client(get())
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
    }

    /**
     * Singleton StooqApi interface implementation
     */
    single {
        get<Retrofit>().create(StooqApi::class.java)
    }

    /**
     * Singleton StooqRepository
     */
    single {
        StooqRepository(get())
    }
}
