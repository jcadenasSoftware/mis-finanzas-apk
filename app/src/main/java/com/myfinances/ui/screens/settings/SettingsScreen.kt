package com.myfinances.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.myfinances.ui.util.CountryCurrency
import com.myfinances.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }

    var countryExpanded by remember { mutableStateOf(false) }

    var showAddRate by remember { mutableStateOf(false) }
    var rateFrom by remember { mutableStateOf("USD") }
    var rateTo by remember { mutableStateOf(state.baseCurrency) }
    var rateValue by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.syncFromFirestore() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sincronizar")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("País")
            Box {
                OutlinedTextField(
                    value = CountryCurrency.displayName(state.countryCode),
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { countryExpanded = true }
                )
                DropdownMenu(
                    expanded = countryExpanded,
                    onDismissRequest = { countryExpanded = false }
                ) {
                    CountryCurrency.options.forEach { opt ->
                        DropdownMenuItem(
                            text = { Text("${opt.displayName} (${opt.suggestedCurrency})") },
                            onClick = {
                                countryExpanded = false
                                viewModel.saveCountry(opt.code)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = state.baseCurrency,
                onValueChange = { viewModel.saveBaseCurrency(it.trim().uppercase()) },
                label = { Text("Moneda base (ISO)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Tasas manuales")
                Button(onClick = {
                    rateTo = state.baseCurrency
                    showAddRate = true
                }) {
                    Text("Agregar")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn {
                items(state.rates) { r ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${r.fromCurrency} -> ${r.toCurrency}: ${r.rate}")
                        IconButton(onClick = { viewModel.deleteRate(r.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                        }
                    }
                }
            }
        }
    }

    if (showAddRate) {
        AlertDialog(
            onDismissRequest = { showAddRate = false },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = rateValue.toDoubleOrNull()
                    if (parsed != null) {
                        viewModel.upsertRate(rateFrom.trim().uppercase(), rateTo.trim().uppercase(), parsed)
                        showAddRate = false
                        rateValue = ""
                    }
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRate = false }) {
                    Text("Cancelar")
                }
            },
            title = { Text("Nueva tasa") },
            text = {
                Column {
                    OutlinedTextField(
                        value = rateFrom,
                        onValueChange = { rateFrom = it },
                        label = { Text("Desde (ISO)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = rateTo,
                        onValueChange = { rateTo = it },
                        label = { Text("Hacia (ISO)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = rateValue,
                        onValueChange = { rateValue = it },
                        label = { Text("Tasa") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }
}
