package com.jcadenas.xpendz.ui.screens.charts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jcadenas.xpendz.ui.components.CompactHeader
import com.jcadenas.xpendz.ui.components.HamburgerMenu
import com.jcadenas.xpendz.ui.components.HamburgerMenuButton
import com.jcadenas.xpendz.ui.components.SyncSwipeRefresh
import com.jcadenas.xpendz.ui.theme.Expense
import com.jcadenas.xpendz.ui.theme.Income
import com.jcadenas.xpendz.ui.theme.Primary
import com.jcadenas.xpendz.ui.theme.XpendzThemeTokens
import com.jcadenas.xpendz.ui.viewmodel.ChartsDashboardTab
import com.jcadenas.xpendz.ui.viewmodel.ChartsKind
import com.jcadenas.xpendz.ui.viewmodel.ChartsViewMode
import com.jcadenas.xpendz.ui.viewmodel.ChartsViewModel
import com.jcadenas.xpendz.ui.viewmodel.SyncViewModel
import kotlin.math.abs
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTransactions: (route: String) -> Unit,
    onNavigateToAddTransaction: () -> Unit,
    onNavigateToBudget: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit,
    viewModel: ChartsViewModel = hiltViewModel()
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val elevation = XpendzThemeTokens.elevation
    val typography = XpendzThemeTokens.typography

    val state by viewModel.state.collectAsState()
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("es", "CO")) }

    var showFilters by remember { mutableStateOf(false) }
    var showHamburgerMenu by remember { mutableStateOf(false) }

    val syncViewModel: SyncViewModel = hiltViewModel()
    val syncVersion by syncViewModel.syncVersion.collectAsState()

    LaunchedEffect(syncVersion) {
        viewModel.load()
    }

    Scaffold(
        topBar = {
            CompactHeader(
                title = {
                    Text(
                        text = "Resumen financiero",
                        style = typography.titleMedium,
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
                            onNavigateToCharts = { },
                            onNavigateToBudget = onNavigateToBudget,
                            onNavigateToReports = onNavigateToReports,
                            onNavigateToSettings = onNavigateToSettings,
                            onLogout = onLogout,
                            currentScreen = "charts"
                        )
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
            val visibleItems = remember(state.items, state.selectedItemId) {
                val selectedId = state.selectedItemId
                if (selectedId.isNullOrBlank()) {
                    state.items
                } else {
                    state.items.filter { it.id == selectedId }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    ChartsQuickSummaryCard(
                        incomeCents = state.summaryIncomeCents,
                        expenseCents = state.summaryExpenseCents,
                        balanceCents = state.summaryBalanceCents,
                        currencyFormat = currencyFormat
                    )
                }

                item {
                    ChartsInsightsCard(
                        monthIndex = state.selectedMonthIndex,
                        insights = state.insights
                    )
                }

                item {
                    ChartsFiltersChipsBar(
                        state = state,
                        onOpenFilters = { showFilters = true },
                        onYear = { viewModel.updateYear(it) },
                        onKind = { viewModel.updateKind(it) },
                        onView = { viewModel.updateViewMode(it) },
                        onAccount = { viewModel.updateAccount(it) },
                        onMonth = { viewModel.updateMonthIndex(it) },
                        onRootCategory = { viewModel.updateRootCategory(it) },
                        onSubCategory = { viewModel.updateSubCategory(it) },
                        isTrendTab = state.selectedDashboardTab == ChartsDashboardTab.TREND
                    )
                }

                item {
                    ChartsDashboardToggle(
                        selected = state.selectedDashboardTab,
                        onSelect = { viewModel.updateDashboardTab(it) }
                    )
                }

                when (state.selectedDashboardTab) {
                    ChartsDashboardTab.CATEGORIES -> {
                        item {
                            val ringColor = if (state.selectedKind == ChartsKind.INCOME) Income else Expense
                            ChartsDonutCard(
                                totalCents = state.totalAmountCents,
                                items = state.items,
                                currencyFormat = currencyFormat,
                                ringColor = ringColor,
                                selectedItemId = state.selectedItemId,
                                onToggleSelectedItem = { id -> viewModel.toggleSelectedItem(id) }
                            )
                        }
                    }

                    ChartsDashboardTab.TREND -> {
                        item {
                            ChartsTrendCard(
                                year = state.selectedYear,
                                incomeData = state.trendIncomeByMonthCents,
                                expenseData = state.trendExpenseByMonthCents,
                                currencyFormat = currencyFormat
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
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        "No hay datos para este filtro",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    Button(onClick = onNavigateToAddTransaction) {
                                        Text(if (state.selectedKind == ChartsKind.INCOME) "Registrar ingreso" else "Registrar gasto")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    itemsIndexed(visibleItems) { index, item ->
                        val canExpand = state.selectedDashboardTab == ChartsDashboardTab.CATEGORIES &&
                            state.selectedView == ChartsViewMode.ROOT
                        val expanded = canExpand && state.expandedRootItemId == item.id
                        val subItems = if (canExpand) state.subItemsByRootId[item.id].orEmpty() else emptyList()

                        ChartBarRow(
                            index = index,
                            name = item.name,
                            amountCents = item.amountCents,
                            percent = item.percent,
                            currencyFormat = currencyFormat,
                            kind = state.selectedKind,
                            selected = state.selectedItemId == item.id,
                            expandable = canExpand,
                            expanded = expanded,
                            onToggleExpand = {
                                viewModel.toggleExpandedRootItem(item.id)
                            },
                            onNavigateToTransactions = {
                                val (fromEpochSec, toEpochSec) = chartsDateRangeEpochSec(
                                    year = state.selectedYear,
                                    monthIndex = state.selectedMonthIndex
                                )
                                val route = com.jcadenas.xpendz.ui.navigation.NavRoutes.Transactions.createRoute(
                                    accountId = state.selectedAccountId,
                                    categoryId = item.id,
                                    fromEpochSec = fromEpochSec,
                                    toEpochSec = toEpochSec
                                )
                                onNavigateToTransactions(route)
                            },
                            subItems = subItems
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

    if (showFilters) {
        ChartsFiltersBottomSheet(
            state = state,
            onDismiss = { showFilters = false },
            onYear = { viewModel.updateYear(it) },
            onKind = { viewModel.updateKind(it) },
            onView = { viewModel.updateViewMode(it) },
            onAccount = { viewModel.updateAccount(it) },
            onMonth = { viewModel.updateMonthIndex(it) },
            onRootCategory = { viewModel.updateRootCategory(it) },
            onSubCategory = { viewModel.updateSubCategory(it) },
            isTrendTab = state.selectedDashboardTab == ChartsDashboardTab.TREND
        )
    }
}

@Composable
private fun ChartsInsightsCard(
    monthIndex: Int,
    insights: List<com.jcadenas.xpendz.ui.viewmodel.ChartsInsight>
) {
    if (insights.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f))
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            val isCompact = maxWidth < 420.dp
            val items = insights.take(2)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (monthIndex in 1..12) "Insights de este mes" else "Insights",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                if (isCompact) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items.forEach { insight ->
                            InsightCardItem(
                                insight = insight,
                                modifier = Modifier.fillMaxWidth(),
                                compact = true
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items.forEach { insight ->
                            InsightCardItem(
                                insight = insight,
                                modifier = Modifier.weight(1f),
                                compact = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightCardItem(
    insight: com.jcadenas.xpendz.ui.viewmodel.ChartsInsight,
    modifier: Modifier = Modifier,
    compact: Boolean
) {
    val (icon, tint, bg) = when (insight.tone) {
        com.jcadenas.xpendz.ui.viewmodel.ChartsInsightTone.POSITIVE ->
            Triple(Icons.Filled.TrendingUp, Income, Income.copy(alpha = 0.12f))
        com.jcadenas.xpendz.ui.viewmodel.ChartsInsightTone.NEGATIVE ->
            Triple(Icons.Filled.TrendingDown, Expense, Expense.copy(alpha = 0.12f))
        com.jcadenas.xpendz.ui.viewmodel.ChartsInsightTone.NEUTRAL ->
            Triple(Icons.Filled.Insights, Primary, Primary.copy(alpha = 0.10f))
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = bg),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 32.dp else 36.dp)
                    .background(bg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    insight.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (compact) 3 else 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    insight.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (compact) 3 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChartsQuickSummaryCard(
    incomeCents: Long,
    expenseCents: Long,
    balanceCents: Long,
    currencyFormat: NumberFormat
) {
    val incomeText = remember(incomeCents) { currencyFormat.format(incomeCents / 100.0) }
    val expenseText = remember(expenseCents) { currencyFormat.format(expenseCents / 100.0) }
    val balanceText = remember(balanceCents) { currencyFormat.format(balanceCents / 100.0) }

    val balanceFontSize = remember(balanceText) {
        when {
            balanceText.length <= 10 -> 30.sp
            balanceText.length <= 14 -> 26.sp
            balanceText.length <= 18 -> 22.sp
            else -> 20.sp
        }
    }

    val incomeColor = Income
    val expenseColor = Expense

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
        Box(modifier = Modifier.fillMaxWidth()) {
            // Graphical background: curved line + bubbles
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                val w = size.width
                val h = size.height

                // Curved gradient line
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, h * 0.65f)
                    cubicTo(
                        w * 0.25f, h * 0.35f,
                        w * 0.55f, h * 0.80f,
                        w * 0.80f, h * 0.45f
                    )
                    cubicTo(
                        w * 0.90f, h * 0.30f,
                        w * 0.95f, h * 0.25f,
                        w, h * 0.20f
                    )
                }
                drawPath(
                    path = path,
                    color = incomeColor.copy(alpha = 0.13f),
                    style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                )

                // Bubble 1 — income tint, top-right area
                drawCircle(
                    color = incomeColor.copy(alpha = 0.08f),
                    radius = h * 0.55f,
                    center = Offset(w * 0.88f, h * 0.10f)
                )
                // Bubble 2 — expense tint, bottom-right
                drawCircle(
                    color = expenseColor.copy(alpha = 0.06f),
                    radius = h * 0.40f,
                    center = Offset(w * 0.78f, h * 0.90f)
                )
                // Bubble 3 — small, center-left
                drawCircle(
                    color = incomeColor.copy(alpha = 0.05f),
                    radius = h * 0.25f,
                    center = Offset(w * 0.15f, h * 0.80f)
                )
            }

            // Content on top of background
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Resumen",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Balance",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 34.dp)
                    ) {
                        Text(
                            balanceText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = balanceFontSize,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            color = if (balanceCents >= 0) Income else Expense
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            "Ingresos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            incomeText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            color = Income
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            "Gastos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            expenseText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            color = Expense
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartsSummaryRow(
    label: String,
    amountCents: Long,
    currencyFormat: NumberFormat,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            currencyFormat.format(amountCents / 100.0),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun ChartsFiltersChipsBar(
    state: com.jcadenas.xpendz.ui.viewmodel.ChartsState,
    onOpenFilters: () -> Unit,
    onYear: (Int) -> Unit,
    onKind: (ChartsKind) -> Unit,
    onView: (ChartsViewMode) -> Unit,
    onAccount: (String?) -> Unit,
    onMonth: (Int) -> Unit,
    onRootCategory: (String?) -> Unit,
    onSubCategory: (String?) -> Unit,
    isTrendTab: Boolean = false
) {
    val monthLabel = state.months.getOrNull(state.selectedMonthIndex) ?: "TOTAL"
    val monthText = if (state.selectedMonthIndex == 0) {
        "TOTAL ${state.selectedYear}"
    } else {
        "${monthLabel} ${state.selectedYear}"
    }
    val kindText = state.selectedKind.label
    val accountText = state.accounts.firstOrNull { it.id == state.selectedAccountId }?.name ?: "Todas las cuentas"

    val rootCategoryLabel = state.rootCategories.firstOrNull { it.id == state.selectedRootCategoryId }?.name
        ?: "Todas las categorías"
    val subCategoryLabel = state.subCategories.firstOrNull { it.id == state.selectedSubCategoryId }?.name
        ?: "Todas las subcategorías"

    val monthOptions = remember(state.months, state.selectedYear, state.trendByMonthCents) {
        val monthsWithData = state.trendByMonthCents
            .mapIndexedNotNull { idx, cents -> if (cents > 0L) (idx + 1) else null }
            .toSet()

        buildList {
            add(0 to "TOTAL ${state.selectedYear}")
            for (m in 1..12) {
                if (monthsWithData.contains(m)) {
                    val label = state.months.getOrNull(m) ?: continue
                    add(m to "$label ${state.selectedYear}")
                }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DropdownChip(
                    label = monthText,
                    options = monthOptions.map { it.second },
                    onSelected = { selected ->
                        val idx = monthOptions.firstOrNull { it.second == selected }?.first ?: 0
                        onMonth(idx)
                    },
                    leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) }
                )

                if (!isTrendTab) {
                    DropdownChip(
                        label = kindText,
                        options = state.kinds.map { it.label },
                        onSelected = { selected ->
                            state.kinds.firstOrNull { it.label == selected }?.let(onKind)
                        },
                        leadingIcon = { Icon(Icons.Filled.Label, contentDescription = null) }
                    )
                }

                if (!isTrendTab && state.selectedView == ChartsViewMode.SUB) {
                    DropdownChip(
                        label = rootCategoryLabel,
                        options = buildList {
                            add("Todas las categorías")
                            addAll(state.rootCategories.map { it.name })
                        },
                        onSelected = { selected ->
                            if (selected == "Todas las categorías") {
                                onRootCategory(null)
                            } else {
                                onRootCategory(state.rootCategories.firstOrNull { it.name == selected }?.id)
                            }
                        },
                        leadingIcon = { Icon(Icons.Filled.Category, contentDescription = null) }
                    )

                    DropdownChip(
                        label = subCategoryLabel,
                        options = buildList {
                            add("Todas las subcategorías")
                            addAll(state.subCategories.map { it.name })
                        },
                        onSelected = { selected ->
                            if (selected == "Todas las subcategorías") {
                                onSubCategory(null)
                            } else {
                                onSubCategory(state.subCategories.firstOrNull { it.name == selected }?.id)
                            }
                        },
                        leadingIcon = { Icon(Icons.Filled.SubdirectoryArrowRight, contentDescription = null) }
                    )
                }

                DropdownChip(
                    label = accountText,
                    options = buildList {
                        add("Todas las cuentas")
                        addAll(state.accounts.map { it.name })
                    },
                    onSelected = { selected ->
                        if (selected == "Todas las cuentas") {
                            onAccount(null)
                        } else {
                            onAccount(state.accounts.firstOrNull { it.name == selected }?.id)
                        }
                    },
                    leadingIcon = { Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null) }
                )

                if (!isTrendTab) {
                    DropdownChip(
                        label = state.selectedYear.toString(),
                        options = state.years.map { it.toString() },
                        onSelected = { selected -> onYear(selected.toInt()) },
                        leadingIcon = { Icon(Icons.Filled.Event, contentDescription = null) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DropdownChip(
    label: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) },
            leadingIcon = leadingIcon,
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp)) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                labelColor = MaterialTheme.colorScheme.onSurface
            ),
            border = null,
            shape = MaterialTheme.shapes.small
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = {
                            expanded = false
                            onSelected(option)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartsDashboardToggle(
    selected: ChartsDashboardTab,
    onSelect: (ChartsDashboardTab) -> Unit
) {
    val options = remember { ChartsDashboardTab.entries }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f))
    ) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            space = 4.dp
        ) {
            options.forEachIndexed { index, tab ->
                SegmentedButton(
                    selected = selected == tab,
                    onClick = { onSelect(tab) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        inactiveContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    icon = {
                        Icon(
                            imageVector = when (tab) {
                                ChartsDashboardTab.CATEGORIES -> Icons.Default.PieChart
                                ChartsDashboardTab.TREND -> Icons.Default.ShowChart
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                ) {
                    Text(tab.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ChartsDonutCard(
    totalCents: Long,
    items: List<com.jcadenas.xpendz.ui.viewmodel.ChartsItem>,
    currencyFormat: NumberFormat,
    ringColor: Color,
    selectedItemId: String?,
    onToggleSelectedItem: (String) -> Unit
) {
    val top3 = remember(items) { items.take(3) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ChartsDonut(
                        items = items,
                        ringBaseColor = ringColor,
                        selectedItemId = selectedItemId,
                        onToggleSelectedItem = onToggleSelectedItem,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Distribución",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Top categorías",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Total",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            currencyFormat.format(totalCents / 100.0),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = ringColor
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f),
                thickness = 1.dp
            )

            if (top3.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    top3.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(
                                            color = ringColor.copy(alpha = 0.12f),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = ringColor
                                    )
                                }
                                Text(
                                    item.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${String.format(Locale("es"), "%.1f", item.percent * 100)}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    currencyFormat.format(item.amountCents / 100.0),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ringColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartsDonut(
    items: List<com.jcadenas.xpendz.ui.viewmodel.ChartsItem>,
    ringBaseColor: Color,
    selectedItemId: String?,
    onToggleSelectedItem: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val ringWidth = 18.dp
    val density = LocalDensity.current
    val ringWidthPx = remember(density) { with(density) { ringWidth.toPx() } }
    val percentages = items.map { it.percent.coerceIn(0f, 1f) }
    val normalizedTotal = percentages.sum().takeIf { it > 0f } ?: 1f
    val colors = remember(ringBaseColor, items.size) {
        val base = ringBaseColor
        List(items.size) { idx ->
            val t = if (items.size <= 1) 0f else idx.toFloat() / (items.size - 1).toFloat()
            base.copy(alpha = (0.35f + (0.55f * (1f - t))).coerceIn(0.35f, 0.90f))
        }
    }

    val tapModifier = modifier.pointerInput(items, normalizedTotal) {
        detectTapGestures { offset ->
            if (items.isEmpty()) return@detectTapGestures

            val cx = size.width / 2f
            val cy = size.height / 2f
            val dx = offset.x - cx
            val dy = offset.y - cy
            val dist = sqrt(dx * dx + dy * dy)

            val outerR = (minOf(size.width, size.height) / 2f)
            val innerR = (outerR - ringWidthPx).coerceAtLeast(0f)

            if (dist < innerR || dist > outerR) return@detectTapGestures

            var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            angle = (angle + 360f) % 360f
            val startAngle = (270f) % 360f
            val rel = (angle - startAngle + 360f) % 360f

            var acc = 0f
            for (i in items.indices) {
                val sweep = (percentages[i] / normalizedTotal) * 360f
                if (rel >= acc && rel < acc + sweep) {
                    onToggleSelectedItem(items[i].id)
                    return@detectTapGestures
                }
                acc += sweep
            }
        }
    }

    Canvas(modifier = tapModifier) {
        val stroke = Stroke(width = ringWidthPx, cap = StrokeCap.Round)
        val pad = ringWidthPx / 2f
        val rect = Rect(pad, pad, size.width - pad, size.height - pad)
        var start = -90f

        for (i in percentages.indices) {
            val sweep = (percentages[i] / normalizedTotal) * 360f
            val selected = selectedItemId != null && items.getOrNull(i)?.id == selectedItemId
            drawArc(
                color = (
                    if (selected) {
                        colors.getOrElse(i) { ringBaseColor }.copy(alpha = 1f)
                    } else {
                        colors.getOrElse(i) { ringBaseColor }
                    }
                ),
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = rect.topLeft,
                size = rect.size,
                style = if (selected) {
                    Stroke(width = (ringWidthPx * 1.15f), cap = StrokeCap.Round)
                } else {
                    stroke
                }
            )
            start += sweep
        }
    }
}

private fun chartsDateRangeEpochSec(year: Int, monthIndex: Int): Pair<Long?, Long?> {
    val zone = ZoneId.systemDefault()
    return if (monthIndex == 0) {
        val from = LocalDate.of(year, 1, 1).atStartOfDay(zone).toEpochSecond()
        val to = LocalDate.of(year, 12, 31).plusDays(1).atStartOfDay(zone).toEpochSecond() - 1
        from to to
    } else {
        val safeMonth = monthIndex.coerceIn(1, 12)
        val start = LocalDate.of(year, safeMonth, 1)
        val endExclusive = start.plusMonths(1)
        val from = start.atStartOfDay(zone).toEpochSecond()
        val to = endExclusive.atStartOfDay(zone).toEpochSecond() - 1
        from to to
    }
}

@Composable
private fun ChartsTrendCard(
    year: Int,
    incomeData: List<Long>,
    expenseData: List<Long>,
    currencyFormat: NumberFormat
) {
    val monthlyBalances = remember(incomeData, expenseData) {
        incomeData.zip(expenseData).map { (income, expense) -> income - expense }
    }
    val bestMonthIndex = remember(monthlyBalances) {
        monthlyBalances.indices.maxByOrNull { monthlyBalances[it] }
    }
    val worstMonthIndex = remember(monthlyBalances) {
        monthlyBalances.indices.minByOrNull { monthlyBalances[it] }
    }
    val positiveMonths = monthlyBalances.count { it > 0 }
    val negativeMonths = monthlyBalances.count { it < 0 }
    val months = listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Evolución financiera ${year}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = Income,
                                shape = CircleShape
                            )
                    )
                    Text(
                        "Ingresos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = Expense,
                                shape = CircleShape
                            )
                    )
                    Text(
                        "Gastos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            ChartsLineChart(
                incomeData = incomeData,
                expenseData = expenseData,
                currencyFormat = currencyFormat,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
            
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f),
                thickness = 1.dp
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (bestMonthIndex != null) {
                    Column {
                        Text(
                            "Mejor mes",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            months[bestMonthIndex],
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Income
                        )
                    }
                }
                if (worstMonthIndex != null) {
                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            "Peor mes",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            months[worstMonthIndex],
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Expense
                        )
                    }
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Meses positivos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "$positiveMonths",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Income
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        "Meses negativos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "$negativeMonths",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Expense
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartsLineChart(
    incomeData: List<Long>,
    expenseData: List<Long>,
    currencyFormat: NumberFormat,
    modifier: Modifier = Modifier
) {
    val incomeValues = remember(incomeData) { incomeData.takeIf { it.isNotEmpty() } ?: listOf(0L) }
    val expenseValues = remember(expenseData) { expenseData.takeIf { it.isNotEmpty() } ?: listOf(0L) }
    val months = remember {
        listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
    }

    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
    val tooltipTitleColorArgb = MaterialTheme.colorScheme.onSurface.toArgb()
    val tooltipSurfaceColorArgb = MaterialTheme.colorScheme.surface.toArgb()
    val tooltipBorderColorArgb = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f).toArgb()
    val verticalLineColorArgb = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f).toArgb()

    fun niceCeil(value: Double): Double {
        if (value <= 0.0) return 0.0
        val exp = floor(log10(value))
        val base = 10.0.pow(exp)
        val f = value / base
        val niceF = when {
            f <= 1.0 -> 1.0
            f <= 2.0 -> 2.0
            f <= 5.0 -> 5.0
            else -> 10.0
        }
        return niceF * base
    }

    val maxIncome = remember(incomeValues) { incomeValues.maxOrNull()?.toDouble() ?: 0.0 }
    val maxExpense = remember(expenseValues) { expenseValues.maxOrNull()?.toDouble() ?: 0.0 }
    val maxValue = remember(maxIncome, maxExpense) { maxOf(maxIncome, maxExpense) }
    val niceMax = remember(maxValue) {
        niceCeil(maxValue).coerceAtLeast(1.0)
    }
    val ticks = remember(niceMax) {
        val n = 4
        (0..n).map { i -> niceMax * (i.toDouble() / n.toDouble()) }
    }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    Canvas(
        modifier = modifier.pointerInput(incomeValues, niceMax) {
            detectTapGestures { tap ->
                val w = size.width
                val leftPad = 44f
                val rightPad = 10f
                val usableW = (w - leftPad - rightPad).coerceAtLeast(1f)
                val count = incomeValues.size
                val dx = if (count <= 1) 0f else usableW / (count - 1).toFloat()
                val x0 = leftPad

                val idx = if (count <= 1) 0 else ((tap.x - x0) / dx).roundToInt().coerceIn(0, count - 1)
                selectedIndex = if (selectedIndex == idx) null else idx
            }
        }
    ) {
        val w = size.width
        val h = size.height
        val leftPad = 44f
        val rightPad = 10f
        val topPad = 10f
        val bottomPad = 34f

        val usableW = (w - leftPad - rightPad).coerceAtLeast(1f)
        val usableH = (h - topPad - bottomPad).coerceAtLeast(1f)

        val count = incomeValues.size
        val dx = if (count <= 1) 0f else usableW / (count - 1).toFloat()

        fun yFor(v: Double): Float {
            val norm = (v / niceMax).toFloat().coerceIn(0f, 1f)
            return (topPad + usableH * (1f - norm)).coerceIn(topPad, topPad + usableH)
        }

        val axisTextSize = 11.sp.toPx()

        val textPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = labelColor.toArgb()
            textSize = axisTextSize
        }

        val tooltipTextPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = tooltipTitleColorArgb
            textSize = 12.sp.toPx()
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }

        val tooltipSubPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = labelColor.toArgb()
            textSize = 11.sp.toPx()
        }

        val tooltipBgPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = tooltipSurfaceColorArgb
        }

        val tooltipBorderPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 1f
            color = tooltipBorderColorArgb
        }

        ticks.forEach { t ->
            val y = yFor(t)
            drawLine(
                color = gridColor,
                start = Offset(leftPad, y),
                end = Offset(w - rightPad, y),
                strokeWidth = 1f
            )
            val label = currencyFormat.format(t / 100.0)
            drawContext.canvas.nativeCanvas.drawText(
                label,
                0f,
                y + (axisTextSize / 3f),
                textPaint
            )
        }

        var lastIncome: Offset? = null
        for (i in 0 until count) {
            val x = leftPad + dx * i
            val y = yFor(incomeValues[i].toDouble())
            val cur = Offset(x, y)
            val prev = lastIncome
            if (prev != null) {
                drawLine(
                    color = Income,
                    start = prev,
                    end = cur,
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }
            val isSelected = selectedIndex == i
            drawCircle(color = Income, radius = if (isSelected) 6f else 4f, center = cur)
            lastIncome = cur
        }

        var lastExpense: Offset? = null
        for (i in 0 until count) {
            val x = leftPad + dx * i
            val y = yFor(expenseValues[i].toDouble())
            val cur = Offset(x, y)
            val prev = lastExpense
            if (prev != null) {
                drawLine(
                    color = Expense,
                    start = prev,
                    end = cur,
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }
            val isSelected = selectedIndex == i
            drawCircle(color = Expense, radius = if (isSelected) 6f else 4f, center = cur)
            lastExpense = cur
        }

        // X-axis month labels (every 2 months to avoid clutter)
        val monthTextPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = labelColor.toArgb()
            textSize = 11.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val yLabel = h - 6f
        for (i in 0 until min(12, count)) {
            if (i % 2 != 0) continue
            if (i == 0) continue
            val x = leftPad + dx * i
            val label = months.getOrNull(i) ?: ""
            drawContext.canvas.nativeCanvas.drawText(label, x, yLabel, monthTextPaint)
        }

        // Tooltip for selected point
        selectedIndex?.let { idx ->
            val x = leftPad + dx * idx
            val incomeV = incomeValues.getOrNull(idx)?.toDouble() ?: 0.0
            val expenseV = expenseValues.getOrNull(idx)?.toDouble() ?: 0.0
            val y = yFor(incomeV)

            drawLine(
                color = Color(verticalLineColorArgb),
                start = Offset(x, topPad),
                end = Offset(x, topPad + usableH),
                strokeWidth = 1f
            )

            val month = months.getOrNull(idx) ?: ""
            val title = month
            val incomeAmount = currencyFormat.format(incomeV / 100.0)
            val expenseAmount = currencyFormat.format(expenseV / 100.0)
            val balanceAmount = currencyFormat.format((incomeV - expenseV) / 100.0)

            val padX = 12f
            val padY = 10f
            val lineGap = 18f
            val titleW = tooltipTextPaint.measureText(title)
            val incomeW = tooltipSubPaint.measureText(incomeAmount)
            val expenseW = tooltipSubPaint.measureText(expenseAmount)
            val balanceW = tooltipSubPaint.measureText(balanceAmount)
            val boxW = max(titleW, max(incomeW, max(expenseW, balanceW))) + padX * 2
            val boxH = padY * 2 + lineGap * 4

            val preferLeft = x > w * 0.55f
            val boxLeft = (if (preferLeft) x - boxW - 10f else x + 10f)
                .coerceIn(leftPad, w - rightPad - boxW)
            val boxTop = (y - boxH / 2)
                .coerceIn(topPad, topPad + usableH - boxH)
            val boxRect = android.graphics.RectF(boxLeft, boxTop, boxLeft + boxW, boxTop + boxH)

            drawContext.canvas.nativeCanvas.drawRoundRect(boxRect, 16f, 16f, tooltipBgPaint)
            drawContext.canvas.nativeCanvas.drawRoundRect(boxRect, 16f, 16f, tooltipBorderPaint)

            val incomeTextPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = Income.toArgb()
                textSize = 11.sp.toPx()
            }
            val expenseTextPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = Expense.toArgb()
                textSize = 11.sp.toPx()
            }
            
            drawContext.canvas.nativeCanvas.drawText(
                title,
                boxLeft + padX,
                boxTop + padY + 14f,
                tooltipTextPaint
            )
            drawContext.canvas.nativeCanvas.drawText(
                "Ingresos: $incomeAmount",
                boxLeft + padX,
                boxTop + padY + 14f + lineGap,
                incomeTextPaint
            )
            drawContext.canvas.nativeCanvas.drawText(
                "Gastos: $expenseAmount",
                boxLeft + padX,
                boxTop + padY + 14f + lineGap * 2,
                expenseTextPaint
            )
            drawContext.canvas.nativeCanvas.drawText(
                "Balance: $balanceAmount",
                boxLeft + padX,
                boxTop + padY + 14f + lineGap * 3,
                tooltipSubPaint
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChartsFiltersBottomSheet(
    state: com.jcadenas.xpendz.ui.viewmodel.ChartsState,
    onDismiss: () -> Unit,
    onYear: (Int) -> Unit,
    onKind: (ChartsKind) -> Unit,
    onView: (ChartsViewMode) -> Unit,
    onAccount: (String?) -> Unit,
    onMonth: (Int) -> Unit,
    onRootCategory: (String?) -> Unit,
    onSubCategory: (String?) -> Unit,
    isTrendTab: Boolean = false
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 42.dp, height = 4.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            shape = CircleShape
                        )
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Filtros",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .padding(top = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isTrendTab) {
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
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .navigationBarsPadding(),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Text("Aplicar")
            }
            Spacer(modifier = Modifier.height(12.dp))
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
    kind: ChartsKind,
    selected: Boolean,
    expandable: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    subItems: List<com.jcadenas.xpendz.ui.viewmodel.ChartsItem>
) {
    val barColor = if (kind == ChartsKind.INCOME) Income else Expense
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (expandable) {
                    onToggleExpand()
                } else {
                    onNavigateToTransactions()
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (selected) {
                barColor.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
            }
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

            if (expandable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (expanded) "Ocultar subcategorías" else "Ver subcategorías",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(onClick = onNavigateToTransactions, modifier = Modifier.size(34.dp)) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "Ver transacciones",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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

            if (expandable && expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (subItems.isEmpty()) {
                        Text(
                            text = "Sin subcategorías",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        subItems.forEach { sub ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = sub.name,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = currencyFormat.format(sub.amountCents / 100.0),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = barColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
