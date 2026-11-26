package com.vesteai.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReleaseHttpLoggingModule {

    /** The logging interceptor is not on the release classpath at all (plan §6.4). */
    @Provides
    @Singleton
    fun provideHttpLogging(): HttpLogging = HttpLogging { emptyList() }
}