package com.example.stocksignal.data.translation

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LlmRuntimeModule {

    @Binds
    @Singleton
    abstract fun bindLocalLlmRuntimeFactory(
        factory: LiteRtLlmRuntimeFactory
    ): LocalLlmRuntimeFactory
}
