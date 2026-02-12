package com.example.stocksignal.notifications

import com.example.stocksignal.core.ExecutionGateDiagnosticsRecorder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Singleton
class ExecutionGateDiagnosticsRecorderImpl @Inject constructor(
    private val diagnosticsRepository: NotificationDiagnosticsRepository
) : ExecutionGateDiagnosticsRecorder {

    private val diagnosticsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun record(scope: String, waitMs: Long, holdMs: Long) {
        diagnosticsScope.launch {
            diagnosticsRepository.recordSerialGateMetric(scope, waitMs, holdMs)
        }
    }
}
