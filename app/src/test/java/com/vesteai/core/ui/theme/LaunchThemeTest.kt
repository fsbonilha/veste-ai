package com.vesteai.core.ui.theme

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.AdaptiveIconDrawable
import android.util.TypedValue
import com.vesteai.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The launch theme and the launcher icon are manifest wiring, so they are checked against the
 * merged resources rather than through Compose. Both are what lint's missing-icon and
 * missing-theme checks look at.
 */
@RunWith(RobolectricTestRunner::class)
class LaunchThemeTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun theApplicationUsesTheVesteLaunchTheme() {
        val info = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA,
        )

        assertEquals("Theme.VesteAI", context.resources.getResourceEntryName(info.theme))
    }

    @Test
    fun theLaunchThemePaintsAWindowBackground() {
        val theme = context.resources.newTheme()
        theme.applyStyle(R.style.Theme_VesteAI, true)
        val value = TypedValue()

        assertTrue(theme.resolveAttribute(android.R.attr.windowBackground, value, true))
        assertNotNull(context.getDrawable(value.resourceId))
    }

    @Test
    fun theLauncherIconIsAnAdaptiveIcon() {
        val info = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA,
        )

        assertTrue(context.getDrawable(info.icon) is AdaptiveIconDrawable)
    }

    @Test
    @Config(qualifiers = "night")
    fun theNightLaunchThemePaintsADarkWindowBackground() {
        val theme = context.resources.newTheme()
        theme.applyStyle(R.style.Theme_VesteAI, true)
        val value = TypedValue()

        assertTrue(theme.resolveAttribute(android.R.attr.windowBackground, value, true))
        assertEquals(
            context.getColor(R.color.window_background),
            context.getColor(value.resourceId),
        )
    }
}
