package com.example.stocksignal.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalExecutionGateTest {

    @Test
    fun withPermitRecordsMetrics() = runBlocking {
        val recorder = RecordingRecorder()
        val gate = ExternalExecutionGate(recorder)

        val result = gate.withPermit(scope = "llm_inference") { "ok" }

        assertEquals("ok", result)
        assertEquals(1, recorder.entries.size)
        val entry = recorder.entries.single()
        assertEquals("llm_inference", entry.scope)
        assertTrue(entry.waitMs >= 0L)
        assertTrue(entry.holdMs >= 0L)
    }

    @Test
    fun withPermitBlockingRecordsMetrics() {
        val recorder = RecordingRecorder()
        val gate = ExternalExecutionGate(recorder)

        val result = gate.withPermitBlocking(scope = "stooq_http") { 123 }

        assertEquals(123, result)
        assertEquals(1, recorder.entries.size)
        val entry = recorder.entries.single()
        assertEquals("stooq_http", entry.scope)
        assertTrue(entry.waitMs >= 0L)
        assertTrue(entry.holdMs >= 0L)
    }

    private class RecordingRecorder : ExecutionGateDiagnosticsRecorder {
        val entries = mutableListOf<Entry>()

        override fun record(scope: String, waitMs: Long, holdMs: Long) {
            entries.add(Entry(scope, waitMs, holdMs))
        }
    }

    private data class Entry(
        val scope: String,
        val waitMs: Long,
        val holdMs: Long
    )
}
