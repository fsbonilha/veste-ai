package com.vesteai.feature.wardrobe

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.vesteai.R
import com.vesteai.domain.model.Garment
import com.vesteai.domain.model.GarmentCategory
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class WardrobeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val resources = RuntimeEnvironment.getApplication().resources

    private fun testGarment() = Garment(
        id = "g1",
        imagePath = "wardrobe/g1.jpg",
        category = GarmentCategory.TOP,
        createdAt = 0L,
        isFavorite = false,
    )

    private fun showScreen(
        state: WardrobeUiState,
        onAddFromGallery: () -> Unit = {},
    ) {
        compose.setContent {
            WardrobeScreen(
                state = state,
                captureError = null,
                imageFile = { File(it) },
                onFilterSelected = {},
                onRetry = {},
                onToggleFavorite = {},
                onRequestDelete = {},
                onConfirmDelete = {},
                onCancelDelete = {},
                onDismissDeleteError = {},
                onDismissCaptureError = {},
                onAddFromGallery = onAddFromGallery,
                onAddFromCamera = {},
            )
        }
    }

    private fun emptyWardrobe() = WardrobeUiState.Content(filter = null, garments = emptyList())

    @Test
    fun addButtonsAreVisibleWhenTheWardrobeIsEmpty() {
        showScreen(emptyWardrobe())

        compose.onNodeWithText(resources.getString(R.string.action_add_from_gallery))
            .assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.action_add_from_camera))
            .assertIsDisplayed()
    }

    @Test
    fun emptyWardrobeMessageIsDisplayed() {
        showScreen(emptyWardrobe())

        compose.onNodeWithText(resources.getString(R.string.wardrobe_empty)).assertIsDisplayed()
    }

    @Test
    fun addFromGalleryButtonReportsTheTap() {
        var taps = 0
        showScreen(emptyWardrobe(), onAddFromGallery = { taps++ })

        compose.onNodeWithText(resources.getString(R.string.action_add_from_gallery)).performClick()

        assertEquals(1, taps)
    }

    @Test
    fun categoryFilterAndAddButtonStayVisibleWithGarmentsPresent() {
        showScreen(WardrobeUiState.Content(filter = null, garments = listOf(testGarment())))

        compose.onNodeWithText(resources.getString(R.string.filter_all)).assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.category_footwear)).assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.action_add_from_gallery))
            .assertIsDisplayed()
    }

    @Test
    fun loadFailureShowsRetryInsteadOfContent() {
        showScreen(WardrobeUiState.Error)

        compose.onNodeWithText(resources.getString(R.string.wardrobe_load_failed)).assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.action_retry)).assertIsDisplayed()
    }
}
