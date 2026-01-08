package com.example.stocksignal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.example.stocksignal.ui.StockSignalApp
import com.example.stocksignal.ui.theme.StockSignalTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val intentState = mutableStateOf<android.content.Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateLaunchIntent(intent)
        setContent {
            StockSignalTheme {
                StockSignalApp(launchIntent = intentState.value)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        updateLaunchIntent(intent)
    }

    fun handleNewIntent(intent: android.content.Intent) {
        updateLaunchIntent(intent)
    }

    private fun updateLaunchIntent(intent: android.content.Intent) {
        intentState.value = intent
    }
}
