package com.vesteai.core.di

import com.vesteai.data.repository.RoomLookbookRepository
import com.vesteai.data.repository.RoomWardrobeRepository
import com.vesteai.domain.repository.LookbookRepository
import com.vesteai.domain.repository.WardrobeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindWardrobeRepository(impl: RoomWardrobeRepository): WardrobeRepository

    @Binds
    abstract fun bindLookbookRepository(impl: RoomLookbookRepository): LookbookRepository
}
