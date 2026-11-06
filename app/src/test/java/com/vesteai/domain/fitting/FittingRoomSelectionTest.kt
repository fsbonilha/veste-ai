package com.vesteai.domain.fitting

import com.vesteai.domain.model.Garment
import com.vesteai.domain.model.GarmentCategory
import com.vesteai.domain.model.GarmentCategory.BOTTOM
import com.vesteai.domain.model.GarmentCategory.FOOTWEAR
import com.vesteai.domain.model.GarmentCategory.FULL_BODY
import com.vesteai.domain.model.GarmentCategory.HEADWEAR
import com.vesteai.domain.model.GarmentCategory.TOP
import org.junit.Assert.assertEquals
import org.junit.Test

class FittingRoomSelectionTest {

    private fun garment(id: String, category: GarmentCategory) = Garment(
        id = id,
        imagePath = "garments/$id.jpg",
        category = category,
        createdAt = 0L,
        isFavorite = false,
    )

    private fun selectionOf(vararg items: Garment): FittingRoomSelection =
        items.fold(FittingRoomSelection.Empty) { selection, item -> selection.toggle(item) }

    private val top = garment("top-1", TOP)
    private val bottom = garment("bottom-1", BOTTOM)
    private val otherBottom = garment("bottom-2", BOTTOM)
    private val dress = garment("dress-1", FULL_BODY)
    private val hat = garment("hat-1", HEADWEAR)
    private val shoes = garment("shoes-1", FOOTWEAR)

    // Row 1: toggle TOP / BOTTOM while a FULL_BODY is active → FULL_BODY cleared, new item active
    @Test
    fun toggleTop_whileFullBodyActive_clearsFullBodyAndActivatesTop() {
        val result = selectionOf(dress).toggle(top)

        assertEquals(mapOf(TOP to top), result.selected)
    }

    @Test
    fun toggleBottom_whileFullBodyActive_clearsFullBodyAndActivatesBottom() {
        val result = selectionOf(dress).toggle(bottom)

        assertEquals(mapOf(BOTTOM to bottom), result.selected)
    }

    // Row 2: toggle FULL_BODY while TOP or BOTTOM active → both cleared, FULL_BODY active
    @Test
    fun toggleFullBody_whileTopActive_clearsTopAndActivatesFullBody() {
        val result = selectionOf(top).toggle(dress)

        assertEquals(mapOf(FULL_BODY to dress), result.selected)
    }

    @Test
    fun toggleFullBody_whileBottomActive_clearsBottomAndActivatesFullBody() {
        val result = selectionOf(bottom).toggle(dress)

        assertEquals(mapOf(FULL_BODY to dress), result.selected)
    }

    @Test
    fun toggleFullBody_whileTopAndBottomActive_clearsBothAndActivatesFullBody() {
        val result = selectionOf(top, bottom).toggle(dress)

        assertEquals(mapOf(FULL_BODY to dress), result.selected)
    }

    @Test
    fun toggleFullBody_whileTopBottomAndAccessoriesActive_keepsAccessories() {
        val result = selectionOf(hat, top, bottom, shoes).toggle(dress)

        assertEquals(mapOf(HEADWEAR to hat, FOOTWEAR to shoes, FULL_BODY to dress), result.selected)
    }

    // Row 3: toggle HEADWEAR / FOOTWEAR → other slots untouched
    @Test
    fun toggleHeadwear_leavesOtherSlotsUntouched() {
        val result = selectionOf(top, bottom, shoes).toggle(hat)

        assertEquals(mapOf(TOP to top, BOTTOM to bottom, FOOTWEAR to shoes, HEADWEAR to hat), result.selected)
    }

    @Test
    fun toggleFootwear_leavesOtherSlotsUntouched() {
        val result = selectionOf(hat, dress).toggle(shoes)

        assertEquals(mapOf(HEADWEAR to hat, FULL_BODY to dress, FOOTWEAR to shoes), result.selected)
    }

    // Row 4: toggle an already-active item → item removed
    @Test
    fun toggleActiveTop_removesOnlyTop() {
        val result = selectionOf(top, bottom).toggle(top)

        assertEquals(mapOf(BOTTOM to bottom), result.selected)
    }

    @Test
    fun toggleActiveFullBody_removesFullBody() {
        val result = selectionOf(hat, dress).toggle(dress)

        assertEquals(mapOf(HEADWEAR to hat), result.selected)
    }

    @Test
    fun toggleActiveHeadwear_removesHeadwear() {
        val result = selectionOf(hat, top).toggle(hat)

        assertEquals(mapOf(TOP to top), result.selected)
    }

    @Test
    fun toggleActiveItem_matchesById_evenIfOtherFieldsChanged() {
        val result = selectionOf(top).toggle(top.copy(isFavorite = true))

        assertEquals(FittingRoomSelection.Empty, result)
    }

    // Row 5: toggle BOTTOM while BOTTOM active → replaces (one per slot)
    @Test
    fun toggleBottom_whileOtherBottomActive_replacesIt() {
        val result = selectionOf(top, bottom).toggle(otherBottom)

        assertEquals(mapOf(TOP to top, BOTTOM to otherBottom), result.selected)
    }

    // PRD §5.1: TOP and BOTTOM may be active together
    @Test
    fun toggleTopThenBottom_keepsBothActive() {
        val result = selectionOf(top, bottom)

        assertEquals(mapOf(TOP to top, BOTTOM to bottom), result.selected)
    }

    @Test
    fun toggle_doesNotMutateOriginalSelection() {
        val original = selectionOf(dress)

        original.toggle(top)

        assertEquals(mapOf(FULL_BODY to dress), original.selected)
    }
}
