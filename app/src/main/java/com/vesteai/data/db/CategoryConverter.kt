package com.vesteai.data.db

import androidx.room.TypeConverter
import com.vesteai.domain.model.GarmentCategory

class CategoryConverter {
    @TypeConverter
    fun fromCategory(category: GarmentCategory): String = category.name

    @TypeConverter
    fun toCategory(name: String): GarmentCategory = GarmentCategory.valueOf(name)
}
