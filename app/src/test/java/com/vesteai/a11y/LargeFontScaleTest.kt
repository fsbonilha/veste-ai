package com.vesteai.a11y

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.vesteai.R
import com.vesteai.core.credentials.CredentialState
import com.vesteai.domain.fitting.FittingRoomSelection
import com.vesteai.domain.model.Garment
import com.vesteai.domain.model.GarmentCategory
import com.vesteai.domain.model.Outfit
import com.vesteai.feature.fittingroom.FittingRoomScreen
import com.vesteai.feature.fittingroom.FittingRoomUiState
import com.vesteai.feature.lookbook.LookCard
import com.vesteai.feature.lookbook.LookbookScreen
import com.vesteai.feature.lookbook.LookbookUiState
import com.vesteai.feature.settings.SettingsScreen
import com.vesteai.feature.settings.SettingsUiState
import com.vesteai.feature.wardrobe.DraftStep
import com.vesteai.feature.wardrobe.DraftUiState
import com.vesteai.feature.wardrobe.GarmentDraftScreen
import com.vesteai.feature.wardrobe.WardrobeScreen
import com.vesteai.feature.wardrobe.WardrobeUiState
import com.vesteai.ui.onboarding.WelcomeCarouselScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The other half of the M8 pass: no fixed height may clip or hide an action at a large font scale.
 * Android's accessibility guidance tops out at 2x; Android 14 adds up to 200% non-linear scaling,
 * so 2x is the scale this app has to survive.
 */
@RunWith(RobolectricTestRunner::class)
class LargeFontScaleTest {

    @get:Rule
    val compose = createComposeRule()

    private val resources = RuntimeEnvironment.getApplication().resources
    private val image = tempImage("scale")
    private val top = Garment("t1", "wardrobe/t1.jpg", GarmentCategory.TOP, 0L, false)

    private fun showAtAccessibilityScale(content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, ACCESSIBILITY_FONT_SCALE),
                content = content,
            )
        }
    }

    private fun string(id: Int, vararg args: Any) = resources.getString(id, *args)

    @Test
    fun theWardrobeAddActionsKeepTheirLabelsAtLargeScale() {
        showAtAccessibilityScale {
            WardrobeScreen(
                state = WardrobeUiState.Content(filter = null, garments = listOf(top)),
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

        compose.assertPrimaryActionReachable(string(R.string.action_add_from_gallery))
        compose.assertPrimaryActionReachable(string(R.string.action_add_from_camera))
    }

    @Test
    fun theFittingRoomActionBarKeepsBothLabelsAtLargeScale() {
        showAtAccessibilityScale {
            Box {
                FittingRoomScreen(
                    state = FittingRoomUiState(
                        baselinePresent = true,
                        garmentsByCategory = mapOf(GarmentCategory.TOP to listOf(top)),
                        selection = FittingRoomSelection.Empty.toggle(top),
                    ),
                    baselineFile = null,
                    imageFile = { image },
                    onToggleGarment = {},
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

        compose.assertPrimaryActionReachable(string(R.string.action_try_on))
        compose.assertPrimaryActionReachable(string(R.string.fitting_room_open_drawer))
    }

    @Test
    fun theOnboardingButtonsKeepTheirLabelsAtLargeScale() {
        showAtAccessibilityScale { WelcomeCarouselScreen(onFinished = {}) }

        compose.assertPrimaryActionReachable(string(R.string.onboarding_skip))
        compose.assertPrimaryActionReachable(string(R.string.onboarding_next))
    }

    @Test
    fun theTaggingStepKeepsSaveReachableAtLargeScale() {
        showAtAccessibilityScale {
            Box {
                GarmentDraftScreen(
                    state = DraftUiState(
                        imagePath = "wardrobe/draft.jpg",
                        step = DraftStep.TAGGING,
                        category = GarmentCategory.TOP,
                    ),
                    imageFile = image,
                    onContinue = {},
                    onBack = {},
                    onSelectCategory = {},
                    onSave = {},
                )
            }
        }

        compose.assertPrimaryActionReachable(string(R.string.action_save))
        compose.assertPrimaryActionReachable(string(R.string.action_back))
    }

    @Test
    fun theLookbookReloadActionStaysReachableAtLargeScale() {
        val cards = listOf(
            LookCard(
                Outfit("look-1", "looks/look-1.jpg", 1_710_000_367_000L, false, listOf(top)),
                createdLabel = "Mar 9, 2024",
            ),
        )
        showAtAccessibilityScale {
            Box {
                LookbookScreen(
                    state = LookbookUiState.Content(looks = cards),
                    imageFile = { image },
                    onRetry = {},
                    onToggleFavorite = {},
                    onReload = {},
                    onRequestDelete = {},
                    onConfirmDelete = {},
                    onCancelDelete = {},
                    onDismissDeleteError = {},
                    onCancelJob = {},
                    onRetryJob = {},
                    onSaveJobToLookbook = {},
                    onDiscardJob = {},
                    onReloadJob = {},
                )
            }
        }

        compose.assertPrimaryActionReachable(string(R.string.action_reload_in_fitting_room))
    }

    @Test
    fun theSettingsActionsStayReachableAtLargeScale() {
        showAtAccessibilityScale {
            SettingsScreen(
                state = SettingsUiState(
                    loading = false,
                    credential = CredentialState.Present("****3456", "https://api.pixelapi.dev"),
                    apiKeyInput = "pk_live_abcdef3456",
                    engineName = "Mock (offline)",
                ),
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

        compose.assertPrimaryActionReachable(string(R.string.settings_key_save))
        compose.assertPrimaryActionReachable(string(R.string.settings_clear_data_action))
    }

    private companion object {
        /** Android's maximum accessibility font scale. */
        const val ACCESSIBILITY_FONT_SCALE = 2.0f
    }
}
