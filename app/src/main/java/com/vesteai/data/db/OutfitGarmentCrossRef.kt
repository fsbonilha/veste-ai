package com.vesteai.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

// garmentId deliberately has no ON DELETE action: a garment must not be deletable while
// a lookbook still references it. Garment deletion removes the containing outfits first.
@Entity(
    tableName = "outfit_garment",
    primaryKeys = ["outfitId", "garmentId"],
    foreignKeys = [
        ForeignKey(
            entity = OutfitEntity::class,
            parentColumns = ["id"],
            childColumns = ["outfitId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GarmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["garmentId"],
        ),
    ],
    indices = [Index("outfitId"), Index("garmentId")],
)
data class OutfitGarmentCrossRef(
    val outfitId: String,
    val garmentId: String,
)
