package com.jcadenas.xpendz.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.jcadenas.xpendz.ui.theme.XpendzThemeTokens

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = XpendzThemeTokens.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = XpendzThemeTokens.colors.onSurfaceVariant,
        modifier = modifier
    )
}
