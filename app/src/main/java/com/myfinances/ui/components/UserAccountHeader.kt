package com.jcadenas.xpendz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.jcadenas.xpendz.ui.theme.XpendzThemeTokens

@Composable
fun UserAccountHeader(
    displayName: String,
    email: String,
    photoUrl: String? = null,
    syncStatus: SyncStatus = SyncStatus.SYNCED,
    modifier: Modifier = Modifier
) {
    val spacing = XpendzThemeTokens.spacing
    val colors = XpendzThemeTokens.colors
    val shapes = XpendzThemeTokens.shapes
    val elevation = XpendzThemeTokens.elevation

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(shapes.extraLarge),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation.level0)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.m)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(spacing.xxxl + spacing.xs)
                        .clip(CircleShape)
                        .background(colors.brand),
                    contentAlignment = Alignment.Center
                ) {
                    if (photoUrl != null) {
                        // TODO: Load image with Coil when photoUrl is available
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = colors.onBrand,
                            modifier = Modifier.size(spacing.xxl)
                        )
                    } else {
                        val initial = displayName.takeIf { it.isNotBlank() }
                            ?.firstOrNull()
                            ?.toString()
                            ?.uppercase()
                            ?: "?"
                        Text(
                            text = initial,
                            style = XpendzThemeTokens.typography.headlineMedium,
                            color = colors.onBrand,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.width(spacing.s))

                // User info
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = displayName,
                        style = XpendzThemeTokens.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(spacing.xxs))
                    Text(
                        text = email,
                        style = XpendzThemeTokens.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.s))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                SyncStatusChip(
                    status = syncStatus
                )
            }
        }
    }
}
