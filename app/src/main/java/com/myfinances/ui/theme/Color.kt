package com.jcadenas.xpendz.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class XpendzColorTokens(
    val brand: Color,
    val brandStrong: Color,
    val brandSubtle: Color,
    val onBrand: Color,
    val secondary: Color,
    val secondaryStrong: Color,
    val onSecondary: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val positive: Color,
    val positiveSubtle: Color,
    val negative: Color,
    val negativeSubtle: Color,
    val information: Color,
    val informationSubtle: Color,
    val warning: Color,
    val warningStrong: Color
)

// Primary colors
private val BrandBase = Color(0xFF1D4ED8)
private val BrandStrong = Color(0xFF1E40AF)
private val BrandSubtle = Color(0xFF3B82F6)
val Primary = BrandBase
val PrimaryDark = BrandStrong
val PrimaryLight = BrandSubtle

// Secondary colors
private val SecondaryBase = Color(0xFFFFD166)
private val SecondaryStrong = Color(0xFFFBBF24)
val Secondary = SecondaryBase
val SecondaryDark = SecondaryStrong
val SecondaryLight = Secondary

// Background colors
private val BackgroundBaseLight = Color(0xFFFBFCFF)
private val BackgroundBaseDark = Color(0xFF070B14)
private val SurfaceBaseLight = Color(0xFFFFFFFF)
private val SurfaceBaseDark = Color(0xFF0F172A)
val BackgroundLight = BackgroundBaseLight
val BackgroundDark = BackgroundBaseDark
val SurfaceLight = SurfaceBaseLight
val SurfaceDark = SurfaceBaseDark

// Text colors
private val OnPrimaryBaseLight = Color(0xFFFFFFFF)
private val OnPrimaryBaseDark = Color(0xFFE5E7EB)
private val OnBackgroundBaseLight = Color(0xFF0F172A)
private val OnBackgroundBaseDark = Color(0xFFE5E7EB)
private val OnSurfaceVariantBaseLight = Color(0xFF5F6B7A)
private val OnSurfaceVariantBaseDark = Color(0xFFB8C2D3)
val OnPrimaryLight = OnPrimaryBaseLight
val OnPrimaryDark = OnPrimaryBaseDark
val OnBackgroundLight = OnBackgroundBaseLight
val OnBackgroundDark = OnBackgroundBaseDark
val OnSurfaceLight = OnBackgroundLight
val OnSurfaceDark = OnBackgroundDark
val OnSurfaceVariantLight = OnSurfaceVariantBaseLight
val OnSurfaceVariantDark = OnSurfaceVariantBaseDark

// Semantic colors
private val PositiveBase = Color(0xFF4CAF50)
private val PositiveSubtle = Color(0xFF81C784)
private val NegativeBase = Color(0xFFF44336)
private val NegativeSubtle = Color(0xFFE57373)
private val InformationBase = Color(0xFF2196F3)
private val InformationSubtle = Color(0xFF64B5F6)
val Income = PositiveBase
val IncomeLight = PositiveSubtle
val Expense = NegativeBase
val ExpenseLight = NegativeSubtle
val Transfer = InformationBase
val TransferLight = InformationSubtle

// Card colors
private val SurfaceVariantBaseLight = SurfaceLight
private val SurfaceVariantBaseDark = Color(0xFF0B1220)
val CardLight = SurfaceVariantBaseLight
val CardDark = SurfaceVariantBaseDark

// Gold accent (from desktop app)
val GoldAccent = Secondary
val GoldAccentDark = SecondaryDark

internal val LightColorTokens = XpendzColorTokens(
    brand = Primary,
    brandStrong = PrimaryDark,
    brandSubtle = PrimaryLight,
    onBrand = OnPrimaryLight,
    secondary = Secondary,
    secondaryStrong = SecondaryDark,
    onSecondary = OnPrimaryLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = CardLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    positive = Income,
    positiveSubtle = IncomeLight,
    negative = Expense,
    negativeSubtle = ExpenseLight,
    information = Transfer,
    informationSubtle = TransferLight,
    warning = GoldAccent,
    warningStrong = GoldAccentDark
)

internal val DarkColorTokens = XpendzColorTokens(
    brand = PrimaryLight,
    brandStrong = PrimaryDark,
    brandSubtle = Primary,
    onBrand = OnPrimaryLight,
    secondary = Secondary,
    secondaryStrong = SecondaryDark,
    onSecondary = OnPrimaryLight,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = CardDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    positive = Income,
    positiveSubtle = IncomeLight,
    negative = Expense,
    negativeSubtle = ExpenseLight,
    information = Transfer,
    informationSubtle = TransferLight,
    warning = GoldAccent,
    warningStrong = GoldAccentDark
)

internal val LocalXpendzColorTokens = staticCompositionLocalOf { LightColorTokens }
