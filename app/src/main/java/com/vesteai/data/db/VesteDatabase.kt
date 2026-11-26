package com.vesteai.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [GarmentEntity::class, OutfitEntity::class, OutfitGarmentCrossRef::class, PendingJobEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(CategoryConverter::class)
abstract class VesteDatabase : RoomDatabase() {
    abstract fun garmentDao(): GarmentDao

    abstract fun outfitDao(): OutfitDao

    abstract fun pendingJobDao(): PendingJobDao

    companion object {
        const val NAME = "veste.db"
    }
}