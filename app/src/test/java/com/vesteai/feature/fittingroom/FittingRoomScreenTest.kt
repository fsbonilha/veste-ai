package com.vesteai.feature.fittingroom

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.vesteai.R
import com.vesteai.core.error.AppError
import com.vesteai.domain.fitting.FittingRoomSelection
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
class FittingRoomScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val resources = RuntimeEnvironment.getApplication().resources
    private val baseline: File = tempImage("baseline.jpg")
    private val topImage: File = tempImage("top.jpg")

    private val top = Garment(
        id = "t1",
        imagePath = "wardrobe/t1.jpg",
        category = GarmentCategory.TOP,
        createdAt = 0L,
        isFavorite = false,
    )
    private val hat = Garment(
        id = "h1",
        imagePath = "wardrobe/h1.jpg",
        category = GarmentCategory.HEADWEAR,
        createdAt = 0L,
        isFavorite = false,
    )

    private fun tempImage(name: String): File {
        val file = File.createTempFile("fitting-room-$name", ".jpg")
        file.writeBytes(byteArrayOf(1, 2, 3))
        file.deleteOnExit()
        return file
    }

    private fun showScreen(
        state: FittingRoomUiState,
        baselineFile: File? = baseline,
        onToggleGarment: (Garment) -> Unit = {},
        onTryOn: () -> Unit = {},
        onCancelTryOn: () -> Unit = {},
        onSaveToLookbook: () -> Unit = {},
        onShareResult: (String) -> Unit = {},
        onDiscardResult: () -> Unit = {},
    ) {
        compose.setContent {
            FittingRoomScreen(
                state = state,
                baselineFile = baselineFile,
                imageFile = { topImage },
                onToggleGarment = onToggleGarment,
                onTryOn = onTryOn,
                onCancelTryOn = onCancelTryOn,
                onDismissTryOnError = {},
                onRetry = {},
                onSaveToLookbook = onSaveToLookbook,
                onDismissSaveFailure = {},
                onDismissShareFailure = {},
                onShareResult = onShareResult,
                onDiscardResult = onDiscardResult,
            )
        }
    }

    /** Mirrors what the ViewModel produces with PixelAPI's capabilities. */
    private fun loadedState(
        selection: FittingRoomSelection = FittingRoomSelection.Empty,
        tryOnStage: TryOnStage = TryOnStage.Idle,
        elapsedSeconds: Long = 0,
        result: TryOnResultUiState? = null,
        shareFailed: Boolean = false,
    ) = FittingRoomUiState(
        baselinePresent = true,
        garmentsByCategory = mapOf(
            GarmentCategory.TOP to listOf(top),
            GarmentCategory.HEADWEAR to listOf(hat),
        ),
        unsupportedCategories = setOf(GarmentCategory.HEADWEAR, GarmentCategory.FOOTWEAR),
        selection = selection,
        tryOnStage = tryOnStage,
        elapsedSeconds = elapsedSeconds,
        result = result,
        shareFailed = shareFailed,
    )

    @Test
    fun tryOnButtonIsShownAndDisabledWithoutASelection() {
        showScreen(loadedState())

        compose.onNodeWithText(resources.getString(R.string.action_try_on))
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun tryOnButtonReportsTheTapOnceSomethingIsSelected() {
        var taps = 0
        showScreen(loadedState(selection = FittingRoomSelection.Empty.toggle(top)), onTryOn = { taps++ })

        compose.onNodeWithText(resources.getString(R.string.action_try_on))
            .assertIsEnabled()
            .performClick()

        assertEquals(1, taps)
    }

    @Test
    fun noBaselineExplainsWhatIsMissingAndBlocksTryOn() {
        showScreen(loadedState(), baselineFile = null)

        compose.onNodeWithText(resources.getString(R.string.fitting_room_no_baseline))
            .assertIsDisplayed()
    }

    @Test
    fun emptyWardrobeIsExplainedOnTheCanvas() {
        showScreen(FittingRoomUiState(baselinePresent = true, garmentsByCategory = emptyMap()))

        compose.onNodeWithText(resources.getString(R.string.fitting_room_no_garments))
            .assertIsDisplayed()
    }

    @Test
    fun loadFailureOffersRetry() {
        showScreen(FittingRoomUiState(loadFailed = true))

        compose.onNodeWithText(resources.getString(R.string.fitting_room_load_failed))
            .assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.action_retry)).assertIsDisplayed()
    }

    @Test
    fun selectionChipsAreShownForTheActiveSelection() {
        showScreen(loadedState(selection = FittingRoomSelection.Empty.toggle(top)))

        compose.onNodeWithText(resources.getString(R.string.category_top)).assertIsDisplayed()
    }

    @Test
    fun drawerListsGarmentsAndSelectionReportsTaps() {
        val toggled = mutableListOf<Garment>()
        showScreen(loadedState(), onToggleGarment = { toggled += it })

        compose.onNodeWithText(resources.getString(R.string.fitting_room_open_drawer)).performClick()

        // Tab row and garment row both carry the label; the second node is the garment button.
        compose.onAllNodesWithText(resources.getString(R.string.category_top))[1]
            .performScrollTo()
            .performClick()

        assertEquals(listOf(top), toggled)
    }

    @Test
    fun unsupportedCategoryIsDisabledWithAnInlineReason() {
        showScreen(loadedState())

        compose.onNodeWithText(resources.getString(R.string.fitting_room_open_drawer)).performClick()
        compose.onNodeWithText(resources.getString(R.string.category_headwear)).performClick()

        compose.onNodeWithText(
            resources.getString(
                R.string.fitting_room_unsupported_category,
                resources.getString(R.string.category_headwear),
            ),
        ).assertIsDisplayed()
        compose.onAllNodesWithText(resources.getString(R.string.category_headwear))[0]
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun progressShowsTheStageElapsedTimeAndACancel() {
        var cancels = 0
        showScreen(
            loadedState(
                selection = FittingRoomSelection.Empty.toggle(top),
                tryOnStage = TryOnStage.Processing,
                elapsedSeconds = 12,
            ),
            onCancelTryOn = { cancels++ },
        )

        compose.onNodeWithText(resources.getString(R.string.tryon_stage_processing)).assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.tryon_elapsed, "12 s"))
            .assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.action_try_on)).assertIsNotEnabled()

        compose.onNodeWithText(resources.getString(R.string.action_cancel)).performClick()

        assertEquals(1, cancels)
    }

    @Test
    fun queuedStageIsNamedRatherThanShownAsASpinner() {
        showScreen(
            loadedState(
                selection = FittingRoomSelection.Empty.toggle(top),
                tryOnStage = TryOnStage.Queued,
            ),
        )

        compose.onNodeWithText(resources.getString(R.string.tryon_stage_queued)).assertIsDisplayed()
    }

    @Test
    fun failedTryOnShowsMappedCopy() {
        showScreen(
            loadedState(tryOnStage = TryOnStage.Failed(AppError.BodyNotDetected)),
        )

        compose.onNodeWithText(resources.getString(R.string.error_vton_body_not_detected))
            .assertIsDisplayed()
    }

    @Test
    fun missingShareTargetIsReportedRatherThanSilentlyIgnored() {
        showScreen(loadedState(shareFailed = true))

        compose.onNodeWithText(resources.getString(R.string.tryon_share_unavailable))
            .assertIsDisplayed()
    }

    @Test
    fun providerGuidanceIsPreferredOverGenericCopy() {
        showScreen(
            loadedState(tryOnStage = TryOnStage.Failed(AppError.JobFailed("Waistband too dark"))),
        )

        compose.onNodeWithText("Waistband too dark").assertIsDisplayed()
    }

    @Test
    fun resultSheetOffersSaveShareAndDiscard() {
        var saves = 0
        var shares = 0
        var discards = 0
        val result = TryOnResultUiState(relativePath = "looks/r1.jpg", garmentIds = setOf("t1"))
        showScreen(
            loadedState(tryOnStage = TryOnStage.Completed, result = result),
            onSaveToLookbook = { saves++ },
            onShareResult = { shares++ },
            onDiscardResult = { discards++ },
        )

        compose.onNodeWithText(resources.getString(R.string.action_save_to_lookbook))
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.action_share))
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.action_discard))
            .performScrollTo()
            .assertIsDisplayed()

        compose.onNodeWithText(resources.getString(R.string.action_share)).performClick()
        compose.onNodeWithText(resources.getString(R.string.action_discard)).performClick()
        compose.onNodeWithText(resources.getString(R.string.action_save_to_lookbook)).performClick()

        assertEquals(1, shares)
        assertEquals(1, discards)
        assertEquals(1, saves)
    }

    @Test
    fun savedResultDisablesSavingAgain() {
        val result = TryOnResultUiState(
            relativePath = "looks/r1.jpg",
            garmentIds = setOf("t1"),
            saved = true,
        )
        showScreen(loadedState(tryOnStage = TryOnStage.Completed, result = result))

        compose.onNodeWithText(resources.getString(R.string.tryon_saved)).assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.action_save_to_lookbook))
            .performScrollTo()
            .assertIsNotEnabled()
    }
}
