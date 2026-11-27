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
        onSelectTab: (FittingRoomTab) -> Unit = {},
    ) {
        compose.setContent {
            FittingRoomScreen(
                state = state,
                baselineFile = baselineFile,
                imageFile = { topImage },
                onToggleGarment = onToggleGarment,
                onSelectTab = onSelectTab,
                onTryOn = onTryOn,
                onDismissTryOnError = {},
                onDismissSavedConfirmation = {},
                onRetry = {},
                onCancelJob = {},
                onRetryJob = {},
                onRetrySaveJob = {},
            )
        }
    }

    /** Mirrors what the ViewModel produces with PixelAPI's capabilities. */
    private fun loadedState(
        selection: FittingRoomSelection = FittingRoomSelection.Empty,
        tryOnStage: TryOnStage = TryOnStage.Idle,
        tab: FittingRoomTab = FittingRoomTab.TryOn,
        savedConfirmation: Boolean = false,
        jobs: List<com.vesteai.domain.vton.PendingJob> = emptyList(),
    ) = FittingRoomUiState(
        baselinePresent = true,
        garmentsByCategory = mapOf(
            GarmentCategory.TOP to listOf(top),
            GarmentCategory.HEADWEAR to listOf(hat),
        ),
        unsupportedCategories = setOf(GarmentCategory.HEADWEAR, GarmentCategory.FOOTWEAR),
        selection = selection,
        tryOnStage = tryOnStage,
        tab = tab,
        savedConfirmation = savedConfirmation,
        jobs = jobs,
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
    fun whileProcessingTheTryOnButtonBecomesAWayIntoOngoing() {
        var opened = 0
        showScreen(
            loadedState(
                selection = FittingRoomSelection.Empty.toggle(top),
                tryOnStage = TryOnStage.Processing,
                jobs = listOf(processingJob()),
            ),
            onSelectTab = { if (it == FittingRoomTab.Ongoing) opened++ },
        )

        // No progress bar on the canvas, and no Try On button to press while one runs.
        compose.onNodeWithText(resources.getString(R.string.tryon_stage_processing))
            .assertDoesNotExist()
        compose.onNodeWithText(resources.getString(R.string.action_try_on)).assertDoesNotExist()

        compose.onNodeWithText(resources.getString(R.string.fitting_room_ongoing_count, 1))
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, opened)
    }

    @Test
    fun aRunningJobAloneAlsoSwapsTheButtonEvenFromIdle() {
        // An active job can outlive the render coroutine that started it, so the button must key
        // off the job count, not just the in-memory stage.
        showScreen(
            loadedState(jobs = listOf(processingJob())),
        )

        compose.onNodeWithText(resources.getString(R.string.fitting_room_ongoing_count, 1))
            .assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.action_try_on)).assertDoesNotExist()
    }

    @Test
    fun withNoJobsTheTryOnButtonIsBack() {
        showScreen(loadedState(selection = FittingRoomSelection.Empty.toggle(top)))

        compose.onNodeWithText(resources.getString(R.string.action_try_on)).assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.fitting_room_ongoing_count, 0))
            .assertDoesNotExist()
    }

    private fun processingJob() = com.vesteai.domain.vton.PendingJob(
        id = "j1",
        providerName = "PixelAPI",
        garmentIds = listOf("t1"),
        baselineImagePath = "baseline/baseline.jpg",
        submittedAt = 0L,
        status = com.vesteai.domain.vton.JobStatus.Processing,
    )

    @Test
    fun failedTryOnShowsMappedCopy() {
        showScreen(
            loadedState(tryOnStage = TryOnStage.Failed(AppError.BodyNotDetected)),
        )

        compose.onNodeWithText(resources.getString(R.string.error_vton_body_not_detected))
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
    fun ongoingTabIsSelectableAndShowsTheEmptyState() {
        var selected: FittingRoomTab? = null
        compose.setContent {
            FittingRoomScreen(
                state = loadedState(),
                baselineFile = baseline,
                imageFile = { topImage },
                onToggleGarment = {},
                onSelectTab = { selected = it },
                onTryOn = {},
                onDismissTryOnError = {},
                onDismissSavedConfirmation = {},
                onRetry = {},
                onCancelJob = {},
                onRetryJob = {},
                onRetrySaveJob = {},
            )
        }

        compose.onNodeWithText(resources.getString(R.string.fitting_room_tab_ongoing)).performClick()

        assertEquals(FittingRoomTab.Ongoing, selected)
    }

    @Test
    fun ongoingTabListsRunningJobsWithACancel() {
        var cancels = 0
        val job = com.vesteai.domain.vton.PendingJob(
            id = "j1",
            providerName = "PixelAPI",
            garmentIds = listOf("t1"),
            baselineImagePath = "baseline/baseline.jpg",
            submittedAt = 0L,
            status = com.vesteai.domain.vton.JobStatus.Processing,
        )
        compose.setContent {
            FittingRoomScreen(
                state = loadedState(tab = FittingRoomTab.Ongoing, jobs = listOf(job)),
                baselineFile = baseline,
                imageFile = { topImage },
                onToggleGarment = {},
                onSelectTab = {},
                onTryOn = {},
                onDismissTryOnError = {},
                onDismissSavedConfirmation = {},
                onRetry = {},
                onCancelJob = { cancels++ },
                onRetryJob = {},
                onRetrySaveJob = {},
            )
        }

        compose.onNodeWithText(resources.getString(R.string.tryon_stage_processing)).assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.action_cancel)).performClick()

        assertEquals(1, cancels)
    }

    @Test
    fun ongoingTabOffersRetryOnAFailedJob() {
        var retries = 0
        val job = com.vesteai.domain.vton.PendingJob(
            id = "j1",
            providerName = "PixelAPI",
            garmentIds = listOf("t1"),
            baselineImagePath = "baseline/baseline.jpg",
            submittedAt = 0L,
            status = com.vesteai.domain.vton.JobStatus.Failed(
                com.vesteai.domain.vton.VtonError.JobFailed(null),
                null,
            ),
        )
        compose.setContent {
            FittingRoomScreen(
                state = loadedState(tab = FittingRoomTab.Ongoing, jobs = listOf(job)),
                baselineFile = baseline,
                imageFile = { topImage },
                onToggleGarment = {},
                onSelectTab = {},
                onTryOn = {},
                onDismissTryOnError = {},
                onDismissSavedConfirmation = {},
                onRetry = {},
                onCancelJob = {},
                onRetryJob = { retries++ },
                onRetrySaveJob = {},
            )
        }

        compose.onNodeWithText(resources.getString(R.string.action_retry)).performClick()

        assertEquals(1, retries)
    }

    @Test
    fun savedConfirmationIsShownAfterAnAutoSave() {
        showScreen(loadedState(savedConfirmation = true))

        compose.onNodeWithText(resources.getString(R.string.tryon_saved)).assertIsDisplayed()
    }
}
