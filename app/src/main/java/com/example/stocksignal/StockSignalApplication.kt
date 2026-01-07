package com.example.stocksignal

import android.app.Application
import android.util.Log
import com.example.stocksignal.data.stooq.di.stooqModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * Application class for StockSignal app.
 * Initializes Koin dependency injection framework.
 */
class StockSignalApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Koin
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@StockSignalApplication)
            modules(stooqModule)
        }

        Log.d(TAG, "onCreate: Koin started")
    }

    companion object {
        private const val TAG = "StockSignalApplication"
    }
}
