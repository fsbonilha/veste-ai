package com.vesteai.feature.lookbook

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.ZoneId
import java.util.Locale
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LookbookModule {
    @Provides
    @Singleton
    fun provideDateFormatter(): LookbookDateFormatter =
        LookbookDateFormatter(Locale.getDefault(), ZoneId.systemDefault())
}
