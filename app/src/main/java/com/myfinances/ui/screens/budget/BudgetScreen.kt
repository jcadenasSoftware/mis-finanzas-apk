package com.myfinances.ui.screens.budget

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.myfinances.ui.theme.Income
import com.myfinances.ui.util.CountryCurrency
import com.myfinances.ui.viewmodel.BudgetViewModel
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
    viewModel: BudgetViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Metas", "Mensual")
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

    var showEditMonthlyLimit by remember { mutableStateOf(false) }
    var editMonthlyCategoryId by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Presupuesto") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
        ,
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(onClick = { showCreateGoal = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Nueva meta")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> {
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

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(goal.name, fontWeight = FontWeight.SemiBold)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                "Objetivo: ${currencyFormat.format(goal.targetCents / 100.0)}",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                "Fecha objetivo: ${dateFormat.format(Date(goal.targetDateEpochSec * 1000))}",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                "Ahorrado: ${currencyFormat.format(savedCents / 100.0)}",
                                                color = Income
                                            )
                                            Text(
                                                "Restante: ${currencyFormat.format(remaining / 100.0)}",
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Text("${(progress * 100).toInt()}%")
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        "Sugerido mensual: ${currencyFormat.format(suggestedMonthly / 100.0)} (en $monthsLeft mes(es))",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = {
                                                depositGoalId = goal.id
                                                showDeposit = true
                                            }
                                        ) {
                                            Icon(Icons.Default.Savings, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Depositar")
                                        }
                                        TextButton(
                                            onClick = {
                                                withdrawGoalId = goal.id
                                                showWithdraw = true
                                            }
                                        ) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Retirar")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    val currencies = remember(state.accounts) {
                        state.accounts.map { it.currency }.filter { it.isNotBlank() }.distinct().sorted()
                    }
                    var currencyExpanded by remember { mutableStateOf(false) }

                    val totalLimit = state.monthlyTotalLimitCents
                    val totalSpent = state.monthlyTotalSpentCents
                    val available = totalLimit - totalSpent

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

                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        IconButton(onClick = { viewModel.shiftMonthlyMonth(-1) }) {
                                            Icon(Icons.Default.ChevronLeft, contentDescription = "Mes anterior")
                                        }
                                        Column {
                                            Text(
                                                formatMonthKey(state.monthlyMonth),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                "Presupuesto mensual",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { viewModel.shiftMonthlyMonth(1) }) {
                                            Icon(Icons.Default.ChevronRight, contentDescription = "Mes siguiente")
                                        }
                                    }

                                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        Box {
                                            val selectedCurrency = state.monthlyCurrency.ifBlank { currencies.firstOrNull().orEmpty() }
                                            AssistChip(
                                                onClick = { currencyExpanded = true },
                                                label = {
                                                    Text(
                                                        if (selectedCurrency.isBlank()) "Moneda" else selectedCurrency,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Default.AttachMoney, contentDescription = null)
                                                },
                                                trailingIcon = {
                                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                                                },
                                                colors = AssistChipDefaults.assistChipColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                    labelColor = MaterialTheme.colorScheme.onSurface
                                                )
                                            )
                                            DropdownMenu(
                                                expanded = currencyExpanded,
                                                onDismissRequest = { currencyExpanded = false }
                                            ) {
                                                currencies.forEach { code ->
                                                    DropdownMenuItem(
                                                        text = { Text(code) },
                                                        onClick = {
                                                            currencyExpanded = false
                                                            viewModel.setMonthlyCurrency(code)
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        FilledTonalIconButton(
                                            onClick = { viewModel.copyPreviousMonthBudgets() }
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copiar mes anterior")
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        "Resumen",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Presupuestado", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(currencyFormat.format(totalLimit / 100.0), fontWeight = FontWeight.SemiBold)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Gastado", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(currencyFormat.format(totalSpent / 100.0), fontWeight = FontWeight.SemiBold)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Disponible", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            currencyFormat.format(available / 100.0),
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (available >= 0) Income else MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }

                        if (currencies.isEmpty()) {
                            item {
                                Text(
                                    "No hay cuentas para determinar moneda.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (state.monthlyItems.isEmpty()) {
                            item {
                                Text(
                                    "Aún no tienes presupuestos ni gastos en este mes/moneda.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        items(state.monthlyItems, key = { "budget_${it.categoryId}" }) { item ->
                            val limit = item.limitCents
                            val spent = item.spentCents
                            val progress = if (limit <= 0) 0f else (spent.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
                            val over = spent > limit && limit > 0

                            val subItems = state.monthlySubcategoryItemsByRootId[item.categoryId].orEmpty()

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        editMonthlyCategoryId = item.categoryId
                                        showEditMonthlyLimit = true
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                    ) {
                                        Text(
                                            item.categoryName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (limit > 0) {
                                            val pct = (progress * 100).toInt()
                                            Surface(
                                                shape = MaterialTheme.shapes.extraLarge,
                                                color = (if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                                    .copy(alpha = 0.10f)
                                            ) {
                                                Text(
                                                    "$pct%",
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "${currencyFormat.format(spent / 100.0)} / ${if (limit > 0) currencyFormat.format(limit / 100.0) else "Sin límite"}",
                                        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )

                                    if (subItems.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            "Subcategorías",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))

                                        subItems.forEach { sub ->
                                            val subLimit = sub.limitCents
                                            val subSpent = sub.spentCents
                                            val subProgress = if (subLimit <= 0) 0f else (subSpent.toFloat() / subLimit.toFloat()).coerceIn(0f, 1f)
                                            val subOver = subSpent > subLimit && subLimit > 0

                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(MaterialTheme.shapes.medium)
                                                    .clickable {
                                                        editMonthlyCategoryId = sub.categoryId
                                                        showEditMonthlyLimit = true
                                                    }
                                                    .padding(vertical = 6.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        sub.categoryName,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        "${currencyFormat.format(subSpent / 100.0)} / ${if (subLimit > 0) currencyFormat.format(subLimit / 100.0) else "Sin límite"}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (subOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(6.dp))
                                                LinearProgressIndicator(
                                                    progress = { subProgress },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    color = if (subOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Configurar límites por categoría y subcategoría",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        val rootCats = state.monthlyRootCategories.sortedBy { it.name }
                        items(rootCats, key = { "config_root_${it.id}" }) { root ->
                            val currentLimit = state.monthlyLimitsByCategoryId[root.id] ?: 0L

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        editMonthlyCategoryId = root.id
                                        showEditMonthlyLimit = true
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Text(
                                        root.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        if (currentLimit > 0) currencyFormat.format(currentLimit / 100.0) else "Sin límite",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            val children = state.monthlyChildrenMap[root.id].orEmpty().sortedBy { it.name }
                            children.forEach { child ->
                                val childLimit = state.monthlyLimitsByCategoryId[child.id] ?: 0L
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp)
                                        .clickable {
                                            editMonthlyCategoryId = child.id
                                            showEditMonthlyLimit = true
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                    ) {
                                        Text(
                                            child.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            if (childLimit > 0) currencyFormat.format(childLimit / 100.0) else "Sin límite",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyMedium
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
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = goalAmountText,
                        onValueChange = { goalAmountText = it },
                        label = { Text("Monto objetivo") },
                        leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker = true }
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
                    enabled = goalName.isNotBlank() && goalAmountText.isNotBlank() && !state.isLoading
                ) {
                    Text("Crear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGoal = false }) {
                    Text("Cancelar")
                }
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
                            onDismissRequest = { fromExpanded = false }
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
                        leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker = true }
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
                            onDismissRequest = { toExpanded = false }
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker = true }
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
