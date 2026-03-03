package com.myfinances.ui.components

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.unit.dp
import com.myfinances.ui.util.CountryCurrency
import java.util.Currency
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: String, currency: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("BANK") }
    val deviceCountry = remember { Locale.getDefault().country }
    val defaultCurrency = remember(deviceCountry) {
        CountryCurrency.suggestedCurrency(deviceCountry)
    }
    var selectedCurrency by remember { mutableStateOf(defaultCurrency) }
    var typeExpanded by remember { mutableStateOf(false) }
    var currencyExpanded by remember { mutableStateOf(false) }
    var currencyQuery by remember { mutableStateOf("") }
    val currencySearchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val accountTypes = listOf("BANK" to "Banco", "CASH" to "Efectivo", "CREDIT" to "Crédito", "SAVINGS" to "Ahorros")
    val displayLocale = remember { Locale("es", "ES") }
    val allCurrencies = remember {
        Currency.getAvailableCurrencies()
            .asSequence()
            .map { c ->
                val code = c.currencyCode
                val label = c.getDisplayName(displayLocale)
                    .replaceFirstChar { it.titlecase(displayLocale) }
                code to label
            }
            .distinctBy { it.first }
            .sortedBy { it.second }
            .toList()
    }
    val suggestedCurrencies = remember(defaultCurrency, deviceCountry) {
        val suggestedCodes = buildList {
            add(defaultCurrency)
            addAll(CountryCurrency.options.map { it.suggestedCurrency })
            addAll(listOf("USD", "EUR"))
        }.filter { it.isNotBlank() }.distinct()

        val suggested = allCurrencies.filter { (code, _) -> suggestedCodes.contains(code) }
        val preferred = suggested.firstOrNull { it.first == defaultCurrency }
        val rest = suggested.filterNot { it.first == defaultCurrency }.sortedBy { it.second }
        if (preferred == null) rest else listOf(preferred) + rest
    }
    val filteredCurrencies = remember(currencyQuery, suggestedCurrencies, allCurrencies) {
        val q = currencyQuery.trim()
        if (q.isBlank()) {
            suggestedCurrencies
        } else {
            val byCurrency = allCurrencies.filter { (code, label) ->
                code.contains(q, ignoreCase = true) || label.contains(q, ignoreCase = true)
            }

            val matchedCountryCurrencies = CountryCurrency.options
                .asSequence()
                .filter { option -> option.displayName.contains(q, ignoreCase = true) }
                .map { it.suggestedCurrency }
                .filter { it.isNotBlank() }
                .distinct()
                .toList()

            val byCountry = if (matchedCountryCurrencies.isEmpty()) {
                emptyList()
            } else {
                allCurrencies.filter { (code, _) -> matchedCountryCurrencies.contains(code) }
            }

            (byCountry + byCurrency)
                .distinctBy { it.first }
                .sortedBy { it.second }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva cuenta") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre de la cuenta") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Account Type Dropdown
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = accountTypes.find { it.first == selectedType }?.second ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tipo de cuenta") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        accountTypes.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedType = value
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }

                // Currency Dropdown
                OutlinedTextField(
                    value = allCurrencies.find { it.first == selectedCurrency }?.second ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Moneda") },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                currencyExpanded = !currencyExpanded
                                if (!currencyExpanded) currencyQuery = ""
                            }
                        ) {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (currencyExpanded) {
                    LaunchedEffect(Unit) {
                        currencySearchFocusRequester.requestFocus()
                        keyboardController?.show()
                    }

                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = currencyQuery,
                                onValueChange = { currencyQuery = it },
                                singleLine = true,
                                label = { Text("Buscar moneda o país") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(currencySearchFocusRequester)
                                    .padding(12.dp)
                            )

                            Divider()

                            val showList = if (currencyQuery.isBlank()) {
                                filteredCurrencies.take(20)
                            } else {
                                filteredCurrencies.take(50)
                            }

                            if (showList.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Sin resultados") },
                                    onClick = { }
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 180.dp, max = 320.dp),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    items(showList) { (value, label) ->
                                        DropdownMenuItem(
                                            text = { Text("$label ($value)") },
                                            onClick = {
                                                selectedCurrency = value
                                                currencyExpanded = false
                                                currencyQuery = ""
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name, selectedType, selectedCurrency)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Crear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
