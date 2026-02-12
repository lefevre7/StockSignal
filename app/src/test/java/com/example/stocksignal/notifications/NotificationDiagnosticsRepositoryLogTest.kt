package com.example.stocksignal.notifications

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.example.stocksignal.data.settings.settingsDataStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NotificationDiagnosticsRepositoryLogTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val repository = NotificationDiagnosticsRepository(context.settingsDataStore)

    @Before
    fun setUp() {
        runBlocking {
            context.settingsDataStore.edit { it.clear() }
        }
    }

    @Test
    fun backgroundWorkerLogKeepsMostRecentEntries() = runBlocking {
        repeat(85) { idx ->
            repository.recordBackgroundWorkerEvent(
                worker = "premarket_quote",
                phase = "start",
                key = "sample-$idx"
            )
        }

        val entries = repository.getBackgroundWorkerLog()

        assertEquals(80, entries.size)
        assertTrue(entries.first().contains("sample-5"))
        assertTrue(entries.last().contains("sample-84"))
    }

    @Test
    fun serialGateLogIncludesScopeWaitAndHold() = runBlocking {
        repository.recordSerialGateMetric(
            scope = "llm_inference",
            waitMs = 42L,
            holdMs = 128L
        )

        val entries = repository.getSerialGateMetricsLog()

        assertEquals(1, entries.size)
        val entry = entries.single()
        assertTrue(entry.contains("llm_inference"))
        assertTrue(entry.contains("wait=42ms"))
        assertTrue(entry.contains("hold=128ms"))
    }
}
