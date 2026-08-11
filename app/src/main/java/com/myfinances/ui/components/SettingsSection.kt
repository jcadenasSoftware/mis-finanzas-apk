package com.jcadenas.xpendz.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
import com.jcadenas.xpendz.ui.theme.XpendzThemeTokens

data class SettingsItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String? = null,
    val onClick: () -> Unit = {},
    val showChevron: Boolean = true
)

@Composable
fun SettingsSection(
    title: String,
    items: List<SettingsItem>,
    modifier: Modifier = Modifier
) {
    val spacing = XpendzThemeTokens.spacing
    val colors = XpendzThemeTokens.colors
    val shapeTokens = XpendzThemeTokens.shapes
    val elevation = XpendzThemeTokens.elevation

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = title,
            modifier = Modifier.padding(horizontal = spacing.m, vertical = spacing.xs)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.m),
            shape = RoundedCornerShape(shapeTokens.large),
            colors = CardDefaults.cardColors(
                containerColor = colors.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = elevation.level0)
        ) {
            Column {
                items.forEachIndexed { index, item ->
                    SettingsRow(
                        icon = item.icon,
                        title = item.title,
                        subtitle = item.subtitle,
                        onClick = item.onClick,
                        showChevron = item.showChevron
                    )
                    if (index < items.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = spacing.m),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}
