package com.jcadenas.xpendz.ui.screens.loans

import android.app.DatePickerDialog
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import com.jcadenas.xpendz.ui.components.CompactHeader
import com.jcadenas.xpendz.ui.components.HamburgerMenu
import com.jcadenas.xpendz.ui.components.HamburgerMenuButton
import com.jcadenas.xpendz.ui.components.SyncSwipeRefresh
import com.jcadenas.xpendz.ui.theme.Expense
import com.jcadenas.xpendz.ui.theme.Income
import com.jcadenas.xpendz.ui.theme.XpendzThemeTokens
import com.jcadenas.xpendz.ui.viewmodel.LoansViewModel
import com.jcadenas.xpendz.ui.viewmodel.SyncViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCharts: () -> Unit,
    onNavigateToBudget: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit,
    onEditTransaction: (String) -> Unit = {},
    viewModel: LoansViewModel = hiltViewModel()
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val elevation = XpendzThemeTokens.elevation
    val typography = XpendzThemeTokens.typography

    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showHamburgerMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }

    var showCreateLoan by remember { mutableStateOf(false) }
    var createLoanError by remember { mutableStateOf<String?>(null) }
    var loanAccountExpanded by remember { mutableStateOf(false) }
    var selectedAccountId by remember { mutableStateOf("") }
    var counterparty by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var loanType by remember { mutableStateOf(state.selectedTab) }
    var loanDateEpochSec by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
    var showLoanDatePicker by remember { mutableStateOf(false) }

    var showPayment by remember { mutableStateOf(false) }
    var paymentLoanId by remember { mutableStateOf("") }
    var paymentAccountExpanded by remember { mutableStateOf(false) }
    var paymentAccountId by remember { mutableStateOf("") }
    var paymentAmountText by remember { mutableStateOf("") }
    var paymentDateEpochSec by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
    var showPaymentDatePicker by remember { mutableStateOf(false) }

    var showHistory by remember { mutableStateOf(false) }
    var historyLoanId by remember { mutableStateOf("") }
    var historyLoanName by remember { mutableStateOf("") }
    var historyLoanCurrency by remember { mutableStateOf("") }

    var showEditLoan by remember { mutableStateOf(false) }
    var editLoanId by remember { mutableStateOf("") }
    var editCounterparty by remember { mutableStateOf("") }
    var editAmountText by remember { mutableStateOf("") }
    var editNotes by remember { mutableStateOf("") }
    var editAccountId by remember { mutableStateOf("") }
    var editAccountExpanded by remember { mutableStateOf(false) }
    var editCounterpartyError by remember { mutableStateOf<String?>(null) }
    var editAmountError by remember { mutableStateOf<String?>(null) }
    var editAccountError by remember { mutableStateOf<String?>(null) }
    var editLoanData by remember { mutableStateOf<com.jcadenas.xpendz.ui.viewmodel.LoansViewModel.LoanEditData?>(null) }


    val context = LocalContext.current

    val tabs = listOf("LENT" to "Me deben", "BORROWED" to "Yo debo")
    val selectedTabIndex = tabs.indexOfFirst { it.first == state.selectedTab }.coerceAtLeast(0)

    Scaffold(
        topBar = {
            CompactHeader(
                title = {
                    Text(
                        text = "Préstamos",
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
                            onNavigateToCharts = onNavigateToCharts,
                            onNavigateToBudget = onNavigateToBudget,
                            onNavigateToReports = onNavigateToReports,
                            onNavigateToSettings = onNavigateToSettings,
                            onLogout = onLogout,
                            currentScreen = "loans"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    loanType = state.selectedTab
                    selectedAccountId = ""
                    counterparty = ""
                    amountText = ""
                    notes = ""
                    loanDateEpochSec = System.currentTimeMillis() / 1000
                    createLoanError = null
                    showCreateLoan = true
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Nuevo préstamo"
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(spacing.m)
        ) {
            LoansSummaryCard(
                lentRemainingCents = state.totalLentRemainingCents,
                borrowedRemainingCents = state.totalBorrowedRemainingCents
            )

            Spacer(modifier = Modifier.height(spacing.s + spacing.xs / 2))

            LoansSegmentedTabs(
                selectedTab = state.selectedTab,
                onSelectTab = { viewModel.setTab(it) }
            )

            Spacer(modifier = Modifier.height(spacing.s))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = spacing.xxxl * 3 + spacing.xl)
            ) {
                items(state.loans, key = { it.id }) { loan ->
                    val paidCents = state.loanPaidCents[loan.id] ?: 0L
                    LoanCard(
                        loan = loan,
                        paidCents = paidCents,
                        isLent = state.selectedTab == "LENT",
                        onRegisterPayment = {
                            paymentLoanId = loan.id
                            paymentAccountId = loan.accountId ?: ""
                            paymentAmountText = ""
                            paymentDateEpochSec = System.currentTimeMillis() / 1000
                            showPayment = true
                        },
                        onViewHistory = {
                            historyLoanId = loan.id
                            historyLoanName = loan.counterpartyName
                            historyLoanCurrency = loan.currency
                            viewModel.loadLoanMovements(loan.id)
                            showHistory = true
                        },
                        onEditLoan = {
                            editLoanId = loan.id
                            editCounterparty = loan.counterpartyName
                            editAmountText = formatAmount(loan.principalCents)
                            editNotes = loan.notes ?: ""
                            editAccountId = loan.accountId ?: ""
                            editCounterpartyError = null
                            editAmountError = null
                            editAccountError = null
                            showEditLoan = true
                        }
                    )
                }
            }
        }
    }

    if (showCreateLoan) {
        val dialogTitle = if (loanType == "LENT") "Nuevo préstamo otorgado" else "Nuevo préstamo recibido"

        LaunchedEffect(showCreateLoan, state.accounts) {
            if (showCreateLoan && selectedAccountId.isBlank() && state.accounts.size == 1) {
                selectedAccountId = state.accounts.first().id
            }
        }

        AlertDialog(
            onDismissRequest = {
                showCreateLoan = false
                createLoanError = null
            },
            title = { Text(dialogTitle) },
            containerColor = colors.surface,
            confirmButton = {
                Button(onClick = {
                    // Protección adicional contra doble clic
                    if (state.isSavingLoan) return@Button

                    createLoanError = null
                    val cents = runCatching {
                        // Eliminar separadores de miles antes de parsear
                        val withoutThousands = amountText.trim().replace("[.,]".toRegex(), "")
                        val normalized = withoutThousands.replace(',', '.')
                        BigDecimal(normalized).multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()
                    }.getOrNull()

                    if (selectedAccountId.isNotBlank() && !counterparty.isBlank() && cents != null) {
                        scope.launch {
                            val error = viewModel.createLoan(
                                type = loanType,
                                accountId = selectedAccountId,
                                counterparty = counterparty.trim(),
                                principalCents = cents,
                                occurredAtEpochSec = loanDateEpochSec,
                                notes = notes.takeIf { it.isNotBlank() }
                            )
                            if (error == null) {
                                showCreateLoan = false
                                createLoanError = null
                                counterparty = ""
                                amountText = ""
                                notes = ""
                            } else {
                                createLoanError = error
                            }
                        }
                    }
                },
                    enabled = !state.isSavingLoan,
                    shape = RoundedCornerShape(shapes.extraLarge),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.brand)
                ) {
                    if (state.isSavingLoan) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing.s),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(spacing.xl),
                                strokeWidth = elevation.level1,
                                color = Color.White
                            )
                            Text("Guardando...")
                        }
                    } else {
                        Text("Guardar")
                    }
                }
            },
            dismissButton = {
                FilledTonalButton(
                    onClick = {
                        showCreateLoan = false
                        createLoanError = null
                    },
                    shape = RoundedCornerShape(shapes.extraLarge)
                ) { Text("Cancelar") }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                ) {
                    // Mostrar error de saldo insuficiente si existe
                    createLoanError?.let { error ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = colors.negative.copy(alpha = 0.12f)),
                            shape = RoundedCornerShape(shapes.medium)
                        ) {
                            Text(
                                text = error,
                                color = colors.negative,
                                modifier = Modifier.padding(spacing.m),
                                style = typography.bodySmall
                            )
                        }
                        Spacer(modifier = Modifier.height(spacing.m))
                    }

                    LoanTypeSegmentedTabs(
                        selectedType = loanType,
                        onSelect = { loanType = it }
                    )

                    Spacer(modifier = Modifier.height(spacing.l))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentSize(Alignment.TopStart)
                            .clickable { loanAccountExpanded = true }
                    ) {
                        var anchorSize by remember { mutableStateOf(IntSize.Zero) }
                        val density = LocalDensity.current
                        val accountName = state.accounts.firstOrNull { it.id == selectedAccountId }?.name ?: "Selecciona cuenta"
                        val accountCurrency = state.accounts.firstOrNull { it.id == selectedAccountId }?.currency.orEmpty()
                        val selectedBalance = state.accountBalancesCents[selectedAccountId]
                        OutlinedTextField(
                            value = accountName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Cuenta") },
                            supportingText = {
                                if (selectedAccountId.isNotBlank() && selectedBalance != null && accountCurrency.isNotBlank()) {
                                    Text("Disponible: ${formatMoney(selectedBalance, accountCurrency)}")
                                }
                            },
                            trailingIcon = {
                                IconButton(onClick = { loanAccountExpanded = true }) {
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = "Ver cuentas",
                                        tint = colors.onSurfaceVariant
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { anchorSize = it.size }
                            ,
                            shape = RoundedCornerShape(shapes.extraLarge),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = colors.surface,
                                unfocusedContainerColor = colors.surface,
                                disabledContainerColor = colors.surface,
                                focusedBorderColor = colors.brand,
                                unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.30f)
                            )
                        )
                        DropdownMenu(
                            expanded = loanAccountExpanded,
                            onDismissRequest = { loanAccountExpanded = false }
                            ,
                            modifier = Modifier
                                .width(with(density) { anchorSize.width.toDp() })
                                .clip(RoundedCornerShape(shapes.extraLarge))
                                .background(colors.surface),
                            properties = PopupProperties(focusable = true)
                        ) {
                            Column(
                                modifier = Modifier
                                    .heightIn(max = spacing.xxxl * 8 + spacing.xl + spacing.s)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                if (state.accounts.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("No hay cuentas registradas") },
                                        onClick = { loanAccountExpanded = false }
                                    )
                                } else {
                                    state.accounts.forEach { a ->
                                        val bal = state.accountBalancesCents[a.id] ?: 0L
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(a.name, modifier = Modifier.weight(1f))
                                                    Text("Disponible: ${formatMoney(bal, a.currency)}")
                                                }
                                            },
                                            onClick = {
                                                selectedAccountId = a.id
                                                loanAccountExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val cal = remember(loanDateEpochSec) {
                        Calendar.getInstance().apply { timeInMillis = loanDateEpochSec * 1000 }
                    }
                    val dateText = "${cal.get(Calendar.DAY_OF_MONTH)}/${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.YEAR)}"
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Fecha") },
                        trailingIcon = {
                            IconButton(onClick = { showLoanDatePicker = true }) {
                                Icon(Icons.Default.DateRange, contentDescription = "Elegir fecha", tint = colors.brand)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showLoanDatePicker = true
                            }
                        ,
                        shape = RoundedCornerShape(shapes.extraLarge),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface,
                            disabledContainerColor = colors.surface,
                            focusedBorderColor = colors.brand,
                            unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.30f)
                        )
                    )

                    Spacer(modifier = Modifier.height(spacing.m))

                    LoanModernTextField(
                        value = counterparty,
                        onValueChange = { counterparty = it },
                        label = "Persona o entidad",
                        modifier = Modifier.fillMaxWidth()
                    )
                    val accountCurrency = state.accounts.firstOrNull { it.id == selectedAccountId }?.currency.orEmpty()

                    Spacer(modifier = Modifier.height(spacing.m))

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = {
                            Text(if (accountCurrency.isBlank()) "Monto" else "Monto ($accountCurrency)")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(shapes.extraLarge),
                        textStyle = typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface,
                            disabledContainerColor = colors.surface,
                            focusedBorderColor = colors.brand,
                            unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.30f)
                        )
                    )

                    Spacer(modifier = Modifier.height(spacing.m))

                    LoanModernTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = "Nota (opcional)",
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(spacing.m))
                }
            }
        )
    }

    if (showPayment) {
        val paymentTitle = if (state.selectedTab == "LENT") "Registrar abono recibido" else "Registrar pago realizado"

        val selectedLoan = state.loans.firstOrNull { it.id == paymentLoanId }
        val loanCurrency = selectedLoan?.currency.orEmpty()
        val totalDebtCents = selectedLoan?.principalCents ?: 0L
        val alreadyPaidCents = state.loanPaidCents[paymentLoanId] ?: 0L
        val remainingDebtCents = (totalDebtCents - alreadyPaidCents).coerceAtLeast(0L)

        val enteredCentsPreview = runCatching {
            val normalized = paymentAmountText.trim().replace(',', '.')
            if (normalized.isBlank()) return@runCatching null
            BigDecimal(normalized)
                .multiply(BigDecimal(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        }.getOrNull()

        val remainingAfterPreview = if (enteredCentsPreview != null) {
            (remainingDebtCents - enteredCentsPreview).coerceAtLeast(0L)
        } else {
            null
        }

        LaunchedEffect(showPayment, state.accounts) {
            if (showPayment && paymentAccountId.isBlank() && state.accounts.size == 1) {
                paymentAccountId = state.accounts.first().id
            }
        }

        AlertDialog(
            onDismissRequest = { showPayment = false },
            title = { Text(paymentTitle) },
            containerColor = colors.surface,
            confirmButton = {
                Button(onClick = {
                    // Protección adicional contra doble clic
                    if (state.isSavingPayment) return@Button
                    
                    val cents = runCatching {
                        val normalized = paymentAmountText.trim().replace(',', '.')
                        BigDecimal(normalized).multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()
                    }.getOrNull()
                    if (
                        paymentLoanId.isNotBlank() &&
                        paymentAccountId.isNotBlank() &&
                        cents != null &&
                        cents > 0 &&
                        cents <= remainingDebtCents
                    ) {
                        viewModel.registerPayment(
                            loanId = paymentLoanId,
                            accountId = paymentAccountId,
                            principalCents = cents,
                            occurredAtEpochSec = paymentDateEpochSec,
                            note = null
                        )
                        showPayment = false
                        paymentAmountText = ""
                    }
                },
                    enabled = !state.isSavingPayment,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.brand)
                ) {
                    if (state.isSavingPayment) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Text("Guardando...")
                        }
                    } else {
                        Text("Guardar")
                    }
                }
            },
            dismissButton = {
                FilledTonalButton(
                    onClick = { showPayment = false },
                    shape = MaterialTheme.shapes.extraLarge
                ) { Text("Cancelar") }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                ) {
                    if (selectedLoan != null && loanCurrency.isNotBlank()) {
                        val baseColor = if (state.selectedTab == "LENT") Income else Expense
                        val progressPercent = if (totalDebtCents > 0) {
                            (alreadyPaidCents * 100 / totalDebtCents).toInt()
                        } else 0
                        val isFullyPaid = remainingDebtCents <= 0

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = baseColor.copy(alpha = 0.08f),
                            shape = MaterialTheme.shapes.extraLarge,
                            tonalElevation = if (isFullyPaid) 4.dp else 0.dp,
                            shadowElevation = if (isFullyPaid) 4.dp else 0.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Header con porcentaje pagado
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        if (isFullyPaid) "Completado" else "Progreso",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        if (isFullyPaid) "100%" else "$progressPercent%",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = baseColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // ProgressIndicator
                                LinearProgressIndicator(
                                    progress = { if (totalDebtCents > 0) alreadyPaidCents.toFloat() / totalDebtCents else 0f },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = baseColor,
                                    trackColor = baseColor.copy(alpha = 0.2f),
                                    strokeCap = StrokeCap.Round
                                )

                                // Montos
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Saldo pendiente (dato más importante)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                if (state.selectedTab == "LENT") "Pendiente" else "Saldo",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (isFullyPaid) {
                                                Text(
                                                    "Pagado completamente",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = baseColor
                                                )
                                            }
                                        }
                                        Text(
                                            if (isFullyPaid) formatMoney(0L, loanCurrency) else formatMoney(remainingDebtCents, loanCurrency),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = baseColor
                                        )
                                    }

                                    // Total abonado
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            if (state.selectedTab == "LENT") "Abonado" else "Pagado",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            formatMoney(alreadyPaidCents, loanCurrency),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    // Total original
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Total original",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            formatMoney(totalDebtCents, loanCurrency),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (remainingAfterPreview != null && enteredCentsPreview != null && enteredCentsPreview > 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = MaterialTheme.shapes.medium
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Después de este pago",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                formatMoney(remainingAfterPreview, loanCurrency),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = baseColor
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    val cal = remember(paymentDateEpochSec) {
                        Calendar.getInstance().apply { timeInMillis = paymentDateEpochSec * 1000 }
                    }
                    val dateText = "${cal.get(Calendar.DAY_OF_MONTH)}/${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.YEAR)}"
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Fecha") },
                        trailingIcon = {
                            IconButton(onClick = { showPaymentDatePicker = true }) {
                                Icon(Icons.Default.DateRange, contentDescription = "Elegir fecha", tint = colors.brand)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showPaymentDatePicker = true
                            }
                        ,
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface,
                            disabledContainerColor = colors.surface,
                            focusedBorderColor = colors.brand,
                            unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentSize(Alignment.TopStart)
                            .clickable { paymentAccountExpanded = true }
                    ) {
                        var anchorSize by remember { mutableStateOf(IntSize.Zero) }
                        val density = LocalDensity.current
                        val accountName = state.accounts.firstOrNull { it.id == paymentAccountId }?.name ?: "Selecciona cuenta"
                        val accountCurrency = state.accounts.firstOrNull { it.id == paymentAccountId }?.currency.orEmpty()
                        val selectedBalance = state.accountBalancesCents[paymentAccountId]
                        OutlinedTextField(
                            value = accountName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Cuenta") },
                            supportingText = {
                                if (paymentAccountId.isNotBlank() && selectedBalance != null && accountCurrency.isNotBlank()) {
                                    Text("Disponible: ${formatAmount(selectedBalance)}")
                                }
                            },
                            trailingIcon = {
                                IconButton(onClick = { paymentAccountExpanded = true }) {
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = "Ver cuentas",
                                        tint = colors.onSurfaceVariant
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { anchorSize = it.size }
                            ,
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
                            expanded = paymentAccountExpanded,
                            onDismissRequest = { paymentAccountExpanded = false }
                            ,
                            modifier = Modifier
                                .width(with(density) { anchorSize.width.toDp() })
                                .clip(MaterialTheme.shapes.extraLarge)
                                .background(colors.surface),
                            properties = PopupProperties(focusable = true)
                        ) {
                            Column(
                                modifier = Modifier
                                    .heightIn(max = 320.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                if (state.accounts.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("No hay cuentas registradas") },
                                        onClick = { paymentAccountExpanded = false }
                                    )
                                } else {
                                    state.accounts.forEach { a ->
                                        val bal = state.accountBalancesCents[a.id] ?: 0L
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(a.name, modifier = Modifier.weight(1f))
                                                    Text("Disponible: ${formatMoney(bal, a.currency)}")
                                                }
                                            },
                                            onClick = {
                                                paymentAccountId = a.id
                                                paymentAccountExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val accountCurrency = state.accounts.firstOrNull { it.id == paymentAccountId }?.currency.orEmpty()
                    OutlinedTextField(
                        value = paymentAmountText,
                        onValueChange = { paymentAmountText = it },
                        label = {
                            Text(
                                if (accountCurrency.isBlank()) "Monto" else "Monto ($accountCurrency)"
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface,
                            disabledContainerColor = colors.surface,
                            focusedBorderColor = colors.brand,
                            unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.3f)
                        ),
                        supportingText = {
                            if (enteredCentsPreview != null && enteredCentsPreview > remainingDebtCents && loanCurrency.isNotBlank()) {
                                Text(
                                    "El monto excede lo pendiente (${formatMoney(remainingDebtCents, loanCurrency)})",
                                    color = Expense
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        )
    }

    if (showLoanDatePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = loanDateEpochSec * 1000 }
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
                loanDateEpochSec = c.timeInMillis / 1000
                showLoanDatePicker = false
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setOnCancelListener { showLoanDatePicker = false }
        }.show()
    }

    if (showPaymentDatePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = paymentDateEpochSec * 1000 }
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
                paymentDateEpochSec = c.timeInMillis / 1000
                showPaymentDatePicker = false
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setOnCancelListener { showPaymentDatePicker = false }
        }.show()
    }

    if (showHistory) {
        val movements = state.loanMovements[historyLoanId] ?: emptyList()
        val loadError = state.loanMovementsError[historyLoanId]
        val sortedMovements = movements.sortedByDescending { it.occurredAtEpochSec }
        val loanTypeLabel = if (state.selectedTab == "LENT") "ME DEBEN" else "YO DEBO"
        
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { 
                Column {
                    Text(
                        text = "Historial de movimientos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = historyLoanName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (state.selectedTab == "LENT") Income.copy(alpha = 0.12f) else Expense.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = loanTypeLabel,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (state.selectedTab == "LENT") Income else Expense
                            )
                        }
                    }
                }
            },
            containerColor = colors.surface,
            confirmButton = {
                FilledTonalButton(
                    onClick = { showHistory = false },
                    shape = MaterialTheme.shapes.extraLarge
                ) { Text("Cerrar") }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (loadError != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    tint = Expense.copy(alpha = 0.5f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "Error al cargar el historial",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = loadError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (sortedMovements.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Payments,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "No hay movimientos registrados",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        sortedMovements.forEach { movement ->
                            MovementItem(
                                movement = movement,
                                currency = historyLoanCurrency,
                                onEdit = {
                                    movement.linkedTransactionId?.let { transactionId ->
                                        onEditTransaction(transactionId)
                                    } ?: run {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("No se encontró la transacción asociada")
                                        }
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        )
    }

    if (showEditLoan) {
        LaunchedEffect(showEditLoan, state.accounts) {
            if (showEditLoan && editAccountId.isBlank() && state.accounts.size == 1) {
                editAccountId = state.accounts.first().id
            }
            if (showEditLoan && editAccountId.isBlank()) {
                editAccountError = "Selecciona una cuenta"
            } else {
                editAccountError = null
            }
            // Cargar datos del préstamo para mostrar información actual
            if (showEditLoan && editLoanId.isNotBlank()) {
                editLoanData = viewModel.getLoanEditData(editLoanId)
            }
        }

        val isFormValid = editCounterparty.trim().isNotBlank() &&
                editAmountText.isNotBlank() &&
                runCatching {
                    val normalized = editAmountText.trim().replace(',', '.')
                    BigDecimal(normalized).multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact() > 0
                }.getOrDefault(false) &&
                editAccountId.isNotBlank()

        AlertDialog(
            onDismissRequest = { showEditLoan = false },
            title = { Text("Editar préstamo") },
            containerColor = colors.surface,
            confirmButton = {
                val currentCents = runCatching {
                    val withoutThousands = editAmountText.trim().replace("[.,]".toRegex(), "")
                    val normalized = withoutThousands.replace(',', '.')
                    BigDecimal(normalized).multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()
                }.getOrNull()
                val editData = editLoanData
                val newPendingCents = editData?.let { data ->
                    currentCents?.let { viewModel.calculateNewPending(it, data.paidCents) }
                }
                val isNewAmountValid = currentCents != null && editData != null && currentCents >= editData.paidCents
                
                Button(
                    onClick = {
                        // Protección adicional contra doble clic
                        if (state.isSavingEdit) return@Button
                        
                        val counterpartyName = editCounterparty.trim()
                        val cents = runCatching {
                            // Eliminar separadores de miles antes de parsear
                            val withoutThousands = editAmountText.trim().replace("[.,]".toRegex(), "")
                            val normalized = withoutThousands.replace(',', '.')
                            BigDecimal(normalized).multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()
                        }.getOrNull()

                        viewModel.updateLoan(
                            loanId = editLoanId,
                            counterpartyName = counterpartyName,
                            accountId = editAccountId,
                            principalCents = cents!!,
                            notes = editNotes.trim().takeIf { it.isNotBlank() }
                        )
                        showEditLoan = false
                    },
                    enabled = isFormValid && isNewAmountValid && !state.isSavingEdit,
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    if (state.isSavingEdit) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Text("Guardando...")
                        }
                    } else {
                        Text("Guardar")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEditLoan = false },
                    shape = MaterialTheme.shapes.extraLarge
                ) { Text("Cancelar") }
            },
            text = {
                val currentCents = runCatching {
                    val withoutThousands = editAmountText.trim().replace("[.,]".toRegex(), "")
                    val normalized = withoutThousands.replace(',', '.')
                    BigDecimal(normalized).multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()
                }.getOrNull()
                val editData = editLoanData
                val newPendingCents = editData?.let { data ->
                    currentCents?.let { viewModel.calculateNewPending(it, data.paidCents) }
                }
                val isNewAmountValid = currentCents != null && editData != null && currentCents >= editData.paidCents
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Tarjeta de información actual
                    editData?.let { data ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = colors.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            shape = MaterialTheme.shapes.extraLarge
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val baseColor = if (state.selectedTab == "LENT") Income else Expense
                                val isFullyPaid = data.pendingCents <= 0

                                // Header con porcentaje pagado
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Información actual",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.brand,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        if (isFullyPaid) "100%" else "${data.progressPercent}%",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = baseColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // ProgressIndicator
                                LinearProgressIndicator(
                                    progress = { data.progressPercent / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = baseColor,
                                    trackColor = baseColor.copy(alpha = 0.2f),
                                    strokeCap = StrokeCap.Round
                                )

                                // Montos
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Saldo pendiente (dato más importante)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                "Saldo pendiente",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (isFullyPaid) {
                                                Text(
                                                    "Pagado completamente",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = baseColor
                                                )
                                            }
                                        }
                                        Text(
                                            if (isFullyPaid) formatMoney(0L, data.currency) else formatMoney(data.pendingCents, data.currency),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = baseColor
                                        )
                                    }

                                    // Total abonado
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Total abonado",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            formatMoney(data.paidCents, data.currency),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    // Monto original
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Monto original",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            formatMoney(data.originalPrincipalCents, data.currency),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Preview del nuevo saldo cuando cambia el monto
                        if (currentCents != null && currentCents != data.originalPrincipalCents) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isNewAmountValid) Color(0xFFECFDF5) else Color(0xFFFEF2F2)
                                ),
                                shape = MaterialTheme.shapes.extraLarge
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isNewAmountValid) Icons.Default.CheckCircle else Icons.Default.Error,
                                            contentDescription = null,
                                            tint = if (isNewAmountValid) Color(0xFF059669) else Color(0xFFDC2626),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            "Nuevo saldo pendiente",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isNewAmountValid) Color(0xFF059669) else Color(0xFFDC2626)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Nuevo saldo",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            formatMoney(newPendingCents ?: 0L, data.currency),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isNewAmountValid) Color(0xFF059669) else Color(0xFFDC2626)
                                        )
                                    }
                                    val newProgress = currentCents?.let { viewModel.calculateNewProgress(it, data.paidCents) }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Nuevo progreso",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "${newProgress}%",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    if (!isNewAmountValid) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "No puedes establecer un monto inferior al total ya abonado.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFDC2626)
                                        )
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    OutlinedTextField(
                        value = editCounterparty,
                        onValueChange = {
                            editCounterparty = it
                            editCounterpartyError = if (it.trim().isBlank()) "El nombre es obligatorio" else null
                        },
                        label = { Text("Nombre de contraparte *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                        isError = editCounterpartyError != null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface,
                            disabledContainerColor = colors.surfaceVariant.copy(alpha = 0.3f),
                            focusedBorderColor = if (editCounterpartyError != null) Color(0xFFD32F2F) else colors.brand,
                            unfocusedBorderColor = if (editCounterpartyError != null) Color(0xFFD32F2F) else colors.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    )
                    val counterpartyError = editCounterpartyError
                    if (counterpartyError != null) {
                        Text(
                            text = counterpartyError,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFD32F2F)
                        )
                    }

                    OutlinedTextField(
                        value = editAmountText,
                        onValueChange = {
                            editAmountText = it
                            editAmountError = runCatching {
                                val normalized = it.trim().replace(',', '.')
                                val cents = BigDecimal(normalized).multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()
                                if (cents <= 0) "El monto debe ser mayor a cero" else null
                            }.getOrNull() ?: if (it.trim().isBlank()) "El monto es obligatorio" else null
                        },
                        label = { Text("Monto principal *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = MaterialTheme.shapes.extraLarge,
                        isError = editAmountError != null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface,
                            disabledContainerColor = colors.surfaceVariant.copy(alpha = 0.3f),
                            focusedBorderColor = if (editAmountError != null) Color(0xFFD32F2F) else colors.brand,
                            unfocusedBorderColor = if (editAmountError != null) Color(0xFFD32F2F) else colors.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    )
                    val amountError = editAmountError
                    if (amountError != null) {
                        Text(
                            text = amountError,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFD32F2F)
                        )
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = if (editAccountId.isNotBlank()) {
                                state.accounts.find { it.id == editAccountId }?.name ?: ""
                            } else {
                                ""
                            },
                            onValueChange = { },
                            label = { Text("Cuenta asociada *") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editAccountExpanded = true },
                            enabled = false,
                            singleLine = true,
                            shape = MaterialTheme.shapes.extraLarge,
                            isError = editAccountError != null,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = colors.surface,
                                unfocusedContainerColor = colors.surface,
                                disabledContainerColor = colors.surfaceVariant.copy(alpha = 0.3f),
                                focusedBorderColor = if (editAccountError != null) Color(0xFFD32F2F) else colors.brand,
                                unfocusedBorderColor = if (editAccountError != null) Color(0xFFD32F2F) else colors.onSurfaceVariant.copy(alpha = 0.3f),
                                disabledBorderColor = if (editAccountError != null) Color(0xFFD32F2F) else if (editAccountId.isNotBlank()) colors.brand.copy(alpha = 0.5f) else colors.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        )
                        DropdownMenu(
                            expanded = editAccountExpanded,
                            onDismissRequest = { editAccountExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                                .background(colors.surface)
                        ) {
                            state.accounts.forEach { account ->
                                val bal = state.accountBalancesCents[account.id] ?: 0L
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(account.name, modifier = Modifier.weight(1f))
                                            Text("Disponible: ${formatMoney(bal, account.currency)}")
                                        }
                                    },
                                    onClick = {
                                        editAccountId = account.id
                                        editAccountError = null
                                        editAccountExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    val accountError = editAccountError
                    if (accountError != null) {
                        Text(
                            text = accountError,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFD32F2F)
                        )
                    }

                    OutlinedTextField(
                        value = editNotes,
                        onValueChange = { editNotes = it },
                        label = { Text("Descripción") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface,
                            disabledContainerColor = colors.surfaceVariant.copy(alpha = 0.3f),
                            focusedBorderColor = colors.brand,
                            unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    )

                    Text(
                        text = "* Campos obligatorios",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}

@Composable
private fun LoansSummaryCard(
    lentRemainingCents: Long,
    borrowedRemainingCents: Long
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val elevation = XpendzThemeTokens.elevation
    val typography = XpendzThemeTokens.typography

    val currency = ""
    val teDebenText = formatMoney(lentRemainingCents, currency).trim()
    val debesText = formatMoney(borrowedRemainingCents, currency).trim()
    val balanceCents = lentRemainingCents - borrowedRemainingCents
    val balanceText = formatMoney(kotlin.math.abs(balanceCents), currency).trim()
    val balancePositive = balanceCents >= 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.xxs, vertical = spacing.xxs),
        shape = RoundedCornerShape(shapes.extraLarge),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation.level2)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.m, vertical = spacing.m)
        ) {
            Text(
                text = "Balance de préstamos",
                style = typography.titleMedium,
                fontWeight = FontWeight.Normal,
                color = colors.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(spacing.m))

            Surface(
                color = colors.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(shapes.extraLarge),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    LoansSummaryBackgroundGraph(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(horizontal = spacing.xxs, vertical = spacing.xxs)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.m, vertical = spacing.m),
                        verticalArrangement = Arrangement.spacedBy(spacing.m)
                    ) {
                        SummaryRow(label = "Te deben", value = teDebenText, valueColor = Income, labelBold = true)
                        SummaryRow(label = "Debes", value = debesText, valueColor = Expense, labelBold = true)
                        SummaryRow(
                            label = "Balance",
                            value = (if (balancePositive) "+" else "-") + balanceText,
                            valueColor = if (balancePositive) Income else Expense,
                            emphasize = true,
                            labelBold = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoansSummaryBackgroundGraph(
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
private fun SummaryRow(
    label: String,
    value: String,
    valueColor: Color,
    emphasize: Boolean = false,
    labelBold: Boolean = false
) {
    val colors = XpendzThemeTokens.colors
    val typography = XpendzThemeTokens.typography

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (emphasize) typography.bodyMedium else typography.bodySmall,
            color = if (emphasize) colors.onSurfaceVariant else colors.onSurfaceVariant.copy(alpha = 0.75f),
            fontWeight = if (labelBold) FontWeight.SemiBold else FontWeight.Normal
        )
        Text(
            text = value,
            style = if (emphasize) typography.titleMedium else typography.bodyMedium,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.SemiBold,
            color = valueColor
        )
    }
}

@Composable
private fun LoansSegmentedTabs(
    selectedTab: String,
    onSelectTab: (String) -> Unit
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes

    val lentSelected = selectedTab != "BORROWED"
    val lentBg by animateColorAsState(if (lentSelected) Income else Color(0xFFF1F3F7), label = "lentBg")
    val borrowedBg by animateColorAsState(if (!lentSelected) Expense else Color(0xFFF1F3F7), label = "borrowedBg")
    val lentFg = if (lentSelected) Color.White else colors.onSurfaceVariant
    val borrowedFg = if (!lentSelected) Color.White else colors.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(shapes.extraLarge))
            .background(Color(0xFFF1F3F7))
            .padding(spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs)
    ) {
        SegmentTab(
            label = "Me deben",
            icon = Icons.Default.TrendingUp,
            background = lentBg,
            foreground = lentFg,
            onClick = { onSelectTab("LENT") },
            modifier = Modifier.weight(1f)
        )
        SegmentTab(
            label = "Yo debo",
            icon = Icons.Default.TrendingDown,
            background = borrowedBg,
            foreground = borrowedFg,
            onClick = { onSelectTab("BORROWED") },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SegmentTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color,
    foreground: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shapes = XpendzThemeTokens.shapes
    val spacing = XpendzThemeTokens.spacing
    val typography = XpendzThemeTokens.typography

    Surface(
        modifier = modifier
            .height(spacing.xl + spacing.s)
            .clickable(onClick = onClick),
        color = background,
        shape = RoundedCornerShape(shapes.extraLarge)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(spacing.s))
            Spacer(modifier = Modifier.width(spacing.xs))
            Text(
                text = label,
                color = foreground,
                fontWeight = FontWeight.SemiBold,
                style = typography.bodyMedium
            )
        }
    }
}

@Composable
private fun LoanTypeSegmentedTabs(
    selectedType: String,
    onSelect: (String) -> Unit
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes

    val isLent = selectedType != "BORROWED"
    val lentBg by animateColorAsState(
        targetValue = if (isLent) Income else Color(0xFFF1F3F7),
        label = "lentTypeBg"
    )
    val borrowedBg by animateColorAsState(
        targetValue = if (!isLent) Expense else Color(0xFFF1F3F7),
        label = "borrowedTypeBg"
    )
    val lentFg = if (isLent) Color.White else colors.onSurfaceVariant
    val borrowedFg = if (!isLent) Color.White else colors.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(shapes.extraLarge))
            .background(Color(0xFFF1F3F7))
            .padding(spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs)
    ) {
        SegmentTab(
            label = "Presto",
            icon = Icons.Default.TrendingUp,
            background = lentBg,
            foreground = lentFg,
            onClick = { onSelect("LENT") },
            modifier = Modifier.weight(1f)
        )
        SegmentTab(
            label = "Me prestan",
            icon = Icons.Default.TrendingDown,
            background = borrowedBg,
            foreground = borrowedFg,
            onClick = { onSelect("BORROWED") },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LoanModernTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    val colors = XpendzThemeTokens.colors
    val shapes = XpendzThemeTokens.shapes

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        shape = RoundedCornerShape(shapes.extraLarge),
        placeholder = { Text(label, color = colors.onSurfaceVariant.copy(alpha = 0.6f)) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceVariant.copy(alpha = 0.30f),
            unfocusedContainerColor = colors.surfaceVariant.copy(alpha = 0.30f),
            disabledContainerColor = colors.surfaceVariant.copy(alpha = 0.30f),
            focusedBorderColor = colors.brand,
            unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.30f)
        )
    )
}

