package com.vesteai.core.di

import com.vesteai.data.vton.mock.CanvasMockCompositor
import com.vesteai.data.vton.mock.MockVtonEngine
import com.vesteai.domain.vton.VtonEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VtonEngineModule {
    @Provides
    @Singleton
    fun provideVtonEngine(): VtonEngine =
        MockVtonEngine(dispatcher = Dispatchers.Default, compositor = CanvasMockCompositor())
}
