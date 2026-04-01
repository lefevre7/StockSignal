package com.example.stocksignal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StooqExecutionGateTest {

    @Test
    fun `withPermitBlocking records metrics and returns result`() {
        val recorder = RecordingRecorder()
        val gate = StooqExecutionGate(recorder)

        val result = gate.withPermitBlocking(scope = "stooq_http") { "success" }

        assertEquals("success", result)
        assertEquals(1, recorder.entries.size)
        val entry = recorder.entries.single()
        assertEquals("stooq_http", entry.scope)
        assertTrue(entry.waitMs >= 0L)
        assertTrue(entry.holdMs >= 0L)
    }

    @Test
    fun `withPermitBlocking uses DEFAULT_SCOPE when no scope provided`() {
        val recorder = RecordingRecorder()
        val gate = StooqExecutionGate(recorder)

        gate.withPermitBlocking { "result" }

        assertEquals("stooq_http", recorder.entries.single().scope)
    }

    @Test
    fun `withPermitBlocking returns correct integer result`() {
        val gate = StooqExecutionGate()

        val result = gate.withPermitBlocking { 42 }

        assertEquals(42, result)
    }

    @Test
    fun `no-arg constructor uses NoOp recorder and does not throw`() {
        val gate = StooqExecutionGate()
        val result = gate.withPermitBlocking { "no-op" }
        assertEquals("no-op", result)
    }

    @Test
    fun `sequential calls are each recorded independently`() {
        val recorder = RecordingRecorder()
        val gate = StooqExecutionGate(recorder)

        gate.withPermitBlocking(scope = "call_1") { 1 }
        gate.withPermitBlocking(scope = "call_2") { 2 }

        assertEquals(2, recorder.entries.size)
        assertEquals("call_1", recorder.entries[0].scope)
        assertEquals("call_2", recorder.entries[1].scope)
    }

    private class RecordingRecorder : ExecutionGateDiagnosticsRecorder {
        val entries = mutableListOf<Entry>()

        override fun record(scope: String, waitMs: Long, holdMs: Long) {
            entries.add(Entry(scope, waitMs, holdMs))
        }
    }

    private data class Entry(val scope: String, val waitMs: Long, val holdMs: Long)
}
