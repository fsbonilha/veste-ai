package com.vesteai.feature.wardrobe

import kotlinx.serialization.Serializable

@Serializable
data object WardrobeDestination

@Serializable
data class GarmentDraftDestination(val imagePath: String)
