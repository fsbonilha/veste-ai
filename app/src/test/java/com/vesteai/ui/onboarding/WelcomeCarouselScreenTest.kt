package com.vesteai.ui.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.vesteai.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class WelcomeCarouselScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val resources = RuntimeEnvironment.getApplication().resources

    @Test
    fun nextAndSkipAreVisibleOnTheFirstPage() {
        compose.setContent { WelcomeCarouselScreen(onFinished = {}) }

        compose.onNodeWithText(resources.getString(R.string.onboarding_next)).assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.onboarding_skip)).assertIsDisplayed()
    }

    @Test
    fun nextAdvancesToTheSecondPage() {
        compose.setContent { WelcomeCarouselScreen(onFinished = {}) }

        compose.onNodeWithText(resources.getString(R.string.onboarding_next)).performClick()

        compose.onNodeWithText(resources.getString(R.string.onboarding_welcome_title_2))
            .assertIsDisplayed()
    }

    @Test
    fun lastPageShowsGetStartedAndFinishesOnClick() {
        var finished = 0
        compose.setContent { WelcomeCarouselScreen(onFinished = { finished++ }) }

        compose.onNodeWithText(resources.getString(R.string.onboarding_next)).performClick()
        compose.onNodeWithText(resources.getString(R.string.onboarding_next)).performClick()
        compose.onNodeWithText(resources.getString(R.string.onboarding_get_started)).performClick()

        assertEquals(1, finished)
    }

    @Test
    fun skipFinishesImmediately() {
        var finished = 0
        compose.setContent { WelcomeCarouselScreen(onFinished = { finished++ }) }

        compose.onNodeWithText(resources.getString(R.string.onboarding_skip)).performClick()

        assertEquals(1, finished)
    }
}
