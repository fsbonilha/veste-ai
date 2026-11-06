package com.vesteai.data.db

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class OutfitWithGarments(
    @Embedded val outfit: OutfitEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = OutfitGarmentCrossRef::class,
            parentColumn = "outfitId",
            entityColumn = "garmentId",
        ),
    )
    val garments: List<GarmentEntity>,
)
