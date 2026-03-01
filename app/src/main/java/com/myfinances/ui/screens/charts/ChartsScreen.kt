package com.myfinances.ui.screens.charts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.myfinances.ui.components.SyncSwipeRefresh
import com.myfinances.ui.theme.Expense
import com.myfinances.ui.theme.Income
import com.myfinances.ui.theme.Primary
import com.myfinances.ui.viewmodel.ChartsKind
import com.myfinances.ui.viewmodel.ChartsViewMode
import com.myfinances.ui.viewmodel.ChartsViewModel
import com.myfinances.ui.viewmodel.SyncViewModel
import java.text.NumberFormat
import java.time.LocalDate
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ChartsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("es", "CO")) }

    val syncViewModel: SyncViewModel = hiltViewModel()
    val syncVersion by syncViewModel.syncVersion.collectAsState()

    LaunchedEffect(syncVersion) {
        viewModel.load()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gráficos") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { paddingValues ->
        SyncSwipeRefresh(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    FiltersCard(
                        state = state,
                        onYear = { viewModel.updateYear(it) },
                        onKind = { viewModel.updateKind(it) },
                        onView = { viewModel.updateViewMode(it) },
                        onAccount = { viewModel.updateAccount(it) },
                        onMonth = { viewModel.updateMonthIndex(it) },
                        onRootCategory = { viewModel.updateRootCategory(it) },
                        onSubCategory = { viewModel.updateSubCategory(it) }
                    )
                }

                item {
                    val total = state.totalAmountCents
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Total",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                currencyFormat.format(total / 100.0),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (state.selectedKind == ChartsKind.INCOME) Income else Expense
                            )
                        }
                    }
                }

                if (state.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (state.items.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(22.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No hay datos para este filtro",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(state.items) { index, item ->
                        ChartBarRow(
                            index = index,
                            name = item.name,
                            amountCents = item.amountCents,
                            percent = item.percent,
                            currencyFormat = currencyFormat,
                            kind = state.selectedKind
                        )
                    }
                }

                state.error?.let { err ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                err,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FiltersCard(
    state: com.myfinances.ui.viewmodel.ChartsState,
    onYear: (Int) -> Unit,
    onKind: (ChartsKind) -> Unit,
    onView: (ChartsViewMode) -> Unit,
    onAccount: (String?) -> Unit,
    onMonth: (Int) -> Unit,
    onRootCategory: (String?) -> Unit,
    onSubCategory: (String?) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Filtros",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DropdownField(
                    label = "Año",
                    value = state.selectedYear.toString(),
                    options = state.years.map { it.toString() },
                    onSelected = { onYear(it.toInt()) },
                    modifier = Modifier.weight(1f)
                )

                DropdownField(
                    label = "Tipo",
                    value = state.selectedKind.label,
                    options = state.kinds.map { it.label },
                    onSelected = { label ->
                        val k = state.kinds.firstOrNull { it.label == label } ?: ChartsKind.EXPENSE
                        onKind(k)
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DropdownField(
                    label = "Vista",
                    value = state.selectedView.label,
                    options = state.views.map { it.label },
                    onSelected = { label ->
                        val v = state.views.firstOrNull { it.label == label } ?: ChartsViewMode.ROOT
                        onView(v)
                    },
                    modifier = Modifier.weight(1f)
                )

                DropdownField(
                    label = "Mes",
                    value = state.months.getOrNull(state.selectedMonthIndex) ?: "TOTAL",
                    options = state.months,
                    onSelected = { label -> onMonth(state.months.indexOf(label).coerceAtLeast(0)) },
                    modifier = Modifier.weight(1f)
                )
            }

            val accountLabel = state.accounts.firstOrNull { it.id == state.selectedAccountId }?.name
                ?: "(Todas las cuentas)"
            DropdownField(
                label = "Cuenta",
                value = accountLabel,
                options = listOf("(Todas las cuentas)") + state.accounts.map { it.name },
                onSelected = { label ->
                    val id = state.accounts.firstOrNull { it.name == label }?.id
                    onAccount(id)
                }
            )

            if (state.selectedView == ChartsViewMode.SUB) {
                val rootLabel = state.rootCategories.firstOrNull { it.id == state.selectedRootCategoryId }?.name
                    ?: "(Todas las categorías)"
                DropdownField(
                    label = "Categoría",
                    value = rootLabel,
                    options = listOf("(Todas las categorías)") + state.rootCategories.map { it.name },
                    onSelected = { label ->
                        val id = state.rootCategories.firstOrNull { it.name == label }?.id
                        onRootCategory(id)
                    }
                )

                val subLabel = state.subCategories.firstOrNull { it.id == state.selectedSubCategoryId }?.name
                    ?: "(Todas las subcategorías)"
                DropdownField(
                    label = "Subcategoría",
                    value = subLabel,
                    options = listOf("(Todas las subcategorías)") + state.subCategories.map { it.name },
                    onSelected = { label ->
                        val id = state.subCategories.firstOrNull { it.name == label }?.id
                        onSubCategory(id)
                    }
                )
            }

            Text(
                "Sugerencia: usa Vista=Subcategorías y elige una categoría para ver el detalle.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            textStyle = MaterialTheme.typography.bodySmall,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = {
                        Text(
                            opt,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelected(opt)
                    }
                )
            }
        }
    }
}

@Composable
private fun ChartBarRow(
    index: Int,
    name: String,
    amountCents: Long,
    percent: Float,
    currencyFormat: NumberFormat,
    kind: ChartsKind
) {
    val barColor = if (kind == ChartsKind.INCOME) Income else Expense
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    "${index + 1}. $name",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        currencyFormat.format(amountCents / 100.0),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = barColor
                    )
                    Text(
                        "${String.format(Locale("es"), "%.1f", percent * 100)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LinearProgressIndicator(
                progress = { percent.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}
