package com.jcadenas.xpendz.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class XpendzSpacingTokens(
    val xxs: Dp,
    val xs: Dp,
    val s: Dp,
    val m: Dp,
    val l: Dp,
    val xl: Dp,
    val xxl: Dp,
    val xxxl: Dp
)

internal val DefaultXpendzSpacingTokens = XpendzSpacingTokens(
    xxs = 4.dp,
    xs = 8.dp,
    s = 12.dp,
    m = 16.dp,
    l = 20.dp,
    xl = 24.dp,
    xxl = 32.dp,
    xxxl = 48.dp
)

internal val LocalXpendzSpacingTokens = staticCompositionLocalOf { DefaultXpendzSpacingTokens }
