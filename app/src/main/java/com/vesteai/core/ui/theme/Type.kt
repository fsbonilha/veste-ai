package com.vesteai.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Baseline = Typography()

/**
 * Material 3's default scale with the weights and line heights this app's screens were
 * laid out against: headings carry a little more weight, and body copy gets the extra
 * leading the long privacy and permission paragraphs need.
 */
val VesteTypography = Typography(
    headlineMedium = Baseline.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = Baseline.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Baseline.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Baseline.titleMedium.copy(fontWeight = FontWeight.Medium),
    bodyLarge = Baseline.bodyLarge.copy(lineHeight = 24.sp),
    bodyMedium = Baseline.bodyMedium.copy(lineHeight = 20.sp),
    labelLarge = Baseline.labelLarge.copy(fontWeight = FontWeight.Medium),
)
