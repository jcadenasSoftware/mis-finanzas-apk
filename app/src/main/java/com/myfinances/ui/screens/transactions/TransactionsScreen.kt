package com.jcadenas.xpendz.ui.screens.transactions

import android.app.DatePickerDialog
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.jcadenas.xpendz.data.local.dao.TransactionWithDetails
import com.jcadenas.xpendz.ui.components.CompactHeader
import com.jcadenas.xpendz.ui.components.HamburgerMenu
import com.jcadenas.xpendz.ui.components.HamburgerMenuButton
import com.jcadenas.xpendz.ui.components.SyncSwipeRefresh
import com.jcadenas.xpendz.ui.theme.Income
import com.jcadenas.xpendz.ui.theme.Expense
import com.jcadenas.xpendz.ui.viewmodel.SyncViewModel
import com.jcadenas.xpendz.ui.viewmodel.TransactionsPeriodPreset
import com.jcadenas.xpendz.ui.viewmodel.TransactionsViewModel
import androidx.compose.ui.platform.LocalContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    onNavigateBack: () -> Unit,
    onAddTransaction: () -> Unit,
    onEditTransaction: (String) -> Unit,
    onNavigateToCharts: () -> Unit,
    onNavigateToBudget: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit,
    initialAccountId: String? = null,
    initialCategoryId: String? = null,
    initialFromEpochSec: Long? = null,
    initialToEpochSec: Long? = null,
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("es", "CO")) }
    val monthLabelFormat = remember { SimpleDateFormat("MMMM yyyy", Locale("es")) }
    var showHamburgerMenu by remember { mutableStateOf(false) }

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
                },
                actions = {
                    Box {
                        HamburgerMenuButton(onClick = { showHamburgerMenu = true })
                        HamburgerMenu(
                            expanded = showHamburgerMenu,
                            onDismissRequest = { showHamburgerMenu = false },
                            onNavigateToCharts = onNavigateToCharts,
                            onNavigateToBudget = onNavigateToBudget,
                            onNavigateToReports = onNavigateToReports,
                            onNavigateToSettings = onNavigateToSettings,
                            onLogout = onLogout,
                            currentScreen = "transactions"
                        )
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
                        onCustomPeriodSelected = { from, to -> viewModel.setCustomPeriod(from, to) },
                        onMonthSelected = { year, month -> viewModel.setMonth(year, month) },
                        onAccountSelected = { viewModel.filterByAccount(it) },
                        onCategorySelected = { viewModel.filterByCategory(it) }
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
                                    itemsIndexed(state.transactions) { index, transaction ->
                                        val headerUi = remember(transaction.occurredAtEpochSec) {
                                            resolveTransactionDateHeaderUi(
                                                epochSec = transaction.occurredAtEpochSec,
                                                dayHeaderFormat = dayHeaderFormat
                                            )
                                        }
                                        val groupKey = remember(transaction.occurredAtEpochSec) {
                                            transactionDayKey(transaction.occurredAtEpochSec)
                                        }
                                        val previousGroupKey = if (index > 0) {
                                            remember(state.transactions[index - 1].occurredAtEpochSec) {
                                                transactionDayKey(state.transactions[index - 1].occurredAtEpochSec)
                                            }
                                        } else {
                                            null
                                        }

                                        if (index == 0 || previousGroupKey != groupKey) {
                                            DateGroupHeader(
                                                text = headerUi.text,
                                                relative = headerUi.relative
                                            )
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
private fun CustomPeriodBottomSheet(
    initialFromEpochSec: Long?,
    initialToEpochSec: Long?,
    onDismiss: () -> Unit,
    onApply: (Long, Long) -> Unit
) {
    val context = LocalContext.current
    val titleFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale("es")) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val nowMillis = remember { System.currentTimeMillis() }
    var draftFromEpochSec by remember(initialFromEpochSec) {
        mutableStateOf(initialFromEpochSec ?: startOfDayEpochSec(nowMillis))
    }
    var draftToEpochSec by remember(initialToEpochSec) {
        mutableStateOf(initialToEpochSec ?: endOfDayEpochSec(nowMillis))
    }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    if (showFromPicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = draftFromEpochSec * 1000L }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val picked = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                }
                picked.set(Calendar.HOUR_OF_DAY, 0)
                picked.set(Calendar.MINUTE, 0)
                picked.set(Calendar.SECOND, 0)
                picked.set(Calendar.MILLISECOND, 0)
                val from = picked.timeInMillis / 1000L
                draftFromEpochSec = from
                if (from > draftToEpochSec) {
                    draftToEpochSec = endOfDayEpochSec(picked.timeInMillis)
                }
                showFromPicker = false
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setOnCancelListener { showFromPicker = false }
        }.show()
    }

    if (showToPicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = draftToEpochSec * 1000L }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val picked = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                }
                picked.set(Calendar.HOUR_OF_DAY, 23)
                picked.set(Calendar.MINUTE, 59)
                picked.set(Calendar.SECOND, 59)
                picked.set(Calendar.MILLISECOND, 999)
                val to = picked.timeInMillis / 1000L
                draftToEpochSec = to
                if (to < draftFromEpochSec) {
                    draftFromEpochSec = startOfDayEpochSec(picked.timeInMillis)
                }
                showToPicker = false
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setOnCancelListener { showToPicker = false }
        }.show()
    }

    fun dateLabel(epochSec: Long): String = titleFormat.format(Date(epochSec * 1000L))

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
                text = "Rango personalizado",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Selecciona dos fechas para filtrar las transacciones.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterSelectorChip(
                    text = "Desde ${dateLabel(draftFromEpochSec)}",
                    icon = Icons.Default.DateRange,
                    onClick = { showFromPicker = true },
                    modifier = Modifier.weight(1f)
                )
                FilterSelectorChip(
                    text = "Hasta ${dateLabel(draftToEpochSec)}",
                    icon = Icons.Default.DateRange,
                    onClick = { showToPicker = true },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancelar")
                }
                Button(
                    onClick = {
                        val from = minOf(draftFromEpochSec, draftToEpochSec)
                        val to = maxOf(draftFromEpochSec, draftToEpochSec)
                        onApply(from, to)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Aplicar")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun startOfDayEpochSec(epochMillis: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = epochMillis
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis / 1000L
}

private fun endOfDayEpochSec(epochMillis: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = epochMillis
    cal.set(Calendar.HOUR_OF_DAY, 23)
    cal.set(Calendar.MINUTE, 59)
    cal.set(Calendar.SECOND, 59)
    cal.set(Calendar.MILLISECOND, 999)
    return cal.timeInMillis / 1000L
}

private fun formatTransactionNote(kind: String, note: String?): String? {
    val rawNote = note?.trim().orEmpty()
    if (rawNote.isBlank()) return null

    val normalizedKind = kind.trim().uppercase()
    val kindPrefix = when (normalizedKind) {
        "LOAN_LENT_OUT" -> "Préstamo otorgado a"
        "LOAN_BORROWED_IN" -> "Dinero recibido de"
        "LOAN_LENT_TOPUP" -> "Aumento de préstamo otorgado a"
        "LOAN_BORROWED_TOPUP" -> "Aumento de deuda con"
        "LOAN_LENT_CORRECTION" -> "Corrección de préstamo otorgado a"
        "LOAN_BORROWED_CORRECTION" -> "Corrección de deuda con"
        "LOAN_LENT_CORRECTION_IN" -> "Corrección de préstamo otorgado a"
        "LOAN_LENT_CORRECTION_OUT" -> "Corrección de préstamo otorgado a"
        "LOAN_BORROWED_CORRECTION_IN" -> "Corrección de deuda con"
        "LOAN_BORROWED_CORRECTION_OUT" -> "Corrección de deuda con"
        "LOAN_REPAYMENT_PRINCIPAL_IN" -> "Pago recibido de"
        "LOAN_REPAYMENT_PRINCIPAL_OUT" -> "Pago realizado a"
        else -> null
    }

    if (kindPrefix.isNullOrBlank()) return rawNote

    val normalizedNote = rawNote.trim()
    if (!normalizedNote.startsWith(normalizedKind, ignoreCase = true)) {
        return rawNote
    }

    val suffix = normalizedNote.substring(normalizedKind.length).trimStart()
    return when {
        suffix.isBlank() -> kindPrefix
        suffix.startsWith(":") -> {
            val remainder = suffix.removePrefix(":").trimStart()
            if (remainder.isBlank()) kindPrefix else "$kindPrefix: $remainder"
        }
        else -> "$kindPrefix $suffix"
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
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
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
    accounts: List<com.jcadenas.xpendz.data.local.entity.AccountEntity>,
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
                    SelectableSheetRow(
                        text = "Todas las cuentas",
                        selected = selectedAccountId == null,
                        onClick = { onSelected(null) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))

                    accounts.forEach { account ->
                        SelectableSheetRow(
                            text = account.name,
                            selected = selectedAccountId == account.id,
                            onClick = { onSelected(account.id) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SelectableSheetRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val rowTint = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent
    val iconTint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowTint)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = iconTint
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodPickerBottomSheet(
    selectedPreset: TransactionsPeriodPreset,
    onDismiss: () -> Unit,
    onSelected: (TransactionsPeriodPreset) -> Unit,
    onCustomRequested: () -> Unit
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
                text = "Seleccionar periodo",
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
                    val options = listOf(
                        TransactionsPeriodPreset.TODAY to "Hoy",
                        TransactionsPeriodPreset.WEEK to "Esta semana",
                        TransactionsPeriodPreset.MONTH to "Este mes",
                        TransactionsPeriodPreset.CUSTOM to "Personalizado"
                    )

                    options.forEach { (preset, label) ->
                        SelectableSheetRow(
                            text = label,
                            selected = selectedPreset == preset,
                            onClick = {
                                if (preset == TransactionsPeriodPreset.CUSTOM) {
                                    onCustomRequested()
                                } else {
                                    onSelected(preset)
                                }
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPickerBottomSheet(
    categories: List<com.jcadenas.xpendz.data.local.entity.CategoryEntity>,
    selectedCategoryId: String?,
    onDismiss: () -> Unit,
    onSelected: (String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    val rootCategories = remember(categories) { categories.filter { it.parentId == null } }

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
                text = "Seleccionar categoría",
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
                    SelectableSheetRow(
                        text = "Todas",
                        selected = selectedCategoryId == null,
                        onClick = { onSelected(null) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))

                    rootCategories.forEach { category ->
                        SelectableSheetRow(
                            text = category.name,
                            selected = selectedCategoryId == category.id,
                            onClick = { onSelected(category.id) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TransactionsFiltersHeader(
    state: com.jcadenas.xpendz.ui.viewmodel.TransactionsState,
    monthLabelFormat: SimpleDateFormat,
    onSearch: (String) -> Unit,
    onPreset: (TransactionsPeriodPreset) -> Unit,
    onCustomPeriodSelected: (Long, Long) -> Unit,
    onMonthSelected: (Int, Int) -> Unit,
    onAccountSelected: (String?) -> Unit,
    onCategorySelected: (String?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 0.dp)
    ) {
        var showMonthSheet by remember { mutableStateOf(false) }
        var showAccountsSheet by remember { mutableStateOf(false) }
        var showPeriodSheet by remember { mutableStateOf(false) }
        var showCategorySheet by remember { mutableStateOf(false) }
        var showCustomPeriodSheet by remember { mutableStateOf(false) }

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

        if (showPeriodSheet) {
            PeriodPickerBottomSheet(
                selectedPreset = state.selectedPeriodPreset,
                onDismiss = { showPeriodSheet = false },
                onSelected = { preset ->
                    showPeriodSheet = false
                    onPreset(preset)
                },
                onCustomRequested = {
                    showPeriodSheet = false
                    showCustomPeriodSheet = true
                }
            )
        }

        if (showCustomPeriodSheet) {
            CustomPeriodBottomSheet(
                initialFromEpochSec = state.fromEpochSec,
                initialToEpochSec = state.toEpochSec,
                onDismiss = { showCustomPeriodSheet = false },
                onApply = { from, to ->
                    showCustomPeriodSheet = false
                    onCustomPeriodSelected(from, to)
                }
            )
        }

        if (showCategorySheet) {
            CategoryPickerBottomSheet(
                categories = state.categories,
                selectedCategoryId = state.selectedCategoryId,
                onDismiss = { showCategorySheet = false },
                onSelected = { categoryId ->
                    showCategorySheet = false
                    onCategorySelected(categoryId)
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
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterSelectorChip(
                text = monthLabel,
                icon = Icons.Default.DateRange,
                onClick = { showMonthSheet = true },
                modifier = Modifier.weight(1f)
            )

            FilterSelectorChip(
                text = state.accounts.firstOrNull { it.id == state.selectedAccountId }?.name ?: "Todas",
                icon = Icons.Default.AccountBalanceWallet,
                onClick = { showAccountsSheet = true },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        val periodText = when (state.selectedPeriodPreset) {
            TransactionsPeriodPreset.TODAY -> "Hoy"
            TransactionsPeriodPreset.WEEK -> "Esta semana"
            TransactionsPeriodPreset.MONTH -> "Este mes"
            TransactionsPeriodPreset.CUSTOM -> "Personalizado"
        }

        val categoryText = state.categories
            .firstOrNull { it.id == state.selectedCategoryId }
            ?.name ?: "Todas"

        val rootCategories = remember(state.categories) {
            state.categories.filter { it.parentId == null }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Period selector
            FilterSelectorChip(
                text = periodText,
                icon = Icons.Default.CalendarToday,
                onClick = { showPeriodSheet = true },
                modifier = Modifier.weight(1f)
            )

            // Category selector
            FilterSelectorChip(
                text = categoryText,
                icon = Icons.Default.Category,
                onClick = { showCategorySheet = true },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearch,
            placeholder = {
                Text(
                    "Buscar transacción, categoría o nota...",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
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

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun FilterSelectorChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        shape = MaterialTheme.shapes.extraLarge,
        colors = AssistChipDefaults.assistChipColors(
            leadingIconContentColor = MaterialTheme.colorScheme.primary
        ),
        modifier = modifier.heightIn(min = 36.dp)
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Resumen",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

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
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TransactionsSummaryBalanceRow(
                            balanceText = (if (balancePositive) "+" else "") + balanceText,
                            balancePositive = balancePositive
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                            thickness = 1.dp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TransactionsSummaryMetricColumnWithIcon(
                                title = "Ingresos",
                                value = incomeText,
                                accentColor = Income,
                                icon = Icons.Default.TrendingUp,
                                modifier = Modifier.weight(1f)
                            )
                            TransactionsSummaryMetricColumnWithIcon(
                                title = "Gastos",
                                value = expenseText,
                                accentColor = Expense,
                                icon = Icons.Default.TrendingDown,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionsSummaryMetricColumnWithIcon(
    title: String,
    value: String,
    accentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = accentColor.copy(alpha = 0.12f),
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 13.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Medium,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TransactionsSummaryBalanceRow(
    balanceText: String,
    balancePositive: Boolean
) {
    val balanceColor = if (balancePositive) Income else Expense
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Balance",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                shape = MaterialTheme.shapes.small,
                color = balanceColor.copy(alpha = 0.12f),
                modifier = Modifier.size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (balancePositive) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = balanceColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        Text(
            text = balanceText,
            fontSize = 18.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Bold,
            color = balanceColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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

private data class TransactionDateHeaderUi(
    val text: String,
    val relative: Boolean
)

private fun transactionDayKey(epochSec: Long): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = epochSec * 1000
    return buildString(10) {
        append(cal.get(Calendar.YEAR))
        append('-')
        append(cal.get(Calendar.MONTH) + 1)
        append('-')
        append(cal.get(Calendar.DAY_OF_MONTH))
    }
}

private fun resolveTransactionDateHeaderUi(
    epochSec: Long,
    dayHeaderFormat: SimpleDateFormat
): TransactionDateHeaderUi {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = epochSec * 1000 }
    val yesterday = Calendar.getInstance().apply {
        timeInMillis = now.timeInMillis
        add(Calendar.DAY_OF_YEAR, -1)
    }

    return when {
        isSameDay(target, now) -> TransactionDateHeaderUi(text = "Hoy", relative = true)
        isSameDay(target, yesterday) -> TransactionDateHeaderUi(text = "Ayer", relative = true)
        else -> TransactionDateHeaderUi(
            text = dayHeaderFormat.format(Date(epochSec * 1000)),
            relative = false
        )
    }
}

private fun isSameDay(first: Calendar, second: Calendar): Boolean {
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
}

@Composable
private fun DateGroupHeader(
    text: String,
    relative: Boolean
) {
    val backgroundColor = if (relative) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val contentColor = if (relative) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = if (relative) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
    }

    Surface(
        color = backgroundColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
    ) {
        Text(
            text = text,
            style = if (relative) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun FilterChipsRow(
    accounts: List<com.jcadenas.xpendz.data.local.entity.AccountEntity>,
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
    val displayNote = remember(transaction.kind, transaction.note) {
        formatTransactionNote(transaction.kind, transaction.note)
    }

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
                if (!displayNote.isNullOrBlank()) {
                    Text(
                        displayNote,
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
