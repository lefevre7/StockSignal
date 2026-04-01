package com.example.stocksignal.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.stocksignal.notifications.NotificationDiagnosticsRepository
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsFlowIsolationIntegrationTest {

    @Test
    fun diagnosticsWritesDoNotEmitSettingsFlow() = runBlocking {
        val settingsStore = createStore("settings-flow-${UUID.randomUUID()}")
        val diagnosticsStore = createStore("diagnostics-flow-${UUID.randomUUID()}")
        val settingsRepository = SettingsRepository(settingsStore)
        val diagnosticsRepository = NotificationDiagnosticsRepository(diagnosticsStore)

        // Prime initial value.
        settingsRepository.settingsFlow.first()

        val secondEmission = async {
            withTimeoutOrNull(750L) {
                settingsRepository.settingsFlow.drop(1).first()
            }
        }

        repeat(12) { idx ->
            diagnosticsRepository.recordStooqRequest(
                path = "/q/a2/d/",
                method = "GET",
                waitMs = idx.toLong()
            )
        }

        assertNull(
            "Diagnostics writes should not retrigger settings emissions",
            secondEmission.await()
        )
    }

    @Test
    fun identicalSettingWriteDoesNotEmitDuplicateValue() = runBlocking {
        val settingsStore = createStore("settings-flow-identical-${UUID.randomUUID()}")
        val settingsRepository = SettingsRepository(settingsStore)

        // Prime initial value.
        settingsRepository.settingsFlow.first()

        val secondEmission = async {
            withTimeoutOrNull(750L) {
                settingsRepository.settingsFlow.drop(1).first()
            }
        }

        // Default is THREE_PER_DAY, writing the same value should not emit with distinctUntilChanged.
        settingsRepository.setFrequency(NotificationFrequency.THREE_PER_DAY)

        assertNull(
            "Writing the same setting value should not emit a duplicate AppSettings object",
            secondEmission.await()
        )
    }

    @Test
    fun settingsChangeEmitsUpdatedValue() = runBlocking {
        val settingsStore = createStore("settings-flow-change-${UUID.randomUUID()}")
        val settingsRepository = SettingsRepository(settingsStore)

        // Prime initial value.
        settingsRepository.settingsFlow.first()

        val secondEmission = async {
            withTimeout(5_000L) {
                settingsRepository.settingsFlow.drop(1).first()
            }
        }

        settingsRepository.setFrequency(NotificationFrequency.ONE_PER_DAY)

        assertEquals(
            NotificationFrequency.ONE_PER_DAY,
            secondEmission.await().frequency
        )
    }

    private fun createStore(fileName: String): DataStore<Preferences> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile(fileName) }
        )
    }
}
