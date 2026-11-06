package com.vesteai.core.di

import android.content.Context
import androidx.room.Room
import com.vesteai.data.db.GarmentDao
import com.vesteai.data.db.OutfitDao
import com.vesteai.data.db.VesteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    // No fallbackToDestructiveMigration: a schema bump without a migration must fail loudly
    // instead of wiping the user's wardrobe and paid-for lookbook renders.
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VesteDatabase =
        Room.databaseBuilder(context, VesteDatabase::class.java, VesteDatabase.NAME).build()

    @Provides
    fun provideGarmentDao(database: VesteDatabase): GarmentDao = database.garmentDao()

    @Provides
    fun provideOutfitDao(database: VesteDatabase): OutfitDao = database.outfitDao()
}
