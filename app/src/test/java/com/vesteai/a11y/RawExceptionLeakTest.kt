package com.vesteai.a11y

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.vesteai.R
import com.vesteai.core.error.AppError
import com.vesteai.core.error.messageRes
import com.vesteai.core.error.toAppError
import com.vesteai.domain.fitting.FittingRoomSelection
import com.vesteai.domain.model.Garment
import com.vesteai.domain.model.GarmentCategory
import com.vesteai.domain.vton.VtonError
import com.vesteai.domain.vton.VtonException
import com.vesteai.feature.fittingroom.FittingRoomScreen
import com.vesteai.feature.fittingroom.FittingRoomUiState
import com.vesteai.feature.fittingroom.TryOnStage
import com.vesteai.feature.lookbook.LookbookScreen
import com.vesteai.feature.lookbook.LookbookUiState
import com.vesteai.feature.settings.SettingsScreen
import com.vesteai.feature.settings.SettingsUiState
import com.vesteai.feature.wardrobe.WardrobeScreen
import com.vesteai.feature.wardrobe.WardrobeUiState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Guards plan §7's "never a raw exception string": no screen may render a throwable's own text, and
 * every provider failure arrives as copy from the [AppError] mapper instead.
 *
 * Raw-exception detection looks at the rendered text and content descriptions only, because a
 * printed semantics tree would otherwise match on the app's own package on every node.
 */
@RunWith(RobolectricTestRunner::class)
class RawExceptionLeakTest {

    @get:Rule
    val compose = createComposeRule()

    private val resources = RuntimeEnvironment.getApplication().resources
    private val image = tempImage("leak")

    private val top = Garment("t1", "wardrobe/t1.jpg", GarmentCategory.TOP, 0L, false)

    /** Strings a screen only ever produces by printing a throwable instead of mapping it. */
    private val forbidden = listOf(
        "Exception",
        "Throwable",
        "VtonException",
        "java.lang",
        "kotlin.io",
        "at com.vesteai",
        "SQLiteException",
        "IOException",
        "IllegalStateException",
        "StackTrace",
        "status code",
    )

    private fun assertNoRawExceptionText() {
        val rendered = compose.wordings()
        forbidden.forEach { needle ->
            assertTrue(
                "A screen rendered raw exception text '$needle': $rendered",
                rendered.none { it.contains(needle, ignoreCase = true) },
            )
        }
    }

    @Test
    fun everyVtonFailureReachesTheScreenAsMappedCopy() {
        // One composition, driven by state: setContent may only be called once per test.
        val failure = mutableStateOf<TryOnStage>(TryOnStage.Failed(AppError.Unknown))
        compose.setContent { fittingRoom(failure.value) }

        VtonErrors.all.forEach { vtonError ->
            val appError = vtonError.toAppError()
            compose.runOnIdle { failure.value = TryOnStage.Failed(appError) }

            // The provider's own guidance wins when it exists (plan §7), so it is what the
            // screen must show; otherwise the mapped copy has to appear.
            val expected = vtonError.guidance()
                ?: resources.getString(appError.messageRes())

            compose.assertTextDisplayed(expected)
            assertNoRawExceptionText()
        }
    }

    @Test
    fun aVtonExceptionNeverShowsItsOwnMessage() {
        val exception = VtonException(VtonError.BodyNotDetected)
        val mapped = exception.toAppError()
        assertEquals(AppError.BodyNotDetected, mapped)

        compose.setContent { fittingRoom(TryOnStage.Failed(mapped)) }

        assertEquals(0, compose.onAllNodesWithText(exception.toString()).fetchSemanticsNodes().size)
        assertNoRawExceptionText()
    }

    @Test
    fun theWardrobeFailureCopyIsNotAnException() {
        compose.setContent {
            WardrobeScreen(
                state = WardrobeUiState.Error,
                captureError = null,
                imageFile = { image },
                onFilterSelected = {},
                onRetry = {},
                onToggleFavorite = {},
                onRequestDelete = {},
                onConfirmDelete = {},
                onCancelDelete = {},
                onDismissDeleteError = {},
                onDismissCaptureError = {},
                onAddFromGallery = {},
                onAddFromCamera = {},
            )
        }

        compose.assertTextDisplayed(resources.getString(R.string.wardrobe_load_failed))
        assertNoRawExceptionText()
    }

    @Test
    fun theLookbookFailureCopyIsNotAnException() {
        compose.setContent {
            LookbookScreen(
                state = LookbookUiState.Error,
                imageFile = { image },
                onRetry = {},
                onOpenLook = {},
                onCloseLook = {},
                onShareLook = {},
                onToggleFavorite = {},
                onReload = {},
                onRequestDelete = {},
                onConfirmDelete = {},
                onCancelDelete = {},
                onDismissDeleteError = {},
            )
        }

        compose.assertTextDisplayed(resources.getString(R.string.lookbook_load_failed))
        assertNoRawExceptionText()
    }

    @Test
    fun theSettingsFailureCopyIsNotAnException() {
        compose.setContent {
            SettingsScreen(
                state = SettingsUiState(loading = false, loadFailed = true),
                onApiKeyChanged = {},
                onSaveKey = {},
                onRemoveKey = {},
                onBaseUrlChanged = {},
                onRetakeBaseline = {},
                onRequestClearData = {},
                onConfirmClearData = {},
                onCancelClearData = {},
                onDismissClearDataResult = {},
                onDismissKeyMessage = {},
            )
        }

        compose.assertTextDisplayed(resources.getString(R.string.settings_load_failed))
        assertNoRawExceptionText()
    }

    @Composable
    private fun fittingRoom(stage: TryOnStage) {
        FittingRoomScreen(
            state = FittingRoomUiState(
                baselinePresent = true,
                garmentsByCategory = mapOf(GarmentCategory.TOP to listOf(top)),
                selection = FittingRoomSelection.Empty.toggle(top),
                tryOnStage = stage,
            ),
            baselineFile = null,
            imageFile = { image },
            onToggleGarment = {},
            onSelectTab = {},
            onTryOn = {},
            onDismissTryOnError = {},
            onDismissSavedConfirmation = {},
            onRetry = {},
            onCancelJob = {},
            onRetryJob = {},
            onRetrySaveJob = {},
        )
    }
}

/** The provider's own guidance, which the fitting room shows verbatim when it is present. */
private fun VtonError.guidance(): String? = when (this) {
    is VtonError.TooManyConcurrentJobs -> guidance
    is VtonError.JobFailed -> guidance
    else -> null
}?.takeIf { it.isNotBlank() }

/** Every documented provider failure, so the sweep above cannot silently shrink. */
private object VtonErrors {
    val all = listOf(
        VtonError.NotConfigured,
        VtonError.AuthenticationFailed,
        VtonError.UnsupportedCombination,
        VtonError.NoNetwork,
        VtonError.Timeout,
        VtonError.TooManyConcurrentJobs("Only one job at a time"),
        VtonError.ProcessingBusy(true),
        VtonError.InputTooSmall("120x160"),
        VtonError.WrongAspectRatio,
        VtonError.BodyNotDetected,
        VtonError.InvalidImage,
        VtonError.JobFailed(null),
        VtonError.ServerError(503),
    )
}
