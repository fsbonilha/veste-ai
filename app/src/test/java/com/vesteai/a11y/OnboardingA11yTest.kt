package com.vesteai.a11y

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.vesteai.R
import com.vesteai.domain.repository.ProfileRepository
import com.vesteai.ui.onboarding.BaselineCaptureScreen
import com.vesteai.ui.onboarding.BaselineCaptureViewModel
import com.vesteai.ui.onboarding.WelcomeCarouselScreen
import java.io.File
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** TalkBack and touch-target audit for onboarding (plan §7, §8 M8). */
@RunWith(RobolectricTestRunner::class)
class OnboardingA11yTest {

    @get:Rule
    val compose = createComposeRule()

    private val resources = RuntimeEnvironment.getApplication().resources

    private fun string(id: Int, vararg args: Any) = resources.getString(id, *args)

    @Test
    fun theCarouselNamesItsPositionSoThePageChangeIsAnnounced() {
        compose.setContent { WelcomeCarouselScreen(onFinished = {}) }

        compose.onNodeWithText(string(R.string.onboarding_page_indicator, 1, 3))
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun thePositionAdvancesWithTheCarousel() {
        compose.setContent { WelcomeCarouselScreen(onFinished = {}) }

        compose.onNodeWithText(string(R.string.onboarding_next)).performClick()

        compose.onNodeWithText(string(R.string.onboarding_page_indicator, 2, 3))
            .assertIsDisplayed()
    }

    @Test
    fun theProgressDotsAreNotAnnouncedAsThreeUnnamedControls() {
        compose.setContent { WelcomeCarouselScreen(onFinished = {}) }

        // The dots are decoration. Announcements come from the position text, and nothing
        // clickable is left without a name.
        compose.assertControlsAreAccessible()
    }

    @Test
    fun skipAndNextStayReachableAndTouchSized() {
        compose.setContent { WelcomeCarouselScreen(onFinished = {}) }

        compose.assertPrimaryActionReachable(string(R.string.onboarding_skip))
        compose.assertPrimaryActionReachable(string(R.string.onboarding_next))
    }

    @Test
    fun theLastPagePrimaryActionIsGetStarted() {
        compose.setContent { WelcomeCarouselScreen(onFinished = {}) }

        compose.onNodeWithText(string(R.string.onboarding_next)).performClick()
        compose.onNodeWithText(string(R.string.onboarding_next)).performClick()

        compose.assertPrimaryActionReachable(string(R.string.onboarding_get_started))
    }

    @Test
    fun theBaselineCameraControlsAreNamedAndTouchSized() {
        val context = RuntimeEnvironment.getApplication()
        val viewModel = BaselineCaptureViewModel(
            imageImporter = imageImporter(context),
            profileRepository = NoProfile(),
            clock = Clock.systemUTC(),
        )
        compose.setContent { BaselineCaptureScreen(onSaved = {}, viewModel = viewModel) }

        compose.assertPrimaryActionReachable(string(R.string.baseline_capture))
        compose.assertPrimaryActionReachable(string(R.string.baseline_pick))
        compose.assertControlsAreAccessible()
    }

    @Test
    fun beingDeniedTheCameraIsCopyThatStillOffersTheGallery() {
        val context = RuntimeEnvironment.getApplication()
        val viewModel = BaselineCaptureViewModel(
            imageImporter = imageImporter(context),
            profileRepository = NoProfile(),
            clock = Clock.systemUTC(),
        )
        compose.setContent { BaselineCaptureScreen(onSaved = {}, viewModel = viewModel) }

        // There is no camera in this environment, so the preview must explain itself in copy
        // instead of leaving a black rectangle, and the gallery fallback has to stay tappable.
        compose.assertTextDisplayed(string(R.string.baseline_camera_not_allowed))
        compose.assertPrimaryActionReachable(string(R.string.baseline_pick))
    }

    private fun imageImporter(context: android.content.Context) =
        com.vesteai.core.media.ImageImporter(
            context,
            com.vesteai.core.media.ImageStore(File(context.filesDir, "a11y-store")),
            com.vesteai.core.media.ImageNormalizer(),
            Dispatchers.Main,
        )

    private class NoProfile : ProfileRepository {
        override fun observeOnboardingComplete(): Flow<Boolean> = flowOf(false)

        override fun observeBaselinePresent(): Flow<Boolean> = flowOf(false)

        override fun observeBaselineSavedAtMillis(): Flow<Long?> = flowOf(null)

        override suspend fun completeOnboarding() = Unit

        override suspend fun markBaselineSaved(savedAtMillis: Long) = Unit

        override suspend fun reset() = Unit
    }
}
