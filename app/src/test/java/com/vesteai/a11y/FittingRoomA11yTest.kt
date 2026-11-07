package com.vesteai.a11y

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.vesteai.R
import com.vesteai.core.error.AppError
import com.vesteai.domain.fitting.FittingRoomSelection
import com.vesteai.domain.model.Garment
import com.vesteai.domain.model.GarmentCategory
import com.vesteai.feature.fittingroom.FittingRoomScreen
import com.vesteai.feature.fittingroom.FittingRoomUiState
import com.vesteai.feature.fittingroom.TryOnResultUiState
import com.vesteai.feature.fittingroom.TryOnStage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * TalkBack and touch-target audit for the fitting room (plan §7, §8 M8): every image is described,
 * every control is named, and the primary action stays on screen with its label intact.
 */
@RunWith(RobolectricTestRunner::class)
class FittingRoomA11yTest {

    @get:Rule
    val compose = createComposeRule()

    private val resources = RuntimeEnvironment.getApplication().resources
    private val baseline = tempImage("baseline")
    private val garmentImage = tempImage("top")

    private val top = Garment("t1", "wardrobe/t1.jpg", GarmentCategory.TOP, 0L, isFavorite = false)

    private fun showScreen(
        state: FittingRoomUiState = loadedState(),
        baselineFile: File? = baseline,
        onToggleGarment: (Garment) -> Unit = {},
    ) {
        compose.setContent {
            FittingRoomScreen(
                state = state,
                baselineFile = baselineFile,
                imageFile = { garmentImage },
                onToggleGarment = onToggleGarment,
                onTryOn = {},
                onCancelTryOn = {},
                onDismissTryOnError = {},
                onRetry = {},
                onSaveToLookbook = {},
                onDismissSaveFailure = {},
                onDismissShareFailure = {},
                onShareResult = {},
                onDiscardResult = {},
            )
        }
    }

    private fun loadedState(
        selection: FittingRoomSelection = FittingRoomSelection.Empty,
        tryOnStage: TryOnStage = TryOnStage.Idle,
        result: TryOnResultUiState? = null,
    ) = FittingRoomUiState(
        baselinePresent = true,
        garmentsByCategory = mapOf(GarmentCategory.TOP to listOf(top)),
        unsupportedCategories = setOf(GarmentCategory.HEADWEAR, GarmentCategory.FOOTWEAR),
        selection = selection,
        tryOnStage = tryOnStage,
        result = result,
    )

    private fun string(id: Int, vararg args: Any) = resources.getString(id, *args)

    @Test
    fun theBaselinePhotoIsDescribed() {
        showScreen()

        compose.assertImageHasDescription(string(R.string.fitting_room_baseline_description))
    }

    @Test
    fun theTryOnActionAndTheDrawerActionAreBothNamedAndTouchSized() {
        showScreen(loadedState(selection = FittingRoomSelection.Empty.toggle(top)))

        compose.assertPrimaryActionReachable(string(R.string.action_try_on))
        compose.assertPrimaryActionReachable(string(R.string.fitting_room_open_drawer))
        compose.assertControlsAreAccessible()
    }

