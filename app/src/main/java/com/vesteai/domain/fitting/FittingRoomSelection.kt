package com.vesteai.domain.fitting

import com.vesteai.domain.model.Garment
import com.vesteai.domain.model.GarmentCategory

/**
 * Single source of truth for fitting-room selection rules (plan §6.3, PRD §5.1).
 * At most one garment per category; FULL_BODY is mutually exclusive with TOP and BOTTOM.
 */
class FittingRoomSelection private constructor(
    val selected: Map<GarmentCategory, Garment>,
) {
    fun isSelected(garment: Garment): Boolean = selected[garment.category]?.id == garment.id

    fun toggle(item: Garment): FittingRoomSelection {
        if (isSelected(item)) return FittingRoomSelection(selected - item.category)
        val cleared = when (item.category) {
            GarmentCategory.FULL_BODY -> selected - GarmentCategory.TOP - GarmentCategory.BOTTOM
            GarmentCategory.TOP, GarmentCategory.BOTTOM -> selected - GarmentCategory.FULL_BODY
            GarmentCategory.HEADWEAR, GarmentCategory.FOOTWEAR -> selected
        }
        return FittingRoomSelection(cleared + (item.category to item))
    }

    override fun equals(other: Any?): Boolean =
        other is FittingRoomSelection && other.selected == selected

    override fun hashCode(): Int = selected.hashCode()

    override fun toString(): String = "FittingRoomSelection(selected=$selected)"

    companion object {
        val Empty = FittingRoomSelection(emptyMap())
    }
}
