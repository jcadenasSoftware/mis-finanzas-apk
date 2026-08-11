package com.jcadenas.xpendz.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class XpendzShapeTokens(
    val extraSmall: Dp,
    val small: Dp,
    val medium: Dp,
    val large: Dp,
    val extraLarge: Dp
)

internal val DefaultXpendzShapeTokens = XpendzShapeTokens(
    extraSmall = 8.dp,
    small = 10.dp,
    medium = 12.dp,
    large = 14.dp,
    extraLarge = 18.dp
)

internal val LocalXpendzShapeTokens = staticCompositionLocalOf { DefaultXpendzShapeTokens }

private fun XpendzShapeTokens.toMaterialShapes() = Shapes(
    extraSmall = RoundedCornerShape(extraSmall),
    small = RoundedCornerShape(small),
    medium = RoundedCornerShape(medium),
    large = RoundedCornerShape(large),
    extraLarge = RoundedCornerShape(extraLarge)
)

val Shapes = DefaultXpendzShapeTokens.toMaterialShapes()
