package com.vesteai.data.db

import androidx.room.Room
import com.vesteai.domain.model.GarmentCategory
import org.junit.Assert.assertTrue
import org.robolectric.RuntimeEnvironment

internal fun inMemoryDatabase(): VesteDatabase =
    Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), VesteDatabase::class.java)
        .allowMainThreadQueries()
        .build()

internal fun garment(
    id: String,
    category: GarmentCategory = GarmentCategory.TOP,
    createdAt: Long = 1_000L,
    isFavorite: Boolean = false,
    displayName: String? = null,
) = GarmentEntity(
    id = id,
    imagePath = "wardrobe/$id.jpg",
    category = category,
    createdAt = createdAt,
    isFavorite = isFavorite,
    displayName = displayName,
)

internal fun outfit(id: String, createdAt: Long = 2_000L, isFavorite: Boolean = false) =
    OutfitEntity(
        id = id,
        resultImagePath = "looks/$id.jpg",
        createdAt = createdAt,
        isFavorite = isFavorite,
    )

internal fun crossRefs(outfitId: String, vararg garmentIds: String) =
    garmentIds.map { OutfitGarmentCrossRef(outfitId, it) }

internal fun VesteDatabase.crossRefCount(): Int =
    openHelper.readableDatabase.query("SELECT COUNT(*) FROM outfit_garment").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }

internal inline fun <reified T : Throwable> assertFailsWith(block: () -> Unit) {
    val thrown = runCatching(block).exceptionOrNull()
    assertTrue("Expected ${T::class.simpleName}, got $thrown", thrown is T)
}
