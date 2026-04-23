package com.myfinances.ui.screens.budget

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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Savings
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.myfinances.data.local.entity.BudgetEntity
import com.myfinances.ui.components.CompactHeader
import com.myfinances.ui.components.SyncSwipeRefresh
import com.myfinances.ui.theme.Income
import com.myfinances.ui.theme.Expense
import com.myfinances.ui.util.CountryCurrency
import com.myfinances.ui.viewmodel.BudgetViewModel
import com.myfinances.ui.viewmodel.SyncViewModel
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
    val context = LocalContext.current
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("es", "CO")) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale("es")) }

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
            containerColor = Color.White,
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
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                disabledContainerColor = Color.White,
                                focusedBorderColor = Color(0xFF2463EB),
                                unfocusedBorderColor = Color(0xFFD8DFEA)
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2463EB))
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
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
        ,
        floatingActionButton = {
            if (selectedTab == 1) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (showFabMenu) {
                        BudgetFabAction(
                            label = "Registrar movimiento",
                            icon = Icons.Default.AttachMoney,
                            onClick = {
                                showFabMenu = false
                                showQuickMove = true
                            }
                        )
                        BudgetFabAction(
                            label = "Nueva meta",
                            icon = Icons.Default.Add,
                            onClick = {
                                showFabMenu = false
                                showCreateGoal = true
                            }
                        )
                    }
                    FloatingActionButton(onClick = { showFabMenu = !showFabMenu }) {
                        Icon(
                            imageVector = if (showFabMenu) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = "Acciones"
                        )
                    }
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

                    var expandedRootId by remember { mutableStateOf<String?>(null) }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            state.error?.let { err ->
                                Text(err, color = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.height(8.dp))
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
                            item {
                                Text(
                                    "Tus categorías",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            items(monthlyCards, key = { "budget_${it.categoryId}" }) { model ->
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

                        GoalsGlobalSummaryCard(
                            totalSavedCents = totalSavedCents,
                            totalTargetCents = totalTargetCents,
                            progress = totalProgress,
                            currencyFormat = currencyFormat
                        )

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
            title = { Text("Límite mensual") },
            containerColor = Color.White,
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(catName ?: "")
                    OutlinedTextField(
                        value = limitText,
                        onValueChange = { limitText = it },
                        label = { Text("Límite") },
                        leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Deja vacío para no establecer límite.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
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
                    enabled = editMonthlyCategoryId.isNotBlank() && !state.isLoading
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
            title = { Text("Nueva meta") },
            containerColor = Color.White,
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = goalName,
                        onValueChange = { goalName = it },
                        label = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            disabledContainerColor = Color.White,
                            focusedBorderColor = Color(0xFF2463EB),
                            unfocusedBorderColor = Color(0xFFD8DFEA)
                        )
                    )
                    OutlinedTextField(
                        value = goalAmountText,
                        onValueChange = { goalAmountText = it },
                        label = { Text("Monto objetivo") },
                        leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF5F7FA),
                            unfocusedContainerColor = Color(0xFFF5F7FA),
                            disabledContainerColor = Color(0xFFF5F7FA),
                            focusedBorderColor = Color(0xFF2463EB),
                            unfocusedBorderColor = Color(0xFFD8DFEA)
                        )
                    )

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
                                Icon(Icons.Default.Savings, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            disabledContainerColor = Color.White,
                            focusedBorderColor = Color(0xFF2463EB),
                            unfocusedBorderColor = Color(0xFFD8DFEA)
                        )
                    )

                    if (currencyExpanded) {
                        LaunchedEffect(Unit) {
                            currencySearchFocusRequester.requestFocus()
                            keyboardController?.show()
                        }

                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
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
                        leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            disabledContainerColor = Color.White,
                            focusedBorderColor = Color(0xFF2463EB),
                            unfocusedBorderColor = Color(0xFFD8DFEA)
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2463EB))
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
            title = { Text("Depositar a meta") },
            containerColor = Color.White,
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(goal?.name ?: "")

                    Box(modifier = Modifier.fillMaxWidth()) {
                        val selected = fromAccounts.find { it.id == fromAccountId }
                        val selectedBalance = selected?.id?.let { state.accountBalancesCents[it] } ?: 0L
                        OutlinedTextField(
                            value = if (selected == null) "" else "${selected.name} (${currencyFormat.format(selectedBalance / 100.0)})",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Cuenta origen") },
                            trailingIcon = {
                                IconButton(onClick = { fromExpanded = true }) {
                                    Icon(Icons.Default.Savings, contentDescription = null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = fromExpanded,
                            onDismissRequest = { fromExpanded = false },
                            modifier = Modifier.background(Color.White)
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
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Monto") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = dateFormat.format(Date(occurredAtEpochSec * 1000)),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Fecha") },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            disabledContainerColor = Color.White,
                            focusedBorderColor = Color(0xFF2463EB),
                            unfocusedBorderColor = Color(0xFFD8DFEA)
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
            title = { Text("Retirar de meta") },
            containerColor = Color.White,
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(goal?.name ?: "")

                    Box(modifier = Modifier.fillMaxWidth()) {
                        val selected = toAccounts.find { it.id == toAccountId }
                        val selectedBalance = selected?.id?.let { state.accountBalancesCents[it] } ?: 0L
                        OutlinedTextField(
                            value = if (selected == null) "" else "${selected.name} (${currencyFormat.format(selectedBalance / 100.0)})",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Cuenta destino") },
                            trailingIcon = {
                                IconButton(onClick = { toExpanded = true }) {
                                    Icon(Icons.Default.Savings, contentDescription = null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = toExpanded,
                            onDismissRequest = { toExpanded = false },
                            modifier = Modifier.background(Color.White)
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
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Monto") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = dateFormat.format(Date(occurredAtEpochSec * 1000)),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Fecha") },
                        leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker = true },
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            disabledContainerColor = Color.White,
                            focusedBorderColor = Color(0xFF2463EB),
                            unfocusedBorderColor = Color(0xFFD8DFEA)
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
    val leftSelected = selectedTab == 0
    val leftBg by animateColorAsState(if (leftSelected) Color(0xFF2463EB) else Color(0xFFF1F3F7), label = "budgetTabLeftBg")
    val rightBg by animateColorAsState(if (!leftSelected) Color(0xFF2463EB) else Color(0xFFF1F3F7), label = "budgetTabRightBg")
    val leftFg = if (leftSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val rightFg = if (!leftSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        color = Color(0xFFF1F3F7),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.extraLarge)
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
                    .clip(MaterialTheme.shapes.extraLarge)
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
    val pct = (progress * 100).toInt().coerceIn(0, 100)
    val pctColor = goalProgressColor(progress)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Tus metas",
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
                    GoalsSummaryBackgroundGraph(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Progreso general",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Surface(
                                color = pctColor.copy(alpha = 0.12f),
                                shape = MaterialTheme.shapes.extraLarge
                            ) {
                                Text(
                                    "$pct%",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    color = pctColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        SummaryLine(label = "Ahorrado total", value = currencyFormat.format(totalSavedCents / 100.0), valueColor = Income)
                        SummaryLine(label = "Objetivo total", value = currencyFormat.format(totalTargetCents / 100.0), valueColor = MaterialTheme.colorScheme.onSurface)

                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(MaterialTheme.shapes.extraLarge),
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
    val primaryOverlay = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
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
    val motivationalLabel = when {
        progress >= 0.75f -> "Vas muy bien"
        progress >= 0.35f -> "Vas a mitad de camino"
        else -> "Aún falta bastante"
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "$pct%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = pctColor
                )
            }

            Text(
                text = "$savedText / $targetText",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )

            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(MaterialTheme.shapes.extraLarge),
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
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "\uD83D\uDCC5 $dateText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                color = pctColor.copy(alpha = 0.08f),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        motivationalLabel,
                        fontWeight = FontWeight.Bold,
                        color = pctColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (remainingCents > 0L) {
                        val dailyText = currencyFormat.format(suggestedDailyCents / 100.0)
                        Text(
                            "Si ahorras $dailyText/día lo logras en $daysLeft día(s)",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Sugerido mensual: ${currencyFormat.format(suggestedMonthlyCents / 100.0)} (en $monthsLeft mes(es))",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onDeposit,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2463EB))
                ) {
                    Text("+ Depositar")
                }
                OutlinedButton(
                    onClick = onWithdraw,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.extraLarge
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

private enum class MonthlyStatus(val label: String, val priority: Int) {
    OK("Bien", 1),
    RISK("En riesgo", 2),
    EXCEEDED("Excedido", 3)
}

private data class MonthlyCategoryCardModel(
    val categoryId: String,
    val categoryName: String,
    val limitCents: Long,
    val spentCents: Long,
    val progress: Float,
    val status: MonthlyStatus
)

private fun monthlyStatus(progress: Float, limitCents: Long): MonthlyStatus {
    if (limitCents <= 0L) return MonthlyStatus.OK
    return when {
        progress > 1f -> MonthlyStatus.EXCEEDED
        progress >= 0.7f -> MonthlyStatus.RISK
        else -> MonthlyStatus.OK
    }
}

private fun monthlyStatusColor(status: MonthlyStatus): Color {
    return when (status) {
        MonthlyStatus.OK -> Color(0xFF2463EB)
        MonthlyStatus.RISK -> Color(0xFFF59E0B)
        MonthlyStatus.EXCEEDED -> Color(0xFFEF4444)
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
    val noDecimals = remember(currencyFormat) {
        (currencyFormat.clone() as NumberFormat).apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Este mes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Box {
                    AssistChip(
                        onClick = { monthExpanded = true },
                        label = {
                            Text(
                                formatMonthKey(monthKey),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                        trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    DropdownMenu(
                        expanded = monthExpanded,
                        onDismissRequest = { monthExpanded = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        Surface(color = Color.White, shape = MaterialTheme.shapes.large) {
                            Column(
                                modifier = Modifier
                                    .background(Color.White)
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

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    MonthlySummaryBackgroundGraph(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Row 1: Presupuesto and Gastado
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Presupuesto", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    noDecimals.format(totalLimitCents / 100.0),
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    softWrap = false
                                )
                            }
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text("Gastado", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    noDecimals.format(totalSpentCents / 100.0),
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    softWrap = false
                                )
                            }
                        }

                        // Row 2: Disponible centered
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Disponible", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    noDecimals.format(availableCents / 100.0),
                                    fontWeight = FontWeight.Bold,
                                    color = if (availableCents >= 0L) Color(0xFF16A34A) else Color(0xFFEF4444),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    softWrap = false
                                )
                            }
                        }

                        // Progress bar and percentage
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LinearProgressIndicator(
                                progress = { progress.coerceAtLeast(0f).coerceAtMost(1f) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(10.dp)
                                    .clip(MaterialTheme.shapes.extraLarge),
                                color = statusColor,
                                trackColor = Color(0xFFE9EEF6)
                            )
                            Text(
                                "$pct%",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlySummaryBackgroundGraph(
    modifier: Modifier = Modifier
) {
    val primaryOverlay = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
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
private fun MonthlyCategoryBudgetCard(
    model: MonthlyCategoryCardModel,
    currencyFormat: NumberFormat,
    expanded: Boolean,
    children: List<com.myfinances.ui.viewmodel.MonthlyBudgetItem>,
    onToggleExpand: () -> Unit,
    onEditRoot: () -> Unit,
    onEditChild: (String) -> Unit
) {
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = statusColor.copy(alpha = 0.10f)
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                model.categoryName.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            model.categoryName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val topLine = if (model.limitCents > 0L) {
                            "${currencyFormat.format(model.spentCents / 100.0)} / ${currencyFormat.format(model.limitCents / 100.0)}"
                        } else {
                            currencyFormat.format(model.spentCents / 100.0)
                        }
                        Text(
                            topLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = statusColor.copy(alpha = 0.12f),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Text(
                            "$pct%",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    if (children.isEmpty()) {
                        IconButton(onClick = onEditRoot) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar límite")
                        }
                    }
                }
            }

            LinearProgressIndicator(
                progress = { model.progress.coerceAtLeast(0f).coerceAtMost(1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(MaterialTheme.shapes.extraLarge),
                color = statusColor,
                trackColor = Color(0xFFE9EEF6)
            )

            Text(
                availableLabel,
                style = MaterialTheme.typography.bodySmall,
                color = if (model.status == MonthlyStatus.EXCEEDED) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (model.status == MonthlyStatus.EXCEEDED) FontWeight.SemiBold else FontWeight.Normal
            )

            if (expanded && children.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
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
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
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
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Surface(
                                        color = childColor.copy(alpha = 0.12f),
                                        shape = MaterialTheme.shapes.extraLarge
                                    ) {
                                        Text(
                                            "$childPct%",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            color = childColor,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }

                                LinearProgressIndicator(
                                    progress = { childProgress.coerceAtLeast(0f).coerceAtMost(1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(MaterialTheme.shapes.extraLarge),
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
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Aún no has definido presupuestos",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Crea tu primer límite para controlar tus gastos por categoría.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onCreateFirst,
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2463EB))
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
    Surface(
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF2463EB), modifier = Modifier.size(18.dp))
            Text(label, fontWeight = FontWeight.SemiBold)
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
