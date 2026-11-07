package com.vesteai.a11y

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.vesteai.R
import com.vesteai.domain.model.Garment
import com.vesteai.domain.model.GarmentCategory
import com.vesteai.domain.model.Outfit
import com.vesteai.feature.lookbook.LookCard
import com.vesteai.feature.lookbook.LookbookScreen
import com.vesteai.feature.lookbook.LookbookUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** TalkBack and touch-target audit for the lookbook grid (plan §7, §8 M8). */
@RunWith(RobolectricTestRunner::class)
class LookbookA11yTest {

    @get:Rule
    val compose = createComposeRule()

    private val resources = RuntimeEnvironment.getApplication().resources
    private val image = tempImage("look")

    private val top = Garment("top", "wardrobe/top.jpg", GarmentCategory.TOP, 1L, false)
    private val looks = listOf(
        LookCard(
            Outfit("look-1", "looks/look-1.jpg", 1_710_000_367_000L, false, listOf(top)),
            createdLabel = "Mar 9, 2024",
        ),
    )

    private fun showScreen(
        state: LookbookUiState,
        onToggleFavorite: (LookCard) -> Unit = {},
        onReload: (LookCard) -> Unit = {},
    ) {
        compose.setContent {
            LookbookScreen(
                state = state,
                imageFile = { image },
                onRetry = {},
                onToggleFavorite = onToggleFavorite,
                onReload = onReload,
                onRequestDelete = {},
                onConfirmDelete = {},
                onCancelDelete = {},
                onDismissDeleteError = {},
            )
        }
    }

    private fun string(id: Int, vararg args: Any) = resources.getString(id, *args)

    @Test
    fun theSavedLookIsDescribedByItsDateSoCardsAreTellableApart() {
        showScreen(LookbookUiState.Content(looks = looks))

        compose.assertImageHasDescription(string(R.string.lookbook_saved_on, "Mar 9, 2024"))
    }

    @Test
    fun everyCardActionNamesTheLookItActsOn() {
        showScreen(LookbookUiState.Content(looks = looks))

        compose.onNodeWithText(string(R.string.action_delete))
            .performScrollTo()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf(string(R.string.action_delete_look_named, "Mar 9, 2024")),
                ),
            )
        compose.onNodeWithText(string(R.string.action_favorite))
            .performScrollTo()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf(string(R.string.action_favorite_look_named, "Mar 9, 2024")),
                ),
            )
        compose.onNodeWithText(string(R.string.action_reload_in_fitting_room))
            .performScrollTo()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf(string(R.string.action_reload_look_named, "Mar 9, 2024")),
                ),
            )
    }

    @Test
    fun theReloadActionIsThePrimaryActionAndStaysTouchSized() {
        var reloaded: LookCard? = null
        showScreen(
            LookbookUiState.Content(looks = looks),
            onReload = { reloaded = it },
        )

        compose.assertPrimaryActionReachable(string(R.string.action_reload_in_fitting_room))
        compose.onNodeWithText(string(R.string.action_reload_in_fitting_room)).performClick()

        assertEquals("look-1", reloaded?.outfit?.id)
        compose.assertControlsAreAccessible()
    }

    @Test
    fun theThumbnailStripNamesEachGarmentInsteadOfRepeatingOneLabel() {
        showScreen(LookbookUiState.Content(looks = looks))

        compose.onNode(
            hasContentDescription(string(R.string.category_top)),
        ).assertIsDisplayed()
    }

    @Test
    fun theLoadingSpinnerIsAnnounced() {
        showScreen(LookbookUiState.Loading)

        compose.assertImageHasDescription(string(R.string.state_loading))
    }

    @Test
    fun anEmptyLookbookExplainsHowToFillIt() {
        showScreen(LookbookUiState.Content(looks = emptyList()))

        compose.assertTextDisplayed(string(R.string.lookbook_empty))
    }

    @Test
    fun aLoadFailureOffersCopyAndARetry() {
        showScreen(LookbookUiState.Error)

        compose.assertTextDisplayed(string(R.string.lookbook_load_failed))
        compose.assertPrimaryActionReachable(string(R.string.action_retry))
        compose.assertControlsAreAccessible()
    }

    @Test
    fun theDeleteDialogIsNamedAndTouchSized() {
        showScreen(LookbookUiState.Content(looks = looks, pendingDeletion = looks.single()))

        compose.assertTextDisplayed(string(R.string.delete_lookbook_title))
        compose.assertTextDisplayed(string(R.string.action_cancel))
        compose.assertControlsAreAccessible()
    }

    @Test
    fun aDeleteFailureIsCopyNotAnException() {
        showScreen(LookbookUiState.Content(looks = looks, deleteFailed = true))

        compose.assertTextDisplayed(string(R.string.lookbook_delete_failed))
        compose.assertControlsAreAccessible()
    }

    @Test
    fun theFavoriteActionStillReportsTheCardItWasTappedOn() {
        var toggled: LookCard? = null
        showScreen(
            LookbookUiState.Content(looks = looks),
            onToggleFavorite = { toggled = it },
        )

        compose.onNode(
            hasText(string(R.string.action_favorite)) and
                hasContentDescription(string(R.string.action_favorite_look_named, "Mar 9, 2024")),
        ).performScrollTo().performClick()

        assertEquals("look-1", toggled?.outfit?.id)
    }
}
