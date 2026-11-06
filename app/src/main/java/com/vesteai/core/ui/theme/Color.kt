package com.vesteai.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Brand palette. The tonal values follow the Material 3 baseline ramps so that contrast
 * ratios stay inside the WCAG AA bounds Material already validated; only the hue is ours.
 */
internal val VesteViolet80 = Color(0xFFD0BCFF)
internal val VesteViolet40 = Color(0xFF5B4B8A)
internal val VesteViolet90 = Color(0xFFE7DEFF)
internal val VesteViolet10 = Color(0xFF17073B)
internal val VesteViolet30 = Color(0xFF4F378B)

internal val VesteNeutral10 = Color(0xFF1C1B1F)
internal val VesteNeutral20 = Color(0xFF313033)
internal val VesteNeutral90 = Color(0xFFE6E1E5)
internal val VesteNeutral95 = Color(0xFFF4EFF4)
internal val VesteNeutral99 = Color(0xFFFFFBFE)
internal val VesteNeutralVariant30 = Color(0xFF49454F)
internal val VesteNeutralVariant50 = Color(0xFF79747E)
internal val VesteNeutralVariant60 = Color(0xFF938F99)
internal val VesteNeutralVariant80 = Color(0xFFCAC4D0)
internal val VesteNeutralVariant90 = Color(0xFFE7E0EC)

internal val VesteRose40 = Color(0xFF7D5260)
internal val VesteRose80 = Color(0xFFEFB8C8)
internal val VesteRose90 = Color(0xFFFFD8E4)
internal val VesteRose10 = Color(0xFF31111D)
internal val VesteRose30 = Color(0xFF633B48)

internal val VesteError10 = Color(0xFF410E0B)
internal val VesteError20 = Color(0xFF601410)
internal val VesteError30 = Color(0xFF8C1D18)
internal val VesteError40 = Color(0xFFB3261E)
internal val VesteError80 = Color(0xFFF2B8B5)
internal val VesteError90 = Color(0xFFF9DEDC)

val VesteLightColorScheme = lightColorScheme(
    primary = VesteViolet40,
    onPrimary = Color.White,
    primaryContainer = VesteViolet90,
    onPrimaryContainer = VesteViolet10,
    inversePrimary = VesteViolet80,
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = VesteRose40,
    onTertiary = Color.White,
    tertiaryContainer = VesteRose90,
    onTertiaryContainer = VesteRose10,
    error = VesteError40,
    onError = Color.White,
    errorContainer = VesteError90,
    onErrorContainer = VesteError10,
    background = VesteNeutral99,
    onBackground = VesteNeutral10,
    surface = VesteNeutral99,
    onSurface = VesteNeutral10,
    surfaceVariant = VesteNeutralVariant90,
    onSurfaceVariant = VesteNeutralVariant30,
    surfaceTint = VesteViolet40,
    inverseSurface = VesteNeutral20,
    inverseOnSurface = VesteNeutral95,
    outline = VesteNeutralVariant50,
    outlineVariant = VesteNeutralVariant80,
    scrim = Color.Black,
)

val VesteDarkColorScheme = darkColorScheme(
    primary = VesteViolet80,
    onPrimary = Color(0xFF381E72),
    primaryContainer = VesteViolet30,
    onPrimaryContainer = VesteViolet90,
    inversePrimary = VesteViolet40,
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = VesteRose80,
    onTertiary = Color(0xFF492532),
    tertiaryContainer = VesteRose30,
    onTertiaryContainer = VesteRose90,
    error = VesteError80,
    onError = VesteError20,
    errorContainer = VesteError30,
    onErrorContainer = VesteError90,
    background = VesteNeutral10,
    onBackground = VesteNeutral90,
    surface = VesteNeutral10,
    onSurface = VesteNeutral90,
    surfaceVariant = VesteNeutralVariant30,
    onSurfaceVariant = VesteNeutralVariant80,
    surfaceTint = VesteViolet80,
    inverseSurface = VesteNeutral90,
    inverseOnSurface = VesteNeutral20,
    outline = VesteNeutralVariant60,
    outlineVariant = VesteNeutralVariant30,
    scrim = Color.Black,
)
