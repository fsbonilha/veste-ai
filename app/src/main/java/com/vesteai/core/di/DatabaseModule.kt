package com.vesteai.core.di

import android.content.Context
import androidx.room.Room
import com.vesteai.data.db.GarmentDao
import com.vesteai.data.db.OutfitDao
import com.vesteai.data.db.PendingJobDao
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
    // Migration from v1 to v2 adds the pending_job table. Pure additive — existing
    // tables (garment, outfit, outfit_garment) are untouched.
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VesteDatabase =
        Room.databaseBuilder(context, VesteDatabase::class.java, VesteDatabase.NAME)
            // Dev: recreate the DB if the schema changed. Release builds must provide
            // a proper migration script before bumping the schema version.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideGarmentDao(database: VesteDatabase): GarmentDao = database.garmentDao()

    @Provides
    fun provideOutfitDao(database: VesteDatabase): OutfitDao = database.outfitDao()

    @Provides
    fun providePendingJobDao(database: VesteDatabase): PendingJobDao = database.pendingJobDao()
}