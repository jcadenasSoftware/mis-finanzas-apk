package com.myfinances.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myfinances.data.local.entity.AccountEntity
import com.myfinances.ui.theme.Income
import com.myfinances.ui.theme.Expense
import java.text.NumberFormat
import java.util.Locale

private fun accountTypeLabel(raw: String?): String {
    val t = raw?.trim()?.uppercase().orEmpty()
    return when (t) {
        "BANK" -> "Banco"
        "CASH" -> "Efectivo"
        "SAVINGS" -> "Ahorro"
        "VIRTUAL_WALLET" -> "Billetera virtual"
        "DIGITAL_ACCOUNT" -> "Cuenta digital"
        "CREDIT" -> "Banco"
        "INVESTMENT" -> "Ahorro"
        "OTHER" -> "Banco"
        "CHECKING" -> "Banco"
        else -> if (t.isBlank()) "Cuenta" else t
    }
}

@Composable
fun AccountCard(
    account: AccountEntity,
    balanceCents: Long,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("es", "CO")) }
    val isGoalAccount = account.name.contains("meta", ignoreCase = true) || account.type.equals("SAVINGS", ignoreCase = true)
    val accountIconColor = when {
        isGoalAccount -> Color(0xFF8E44AD)
        account.type.equals("CASH", ignoreCase = true) -> Color(0xFF1565C0)
        else -> Color(0xFF2563EB)
    }
    val accountIconBackground = when {
        isGoalAccount -> Color(0xFFF3E8FF)
        account.type.equals("CASH", ignoreCase = true) -> Color(0xFFE3F2FD)
        else -> Color(0xFFE8F0FF)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isPressed) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f) else Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isPressed) 2.dp else 5.dp
        ),
        shape = MaterialTheme.shapes.large,
        border = CardDefaults.outlinedCardBorder().copy(
            width = 1.dp,
            brush = SolidColor(MaterialTheme.colorScheme.primary.copy(alpha = if (isPressed) 0.15f else 0.08f))
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Account icon
            Surface(
                shape = MaterialTheme.shapes.large,
                color = accountIconBackground,
                shadowElevation = 1.dp,
                modifier = Modifier.size(50.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        when (account.type) {
                            "CASH" -> Icons.Default.Money
                            "DIGITAL_ACCOUNT" -> Icons.Default.PhoneAndroid
                            "VIRTUAL_WALLET" -> Icons.Default.AccountBalanceWallet
                            "SAVINGS" -> Icons.Default.Savings
                            else -> Icons.Default.AccountBalance
                        },
                        contentDescription = null,
                        tint = accountIconColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Account info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    account.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    "${accountTypeLabel(account.type)} • ${account.currency}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Balance
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    currencyFormat.format(balanceCents / 100.0),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (balanceCents >= 0) Income else Expense
                )
            }

            // Menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Editar") },
                        onClick = {
                            showMenu = false
                            showRenameDialog = true
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar") },
                        onClick = {
                            showMenu = false
                            showDeleteDialog = true
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                    )
                }
            }
        }
    }

    // Rename Dialog
    if (showRenameDialog) {
        var newName by remember { mutableStateOf(account.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Renombrar cuenta") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nombre") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onRename(newName)
                            showRenameDialog = false
                        }
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Delete Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar cuenta") },
            text = { Text("¿Estás seguro de que deseas eliminar la cuenta \"${account.name}\"? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
