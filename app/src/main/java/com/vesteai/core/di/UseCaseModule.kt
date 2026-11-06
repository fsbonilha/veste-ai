package com.vesteai.core.di

import com.vesteai.domain.usecase.OrphanFileSweep
import com.vesteai.domain.usecase.OrphanFileSweepUseCase
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class UseCaseModule {
    @Binds
    abstract fun bindOrphanFileSweep(impl: OrphanFileSweepUseCase): OrphanFileSweep
}
