package com.example.stocksignal.notifications

import com.example.stocksignal.core.ExecutionGateDiagnosticsRecorder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundStooqExecutionGateTest {

    @Test
    fun `withPermit records metrics and returns result`() = runTest {
        val recorder = RecordingRecorder()
        val gate = BackgroundStooqExecutionGate(recorder)

        val result = gate.withPermit(scope = "stooq_background") { "ok" }

        assertEquals("ok", result)
        assertEquals(1, recorder.entries.size)
        val entry = recorder.entries.single()
        assertEquals("stooq_background", entry.scope)
        assertTrue(entry.waitMs >= 0L)
        assertTrue(entry.holdMs >= 0L)
    }

    @Test
    fun `withPermit uses DEFAULT_SCOPE when no scope provided`() = runTest {
        val recorder = RecordingRecorder()
        val gate = BackgroundStooqExecutionGate(recorder)

        gate.withPermit { "result" }

        assertEquals("stooq_background", recorder.entries.single().scope)
    }

    @Test
    fun `withPermit returns correct integer result`() = runTest {
        val gate = BackgroundStooqExecutionGate()

        val result = gate.withPermit { 99 }

        assertEquals(99, result)
    }

    @Test
    fun `no-arg constructor uses NoOp recorder and does not throw`() = runTest {
        val gate = BackgroundStooqExecutionGate()
        val result = gate.withPermit { "no-op" }
        assertEquals("no-op", result)
    }

    @Test
    fun `sequential suspending calls are each recorded independently`() = runTest {
        val recorder = RecordingRecorder()
        val gate = BackgroundStooqExecutionGate(recorder)

        gate.withPermit(scope = "call_a") { "a" }
        gate.withPermit(scope = "call_b") { "b" }

        assertEquals(2, recorder.entries.size)
        assertEquals("call_a", recorder.entries[0].scope)
        assertEquals("call_b", recorder.entries[1].scope)
    }

    private class RecordingRecorder : ExecutionGateDiagnosticsRecorder {
        val entries = mutableListOf<Entry>()

        override fun record(scope: String, waitMs: Long, holdMs: Long) {
            entries.add(Entry(scope, waitMs, holdMs))
        }
    }

    private data class Entry(val scope: String, val waitMs: Long, val holdMs: Long)
}
