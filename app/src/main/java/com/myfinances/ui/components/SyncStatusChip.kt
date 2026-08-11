package com.jcadenas.xpendz.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.jcadenas.xpendz.ui.theme.XpendzThemeTokens

enum class SyncStatus {
    SYNCED,
    SYNCING,
    OFFLINE
}

@Composable
fun SyncStatusChip(
    status: SyncStatus,
    modifier: Modifier = Modifier
) {
    val spacing = XpendzThemeTokens.spacing
    val colors = XpendzThemeTokens.colors

    val (icon, label) = when (status) {
        SyncStatus.SYNCED -> Icons.Default.CloudDone to "Sincronizado"
        SyncStatus.SYNCING -> Icons.Default.CloudSync to "Sincronizando"
        SyncStatus.OFFLINE -> Icons.Default.CloudOff to "Sin conexión"
    }

    AssistChip(
        onClick = {},
        label = {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(spacing.s + spacing.xxs / 2)
                )
                Spacer(modifier = Modifier.width(spacing.xxs))
                Text(
                    text = label,
                    style = XpendzThemeTokens.typography.labelSmall,
                    fontWeight = FontWeight.Normal
                )
            }
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = colors.surfaceVariant,
            labelColor = colors.onSurfaceVariant
        ),
        modifier = modifier
    )
}
