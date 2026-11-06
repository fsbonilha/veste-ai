package com.vesteai.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.vesteai.R
import com.vesteai.ui.onboarding.WelcomeCarouselScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The theme must render in both modes regardless of the host's system setting, so every test
 * here passes an explicit [ColorScheme] and never reads `isSystemInDarkTheme()`.
 */
@RunWith(RobolectricTestRunner::class)
class VesteThemeTest {

    @get:Rule
    val compose = createComposeRule()

    private val resources = RuntimeEnvironment.getApplication().resources

    @Test
    fun lightSchemeRendersTheOnboardingScreen() {
        compose.setContent {
            VesteTheme(colorScheme = VesteLightColorScheme) {
                Surface { WelcomeCarouselScreen(onFinished = {}) }
            }
        }

        compose.onNodeWithText(resources.getString(R.string.onboarding_welcome_title_1))
            .assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.onboarding_next))
            .assertIsDisplayed()
    }

    @Test
    fun darkSchemeRendersTheOnboardingScreen() {
        compose.setContent {
            VesteTheme(colorScheme = VesteDarkColorScheme) {
                Surface { WelcomeCarouselScreen(onFinished = {}) }
            }
        }

        compose.onNodeWithText(resources.getString(R.string.onboarding_welcome_title_1))
            .assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.onboarding_next))
            .assertIsDisplayed()
    }

    @Test
    fun theGivenSchemeReachesMaterialTheme() {
        var seen: ColorScheme? = null
        compose.setContent {
            VesteTheme(colorScheme = VesteDarkColorScheme) {
                seen = MaterialTheme.colorScheme
                Text("probe")
            }
        }

        compose.onNodeWithText("probe").assertIsDisplayed()
        assertEquals(VesteDarkColorScheme, seen)
    }

    /** A night-qualified host must not override an explicitly requested light scheme. */
    @Test
    @Config(qualifiers = "night")
    fun explicitLightSchemeWinsOverANightSystemSetting() {
        var seen: ColorScheme? = null
        compose.setContent {
            VesteTheme(colorScheme = VesteLightColorScheme) {
                seen = MaterialTheme.colorScheme
                Text("probe")
            }
        }

        compose.onNodeWithText("probe").assertIsDisplayed()
        assertEquals(VesteLightColorScheme, seen)
    }

    @Test
    fun lightAndDarkSchemesDifferOnSurfaceAndOnSurface() {
        assertNotEquals(VesteLightColorScheme.surface, VesteDarkColorScheme.surface)
        assertNotEquals(VesteLightColorScheme.onSurface, VesteDarkColorScheme.onSurface)
    }
}
