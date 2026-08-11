package com.jcadenas.xpendz.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.jcadenas.xpendz.ui.util.CountryCurrency
import com.jcadenas.xpendz.ui.theme.XpendzThemeTokens
import java.util.Currency
import java.util.Locale

private fun defaultIconKeyForType(accountType: String): String {
    return when (accountType) {
        "BANK" -> "bank"
        "CASH" -> "cash"
        "SAVINGS" -> "savings"
        "VIRTUAL_WALLET" -> "wallet"
        "DIGITAL_ACCOUNT" -> "digital"
        else -> "bank"
    }
}

private fun accountIconForKey(key: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (key.lowercase()) {
        "bank" -> Icons.Default.AccountBalance
        "wallet" -> Icons.Default.AccountBalanceWallet
        "cash" -> Icons.Default.Money
        "card" -> Icons.Default.CreditCard
        "savings" -> Icons.Default.Savings
        "digital" -> Icons.Default.PhoneAndroid
        else -> Icons.Default.AccountBalance
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: String, currency: String, iconKey: String?, colorHex: String?) -> Unit
) {
    val spacing = XpendzThemeTokens.spacing
    val colors = XpendzThemeTokens.colors
    val elevation = XpendzThemeTokens.elevation
    val shapes = XpendzThemeTokens.shapes

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
    var selectedIconKey by remember { mutableStateOf("bank") }
    var selectedColorHex by remember { mutableStateOf("#2463EB") }
    val currencySearchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val accountTypes = listOf(
        "BANK" to "Banco",
        "CASH" to "Efectivo",
        "SAVINGS" to "Ahorro",
        "VIRTUAL_WALLET" to "Billetera virtual",
        "DIGITAL_ACCOUNT" to "Cuenta digital"
    )
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
        containerColor = colors.surface,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(spacing.m)
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
                        onDismissRequest = { typeExpanded = false },
                        containerColor = colors.surface
                    ) {
                        accountTypes.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedType = value
                                    selectedIconKey = defaultIconKeyForType(value)
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = colors.surface
                        )
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
                                    .padding(spacing.s)
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
                                        .heightIn(
                                            min = spacing.xxxl * 3 + spacing.xxl + spacing.xxs,
                                            max = spacing.xxxl * 4 + spacing.xxl * 4
                                        ),
                                    contentPadding = PaddingValues(vertical = spacing.xs)
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

                Text(text = "Icono", style = XpendzThemeTokens.typography.labelLarge)
                Surface(
                    modifier = Modifier.size(spacing.xxxl + spacing.xs),
                    shape = RoundedCornerShape(shapes.extraLarge),
                    color = colors.brand.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Icon(
                            imageVector = accountIconForKey(selectedIconKey),
                            contentDescription = null,
                            tint = colors.brand,
                            modifier = Modifier.size(spacing.xl + spacing.xxs / 2)
                        )
                    }
                }

                Text(text = "Color", style = XpendzThemeTokens.typography.labelLarge)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = spacing.xxs),
                    horizontalArrangement = Arrangement.spacedBy(spacing.s - spacing.xxs / 2)
                ) {
                    val colorOptions = listOf(
                        "#8A05BE",
                        "#FF6B6B",
                        "#D32F2F",
                        "#2463EB",
                        "#10B981",
                        "#0EA5E9",
                        "#14B8A6",
                        "#FACC15",
                        "#EAB308",
                        "#F59E0B",
                        "#F97316",
                        "#EC4899",
                        "#6366F1",
                        "#A855F7",
                        "#22C55E",
                        "#111827",
                        "#6B7280"
                    )

                    items(colorOptions) { hex ->
                        val c = runCatching { Color(android.graphics.Color.parseColor(hex)) }
                            .getOrNull() ?: colors.brand
                        val selected = selectedColorHex.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(spacing.xxl + spacing.xxs)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (selected) elevation.level2 else elevation.level1,
                                    color = if (selected) colors.onSurface else colors.onSurface.copy(alpha = 0.18f),
                                    shape = CircleShape
                                )
                                .clickable { selectedColorHex = hex },
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = colors.onBrand,
                                    modifier = Modifier.size(spacing.l - spacing.xxs / 2)
                                )
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
                        onConfirm(name, selectedType, selectedCurrency, selectedIconKey, selectedColorHex)
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
