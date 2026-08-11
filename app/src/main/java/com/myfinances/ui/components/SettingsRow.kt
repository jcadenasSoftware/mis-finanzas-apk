package com.jcadenas.xpendz.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.jcadenas.xpendz.ui.theme.XpendzThemeTokens

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit = {},
    showChevron: Boolean = true,
    modifier: Modifier = Modifier
) {
    val spacing = XpendzThemeTokens.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.m, vertical = spacing.s + spacing.xxs / 2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = XpendzThemeTokens.colors.brand,
            modifier = Modifier.size(spacing.xl)
        )

        Spacer(modifier = Modifier.width(spacing.m))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = XpendzThemeTokens.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(spacing.xxs / 2))
                Text(
                    text = subtitle,
                    style = XpendzThemeTokens.typography.bodySmall,
                    color = XpendzThemeTokens.colors.onSurfaceVariant
                )
            }
        }

        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = XpendzThemeTokens.colors.onSurfaceVariant,
                modifier = Modifier.size(spacing.l)
            )
        }
    }
}
