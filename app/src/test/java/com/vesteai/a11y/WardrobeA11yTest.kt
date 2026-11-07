package com.vesteai.a11y

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import com.vesteai.R
import com.vesteai.domain.model.Garment
import com.vesteai.domain.model.GarmentCategory
import com.vesteai.domain.usecase.DeletionPlan
import com.vesteai.feature.wardrobe.PendingDeletion
import com.vesteai.feature.wardrobe.WardrobeScreen
import com.vesteai.feature.wardrobe.WardrobeUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** TalkBack and touch-target audit for the wardrobe grid (plan §7, §8 M8). */
@RunWith(RobolectricTestRunner::class)
class WardrobeA11yTest {

    @get:Rule
    val compose = createComposeRule()

    private val resources = RuntimeEnvironment.getApplication().resources
    private val image = tempImage("garment")

    private fun garment(
        id: String = "g1",
        category: GarmentCategory = GarmentCategory.TOP,
        favorite: Boolean = false,
        displayName: String? = null,
    ) = Garment(id, "wardrobe/$id.jpg", category, 0L, favorite, displayName)

    private fun showScreen(
        state: WardrobeUiState,
        onToggleFavorite: (Garment) -> Unit = {},
        onRequestDelete: (Garment) -> Unit = {},
    ) {
        compose.setContent {
            WardrobeScreen(
                state = state,
                captureError = null,
                imageFile = { image },
                onFilterSelected = {},
                onRetry = {},
                onToggleFavorite = onToggleFavorite,
                onRequestDelete = onRequestDelete,
                onConfirmDelete = {},
                onCancelDelete = {},
                onDismissDeleteError = {},
                onDismissCaptureError = {},
                onAddFromGallery = {},
                onAddFromCamera = {},
            )
        }
    }

    private fun string(id: Int, vararg args: Any) = resources.getString(id, *args)

    @Test
    fun theGarmentPhotoIsDescribedByTheGarmentRatherThanBeingDecorative() {
        showScreen(WardrobeUiState.Content(filter = null, garments = listOf(garment())))

        compose.assertImageHasDescription(string(R.string.category_top))
    }

    @Test
    fun theAddButtonsStayReachableAndKeepTheirLabels() {
        showScreen(WardrobeUiState.Content(filter = null, garments = emptyList()))

        compose.assertPrimaryActionReachable(string(R.string.action_add_from_gallery))
        compose.assertPrimaryActionReachable(string(R.string.action_add_from_camera))
        compose.assertControlsAreAccessible()
    }

    @Test
    fun everyTileActionNamesItsOwnGarment() {
        showScreen(WardrobeUiState.Content(filter = null, garments = listOf(garment(displayName = "Blue tee"))))

        val favorited = compose.onNodeWithText(string(R.string.action_favorite))
            .performScrollTo()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf(string(R.string.action_favorite_named, "Blue tee")),
                ),
            )
        favorited.assertIsDisplayed()

        compose.onNodeWithText(string(R.string.action_delete))
            .performScrollTo()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf(string(R.string.action_delete_named, "Blue tee")),
                ),
            )
    }

    @Test
    fun unfavoriteIsAnnouncedAsTheInverseAction() {
        showScreen(
            WardrobeUiState.Content(filter = null, garments = listOf(garment(favorite = true))),
        )

        compose.onNodeWithText(string(R.string.action_unfavorite))
            .performScrollTo()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf(string(R.string.action_unfavorite_named, string(R.string.category_top))),
                ),
            )
    }

    @Test
    fun tileActionsStillReportTheirOwnGarment() {
        var deleted: Garment? = null
        val first = garment(id = "a")
        val second = garment(id = "b", category = GarmentCategory.BOTTOM)
        showScreen(
            WardrobeUiState.Content(filter = null, garments = listOf(first, second)),
            onRequestDelete = { deleted = it },
        )

        // Two tiles each carry a "Delete"; the second tile's is the one that names the bottom.
        // The grid is lazy, so the second tile only exists once its row is scrolled to.
        compose.onAllNodes(hasScrollToIndexAction())[1].performScrollToIndex(1)
        compose.onNode(
            hasText(string(R.string.action_delete)) and
                hasContentDescription(string(R.string.action_delete_named, string(R.string.category_bottom))),
        ).performScrollTo().performClick()

        assertEquals(second, deleted)
    }

    @Test
    fun theFilterRowIsReachableAndNamed() {
        showScreen(WardrobeUiState.Content(filter = null, garments = listOf(garment())))

        compose.assertPrimaryActionReachable(string(R.string.filter_all))
        compose.assertPrimaryActionReachable(string(R.string.category_headwear))
        compose.assertControlsAreAccessible()
    }

    @Test
    fun theLoadingSpinnerIsAnnounced() {
        showScreen(WardrobeUiState.Loading)

        // A bare spinner is silent to a screen reader, so it has to carry a description.
        compose.assertImageHasDescription(string(R.string.state_loading))
    }

    @Test
    fun anEmptyWardrobeExplainsHowToFillIt() {
        showScreen(WardrobeUiState.Content(filter = null, garments = emptyList()))

        compose.assertTextDisplayed(string(R.string.wardrobe_empty))
    }

    @Test
    fun anEmptyFilterNamesTheFilterRatherThanTheWholeWardrobe() {
        showScreen(WardrobeUiState.Content(filter = GarmentCategory.BOTTOM, garments = emptyList()))

        compose.assertTextDisplayed(string(R.string.wardrobe_empty_filtered))
    }

    @Test
    fun aLoadFailureOffersCopyAndARetry() {
        showScreen(WardrobeUiState.Error)

        compose.assertTextDisplayed(string(R.string.wardrobe_load_failed))
        compose.assertPrimaryActionReachable(string(R.string.action_retry))
        compose.assertControlsAreAccessible()
    }

    @Test
    fun theDeleteDialogIsNamedAndItsActionsAreTouchSized() {
        val target = garment()
        showScreen(
            WardrobeUiState.Content(
                filter = null,
                garments = listOf(target),
                pendingDeletion = PendingDeletion(target, DeletionPlan.CascadesLookbooks(2)),
            ),
        )

        compose.assertTextDisplayed(string(R.string.delete_garment_cascade_title))
        compose.assertTextDisplayed(string(R.string.action_cancel))
        // The tile behind the dialog also says "Delete", so the dialog's button is matched by role.
        compose.onAllNodesWithText(string(R.string.action_delete))
            .fetchSemanticsNodes()
            .let { assertEquals(2, it.size) }
        compose.assertControlsAreAccessible()
    }

    @Test
    fun aDeleteFailureIsCopyNotAnException() {
        showScreen(
            WardrobeUiState.Content(
                filter = null,
                garments = listOf(garment()),
                deleteFailed = true,
            ),
        )

        compose.assertTextDisplayed(string(R.string.garment_delete_failed))
        compose.assertControlsAreAccessible()
    }
}
