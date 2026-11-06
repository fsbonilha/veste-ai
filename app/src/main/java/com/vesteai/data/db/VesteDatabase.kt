package com.vesteai.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [GarmentEntity::class, OutfitEntity::class, OutfitGarmentCrossRef::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(CategoryConverter::class)
abstract class VesteDatabase : RoomDatabase() {
    abstract fun garmentDao(): GarmentDao

    abstract fun outfitDao(): OutfitDao

    companion object {
        const val NAME = "veste.db"
    }
}
