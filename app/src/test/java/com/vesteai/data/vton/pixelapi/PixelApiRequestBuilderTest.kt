package com.vesteai.data.vton.pixelapi

import com.vesteai.domain.model.GarmentCategory
import com.vesteai.domain.vton.VtonRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class PixelApiRequestBuilderTest {

    private val person = byteArrayOf(1, 2, 3)
    private val top = byteArrayOf(10, 11)
    private val bottom = byteArrayOf(20, 21)
    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    @Test
    fun singleTop_buildsSingleSubmissionWithUpperbodyCategory() {
        val submission = PixelApiRequestBuilder.build(VtonRequest(person, mapOf(GarmentCategory.TOP to top)))

        val single = submission as PixelApiSubmission.Single
        assertEquals(b64(person), single.body.personImage)
        assertEquals(b64(top), single.body.garmentImage)
        assertEquals("upperbody", single.body.category)
    }

    @Test
    fun singleBottom_mapsToLowerbody() {
        val submission = PixelApiRequestBuilder.build(VtonRequest(person, mapOf(GarmentCategory.BOTTOM to bottom)))

        assertEquals("lowerbody", (submission as PixelApiSubmission.Single).body.category)
    }

    @Test
    fun singleFullBody_mapsToDress() {
        val submission = PixelApiRequestBuilder.build(VtonRequest(person, mapOf(GarmentCategory.FULL_BODY to top)))

        assertEquals("dress", (submission as PixelApiSubmission.Single).body.category)
    }

    @Test
    fun topAndBottom_buildsOutfitSubmission() {
        val submission = PixelApiRequestBuilder.build(
            VtonRequest(person, mapOf(GarmentCategory.TOP to top, GarmentCategory.BOTTOM to bottom)),
        )

        val outfit = submission as PixelApiSubmission.Outfit
        assertEquals(b64(person), outfit.body.personImage)
        assertEquals(b64(top), outfit.body.upperbodyGarment)
        assertEquals(b64(bottom), outfit.body.lowerbodyGarment)
    }

    @Test
    fun imagesAreRawBase64_withoutDataUriPrefix() {
        val submission = PixelApiRequestBuilder.build(VtonRequest(person, mapOf(GarmentCategory.TOP to top)))

        val single = submission as PixelApiSubmission.Single
        assertTrue(!single.body.personImage.startsWith("data:"))
        assertTrue(!single.body.garmentImage.startsWith("data:"))
    }

    @Test
    fun unsupportedCategory_returnsNull() {
        assertNull(PixelApiRequestBuilder.build(VtonRequest(person, mapOf(GarmentCategory.FOOTWEAR to top))))
    }

    @Test
    fun emptySelection_returnsNull() {
        assertNull(PixelApiRequestBuilder.build(VtonRequest(person, emptyMap())))
    }

    @Test
    fun categoryMap_supportsOnlyTopBottomFullBody() {
        assertEquals(
            setOf(GarmentCategory.TOP, GarmentCategory.BOTTOM, GarmentCategory.FULL_BODY),
            PixelApiCategoryMap.supportedCategories,
        )
    }
}
