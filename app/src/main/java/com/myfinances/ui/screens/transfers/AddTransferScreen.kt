package com.myfinances.ui.screens.transfers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.myfinances.ui.theme.Expense
import com.myfinances.ui.theme.Income
import com.myfinances.ui.viewmodel.TransfersViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransferScreen(
    transferId: String? = null,
    onNavigateBack: () -> Unit,
    onTransferSaved: () -> Unit,
    viewModel: TransfersViewModel = hiltViewModel()
) {
    val formState by viewModel.formState.collectAsState()
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale("es")) }
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("es", "CO")) }

    LaunchedEffect(transferId) {
        viewModel.initForm(transferId)
    }

    LaunchedEffect(formState.isSaved) {
        if (formState.isSaved) {
            onTransferSaved()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (transferId != null) "Editar transferencia" else "Nueva transferencia") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (formState.isLoading && formState.accounts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // From Account Dropdown
                var fromAccountExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = fromAccountExpanded,
                    onExpandedChange = { fromAccountExpanded = it }
                ) {
                    OutlinedTextField(
                        value = formState.accounts.find { it.id == formState.fromAccountId }?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Cuenta origen") },
                        leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fromAccountExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = fromAccountExpanded,
                        onDismissRequest = { fromAccountExpanded = false }
                    ) {
                        formState.accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = {
                                    viewModel.updateFormFromAccount(account.id)
                                    fromAccountExpanded = false
                                }
                            )
                        }
                    }
                }

                formState.fromAccountBalanceCents?.let { balanceCents ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Saldo disponible",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                currencyFormat.format(balanceCents / 100.0),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (balanceCents >= 0) Income else Expense
                            )
                        }
                    }
                }

                // Arrow icon
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // To Account Dropdown
                var toAccountExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = toAccountExpanded,
                    onExpandedChange = { toAccountExpanded = it }
                ) {
                    OutlinedTextField(
                        value = formState.accounts.find { it.id == formState.toAccountId }?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Cuenta destino") },
                        leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = toAccountExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = toAccountExpanded,
                        onDismissRequest = { toAccountExpanded = false }
                    ) {
                        formState.accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = {
                                    viewModel.updateFormToAccount(account.id)
                                    toAccountExpanded = false
                                }
                            )
                        }
                    }
                }

                // Amount
                OutlinedTextField(
                    value = formState.amountText,
                    onValueChange = { viewModel.updateFormAmount(it) },
                    label = { Text("Monto") },
                    leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Date
                OutlinedTextField(
                    value = dateFormat.format(Date(formState.occurredAtEpochSec * 1000)),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Fecha") },
                    leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Note
                OutlinedTextField(
                    value = formState.note,
                    onValueChange = { viewModel.updateFormNote(it) },
                    label = { Text("Nota (opcional)") },
                    leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // Error
                formState.error?.let { error ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Save Button
                Button(
                    onClick = { viewModel.saveTransfer() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !formState.isLoading
                ) {
                    if (formState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(if (transferId != null) "Actualizar" else "Guardar")
                    }
                }
            }
        }
    }
}
