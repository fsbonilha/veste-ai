package com.vesteai.a11y

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.vesteai.R
import com.vesteai.domain.model.GarmentCategory
import com.vesteai.feature.wardrobe.DraftStep
import com.vesteai.feature.wardrobe.DraftUiState
import com.vesteai.feature.wardrobe.GarmentDraftScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** TalkBack and touch-target audit for the garment capture and tagging steps (plan §8 M8). */
@RunWith(RobolectricTestRunner::class)
class GarmentDraftA11yTest {

    @get:Rule
    val compose = createComposeRule()

    private val resources = RuntimeEnvironment.getApplication().resources
    private val image = tempImage("draft")

    private fun showScreen(state: DraftUiState) {
        compose.setContent {
            GarmentDraftScreen(
                state = state,
                imageFile = image,
                onContinue = {},
                onBack = {},
                onSelectCategory = {},
                onSave = {},
            )
        }
    }

    private fun string(id: Int, vararg args: Any) = resources.getString(id, *args)

    @Test
    fun theGarmentPhotoIsDescribed() {
        showScreen(DraftUiState(imagePath = "wardrobe/draft.jpg"))

        compose.assertImageHasDescription(string(R.string.draft_image_description))
    }

    @Test
    fun theCropHintPrimaryActionIsReachableAndTouchSized() {
        showScreen(DraftUiState(imagePath = "wardrobe/draft.jpg"))

        compose.assertPrimaryActionReachable(string(R.string.action_continue))
        compose.assertControlsAreAccessible()
    }

    @Test
    fun everyCategoryChoiceIsNamedAndTouchSized() {
        showScreen(
            DraftUiState(
                imagePath = "wardrobe/draft.jpg",
                step = DraftStep.TAGGING,
            ),
        )

        GarmentCategory.entries.forEach { category ->
            compose.onNodeWithText(resources.getString(categoryId(category)))
                .performScrollTo()
                .assertIsDisplayed()
        }
        compose.assertControlsAreAccessible()
    }

    @Test
    fun theChosenCategoryIsExposedAsSelectedRatherThanOnlyAsAColour() {
        showScreen(
            DraftUiState(
                imagePath = "wardrobe/draft.jpg",
                step = DraftStep.TAGGING,
                category = GarmentCategory.TOP,
            ),
        )

        compose.onNodeWithText(string(R.string.category_top))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        compose.onNodeWithText(string(R.string.category_bottom))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
    }

    @Test
    fun savingIsThePrimaryActionAndStaysReachable() {
        showScreen(
            DraftUiState(
                imagePath = "wardrobe/draft.jpg",
                step = DraftStep.TAGGING,
                category = GarmentCategory.TOP,
            ),
        )

        compose.assertPrimaryActionReachable(string(R.string.action_save))
        compose.assertPrimaryActionReachable(string(R.string.action_back))
    }

    @Test
    fun aFailedSaveIsCopyRatherThanSilence() {
        showScreen(
            DraftUiState(
                imagePath = "wardrobe/draft.jpg",
                step = DraftStep.TAGGING,
                category = GarmentCategory.TOP,
                saveFailed = true,
            ),
        )

        compose.assertTextReachable(string(R.string.garment_save_failed))
        // Every category, the save button and back are all still reachable with the error shown.
        compose.onNodeWithText(string(R.string.action_save)).performScrollTo().assertIsDisplayed()
        compose.assertControlsAreAccessible()
    }

    private fun categoryId(category: GarmentCategory): Int = when (category) {
        GarmentCategory.TOP -> R.string.category_top
        GarmentCategory.BOTTOM -> R.string.category_bottom
        GarmentCategory.FULL_BODY -> R.string.category_full_body
        GarmentCategory.FOOTWEAR -> R.string.category_footwear
        GarmentCategory.HEADWEAR -> R.string.category_headwear
    }
}
