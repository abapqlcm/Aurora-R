package com.aurora.r.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AuroraColors = darkColorScheme(
    primary = AuroraGold,
    onPrimary = AuroraBlack,
    secondary = AuroraGoldBright,
    onSecondary = AuroraBlack,
    background = AuroraBlack,
    onBackground = AuroraTextHigh,
    surface = AuroraSurface,
    onSurface = AuroraTextHigh,
    surfaceVariant = AuroraSurface2,
    onSurfaceVariant = AuroraTextMid,
    error = AuroraRed,
    outline = AuroraGoldDim,
)

@Composable
fun AuroraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AuroraColors,
        typography = Typography(),
        content = content
    )
}
