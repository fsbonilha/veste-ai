package com.vesteai.core.di

import android.content.Context
import com.vesteai.core.media.ImageStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaModule {
    @Provides
    @Singleton
    fun provideImageStore(@ApplicationContext context: Context): ImageStore =
        ImageStore(context.filesDir)
}
