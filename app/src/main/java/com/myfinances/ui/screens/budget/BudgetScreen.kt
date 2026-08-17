package com.jcadenas.xpendz.ui.screens.budget

import android.app.DatePickerDialog
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jcadenas.xpendz.data.local.entity.BudgetEntity
import com.jcadenas.xpendz.ui.components.CompactHeader
import com.jcadenas.xpendz.ui.components.HamburgerMenu
import com.jcadenas.xpendz.ui.components.HamburgerMenuButton
import com.jcadenas.xpendz.ui.components.SyncSwipeRefresh
import com.jcadenas.xpendz.ui.theme.Income
import com.jcadenas.xpendz.ui.theme.Expense
import com.jcadenas.xpendz.ui.theme.XpendzThemeTokens
import com.jcadenas.xpendz.ui.util.CountryCurrency
import com.jcadenas.xpendz.ui.viewmodel.BudgetViewModel
import com.jcadenas.xpendz.ui.viewmodel.SyncViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCharts: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit,
    initialTab: String = "",
    viewModel: BudgetViewModel = hiltViewModel()
) {
    val initialTabIndex = remember(initialTab) {
        when (initialTab.trim().lowercase()) {
            "goals", "metas" -> 1
            "monthly", "mensual" -> 0
            else -> 0
        }
    }
    var selectedTab by remember { mutableIntStateOf(initialTabIndex) }
    val tabs = listOf("Mensual", "Metas")
    val state by viewModel.state.collectAsState()
    var showHamburgerMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("es", "CO")) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale("es")) }
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val typography = XpendzThemeTokens.typography

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    var showCreateGoal by remember { mutableStateOf(false) }
    var showDeposit by remember { mutableStateOf(false) }
    var depositGoalId by remember { mutableStateOf("") }
    var showWithdraw by remember { mutableStateOf(false) }
    var withdrawGoalId by remember { mutableStateOf("") }

    var showFabMenu by remember { mutableStateOf(false) }
    var showQuickMove by remember { mutableStateOf(false) }
    var quickMoveGoalExpanded by remember { mutableStateOf(false) }
    var quickMoveGoalId by remember { mutableStateOf("") }
    var quickMoveType by remember { mutableStateOf("DEPOSIT") }

    if (showQuickMove) {
        val goals = state.goals
        LaunchedEffect(showQuickMove, goals) {
            if (showQuickMove && quickMoveGoalId.isBlank()) {
                quickMoveGoalId = goals.firstOrNull()?.id.orEmpty()
            }
        }

        AlertDialog(
            onDismissRequest = { showQuickMove = false },
            title = { Text("Registrar movimiento") },
            containerColor = colors.surface,
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BudgetSegmentedTabs(
                        selectedTab = if (quickMoveType == "DEPOSIT") 0 else 1,
                        tabs = listOf("Depositar", "Retirar"),
                        onSelectTab = { idx -> quickMoveType = if (idx == 0) "DEPOSIT" else "WITHDRAW" }
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        val selected = goals.firstOrNull { it.id == quickMoveGoalId }
                        OutlinedTextField(
                            value = selected?.name ?: "Selecciona meta",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Meta") },
                            trailingIcon = {
                                IconButton(onClick = { quickMoveGoalExpanded = true }) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = colors.surface,
                                unfocusedContainerColor = colors.surface,
                                disabledContainerColor = colors.surface,
                                focusedBorderColor = colors.brand,
                                unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        )
                        DropdownMenu(
                            expanded = quickMoveGoalExpanded,
                            onDismissRequest = { quickMoveGoalExpanded = false },
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.extraLarge)
                                .background(Color.White)
                        ) {
                            if (goals.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No hay metas") },
                                    onClick = { quickMoveGoalExpanded = false }
                                )
                            } else {
                                goals.forEach { g ->
                                    DropdownMenuItem(
                                        text = { Text(g.name) },
                                        onClick = {
                                            quickMoveGoalId = g.id
                                            quickMoveGoalExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showQuickMove = false
                        if (quickMoveGoalId.isNotBlank()) {
                            if (quickMoveType == "DEPOSIT") {
                                depositGoalId = quickMoveGoalId
                                showDeposit = true
                            } else {
                                withdrawGoalId = quickMoveGoalId
                                showWithdraw = true
                            }
                        }
                    },
                    enabled = quickMoveGoalId.isNotBlank(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.brand)
                ) {
                    Text("Continuar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickMove = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    var showEditMonthlyLimit by remember { mutableStateOf(false) }
    var editMonthlyCategoryId by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            CompactHeader(
                title = {
                    Text(
                        text = "Presupuesto",
                        style = typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    Box {
                        HamburgerMenuButton(onClick = { showHamburgerMenu = true })
                        HamburgerMenu(
                            expanded = showHamburgerMenu,
                            onDismissRequest = { showHamburgerMenu = false },
                            onNavigateToCharts = onNavigateToCharts,
                            onNavigateToBudget = { },
                            onNavigateToReports = onNavigateToReports,
                            onNavigateToSettings = onNavigateToSettings,
                            onLogout = onLogout,
                            currentScreen = "budget"
                        )
                    }
                }
            )
        }
        ,
        floatingActionButton = {
            if (selectedTab == 1) {
                FloatingActionButton(onClick = { showCreateGoal = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Nueva meta"
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            BudgetSegmentedTabs(
                selectedTab = selectedTab,
                tabs = tabs,
                onSelectTab = { selectedTab = it }
            )

            when (selectedTab) {
                0 -> {
                    val totalLimit = state.monthlyTotalLimitCents
                    val totalSpent = state.monthlyTotalSpentCents
                    val available = totalLimit - totalSpent

                    val monthlyProgress = remember(totalLimit, totalSpent) {
                        if (totalLimit <= 0L) 0f else (totalSpent.toFloat() / totalLimit.toFloat()).coerceAtLeast(0f)
                    }

                    val rootCatsSorted = remember(state.monthlyRootCategories) {
                        state.monthlyRootCategories.sortedBy { it.name }
                    }

                    val monthlyCards = remember(state.monthlyItems, totalLimit) {
                        state.monthlyItems.map { item ->
                            val p = if (item.limitCents <= 0L) 0f else (item.spentCents.toFloat() / item.limitCents.toFloat()).coerceAtLeast(0f)
                            val status = monthlyStatus(p, item.limitCents)
                            MonthlyCategoryCardModel(
                                categoryId = item.categoryId,
                                categoryName = item.categoryName,
                                limitCents = item.limitCents,
                                spentCents = item.spentCents,
                                progress = p,
                                status = status
                            )
                        }.sortedWith(
                            compareByDescending<MonthlyCategoryCardModel> { it.status.priority }
                                .thenByDescending { it.progress }
                                .thenBy { it.categoryName }
                        )
                    }

                    val attentionCards = remember(state.monthlyItems) {
                        state.monthlyItems.mapNotNull { item ->
                            if (item.limitCents <= 0L) return@mapNotNull null
                            val progress = item.spentCents.toFloat() / item.limitCents.toFloat()
                            val severity = monthlyAttentionSeverity(progress, item.limitCents) ?: return@mapNotNull null

                            MonthlyAttentionItem(
                                categoryId = item.categoryId,
                                categoryName = item.categoryName,
                                progress = progress,
                                severity = severity
                            )
                        }.sortedWith(
                            compareByDescending<MonthlyAttentionItem> { it.progress }
                                .thenBy { it.categoryName }
                        ).take(5)
                    }

                    var expandedRootId by remember { mutableStateOf<String?>(null) }
                    var selectedFilter by rememberSaveable { mutableStateOf(CategoryFilter.ALL) }
                    var filterExpanded by remember { mutableStateOf(false) }

                    val filteredMonthlyCards by remember(monthlyCards, selectedFilter) {
                        derivedStateOf {
                            monthlyCards.filter { shouldShowCategory(it, selectedFilter) }
                        }
                    }

                    val budgetInsights by remember(monthlyCards, monthlyProgress, totalLimit, totalSpent) {
                        derivedStateOf {
                            generateBudgetInsights(monthlyCards, monthlyProgress, totalLimit, totalSpent)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(spacing.m),
                        contentPadding = PaddingValues(bottom = spacing.m),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            state.error?.let { err ->
                                Text(err, color = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.height(spacing.xs))
                            }
                        }

                        item {
                            MonthlyGlobalSummaryCard(
                                monthKey = state.monthlyMonth,
                                monthsWithMovements = state.monthlyExpenseMonths,
                                totalLimitCents = totalLimit,
                                totalSpentCents = totalSpent,
                                availableCents = available,
                                progress = monthlyProgress,
                                currencyFormat = currencyFormat,
                                onSelectMonthKey = { targetMonthKey ->
                                    val diff = monthsDiff(state.monthlyMonth, targetMonthKey)
                                    if (diff != 0) viewModel.shiftMonthlyMonth(diff)
                                }
                            )
                        }

                        if (budgetInsights.isNotEmpty()) {
                            item {
                                InsightsSection(insights = budgetInsights, currencyFormat = currencyFormat)
                            }
                        }

                        if (monthlyCards.isEmpty()) {
                            item {
                                MonthlyEmptyState(
                                    onCreateFirst = {
                                        val firstRoot = rootCatsSorted.firstOrNull()
                                        if (firstRoot != null) {
                                            val firstChild = state.monthlyChildrenMap[firstRoot.id]
                                                .orEmpty()
                                                .sortedBy { it.name }
                                                .firstOrNull()
                                            val targetId = firstChild?.id ?: firstRoot.id
                                            editMonthlyCategoryId = targetId
                                            showEditMonthlyLimit = true
                                        }
                                    }
                                )
                            }
                        } else {
                            if (attentionCards.isNotEmpty()) {
                                item {
                                    AttentionNeededSection(items = attentionCards)
                                }
                            }

                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Tus categorías",
                                        style = typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.onSurface
                                    )

                                    Box {
                                        AssistChip(
                                            onClick = { filterExpanded = true },
                                            label = { Text(selectedFilter.label) },
                                            leadingIcon = {
                                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = colors.onSurfaceVariant)
                                            },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = colors.surfaceVariant,
                                                labelColor = colors.onSurface
                                            )
                                        )

                                        DropdownMenu(
                                            expanded = filterExpanded,
                                            onDismissRequest = { filterExpanded = false },
                                            modifier = Modifier.background(colors.surface)
                                        ) {
                                            CategoryFilter.entries.forEach { filter ->
                                                DropdownMenuItem(
                                                    text = { Text(filter.label) },
                                                    onClick = {
                                                        selectedFilter = filter
                                                        filterExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            items(filteredMonthlyCards, key = { "budget_${it.categoryId}" }) { model ->
                                val children = state.monthlySubcategoryItemsByRootId[model.categoryId].orEmpty()
                                val isExpanded = expandedRootId == model.categoryId
                                MonthlyCategoryBudgetCard(
                                    model = model,
                                    currencyFormat = currencyFormat,
                                    expanded = isExpanded,
                                    children = children,
                                    onToggleExpand = {
                                        expandedRootId = if (isExpanded) null else model.categoryId
                                    },
                                    onEditRoot = {
                                        editMonthlyCategoryId = model.categoryId
                                        showEditMonthlyLimit = true
                                    },
                                    onEditChild = { childId ->
                                        editMonthlyCategoryId = childId
                                        showEditMonthlyLimit = true
                                    }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    val totalSavedCents = remember(state.goals, state.goalAccountBalancesCents) {
                        state.goals.sumOf { g -> state.goalAccountBalancesCents[g.id] ?: 0L }
                    }
                    val totalTargetCents = remember(state.goals) {
                        state.goals.sumOf { it.targetCents }
                    }
                    val totalProgress = remember(totalSavedCents, totalTargetCents) {
                        if (totalTargetCents <= 0L) 0f else (totalSavedCents.toFloat() / totalTargetCents.toFloat()).coerceIn(0f, 1f)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        state.error?.let { err ->
                            Text(err, color = MaterialTheme.colorScheme.error)
                        }

                        if (state.goals.size > 1) {
                            GoalsGlobalSummaryCard(
                                totalSavedCents = totalSavedCents,
                                totalTargetCents = totalTargetCents,
                                progress = totalProgress,
                                currencyFormat = currencyFormat
                            )
                        }

                        if (state.goals.isEmpty()) {
                            Text(
                                "No tienes metas. Crea una para empezar a ahorrar.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        state.goals.forEach { goal ->
                            val savedCents = state.goalAccountBalancesCents[goal.id] ?: 0L
                            val remaining = (goal.targetCents - savedCents).coerceAtLeast(0L)
                            val progress = if (goal.targetCents <= 0) 0f else (savedCents.toFloat() / goal.targetCents.toFloat()).coerceIn(0f, 1f)
                            val now = System.currentTimeMillis() / 1000
                            val monthsLeft = viewModel.monthsUntil(goal.targetDateEpochSec, now)
                            val suggestedMonthly = if (remaining <= 0) 0L else (remaining / monthsLeft)

                            GoalModernCard(
                                title = goal.name,
                                savedCents = savedCents,
                                targetCents = goal.targetCents,
                                remainingCents = remaining,
                                progress = progress,
                                targetDateEpochSec = goal.targetDateEpochSec,
                                currencyFormat = currencyFormat,
                                dateFormat = dateFormat,
                                suggestedMonthlyCents = suggestedMonthly,
                                monthsLeft = monthsLeft,
                                onDeposit = {
                                    depositGoalId = goal.id
                                    showDeposit = true
                                },
                                onWithdraw = {
                                    withdrawGoalId = goal.id
                                    showWithdraw = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditMonthlyLimit) {
        val catName = remember(state.monthlyRootCategories, state.monthlyChildrenMap, editMonthlyCategoryId) {
            val root = state.monthlyRootCategories.firstOrNull { it.id == editMonthlyCategoryId }
            if (root != null) {
                root.name
            } else {
                state.monthlyChildrenMap.values
                    .asSequence()
                    .flatten()
                    .firstOrNull { it.id == editMonthlyCategoryId }
                    ?.name
            }
        }

        val currentLimitCents = state.monthlyLimitsByCategoryId[editMonthlyCategoryId] ?: 0L
        var limitText by remember {
            mutableStateOf(if (currentLimitCents > 0) (currentLimitCents / 100.0).toString() else "")
        }

        AlertDialog(
            onDismissRequest = { showEditMonthlyLimit = false },
            title = { Text("Límite mensual", color = colors.onSurface, style = typography.titleLarge) },
            containerColor = colors.surface,
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(spacing.m)
                ) {
                    Text(catName ?: "", color = colors.onSurface, style = typography.bodyMedium)
                    OutlinedTextField(
                        value = limitText,
                        onValueChange = { limitText = it },
                        label = { Text("Límite") },
                        leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null, tint = colors.brand) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(shapes.extraLarge),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface,
                            disabledContainerColor = colors.surface,
                            focusedBorderColor = colors.brand,
                            unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    )
                    Text(
                        "Deja vacío para no establecer límite.",
                        color = colors.onSurfaceVariant.copy(alpha = 0.85f),
                        style = typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cents = if (limitText.isBlank()) {
                            0L
                        } else {
                            runCatching { (limitText.toDouble() * 100).toLong() }.getOrNull() ?: 0L
                        }
                        viewModel.upsertMonthlyLimit(editMonthlyCategoryId, cents)
                        showEditMonthlyLimit = false
                    },
                    enabled = editMonthlyCategoryId.isNotBlank() && !state.isLoading,
                    shape = RoundedCornerShape(shapes.extraLarge),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.brand)
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditMonthlyLimit = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showCreateGoal) {
        var goalName by remember { mutableStateOf("") }
        var goalAmountText by remember { mutableStateOf("") }
        val deviceCountry = remember { Locale.getDefault().country }
        val defaultCurrency = remember(deviceCountry) {
            CountryCurrency.suggestedCurrency(deviceCountry)
        }
        var selectedCurrency by remember { mutableStateOf(defaultCurrency) }
        var currencyExpanded by remember { mutableStateOf(false) }
        var currencyQuery by remember { mutableStateOf("") }
        val currencySearchFocusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current

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

        var targetDateEpochSec by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
        var showDatePicker by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCreateGoal = false },
            containerColor = colors.surface,
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(spacing.m)
                ) {
                    GoalDialogHeader(
                        title = "Nueva meta",
                        subtitle = "Define un objetivo de ahorro."
                    )
                    OutlinedTextField(
                        value = goalAmountText,
                        onValueChange = { goalAmountText = it },
                        label = { Text("Monto objetivo") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.AttachMoney,
                                contentDescription = null,
                                tint = colors.brand
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = colors.surfaceVariant.copy(alpha = 0.3f),
                            disabledContainerColor = colors.surfaceVariant.copy(alpha = 0.3f),
                            focusedBorderColor = colors.brand,
                            unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    )
                    OutlinedTextField(
                        value = goalName,
                        onValueChange = { goalName = it },
                        label = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface,
                            disabledContainerColor = colors.surface,
                            focusedBorderColor = colors.brand,
                            unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    )

                    OutlinedTextField(
                        value = allCurrencies.find { it.first == selectedCurrency }?.second ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Moneda") },
                        placeholder = { Text("Selecciona moneda", color = colors.onSurfaceVariant.copy(alpha = 0.6f)) },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    currencyExpanded = !currencyExpanded
                                    if (!currencyExpanded) currencyQuery = ""
                                }
                            ) {
                                Icon(Icons.Default.Savings, contentDescription = null, tint = colors.brand)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface,
                            disabledContainerColor = colors.surface,
                            focusedBorderColor = colors.brand,
                            unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    )

                    if (currencyExpanded) {
                        LaunchedEffect(Unit) {
                            currencySearchFocusRequester.requestFocus()
                            keyboardController?.show()
                        }

                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(containerColor = colors.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .imePadding()
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
                                            .heightIn(min = 180.dp, max = 260.dp),
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

                    OutlinedTextField(
                        value = dateFormat.format(Date(targetDateEpochSec * 1000)),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Fecha objetivo") },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = colors.brand)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface,
                            disabledContainerColor = colors.surface,
                            focusedBorderColor = colors.brand,
                            unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetCents = runCatching { (goalAmountText.toDouble() * 100).toLong() }.getOrNull() ?: 0L
                        if (goalName.isNotBlank() && targetCents > 0) {
                            viewModel.createGoal(
                                name = goalName.trim(),
                                targetCents = targetCents,
                                targetDateEpochSec = targetDateEpochSec,
                                currency = selectedCurrency
                            )
                            showCreateGoal = false
                        }
                    },
                    enabled = goalName.isNotBlank() && goalAmountText.isNotBlank() && !state.isLoading,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.brand)
                ) {
                    Text("Crear")
                }
            },
            dismissButton = {
                FilledTonalButton(
                    onClick = { showCreateGoal = false },
                    shape = MaterialTheme.shapes.extraLarge
                ) { Text("Cancelar") }
            }
        )

        if (showDatePicker) {
            val cal = Calendar.getInstance().apply { timeInMillis = targetDateEpochSec * 1000 }
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    val c = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                        set(Calendar.DAY_OF_MONTH, day)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    targetDateEpochSec = c.timeInMillis / 1000
                    showDatePicker = false
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).apply {
                setOnCancelListener { showDatePicker = false }
            }.show()
        }
    }

    if (showDeposit) {
        val goal = state.goals.firstOrNull { it.id == depositGoalId }
        var fromExpanded by remember { mutableStateOf(false) }
        var fromAccountId by remember { mutableStateOf("") }
        var amountText by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        var occurredAtEpochSec by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
        var showDatePicker by remember { mutableStateOf(false) }

        val fromAccounts = remember(state.accounts, goal) {
            val goalAccountId = goal?.accountId
            if (goalAccountId.isNullOrBlank()) state.accounts else state.accounts.filterNot { it.id == goalAccountId }
        }

        LaunchedEffect(goal?.id) {
            if (fromAccountId.isBlank()) {
                fromAccountId = fromAccounts.firstOrNull()?.id ?: ""
            }
        }

        AlertDialog(
            onDismissRequest = { showDeposit = false },
            containerColor = colors.surface,
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    GoalDialogHeader(
                        title = "Depositar a meta",
                        subtitle = "Agrega dinero a esta meta."
                    )

                    Text(
                        text = goal?.name ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.brand
                    )

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Monto") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.AttachMoney,
                                contentDescription = null,
                                tint = colors.brand
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface,
                            disabledContainerColor = colors.surfaceVariant.copy(alpha = 0.3f),
                            focusedBorderColor = colors.brand,
                            unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        val selected = fromAccounts.find { it.id == fromAccountId }
                        val selectedBalance = selected?.id?.let { state.accountBalancesCents[it] } ?: 0L
                        OutlinedTextField(
                            value = if (selected == null) "" else "${selected.name} (${currencyFormat.format(selectedBalance / 100.0)})",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Cuenta origen") },
                            placeholder = { Text("Selecciona cuenta") },
                            trailingIcon = {
                                IconButton(onClick = { fromExpanded = true }) {
                                    Icon(Icons.Default.Savings, contentDescription = null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = colors.surface,
                                unfocusedContainerColor = colors.surface,
                                disabledContainerColor = colors.surface,
                                focusedBorderColor = colors.brand,
                                unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        )
                        DropdownMenu(
                            expanded = fromExpanded,
                            onDismissRequest = { fromExpanded = false },
                            modifier = Modifier.background(colors.surface)
                        ) {
                            fromAccounts.forEach { account ->
                                val bal = state.accountBalancesCents[account.id] ?: 0L
                                DropdownMenuItem(
                                    text = { Text("${account.name} (${currencyFormat.format(bal / 100.0)})") },
                                    onClick = {
                                        fromAccountId = account.id
                                        fromExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = dateFormat.format(Date(occurredAtEpochSec * 1000)),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Fecha") },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = colors.brand)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker = true },
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface,
                            disabledContainerColor = colors.surface,
                            focusedBorderColor = colors.brand,
                            unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    )

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Nota (opcional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cents = runCatching { (amountText.toDouble() * 100).toLong() }.getOrNull() ?: 0L
                        if (goal != null && fromAccountId.isNotBlank() && cents > 0) {
                            viewModel.depositToGoal(
                                goalId = goal.id,
                                fromAccountId = fromAccountId,
                                amountCents = cents,
                                occurredAtEpochSec = occurredAtEpochSec,
                                note = note.ifBlank { null }
                            )
                            showDeposit = false
                        }
                    },
                    enabled = goal != null && fromAccountId.isNotBlank() && amountText.isNotBlank() && !state.isLoading
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeposit = false }) {
                    Text("Cancelar")
                }
            }
        )

        if (showDatePicker) {
            val cal = Calendar.getInstance().apply { timeInMillis = occurredAtEpochSec * 1000 }
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    val c = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                        set(Calendar.DAY_OF_MONTH, day)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    occurredAtEpochSec = c.timeInMillis / 1000
                    showDatePicker = false
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).apply {
                setOnCancelListener { showDatePicker = false }
            }.show()
        }
    }

    if (showWithdraw) {
        val goal = state.goals.firstOrNull { it.id == withdrawGoalId }
        var toExpanded by remember { mutableStateOf(false) }
        var toAccountId by remember { mutableStateOf("") }
        var amountText by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        var occurredAtEpochSec by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
        var showDatePicker by remember { mutableStateOf(false) }

        val toAccounts = remember(state.accounts, goal) {
            val goalAccountId = goal?.accountId
            if (goalAccountId.isNullOrBlank()) state.accounts else state.accounts.filterNot { it.id == goalAccountId }
        }

        LaunchedEffect(goal?.id) {
            if (toAccountId.isBlank()) {
                toAccountId = toAccounts.firstOrNull()?.id ?: ""
            }
        }

        AlertDialog(
            onDismissRequest = { showWithdraw = false },
            containerColor = colors.surface,
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    GoalDialogHeader(
                        title = "Retirar de meta",
                        subtitle = "Retira dinero de esta meta."
                    )

                    Text(
                        text = goal?.name ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.brand
                    )

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Monto") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.AttachMoney,
                                contentDescription = null,
                                tint = colors.brand
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface,
                            disabledContainerColor = colors.surfaceVariant.copy(alpha = 0.3f),
                            focusedBorderColor = colors.brand,
                            unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        val selected = toAccounts.find { it.id == toAccountId }
                        val selectedBalance = selected?.id?.let { state.accountBalancesCents[it] } ?: 0L
                        OutlinedTextField(
                            value = if (selected == null) "" else "${selected.name} (${currencyFormat.format(selectedBalance / 100.0)})",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Cuenta destino") },
                            placeholder = { Text("Selecciona cuenta") },
                            trailingIcon = {
                                IconButton(onClick = { toExpanded = true }) {
                                    Icon(Icons.Default.Savings, contentDescription = null, tint = colors.brand)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = colors.surface,
                                unfocusedContainerColor = colors.surface,
                                disabledContainerColor = colors.surface,
                                focusedBorderColor = colors.brand,
                                unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        )
                        DropdownMenu(
                            expanded = toExpanded,
                            onDismissRequest = { toExpanded = false },
                            modifier = Modifier.background(colors.surface)
                        ) {
                            toAccounts.forEach { account ->
                                val bal = state.accountBalancesCents[account.id] ?: 0L
                                DropdownMenuItem(
                                    text = { Text("${account.name} (${currencyFormat.format(bal / 100.0)})") },
                                    onClick = {
                                        toAccountId = account.id
                                        toExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = dateFormat.format(Date(occurredAtEpochSec * 1000)),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Fecha") },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = colors.brand)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker = true },
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface,
                            disabledContainerColor = colors.surface,
                            focusedBorderColor = colors.brand,
                            unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    )

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Nota (opcional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cents = runCatching { (amountText.toDouble() * 100).toLong() }.getOrNull() ?: 0L
                        if (goal != null && toAccountId.isNotBlank() && cents > 0) {
                            viewModel.withdrawFromGoal(
                                goalId = goal.id,
                                toAccountId = toAccountId,
                                amountCents = cents,
                                occurredAtEpochSec = occurredAtEpochSec,
                                note = note.ifBlank { null }
                            )
                            showWithdraw = false
                        }
                    },
                    enabled = goal != null && toAccountId.isNotBlank() && amountText.isNotBlank() && !state.isLoading
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWithdraw = false }) {
                    Text("Cancelar")
                }
            }
        )

        if (showDatePicker) {
            val cal = Calendar.getInstance().apply { timeInMillis = occurredAtEpochSec * 1000 }
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    val c = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                        set(Calendar.DAY_OF_MONTH, day)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    occurredAtEpochSec = c.timeInMillis / 1000
                    showDatePicker = false
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).apply {
                setOnCancelListener { showDatePicker = false }
            }.show()
        }
    }
}

@Composable
private fun BudgetSegmentedTabs(
    selectedTab: Int,
    tabs: List<String>,
    onSelectTab: (Int) -> Unit
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes

    val leftSelected = selectedTab == 0
    val leftBg by animateColorAsState(if (leftSelected) colors.brand.copy(alpha = 0.12f) else colors.surfaceVariant.copy(alpha = 0.5f), label = "budgetTabLeftBg")
    val rightBg by animateColorAsState(if (!leftSelected) colors.brand.copy(alpha = 0.12f) else colors.surfaceVariant.copy(alpha = 0.5f), label = "budgetTabRightBg")
    val leftFg = if (leftSelected) colors.brand else colors.onSurfaceVariant
    val rightFg = if (!leftSelected) colors.brand else colors.onSurfaceVariant

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.m, vertical = 10.dp),
        color = colors.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(shapes.extraLarge)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(shapes.extraLarge))
                    .background(leftBg)
                    .clickable { onSelectTab(0) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(tabs.getOrElse(0) { "" }, color = leftFg, fontWeight = FontWeight.SemiBold)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(shapes.extraLarge))
                    .background(rightBg)
                    .clickable { onSelectTab(1) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(tabs.getOrElse(1) { "" }, color = rightFg, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun GoalsGlobalSummaryCard(
    totalSavedCents: Long,
    totalTargetCents: Long,
    progress: Float,
    currencyFormat: NumberFormat
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val elevation = XpendzThemeTokens.elevation
    val typography = XpendzThemeTokens.typography

    val pct = (progress * 100).toInt().coerceIn(0, 100)
    val pctColor = goalProgressColor(progress)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xxs),
        shape = RoundedCornerShape(shapes.extraLarge),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation.level2)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.s, vertical = spacing.s + spacing.xxs / 2)
        ) {
            Text(
                text = "Tus metas",
                style = typography.titleMedium,
                fontWeight = FontWeight.Normal,
                color = colors.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(spacing.s + spacing.xxs / 2))

            Surface(
                color = colors.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(shapes.extraLarge),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    GoalsSummaryBackgroundGraph(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(horizontal = spacing.xxs, vertical = spacing.xxs)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.s, vertical = spacing.s),
                        verticalArrangement = Arrangement.spacedBy(spacing.s)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Progreso general",
                                    style = typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }

                            Surface(
                                color = pctColor.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(shapes.extraLarge)
                            ) {
                                Text(
                                    "$pct%",
                                    modifier = Modifier.padding(horizontal = spacing.m, vertical = spacing.xs),
                                    color = pctColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        SummaryLine(label = "Ahorrado total", value = currencyFormat.format(totalSavedCents / 100.0), valueColor = Income)
                        SummaryLine(label = "Objetivo total", value = currencyFormat.format(totalTargetCents / 100.0), valueColor = colors.onSurface)

                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(spacing.s)
                                .clip(RoundedCornerShape(shapes.extraLarge)),
                            color = goalProgressColor(progress),
                            trackColor = Color(0xFFE9EEF6)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalsSummaryBackgroundGraph(
    modifier: Modifier = Modifier
) {
    val colors = XpendzThemeTokens.colors

    val primaryOverlay = colors.brand.copy(alpha = 0.08f)
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        // Curved line path
        val linePath = androidx.compose.ui.graphics.Path().apply {
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

        // Decorative circles (bubbles)
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
private fun SummaryLine(
    label: String,
    value: String,
    valueColor: Color
) {
    val colors = XpendzThemeTokens.colors
    val typography = XpendzThemeTokens.typography

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = colors.onSurfaceVariant, style = typography.bodySmall)
        Text(value, color = valueColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GoalModernCard(
    title: String,
    savedCents: Long,
    targetCents: Long,
    remainingCents: Long,
    progress: Float,
    targetDateEpochSec: Long,
    currencyFormat: NumberFormat,
    dateFormat: SimpleDateFormat,
    suggestedMonthlyCents: Long,
    monthsLeft: Int,
    onDeposit: () -> Unit,
    onWithdraw: () -> Unit
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val elevation = XpendzThemeTokens.elevation
    val typography = XpendzThemeTokens.typography

    val pct = (progress.coerceIn(0f, 1f) * 100).toInt()
    val pctColor = goalProgressColor(progress)
    val remainingText = currencyFormat.format(remainingCents / 100.0)
    val targetText = currencyFormat.format(targetCents / 100.0)
    val savedText = currencyFormat.format(savedCents / 100.0)
    val dateText = dateFormat.format(Date(targetDateEpochSec * 1000))

    val nowEpochSec = System.currentTimeMillis() / 1000
    val daysLeft = remember(targetDateEpochSec, nowEpochSec) {
        ((targetDateEpochSec - nowEpochSec) / 86400).toInt().coerceAtLeast(1)
    }
    val suggestedDailyCents = remember(remainingCents, daysLeft) {
        if (remainingCents <= 0L) 0L else (remainingCents / daysLeft.toLong()).coerceAtLeast(0L)
    }
    val (motivationalTitle, motivationalDescription) = getMotivationalMessage(progress)
    val timeRemainingText = getTimeRemainingText(targetDateEpochSec)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(shapes.extraLarge),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = elevation.level1),
        colors = CardDefaults.elevatedCardColors(containerColor = colors.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.m, vertical = spacing.s + spacing.xxs / 2),
            verticalArrangement = Arrangement.spacedBy(spacing.s)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(spacing.s))
                Text(
                    text = "$pct%",
                    style = typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = pctColor
                )
            }

            Text(
                text = "$savedText / $targetText",
                color = colors.onSurfaceVariant,
                style = typography.bodyMedium
            )

            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(spacing.s)
                    .clip(RoundedCornerShape(shapes.extraLarge)),
                color = pctColor,
                trackColor = Color(0xFFE9EEF6)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Faltan: $remainingText",
                    style = typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs)
                ) {
                    Text(
                        text = "📅 $timeRemainingText",
                        style = typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    Text(
                        text = dateText,
                        style = typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            Surface(
                color = pctColor.copy(alpha = 0.08f),
                shape = RoundedCornerShape(shapes.extraLarge)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.m, vertical = spacing.s),
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs)
                ) {
                    Text(
                        motivationalTitle,
                        fontWeight = FontWeight.Bold,
                        color = pctColor,
                        style = typography.bodyMedium
                    )
                    Text(
                        motivationalDescription,
                        color = colors.onSurfaceVariant,
                        style = typography.bodySmall
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.s)
            ) {
                Button(
                    onClick = onDeposit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(shapes.extraLarge),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.brand)
                ) {
                    Text("+ Depositar")
                }
                OutlinedButton(
                    onClick = onWithdraw,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(shapes.extraLarge)
                ) {
                    Text("Retirar")
                }
            }
        }
    }
}

private fun goalProgressColor(progress: Float): Color {
    return when {
        progress >= 0.75f -> Color(0xFF16A34A)
        progress >= 0.35f -> Color(0xFFF59E0B)
        else -> Color(0xFF2463EB)
    }
}

private fun getMotivationalMessage(progress: Float): Pair<String, String> {
    val pct = (progress * 100).toInt()
    return when {
        progress >= 0.9f -> {
            "🔥 Último esfuerzo" to "Estás muy cerca de completar esta meta."
        }
        progress >= 0.75f -> {
            "🚀 Excelente avance" to "Ya recorriste tres cuartas partes del camino. Tu meta está cada vez más cerca."
        }
        progress >= 0.5f -> {
            "🎯 Vas por buen camino" to "Ya completaste el $pct% de tu meta. Manteniendo tu ritmo actual podrás alcanzarla antes de la fecha objetivo."
        }
        progress >= 0.35f -> {
            "📈 Buen progreso" to "Ya has avanzado significativamente. Continúa así para lograr tu meta."
        }
        else -> {
            "🌱 Comienza el camino" to "Cada pequeño aporte te acerca a tu objetivo. ¡Sigue adelante!"
        }
    }
}

private fun getTimeRemainingText(targetDateEpochSec: Long): String {
    val nowEpochSec = System.currentTimeMillis() / 1000
    val secondsLeft = (targetDateEpochSec - nowEpochSec).coerceAtLeast(0)
    val daysLeft = (secondsLeft / 86400).toInt()
    val monthsLeft = daysLeft / 30

    return when {
        monthsLeft >= 12 -> "${monthsLeft / 12} año(s) restante(s)"
        monthsLeft >= 1 -> "$monthsLeft mes(es) restante(s)"
        daysLeft >= 1 -> "$daysLeft día(s) restante(s)"
        else -> "Último día"
    }
}

@Composable
private fun GoalDialogHeader(
    title: String,
    subtitle: String,
    intentColor: Color? = null
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val typography = XpendzThemeTokens.typography

    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.xxs)
    ) {
        Text(
            text = title,
            style = typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = intentColor ?: colors.onSurface
        )
        Text(
            text = subtitle,
            style = typography.bodyMedium,
            color = colors.onSurfaceVariant.copy(alpha = 0.85f)
        )
    }
}

private enum class MonthlyStatus(val label: String, val priority: Int) {
    OK("Dentro del presupuesto", 1),
    RISK("Cerca del límite", 2),
    NEAR_LIMIT("Casi excedido", 3),
    EXCEEDED("Presupuesto excedido", 4)
}

private enum class CategoryFilter(val label: String) {
    ALL("Todas"),
    AT_RISK("En riesgo"),
    EXCEEDED("Excedidas"),
    NO_BUDGET("Sin presupuesto")
}

private enum class InsightType(val priority: Int, val label: String, val color: Color) {
    RISK(1, "Crítico", Color(0xFFEF4444)),
    WARNING(2, "Advertencia", Color(0xFFF97316)),
    SAVING(3, "Positivo", Color(0xFF16A34A)),
    TREND(4, "Tendencia", Color(0xFF2463EB)),
    PROJECTION(5, "Proyección", Color(0xFF8B5CF6))
}

private data class BudgetInsight(
    val type: InsightType,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val monetaryDifference: Long? = null,
    val percentage: Int? = null
)

private fun generateExceededCategoryInsights(items: List<MonthlyCategoryCardModel>): List<BudgetInsight> {
    return items
        .filter { it.status == MonthlyStatus.EXCEEDED }
        .sortedByDescending { it.progress }
        .take(1)
        .map { item ->
            val pct = (item.progress * 100).toInt()
            val excessCents = item.spentCents - item.limitCents
            BudgetInsight(
                type = InsightType.RISK,
                title = item.categoryName,
                description = "Alcanzó el $pct% de su presupuesto",
                icon = Icons.Default.Warning,
                monetaryDifference = excessCents,
                percentage = pct
            )
        }
}

private fun generateNearLimitCategoryInsights(items: List<MonthlyCategoryCardModel>): List<BudgetInsight> {
    return items
        .filter { it.status == MonthlyStatus.NEAR_LIMIT }
        .sortedByDescending { it.progress }
        .take(1)
        .map { item ->
            val pct = (item.progress * 100).toInt()
            val remainingCents = item.limitCents - item.spentCents
            BudgetInsight(
                type = InsightType.WARNING,
                title = item.categoryName,
                description = "Está al $pct% de su presupuesto",
                icon = Icons.Default.Info,
                monetaryDifference = remainingCents,
                percentage = pct
            )
        }
}

private fun generateMonthlyConsumptionInsight(progress: Float, totalLimitCents: Long, totalSpentCents: Long): BudgetInsight? {
    if (totalLimitCents <= 0L) return null
    val pct = (progress * 100).toInt()
    val remainingCents = totalLimitCents - totalSpentCents
    return BudgetInsight(
        type = InsightType.TREND,
        title = "Consumo mensual",
        description = "Ya consumiste el $pct% del presupuesto",
        icon = Icons.Default.AttachMoney,
        monetaryDifference = remainingCents,
        percentage = pct
    )
}

private fun generateEndOfMonthProjectionInsight(progress: Float, totalLimitCents: Long, totalSpentCents: Long): BudgetInsight? {
    if (totalLimitCents <= 0L) return null
    val pct = (progress * 100).toInt()
    val projectedPct = (pct * 2).coerceAtMost(999)
    val projectedExcessCents = (totalSpentCents * 2) - totalLimitCents
    return BudgetInsight(
        type = InsightType.PROJECTION,
        title = "Proyección fin de mes",
        description = "Si continúas a este ritmo utilizarás el $projectedPct%",
        icon = Icons.Default.TrendingUp,
        monetaryDifference = if (projectedExcessCents > 0) projectedExcessCents else null,
        percentage = projectedPct
    )
}

private fun generateBudgetInsights(
    items: List<MonthlyCategoryCardModel>,
    progress: Float,
    totalLimitCents: Long,
    totalSpentCents: Long
): List<BudgetInsight> {
    val insights = mutableListOf<BudgetInsight>()

    insights.addAll(generateExceededCategoryInsights(items))
    insights.addAll(generateNearLimitCategoryInsights(items))

    generateMonthlyConsumptionInsight(progress, totalLimitCents, totalSpentCents)?.let { insights.add(it) }
    generateEndOfMonthProjectionInsight(progress, totalLimitCents, totalSpentCents)?.let { insights.add(it) }

    return insights
        .sortedBy { it.type.priority }
        .take(3)
}

@Composable
private fun InsightsSection(insights: List<BudgetInsight>, currencyFormat: NumberFormat) {
    if (insights.isEmpty()) return

    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val typography = XpendzThemeTokens.typography

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            colors.onSurfaceVariant.copy(alpha = 0.10f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = spacing.s),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "🧠 Resumen inteligente",
                style = typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )

            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                insights.forEach { insight ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = insight.type.color.copy(alpha = 0.08f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            insight.type.color.copy(alpha = 0.20f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.s, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(spacing.s),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = insight.type.color.copy(alpha = 0.15f)
                            ) {
                                Icon(
                                    imageVector = insight.icon,
                                    contentDescription = null,
                                    tint = insight.type.color,
                                    modifier = Modifier.padding(spacing.xs).size(20.dp)
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = insight.title,
                                    style = typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onSurface
                                )
                                Text(
                                    text = insight.description,
                                    style = typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                insight.percentage?.let { pct ->
                                    Text(
                                        text = "$pct%",
                                        style = typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = insight.type.color
                                    )
                                }
                                insight.monetaryDifference?.let { diffCents ->
                                    val sign = if (diffCents >= 0) "+" else ""
                                    val diffValue = currencyFormat.format(diffCents / 100.0)
                                    Text(
                                        text = "$sign$diffValue",
                                        style = typography.labelSmall,
                                        color = if (diffCents >= 0) colors.onSurfaceVariant else insight.type.color,
                                        fontWeight = if (diffCents < 0) FontWeight.SemiBold else FontWeight.Normal
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

private fun shouldShowCategory(model: MonthlyCategoryCardModel, filter: CategoryFilter): Boolean {
    return when (filter) {
        CategoryFilter.ALL -> true
        CategoryFilter.AT_RISK -> model.status == MonthlyStatus.RISK || model.status == MonthlyStatus.NEAR_LIMIT
        CategoryFilter.EXCEEDED -> model.status == MonthlyStatus.EXCEEDED
        CategoryFilter.NO_BUDGET -> model.limitCents <= 0L
    }
}

private data class MonthlyCategoryCardModel(
    val categoryId: String,
    val categoryName: String,
    val limitCents: Long,
    val spentCents: Long,
    val progress: Float,
    val status: MonthlyStatus
)

private enum class MonthlyAttentionSeverity(val label: String, val color: Color) {
    WARNING("Advertencia", Color(0xFFF59E0B)),
    EXCEEDED("Excedida", Color(0xFFEF4444))
}

private data class MonthlyAttentionItem(
    val categoryId: String,
    val categoryName: String,
    val progress: Float,
    val severity: MonthlyAttentionSeverity
)

private fun monthlyStatus(progress: Float, limitCents: Long): MonthlyStatus {
    if (limitCents <= 0L) return MonthlyStatus.OK
    return when {
        progress > 1f -> MonthlyStatus.EXCEEDED
        progress >= 0.9f -> MonthlyStatus.NEAR_LIMIT
        progress >= 0.7f -> MonthlyStatus.RISK
        else -> MonthlyStatus.OK
    }
}

private fun monthlyAttentionSeverity(progress: Float, limitCents: Long): MonthlyAttentionSeverity? {
    if (limitCents <= 0L) return null
    return when {
        progress >= 1f -> MonthlyAttentionSeverity.EXCEEDED
        progress >= 0.9f -> MonthlyAttentionSeverity.WARNING
        else -> null
    }
}

private fun monthlyStatusColor(status: MonthlyStatus): Color {
    return when (status) {
        MonthlyStatus.OK -> Color(0xFF16A34A)
        MonthlyStatus.RISK -> Color(0xFFF59E0B)
        MonthlyStatus.NEAR_LIMIT -> Color(0xFFF97316)
        MonthlyStatus.EXCEEDED -> Color(0xFFEF4444)
    }
}

@Composable
private fun AttentionNeededSection(
    items: List<MonthlyAttentionItem>
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val typography = XpendzThemeTokens.typography

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.xs)
    ) {
        Text(
            text = "⚠ Requieren atención",
            style = typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface
        )

        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            items.forEach { item ->
                AttentionNeededItemRow(item = item)
            }
        }
    }
}

@Composable
private fun AttentionNeededItemRow(
    item: MonthlyAttentionItem
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val typography = XpendzThemeTokens.typography

    val pct = (item.progress * 100).toInt().coerceAtLeast(0)
    val color = item.severity.color

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            color.copy(alpha = 0.18f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.s, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.12f)
            ) {
                Box(
                    modifier = Modifier.size(spacing.s),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(color, CircleShape)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.categoryName,
                    style = typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.severity.label,
                    style = typography.labelSmall,
                    color = color
                )
            }

            Surface(
                shape = RoundedCornerShape(shapes.extraLarge),
                color = color.copy(alpha = 0.12f)
            ) {
                Text(
                    text = "$pct%",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    color = color,
                    fontWeight = FontWeight.Bold,
                    style = typography.labelLarge
                )
            }
        }
    }
}

private fun monthsDiff(currentMonthKey: String, targetMonthKey: String): Int {
    val c = currentMonthKey.split("-")
    val t = targetMonthKey.split("-")
    val cy = c.getOrNull(0)?.toIntOrNull() ?: return 0
    val cm = c.getOrNull(1)?.toIntOrNull() ?: return 0
    val ty = t.getOrNull(0)?.toIntOrNull() ?: return 0
    val tm = t.getOrNull(1)?.toIntOrNull() ?: return 0
    return (ty * 12 + tm) - (cy * 12 + cm)
}

@Composable
private fun MonthlyGlobalSummaryCard(
    monthKey: String,
    monthsWithMovements: List<String>,
    totalLimitCents: Long,
    totalSpentCents: Long,
    availableCents: Long,
    progress: Float,
    currencyFormat: NumberFormat,
    onSelectMonthKey: (String) -> Unit
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val typography = XpendzThemeTokens.typography

    var monthExpanded by remember { mutableStateOf(false) }
    val monthOptions = remember(monthKey, monthsWithMovements) {
        val list = (listOf(monthKey) + monthsWithMovements)
            .distinct()
            .sortedDescending()
        if (list.isEmpty()) listOf(monthKey) else list
    }

    val status = monthlyStatus(progress, totalLimitCents)
    val statusColor = monthlyStatusColor(status)
    val pct = (progress * 100).toInt().coerceAtLeast(0)
    val availableColor = if (availableCents >= 0L) Color(0xFF16A34A) else Color(0xFFEF4444)
    val noDecimals = remember(currencyFormat) {
        (currencyFormat.clone() as NumberFormat).apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(shapes.extraLarge),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            colors.onSurfaceVariant.copy(alpha = 0.10f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Resumen mensual",
                        style = typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                    Text(
                        text = formatMonthKey(monthKey),
                        style = typography.titleMedium,
                        fontWeight = FontWeight.Normal,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box {
                    AssistChip(
                        onClick = { monthExpanded = true },
                        label = {
                            Text("Cambiar mes", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = colors.brand) },
                        trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = colors.onSurfaceVariant) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = colors.surfaceVariant,
                            labelColor = colors.onSurface
                        )
                    )
                    DropdownMenu(
                        expanded = monthExpanded,
                        onDismissRequest = { monthExpanded = false },
                        modifier = Modifier.background(colors.surface)
                    ) {
                        Surface(color = colors.surface, shape = RoundedCornerShape(shapes.large)) {
                            Column(
                                modifier = Modifier
                                    .background(colors.surface)
                                    .heightIn(max = 340.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                monthOptions.forEach { key ->
                                    DropdownMenuItem(
                                        text = { Text(formatMonthKey(key)) },
                                        onClick = {
                                            monthExpanded = false
                                            onSelectMonthKey(key)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                color = colors.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(shapes.extraLarge),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.s, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    Surface(
                        color = statusColor.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(shapes.extraLarge)
                    ) {
                        Text(
                            text = status.label,
                            modifier = Modifier.padding(horizontal = spacing.s, vertical = 6.dp),
                            color = statusColor,
                            fontWeight = FontWeight.SemiBold,
                            style = typography.labelMedium
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.s),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1.35f)) {
                            Text(
                                text = "Gastado",
                                style = typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                            Text(
                                text = noDecimals.format(totalSpentCents / 100.0),
                                style = typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                            Text(
                                text = "de Presupuesto Total",
                                style = typography.labelSmall,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                softWrap = false
                            )
                        }

                        Column(
                            modifier = Modifier.weight(0.85f),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "Disponible",
                                style = typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                            Text(
                                text = noDecimals.format(availableCents / 100.0),
                                style = typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = availableColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinearProgressIndicator(
                            progress = { progress.coerceAtLeast(0f).coerceAtMost(1f) },
                            modifier = Modifier
                                .weight(1f)
                                .height(spacing.xs)
                                .clip(RoundedCornerShape(shapes.extraLarge)),
                            color = statusColor,
                            trackColor = Color(0xFFE9EEF6)
                        )
                        Text(
                            text = "$pct%",
                            style = typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyCategoryBudgetCard(
    model: MonthlyCategoryCardModel,
    currencyFormat: NumberFormat,
    expanded: Boolean,
    children: List<com.jcadenas.xpendz.ui.viewmodel.MonthlyBudgetItem>,
    onToggleExpand: () -> Unit,
    onEditRoot: () -> Unit,
    onEditChild: (String) -> Unit
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val typography = XpendzThemeTokens.typography

    val statusColor = monthlyStatusColor(model.status)
    val pct = (model.progress * 100).toInt().coerceAtLeast(0)
    val availableCents = model.limitCents - model.spentCents
    val availableLabel = if (model.limitCents <= 0L) {
        "Sin límite"
    } else {
        if (availableCents >= 0L) {
            "Disponible: ${currencyFormat.format(availableCents / 100.0)}"
        } else {
            "Excediste por ${currencyFormat.format((-availableCents) / 100.0)}"
        }
    }
    val spentBudgetLabel = if (model.limitCents > 0L) {
        "${currencyFormat.format(model.spentCents / 100.0)} / ${currencyFormat.format(model.limitCents / 100.0)}"
    } else {
        currencyFormat.format(model.spentCents / 100.0)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            colors.onSurfaceVariant.copy(alpha = 0.10f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.s, vertical = spacing.xs),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    model.categoryName,
                    style = typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Surface(
                        color = statusColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(shapes.extraLarge)
                    ) {
                        Text(
                            "$pct%",
                            modifier = Modifier.padding(horizontal = spacing.xs, vertical = spacing.xxs),
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            style = typography.labelMedium
                        )
                    }

                    if (children.isEmpty()) {
                        IconButton(
                            onClick = onEditRoot,
                            modifier = Modifier.size(spacing.xxl)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Editar límite",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            LinearProgressIndicator(
                progress = { model.progress.coerceAtLeast(0f).coerceAtMost(1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(shapes.extraLarge)),
                color = statusColor,
                trackColor = Color(0xFFE9EEF6)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    spentBudgetLabel,
                    style = typography.labelSmall,
                    color = colors.onSurfaceVariant
                )

                Text(
                    availableLabel,
                    style = typography.labelSmall,
                    color = if (model.status == MonthlyStatus.EXCEEDED) Color(0xFFEF4444) else colors.onSurfaceVariant,
                    fontWeight = if (model.status == MonthlyStatus.EXCEEDED) FontWeight.SemiBold else FontWeight.Normal
                )
            }

            if (expanded && children.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    children.forEach { child ->
                        val childProgress = if (child.limitCents <= 0L) 0f else (child.spentCents.toFloat() / child.limitCents.toFloat()).coerceAtLeast(0f)
                        val childStatus = monthlyStatus(childProgress, child.limitCents)
                        val childColor = monthlyStatusColor(childStatus)
                        val childPct = (childProgress * 100).toInt().coerceAtLeast(0)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEditChild(child.categoryId) },
                            colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant.copy(alpha = 0.35f)),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                colors.onSurfaceVariant.copy(alpha = 0.08f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = spacing.s, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            child.categoryName,
                                            style = typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val childTopLine = if (child.limitCents > 0L) {
                                            "${currencyFormat.format(child.spentCents / 100.0)} / ${currencyFormat.format(child.limitCents / 100.0)}"
                                        } else {
                                            currencyFormat.format(child.spentCents / 100.0)
                                        }
                                        Text(
                                            childTopLine,
                                            style = typography.bodySmall,
                                            color = colors.onSurfaceVariant
                                        )
                                    }

                                    Surface(
                                        color = childColor.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(shapes.extraLarge)
                                    ) {
                                        Text(
                                            "$childPct%",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            color = childColor,
                                            fontWeight = FontWeight.Bold,
                                            style = typography.labelLarge
                                        )
                                    }
                                }

                                LinearProgressIndicator(
                                    progress = { childProgress.coerceAtLeast(0f).coerceAtMost(1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(spacing.xs)
                                        .clip(RoundedCornerShape(shapes.extraLarge)),
                                    color = childColor,
                                    trackColor = Color(0xFFE9EEF6)
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
private fun MonthlyEmptyState(
    onCreateFirst: () -> Unit
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val typography = XpendzThemeTokens.typography

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(shapes.extraLarge),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = colors.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.m, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Aún no has definido presupuestos",
                style = typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
            Text(
                "Crea tu primer límite para controlar tus gastos por categoría.",
                style = typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            Button(
                onClick = onCreateFirst,
                shape = RoundedCornerShape(shapes.extraLarge),
                colors = ButtonDefaults.buttonColors(containerColor = colors.brand)
            ) {
                Text("Crear primer presupuesto")
            }
        }
    }
}

@Composable
private fun BudgetFabAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val colors = XpendzThemeTokens.colors
    val elevation = XpendzThemeTokens.elevation

    Surface(
        shape = CircleShape,
        color = Color.White,
        shadowElevation = elevation.level2,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.onSurfaceVariant.copy(alpha = 0.15f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF2463EB), modifier = Modifier.size(18.dp))
            Text(label, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
        }
    }
}

private fun formatMonthKey(monthKey: String): String {
    val parts = monthKey.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return monthKey
    val month = parts.getOrNull(1)?.toIntOrNull() ?: return monthKey
    return try {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        SimpleDateFormat("MMM yyyy", Locale("es")).format(cal.time)
            .replaceFirstChar { it.titlecase(Locale("es")) }
    } catch (_: Exception) {
        monthKey
    }
}