    @Test
    fun aSelectedChipIsAnnouncedAsRemovableRatherThanAsABareCategory() {
        showScreen(loadedState(selection = FittingRoomSelection.Empty.toggle(top)))

        compose.onNodeWithText(string(R.string.category_top))
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(
                SemanticsProperties.ContentDescription,
                listOf(string(R.string.action_remove_selection, string(R.string.category_top))),
            ))
    }

    @Test
    fun theDrawerRowsAreNamedAndTappable() {
        var toggled: Garment? = null
        showScreen(onToggleGarment = { toggled = it })

        compose.onNodeWithText(string(R.string.fitting_room_open_drawer)).performClick()
        // The tab row and the garment row both say "Top"; the garment row is the one with a click
        // action, and it has to be tall enough to hit.
        compose.onAllNodesWithText(string(R.string.category_top))[1]
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals(top, toggled)
        compose.assertControlsAreAccessible()
    }

    @Test
    fun theDrawerThumbnailIsDecorativeBecauseTheRowNameCarriesTheMeaning() {
        showScreen()

        compose.onNodeWithText(string(R.string.fitting_room_open_drawer)).performClick()

        // Exactly one "Top" thumbnail in the drawer is silent, and the row button says "Top".
        compose.onAllNodesWithContentDescription(string(R.string.category_top))
            .fetchSemanticsNodes()
            .let { assertEquals(0, it.size) }
    }

    @Test
    fun theLoadingSpinnerIsAnnouncedRatherThanSilent() {
        showScreen(FittingRoomUiState(loading = true))

        compose.onAllNodesWithContentDescription(string(R.string.state_loading))
            .fetchSemanticsNodes()
            .let { assertEquals(1, it.size) }
    }

    @Test
    fun theLoadFailureIsCopyWithARetryNotAnException() {
        showScreen(FittingRoomUiState(loadFailed = true))

        compose.assertTextDisplayed(string(R.string.fitting_room_load_failed))
        compose.assertTextDisplayed(string(R.string.action_retry))
        compose.assertControlsAreAccessible()
    }

    @Test
    fun anEmptyWardrobeExplainsItselfOnTheCanvas() {
        showScreen(FittingRoomUiState(baselinePresent = true, garmentsByCategory = emptyMap()))

        compose.assertTextDisplayed(string(R.string.fitting_room_no_garments))
    }

    @Test
    fun aMissingBaselineExplainsItselfOnTheCanvas() {
        showScreen(loadedState(), baselineFile = null)

        compose.assertTextDisplayed(string(R.string.fitting_room_no_baseline))
    }

    @Test
    fun aFailedTryOnShowsTheMappedMessageAndItsDismissIsTouchSized() {
        showScreen(loadedState(tryOnStage = TryOnStage.Failed(AppError.BodyNotDetected)))

        compose.assertTextDisplayed(string(R.string.error_vton_body_not_detected))
        compose.assertControlsAreAccessible()
    }

    @Test
    fun inFlightProgressKeepsCancelNamedAndTouchSized() {
        showScreen(
            loadedState(
                selection = FittingRoomSelection.Empty.toggle(top),
                tryOnStage = TryOnStage.Processing,
            ),
        )

        compose.assertPrimaryActionReachable(string(R.string.action_cancel))
        compose.assertTextDisplayed(string(R.string.tryon_stage_processing))
        compose.assertControlsAreAccessible()
    }

    @Test
    fun theResultIsDescribedAndItsActionsAreNamedAndTouchSized() {
        showScreen(
            loadedState(
                tryOnStage = TryOnStage.Completed,
                result = TryOnResultUiState("looks/r1.jpg", setOf("t1")),
            ),
        )

        compose.assertImageHasDescription(string(R.string.tryon_result_description))
        compose.assertPrimaryActionReachable(string(R.string.action_save_to_lookbook))
        compose.assertPrimaryActionReachable(string(R.string.action_share))
        compose.assertPrimaryActionReachable(string(R.string.action_discard))
        compose.assertControlsAreAccessible()
    }

    @Test
    fun theResultHeadingAnnouncesItselfWhenTheSheetOpens() {
        showScreen(
            loadedState(
                tryOnStage = TryOnStage.Completed,
                result = TryOnResultUiState("looks/r1.jpg", setOf("t1")),
            ),
        )

        // The sheet opens without a focus change, so the heading has to be a live region for a
        // screen reader to read it at all.
        compose.onNodeWithText(string(R.string.tryon_stage_completed))
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )
    }

    @Test
    fun everyClickableThingInTheResultSheetReachesTheTouchTarget() {
        showScreen(
            loadedState(
                tryOnStage = TryOnStage.Completed,
                result = TryOnResultUiState("looks/r1.jpg", setOf("t1")),
            ),
        )

        val small = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .filter { !it.boundsInRoot.isEmpty }
            .filter { it.touchBoundsInRoot.width < 48f || it.touchBoundsInRoot.height < 48f }

        assertEquals(emptyList<String>(), small.map { A11y.describe(it) })
    }
}
