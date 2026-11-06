package com.vesteai.domain.usecase

import com.vesteai.core.result.DataResult
import com.vesteai.domain.repository.WardrobeRepository
import javax.inject.Inject

sealed interface DeletionPlan {
    data object Standalone : DeletionPlan

    data class CascadesLookbooks(val lookbookCount: Int) : DeletionPlan
}

class GarmentDeletionUseCase @Inject constructor(
    private val wardrobe: WardrobeRepository,
) {
    /** Advisory: [delete] re-derives the affected lookbooks inside its own transaction. */
    suspend fun plan(garmentId: String): DeletionPlan {
        val lookbooks = wardrobe.countLookbooksContaining(garmentId)
        return if (lookbooks == 0) {
            DeletionPlan.Standalone
        } else {
            DeletionPlan.CascadesLookbooks(lookbooks)
        }
    }

    suspend fun delete(garmentId: String): DataResult<Unit> = wardrobe.deleteGarment(garmentId)
}
