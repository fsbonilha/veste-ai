package com.vesteai.feature.wardrobe

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.vesteai.R
import com.vesteai.domain.model.GarmentCategory
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class GarmentDraftScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val resources = RuntimeEnvironment.getApplication().resources
    private val imageFile: File = File.createTempFile("draft", ".jpg").apply {
        deleteOnExit()
    }

    private fun showScreen(
        state: DraftUiState,
        onContinue: () -> Unit = {},
        onSave: () -> Unit = {},
    ) {
        compose.setContent {
            GarmentDraftScreen(
                state = state,
                imageFile = imageFile,
                onContinue = onContinue,
                onBack = {},
                onSelectCategory = {},
                onSave = onSave,
            )
        }
    }

    private fun taggingState(category: GarmentCategory? = null) = DraftUiState(
        imagePath = "wardrobe/draft.jpg",
        step = DraftStep.TAGGING,
        category = category,
    )

    @Test
    fun cropHintStepShowsContinue() {
        showScreen(DraftUiState(imagePath = "wardrobe/draft.jpg"))

        compose.onNodeWithText(resources.getString(R.string.action_continue))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun taggingStepShowsEveryCategoryAndSave() {
        showScreen(taggingState())

        compose.onNodeWithText(resources.getString(R.string.category_headwear))
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.category_footwear))
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.action_save))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun saveIsReachableAfterChoosingACategory() {
        showScreen(taggingState(category = GarmentCategory.TOP))

        compose.onNodeWithText(resources.getString(R.string.action_save))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun continueReportsTheTap() {
        var taps = 0
        showScreen(DraftUiState(imagePath = "wardrobe/draft.jpg"), onContinue = { taps++ })

        compose.onNodeWithText(resources.getString(R.string.action_continue)).performClick()

        assertEquals(1, taps)
    }
}
