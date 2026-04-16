package com.myfinances.ui.screens.transactions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.myfinances.data.local.dao.TransactionWithDetails
import com.myfinances.ui.components.CompactHeader
import com.myfinances.ui.components.SyncSwipeRefresh
import com.myfinances.ui.theme.Income
import com.myfinances.ui.theme.Expense
import com.myfinances.ui.viewmodel.SyncViewModel
import com.myfinances.ui.viewmodel.TransactionsPeriodPreset
import com.myfinances.ui.viewmodel.TransactionsViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    onNavigateBack: () -> Unit,
    onAddTransaction: () -> Unit,
    onEditTransaction: (String) -> Unit,
    initialAccountId: String? = null,
    initialCategoryId: String? = null,
    initialFromEpochSec: Long? = null,
    initialToEpochSec: Long? = null,
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("es", "CO")) }
    val monthLabelFormat = remember { SimpleDateFormat("MMMM yyyy", Locale("es")) }

    val syncViewModel: SyncViewModel = hiltViewModel()
    val syncVersion by syncViewModel.syncVersion.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.applyInitialFilters(
            accountId = initialAccountId,
            categoryId = initialCategoryId,
            fromEpochSec = initialFromEpochSec,
            toEpochSec = initialToEpochSec
        )

        if (initialFromEpochSec == null && initialToEpochSec == null) {
            viewModel.setPeriodPreset(TransactionsPeriodPreset.MONTH)
        }
    }

    LaunchedEffect(syncVersion) {
        viewModel.loadTransactions()
    }

    Scaffold(
        topBar = {
            CompactHeader(
                title = {
                    Text(
                        text = "Transacciones",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTransaction) {
                Icon(Icons.Default.Add, contentDescription = "Nueva transacción")
            }
        }
    ) { paddingValues ->
        SyncSwipeRefresh(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val dayHeaderFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale("es")) }

            val mainListState = rememberLazyListState()
            val transactionsListState = rememberLazyListState()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = mainListState,
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    TransactionsFiltersHeader(
                        state = state,
                        monthLabelFormat = monthLabelFormat,
                        onSearch = { viewModel.setSearchQuery(it) },
                        onPreset = { viewModel.setPeriodPreset(it) },
                        onMonthSelected = { year, month -> viewModel.setMonth(year, month) },
                        onAccountSelected = { viewModel.filterByAccount(it) }
                    )
                }

                item {
                    TransactionsSummaryCard(
                        incomeCents = state.totalIncomeCents,
                        expenseCents = state.totalExpenseCents,
                        balanceCents = state.balanceCents,
                        currencyFormat = currencyFormat
                    )
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillParentMaxHeight()
                    ) {
                        when {
                            state.isLoading -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }

                            state.transactions.isEmpty() -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.Receipt,
                                            contentDescription = null,
                                            modifier = Modifier.size(64.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            "No hay transacciones",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            else -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    state = transactionsListState,
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    var lastHeader: String? = null
                                    items(state.transactions) { transaction ->
                                        val header = dayHeaderFormat.format(Date(transaction.occurredAtEpochSec * 1000))
                                        if (lastHeader != header) {
                                            lastHeader = header
                                            DateGroupHeader(text = header)
                                        }
                                        TransactionItem(
                                            transaction = transaction,
                                            onEdit = { onEditTransaction(transaction.id) },
                                            onDelete = { viewModel.deleteTransaction(transaction.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthPickerBottomSheet(
    monthOptions: List<Pair<Int, Int>>,
    monthLabelFormat: SimpleDateFormat,
    onDismiss: () -> Unit,
    onSelected: (Int, Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Seleccionar mes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                ) {
                    monthOptions.forEach { (year, month) ->
                        val cal = Calendar.getInstance()
                        cal.set(Calendar.YEAR, year)
                        cal.set(Calendar.MONTH, month - 1)
                        cal.set(Calendar.DAY_OF_MONTH, 1)
                        val optLabel = monthLabelFormat
                            .format(cal.time)
                            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelected(year, month) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null)
                            Text(
                                text = "$optLabel $year",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountPickerBottomSheet(
    accounts: List<com.myfinances.data.local.entity.AccountEntity>,
    selectedAccountId: String?,
    onDismiss: () -> Unit,
    onSelected: (String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Seleccionar cuenta",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                ) {
                    fun radio(selected: Boolean) = if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(null) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(radio(selectedAccountId == null), contentDescription = null)
                        Text("Todas las cuentas", style = MaterialTheme.typography.bodyLarge)
                    }
                    Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))

                    accounts.forEach { account ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelected(account.id) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(radio(selectedAccountId == account.id), contentDescription = null)
                            Text(account.name, style = MaterialTheme.typography.bodyLarge)
                        }
                        Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TransactionsFiltersHeader(
    state: com.myfinances.ui.viewmodel.TransactionsState,
    monthLabelFormat: SimpleDateFormat,
    onSearch: (String) -> Unit,
    onPreset: (TransactionsPeriodPreset) -> Unit,
    onMonthSelected: (Int, Int) -> Unit,
    onAccountSelected: (String?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 0.dp)
    ) {
        var showMonthSheet by remember { mutableStateOf(false) }
        var showAccountsSheet by remember { mutableStateOf(false) }

        val monthLabel = remember(state.selectedYear, state.selectedMonth) {
            val cal = Calendar.getInstance()
            cal.set(Calendar.YEAR, state.selectedYear)
            cal.set(Calendar.MONTH, state.selectedMonth - 1)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            val label = monthLabelFormat.format(cal.time)
            label.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }

        val monthOptions = remember(state.availableMonthsYearMonth) {
            if (state.availableMonthsYearMonth.isEmpty()) {
                listOf(Calendar.getInstance().get(Calendar.YEAR) to (Calendar.getInstance().get(Calendar.MONTH) + 1))
            } else state.availableMonthsYearMonth
        }

        if (showMonthSheet) {
            MonthPickerBottomSheet(
                monthOptions = monthOptions,
                monthLabelFormat = monthLabelFormat,
                onDismiss = { showMonthSheet = false },
                onSelected = { year, month ->
                    showMonthSheet = false
                    onMonthSelected(year, month)
                }
            )
        }

        if (showAccountsSheet) {
            AccountPickerBottomSheet(
                accounts = state.accounts,
                selectedAccountId = state.selectedAccountId,
                onDismiss = { showAccountsSheet = false },
                onSelected = { accountId ->
                    showAccountsSheet = false
                    onAccountSelected(accountId)
                }
            )
        }

        // Primary filters (month + account)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                onClick = { showMonthSheet = true },
                label = { Text(monthLabel) },
                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) }
            )

            AssistChip(
                onClick = { showAccountsSheet = true },
                label = {
                    val name = state.accounts.firstOrNull { it.id == state.selectedAccountId }?.name ?: "Todas las cuentas"
                    Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                modifier = Modifier.weight(1f)
            )

            FilledTonalIconButton(
                onClick = { showAccountsSheet = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Tune, contentDescription = "Cuentas")
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Quick period presets
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                PeriodChip(
                    text = "Hoy",
                    selected = state.selectedPeriodPreset == TransactionsPeriodPreset.TODAY,
                    onClick = { onPreset(TransactionsPeriodPreset.TODAY) }
                )
            }
            item {
                PeriodChip(
                    text = "Semana",
                    selected = state.selectedPeriodPreset == TransactionsPeriodPreset.WEEK,
                    onClick = { onPreset(TransactionsPeriodPreset.WEEK) }
                )
            }
            item {
                PeriodChip(
                    text = "Mes",
                    selected = state.selectedPeriodPreset == TransactionsPeriodPreset.MONTH,
                    onClick = { onPreset(TransactionsPeriodPreset.MONTH) }
                )
            }
            item {
                PeriodChip(
                    text = "Personalizado",
                    selected = state.selectedPeriodPreset == TransactionsPeriodPreset.CUSTOM,
                    onClick = { onPreset(TransactionsPeriodPreset.CUSTOM) }
                )
            }
        }

        // Search
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearch,
            placeholder = {
                Text(
                    "Buscar transacción, categoría o nota...",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .padding(horizontal = 16.dp, vertical = 0.dp)
        )
    }
}

@Composable
private fun PeriodChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        shape = MaterialTheme.shapes.extraLarge,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurface
        ),
        border = if (selected) null else BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.20f)
        ),
        modifier = Modifier.heightIn(min = 34.dp)
    )
}

@Composable
private fun TransactionsSummaryCard(
    incomeCents: Long,
    expenseCents: Long,
    balanceCents: Long,
    currencyFormat: NumberFormat
) {
    val incomeText = currencyFormat.format(incomeCents / 100.0)
    val expenseText = currencyFormat.format(expenseCents / 100.0)
    val balanceText = currencyFormat.format(balanceCents / 100.0)
    val balancePositive = balanceCents >= 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Resumen",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    TransactionsSummaryBackgroundGraph(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            TransactionsSummaryMetricColumn(
                                title = "Ingresos:",
                                value = incomeText,
                                accentColor = Income,
                                modifier = Modifier.weight(1f)
                            )
                            TransactionsSummaryMetricColumn(
                                title = "Gastos:",
                                value = expenseText,
                                accentColor = Expense,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        TransactionsSummaryInlineBalanceMetric(
                            title = "Balance:",
                            value = (if (balancePositive) "+" else "") + balanceText,
                            accentColor = if (balancePositive) Income else Expense,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionsSummaryMetricColumn(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            fontSize = 16.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            maxLines = 2,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun TransactionsSummaryInlineBalanceMetric(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            fontSize = 16.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun TransactionsSummaryBackgroundGraph(
    modifier: Modifier = Modifier
) {
    val primaryOverlay = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val linePath = Path().apply {
            moveTo(0f, height * 0.78f)
            cubicTo(
                width * 0.18f, height * 0.55f,
                width * 0.36f, height * 0.88f,
                width * 0.52f, height * 0.62f
            )
            cubicTo(
                width * 0.68f, height * 0.38f,
                width * 0.82f, height * 0.66f,
                width, height * 0.3f
            )
        }

        drawPath(
            path = linePath,
            color = primaryOverlay,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
        )

        drawCircle(
            color = Income.copy(alpha = 0.08f),
            radius = width * 0.18f,
            center = Offset(width * 0.15f, height * 0.2f)
        )
        drawCircle(
            color = Expense.copy(alpha = 0.06f),
            radius = width * 0.14f,
            center = Offset(width * 0.82f, height * 0.78f)
        )
    }
}

@Composable
private fun DateGroupHeader(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f))
    )
    Spacer(modifier = Modifier.height(6.dp))
}

@Composable
private fun FilterChipsRow(
    accounts: List<com.myfinances.data.local.entity.AccountEntity>,
    selectedAccountId: String?,
    onAccountSelected: (String?) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            val selected = selectedAccountId == null
            FilterChip(
                selected = selected,
                onClick = { onAccountSelected(null) },
                shape = MaterialTheme.shapes.extraLarge,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurface
                ),
                border = if (selected) null else BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                ),
                modifier = Modifier.heightIn(min = 34.dp),
                label = {
                    Text(
                        "Todas",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }

        items(accounts) { account ->
            val selected = selectedAccountId == account.id
            FilterChip(
                selected = selected,
                onClick = { onAccountSelected(account.id) },
                shape = MaterialTheme.shapes.extraLarge,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurface
                ),
                border = if (selected) null else BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                ),
                modifier = Modifier.heightIn(min = 34.dp),
                label = {
                    Text(
                        account.name,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
private fun TransactionItem(
    transaction: TransactionWithDetails,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("es", "CO")) }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale("es")) }

    val isIncome = remember(transaction.kind) { transaction.kind.trim().uppercase() == "INCOME" }
    val amountColor = if (isIncome) Income else Expense
    val sign = if (isIncome) "+" else "-"

    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    val iconSpec = remember(transaction.categoryName, transaction.kind) {
        val name = transaction.categoryName.trim().lowercase()
        when {
            isIncome -> Triple(Icons.Default.AttachMoney, primary, primary.copy(alpha = 0.12f))
            name.contains("comida") || name.contains("mercado") || name.contains("rest") || name.contains("charcut") ->
                Triple(Icons.Default.Restaurant, Expense, Expense.copy(alpha = 0.12f))
            name.contains("trans") || name.contains("uber") || name.contains("taxi") || name.contains("bus") ->
                Triple(Icons.Default.DirectionsCar, Expense, Expense.copy(alpha = 0.12f))
            name.contains("serv") || name.contains("luz") || name.contains("agua") || name.contains("gas") || name.contains("internet") || name.contains("elec") ->
                Triple(Icons.Default.Bolt, Expense, Expense.copy(alpha = 0.12f))
            name.contains("salud") || name.contains("med") || name.contains("farm") ->
                Triple(Icons.Default.MedicalServices, Expense, Expense.copy(alpha = 0.12f))
            name.contains("via") || name.contains("vuelo") || name.contains("hotel") ->
                Triple(Icons.Default.Flight, Expense, Expense.copy(alpha = 0.12f))
            name.contains("prest") || name.contains("deuda") || name.contains("credito") || name.contains("crédito") ->
                Triple(Icons.Default.RequestQuote, Expense, Expense.copy(alpha = 0.12f))
            name.contains("devol") || name.contains("reemb") ->
                Triple(Icons.Default.Undo, Expense, Expense.copy(alpha = 0.12f))
            else -> Triple(Icons.Default.Category, onSurfaceVariant, surfaceVariant.copy(alpha = 0.55f))
        }
    }

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
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = iconSpec.third,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = iconSpec.first,
                        contentDescription = null,
                        tint = iconSpec.second
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    transaction.categoryName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
                Text(
                    transaction.accountName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!transaction.note.isNullOrBlank()) {
                    Text(
                        transaction.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$sign${currencyFormat.format(transaction.amountCents / 100.0)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
                Text(
                    timeFormat.format(Date(transaction.occurredAtEpochSec * 1000)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Menu
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
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
                            onEdit()
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar") },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                    )
                }
            }
        }
    }
}
