package com.example.stocksignal.core

interface ExecutionGateDiagnosticsRecorder {
    fun record(scope: String, waitMs: Long, holdMs: Long)
}

object NoOpExecutionGateDiagnosticsRecorder : ExecutionGateDiagnosticsRecorder {
    override fun record(scope: String, waitMs: Long, holdMs: Long) = Unit
}