private enum class LoanVisualState {
    Paid,
    Partial,
    Pending
}

@Composable
private fun LoanCard(
    loan: com.jcadenas.xpendz.data.local.entity.LoanEntity,
    paidCents: Long,
    isLent: Boolean,
    onRegisterPayment: () -> Unit,
    onViewHistory: () -> Unit = {},
    onEditLoan: () -> Unit = {}
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val elevation = XpendzThemeTokens.elevation
    val typography = XpendzThemeTokens.typography

    val remainingCents = (loan.principalCents - paidCents).coerceAtLeast(0L)
    val remainingText = formatMoney(remainingCents, loan.currency)

    val progress = if (loan.principalCents <= 0L) 0f else (paidCents.toFloat() / loan.principalCents.toFloat()).coerceIn(0f, 1f)
    val percent = (progress * 100).toInt().coerceIn(0, 100)

    val visualState = when {
        remainingCents <= 0L -> LoanVisualState.Paid
        paidCents > 0L -> LoanVisualState.Partial
        else -> LoanVisualState.Pending
    }

    val baseColor = if (isLent) Income else Expense
    val statusColor = when (visualState) {
        LoanVisualState.Paid -> Income
        LoanVisualState.Partial -> Color(0xFFF4B400)
        LoanVisualState.Pending -> baseColor
    }
    val statusLabel = when (visualState) {
        LoanVisualState.Paid -> "Pagado"
        LoanVisualState.Partial -> "Parcial"
        LoanVisualState.Pending -> "Pendiente"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.s),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation.level2),
        shape = RoundedCornerShape(shapes.extraLarge),
        colors = CardDefaults.cardColors(containerColor = colors.surface)
    ) {
        Column(modifier = Modifier.padding(horizontal = spacing.m, vertical = spacing.s + spacing.xxs / 2)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = loan.counterpartyName,
                        style = typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(spacing.xxs))
                    Text(
                        text = if (isLent) "Te deben" else "Tú debes",
                        style = typography.bodySmall,
                        color = colors.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                Text(
                    text = remainingText,
                    style = typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = baseColor
                )
            }

            Spacer(modifier = Modifier.height(spacing.s + spacing.xxs / 2))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pendiente: ${formatMoney(remainingCents, loan.currency)}",
                    style = typography.bodySmall,
                    color = colors.onSurfaceVariant.copy(alpha = 0.85f)
                )
                Surface(
                    shape = RoundedCornerShape(shapes.extraLarge),
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = statusLabel,
                        modifier = Modifier.padding(horizontal = spacing.s, vertical = spacing.xxs),
                        style = typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.s + spacing.xxs / 2))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = statusColor,
                trackColor = colors.onSurfaceVariant.copy(alpha = 0.12f)
            )

            Spacer(modifier = Modifier.height(spacing.xs))

            Text(
                text = "$percent% pagado",
                style = typography.labelSmall,
                color = colors.onSurfaceVariant.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(spacing.s))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.s)
            ) {
                FilledTonalButton(
                    onClick = onRegisterPayment,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(shapes.extraLarge),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = baseColor.copy(alpha = 0.12f)),
                    contentPadding = PaddingValues(horizontal = spacing.m, vertical = spacing.s)
                ) {
                    Icon(Icons.Default.AttachMoney, contentDescription = null, tint = baseColor, modifier = Modifier.size(spacing.s))
                    Spacer(modifier = Modifier.width(spacing.xs))
                    Text(
                        text = if (isLent) "+ Abono" else "+ Pago",
                        color = baseColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
                FilledTonalButton(
                    onClick = onViewHistory,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(shapes.extraLarge),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = colors.surfaceVariant),
                    contentPadding = PaddingValues(horizontal = spacing.m, vertical = spacing.s)
                ) {
                    Icon(Icons.Default.Payments, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(spacing.s))
                    Spacer(modifier = Modifier.width(spacing.xs))
                    Text(
                        text = "Historial",
                        color = colors.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                var showMenu by remember { mutableStateOf(false) }
                Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(spacing.xxl + spacing.xxs)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Ver historial") },
                            onClick = {
                                showMenu = false
                                onViewHistory()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Editar préstamo") },
                            onClick = {
                                showMenu = false
                                onEditLoan()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isLent) "+ Abono" else "+ Pago") },
                            onClick = {
                                showMenu = false
                                onRegisterPayment()
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun formatMoney(amountCents: Long, currency: String): String {
    val amount = BigDecimal(amountCents).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
    val nf = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }
    return "${nf.format(amount)} $currency"
}

private fun formatAmount(amountCents: Long): String {
    val amount = BigDecimal(amountCents).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
    val nf = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }
    return nf.format(amount)
}

@Composable
private fun MovementItem(
    movement: com.jcadenas.xpendz.ui.model.LoanMovementUiModel,
    currency: String,
    onEdit: (() -> Unit)? = null
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val elevation = XpendzThemeTokens.elevation
    val typography = XpendzThemeTokens.typography

    val typeColor = when (movement.movementType) {
        "CREATION" -> Income
        "TOPUP" -> Color(0xFFF4B400)
        "PAYMENT_IN" -> Color(0xFF10B981)
        "PAYMENT_OUT" -> Color(0xFF3B82F6)
        "ADJUSTMENT" -> Color(0xFF9E9E9E)
        "CLOSE" -> Color(0xFF8B5CF6)
        else -> colors.onSurfaceVariant
    }

    val typeLabel = when (movement.movementType) {
        "CREATION" -> "Creación"
        "TOPUP" -> "Aumento"
        "PAYMENT_IN" -> "Pago recibido"
        "PAYMENT_OUT" -> "Pago realizado"
        "ADJUSTMENT" -> "Corrección"
        "CLOSE" -> "Cierre"
        else -> movement.movementType
    }

    val typeSymbol = when (movement.movementType) {
        "CREATION" -> "C"
        "TOPUP" -> "+"
        "PAYMENT_IN" -> "↓"
        "PAYMENT_OUT" -> "↑"
        "ADJUSTMENT" -> "≈"
        "CLOSE" -> "✓"
        else -> typeLabel.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    }

    val isClickable = onEdit != null && movement.linkedTransactionId != null
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed && isClickable) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .then(
                if (isClickable) {
                    Modifier.clickable {
                        onEdit?.invoke()
                    }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(shapes.extraLarge),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface
        ),
        border = if (isClickable) {
            BorderStroke(
                elevation.level1,
                colors.onSurfaceVariant.copy(alpha = 0.10f)
            )
        } else {
            BorderStroke(
                elevation.level1,
                colors.onSurfaceVariant.copy(alpha = 0.05f)
            )
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.s + spacing.xxs / 2, vertical = spacing.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon container
            Surface(
                shape = RoundedCornerShape(shapes.extraLarge),
                color = typeColor.copy(alpha = 0.12f),
                modifier = Modifier.size(spacing.xl + spacing.xxs)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = typeSymbol,
                        style = typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = typeColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(spacing.s))

            // Main content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs)
            ) {
                Text(
                    text = typeLabel,
                    style = typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
                Text(
                    text = movement.occurredAtFormatted,
                    style = typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                if (!movement.note.isNullOrBlank()) {
                    Text(
                        text = movement.note,
                        style = typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Amount and edit icon
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(spacing.xxs)
            ) {
                Text(
                    text = movement.amountFormatted,
                    style = typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = typeColor
                )
                if (isClickable) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar transacción",
                        tint = colors.brand.copy(alpha = 0.5f),
                        modifier = Modifier.size(spacing.s)
                    )
                }
            }
        }
    }
}
