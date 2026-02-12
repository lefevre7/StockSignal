package com.example.stocksignal.notifications

import com.example.stocksignal.core.ExecutionGateDiagnosticsRecorder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExecutionGateDiagnosticsModule {

    @Binds
    @Singleton
    abstract fun bindExecutionGateDiagnosticsRecorder(
        impl: ExecutionGateDiagnosticsRecorderImpl
    ): ExecutionGateDiagnosticsRecorder
}
