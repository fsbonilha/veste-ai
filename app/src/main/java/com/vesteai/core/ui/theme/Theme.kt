package com.vesteai.core.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * The app's single theme entry point.
 *
 * @param darkTheme which scheme to use; defaults to the system setting.
 * @param dynamicColor use the Android 12+ wallpaper palette when the platform offers one.
 *   Pass `false` to keep the brand palette on every API level.
 * @param colorScheme forces an exact scheme, bypassing both of the above. Used by previews
 *   and by tests that must not depend on the host's system settings.
 */
@Composable
fun VesteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    colorScheme: ColorScheme? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val resolved = colorScheme ?: when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> VesteDarkColorScheme
        else -> VesteLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = context.findActivity()?.window ?: return@SideEffect
            // enableEdgeToEdge() draws behind transparent bars, so the only thing left to
            // decide is whether the bar icons are light or dark. It must follow the scheme
            // actually in use, not the system setting, or an in-app override inverts them.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !resolved.isDark()
                isAppearanceLightNavigationBars = !resolved.isDark()
            }
        }
    }

    MaterialTheme(
        colorScheme = resolved,
        typography = VesteTypography,
        content = content,
    )
}

/** True when the scheme's surface is dark, which is what the system bar icons key off. */
private fun ColorScheme.isDark(): Boolean = surface.luminance() < 0.5f

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
