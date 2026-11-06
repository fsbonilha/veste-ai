package com.vesteai.domain.usecase

import com.vesteai.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class GarmentDeletionPluralTest {
    private val resources = RuntimeEnvironment.getApplication().resources

    @Test
    fun singleLookbookUsesTheSingularForm() {
        assertEquals(
            "Deleting this garment will also delete 1 lookbook that contains it. That look is gone for good and cannot be recovered. Do you really want to proceed?",
            resources.getQuantityString(R.plurals.deleting_garment_cascades_lookbooks, 1, 1),
        )
    }

    @Test
    fun severalLookbooksUseThePluralForm() {
        assertEquals(
            "Deleting this garment will also delete 4 lookbooks that contain it. These looks are gone for good and cannot be recovered. Do you really want to proceed?",
            resources.getQuantityString(R.plurals.deleting_garment_cascades_lookbooks, 4, 4),
        )
    }
}
