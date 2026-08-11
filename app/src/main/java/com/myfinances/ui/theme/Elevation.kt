package com.jcadenas.xpendz.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class XpendzElevationTokens(
    val level0: Dp,
    val level1: Dp,
    val level2: Dp,
    val level3: Dp,
    val level4: Dp
)

internal val DefaultXpendzElevationTokens = XpendzElevationTokens(
    level0 = 0.dp,
    level1 = 1.dp,
    level2 = 3.dp,
    level3 = 6.dp,
    level4 = 8.dp
)

internal val LocalXpendzElevationTokens = staticCompositionLocalOf { DefaultXpendzElevationTokens }
