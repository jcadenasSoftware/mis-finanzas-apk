package com.jcadenas.xpendz.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@Composable
fun HamburgerMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onNavigateToCharts: () -> Unit,
    onNavigateToBudget: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit,
    currentScreen: String = "",
    modifier: Modifier = Modifier
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .width(220.dp)
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Xpendz",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Controla tu dinero",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        // Section: Herramientas
        Column {
            Text(
                text = "Herramientas",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal
            )
            Spacer(modifier = Modifier.height(2.dp))
        }

        val isChartsActive = currentScreen == "charts"
        DropdownMenuItem(
            text = {
                Text(
                    "Gráficos",
                    color = if (isChartsActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            },
            leadingIcon = {
                Row {
                    if (isChartsActive) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(4.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Icon(
                        Icons.Default.BarChart,
                        contentDescription = null,
                        tint = if (isChartsActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            onClick = {
                onDismissRequest()
                if (!isChartsActive) onNavigateToCharts()
            },
            modifier = if (isChartsActive) {
                Modifier.background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                    RoundedCornerShape(8.dp)
                )
            } else Modifier
        )

        val isBudgetActive = currentScreen == "budget"
        DropdownMenuItem(
            text = {
                Text(
                    "Presupuesto",
                    color = if (isBudgetActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            },
            leadingIcon = {
                Row {
                    if (isBudgetActive) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(4.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Icon(
                        Icons.Default.AccountBalance,
                        contentDescription = null,
                        tint = if (isBudgetActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            onClick = {
                onDismissRequest()
                if (!isBudgetActive) onNavigateToBudget()
            },
            modifier = if (isBudgetActive) {
                Modifier.background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                    RoundedCornerShape(8.dp)
                )
            } else Modifier
        )

        val isReportsActive = currentScreen == "reports"
        DropdownMenuItem(
            text = {
                Text(
                    "Reportes PDF",
                    color = if (isReportsActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            },
            leadingIcon = {
                Row {
                    if (isReportsActive) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(4.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = if (isReportsActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            onClick = {
                onDismissRequest()
                if (!isReportsActive) onNavigateToReports()
            },
            modifier = if (isReportsActive) {
                Modifier.background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                    RoundedCornerShape(8.dp)
                )
            } else Modifier
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Section: Sistema
        Column {
            Text(
                text = "Sistema",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal
            )
            Spacer(modifier = Modifier.height(2.dp))
        }

        val isSettingsActive = currentScreen == "settings"
        DropdownMenuItem(
            text = {
                Text(
                    "Configuración",
                    color = if (isSettingsActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            },
            leadingIcon = {
                Row {
                    if (isSettingsActive) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(4.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        tint = if (isSettingsActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            onClick = {
                onDismissRequest()
                if (!isSettingsActive) onNavigateToSettings()
            },
            modifier = if (isSettingsActive) {
                Modifier.background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                    RoundedCornerShape(8.dp)
                )
            } else Modifier
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Section: Cuenta
        Column {
            Text(
                text = "Cuenta",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal
            )
            Spacer(modifier = Modifier.height(2.dp))
        }

        DropdownMenuItem(
            text = {
                Text(
                    "Cerrar sesión",
                    color = MaterialTheme.colorScheme.error
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            onClick = {
                onDismissRequest()
                onLogout()
            }
        )
    }
}
