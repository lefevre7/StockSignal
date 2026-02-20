package com.example.stocksignal.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

val Context.notificationDiagnosticsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "notification_diagnostics"
)
