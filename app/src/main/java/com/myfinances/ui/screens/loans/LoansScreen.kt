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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.jcadenas.xpendz.ui.components.CompactHeader
import com.jcadenas.xpendz.ui.components.HamburgerMenu
import com.jcadenas.xpendz.ui.components.HamburgerMenuButton
import com.jcadenas.xpendz.ui.components.MoneyInputField
import com.jcadenas.xpendz.ui.components.MoneyInputFieldVariant
import com.jcadenas.xpendz.ui.components.MoneyInputFormatter
import com.jcadenas.xpendz.ui.components.SyncSwipeRefresh
import com.jcadenas.xpendz.ui.theme.Expense
import com.jcadenas.xpendz.ui.theme.Income
import com.jcadenas.xpendz.ui.theme.XpendzThemeTokens
import com.jcadenas.xpendz.domain.loan.journal.LoanType
import com.jcadenas.xpendz.ui.viewmodel.LoansViewModel
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

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }

    var showCreateLoan by remember { mutableStateOf(false) }
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

    var showTopUp by remember { mutableStateOf(false) }
    var topUpLoanId by remember { mutableStateOf("") }
    var topUpAccountExpanded by remember { mutableStateOf(false) }
    var topUpAccountId by remember { mutableStateOf("") }
    var topUpAmountText by remember { mutableStateOf("") }
    var topUpNote by remember { mutableStateOf("") }

    var showHistory by remember { mutableStateOf(false) }
    var historyLoanId by remember { mutableStateOf("") }
    var historyLoanName by remember { mutableStateOf("") }
    var historyLoanCurrency by remember { mutableStateOf("") }
    var paymentToReverse by remember {
        mutableStateOf<com.jcadenas.xpendz.domain.loan.projection.LoanPaymentProjection?>(null)
    }

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

            state.createdLoanSnapshot?.let { snapshot ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = colors.positive.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(shapes.medium)
                ) {
                    Column(modifier = Modifier.padding(spacing.m)) {
                        Text(
                            text = "Préstamo creado",
                            style = typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.positive
                        )
                        Spacer(modifier = Modifier.height(spacing.xs))
                        Text(
                            text = "${snapshot.counterpartyName} · ${formatMoney(snapshot.principalCents, snapshot.currency)}",
                            style = typography.bodyMedium
                        )
                        Text(
                            text = "Pendiente: ${formatMoney(snapshot.pendingCents, snapshot.currency)} · Estado: ${snapshot.status}",
                            style = typography.bodySmall
                        )
                    }
                }
                Spacer(modifier = Modifier.height(spacing.m))
            }

            state.paidLoanSnapshot?.let { snapshot ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = colors.positive.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(shapes.medium)
                ) {
                    Column(modifier = Modifier.padding(spacing.m)) {
                        Text(
                            text = "Pago registrado",
                            style = typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.positive
                        )
                        Spacer(modifier = Modifier.height(spacing.xs))
                        Text(
                            text = "${snapshot.counterpartyName} · ${formatMoney(snapshot.totalPaidCents, snapshot.currency)}",
                            style = typography.bodyMedium
                        )
                        Text(
                            text = "Pendiente: ${formatMoney(snapshot.pendingCents, snapshot.currency)} · Estado: ${snapshot.status}",
                            style = typography.bodySmall
                        )
                    }
                }
                Spacer(modifier = Modifier.height(spacing.m))
            }

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
                items(state.loans, key = { it.loanId }) { loan ->
                    LoanCard(
                        loan = loan,
                        isLent = loan.loanType == LoanType.LENT,
                        onRegisterPayment = {
                            paymentLoanId = loan.loanId
                            paymentAccountId = loan.defaultAccountId ?: ""
                            paymentAmountText = ""
                            paymentDateEpochSec = System.currentTimeMillis() / 1000
                            viewModel.preparePayment(loan.loanId)
                            showPayment = true
                        },
                        onViewHistory = {
                            historyLoanId = loan.loanId
                            historyLoanName = loan.counterparty
                            historyLoanCurrency = loan.currency
                            viewModel.loadPaymentProjections(loan.loanId)
                            showHistory = true
                        },
                        onEditLoan = {
                            editLoanId = loan.loanId
                            editCounterparty = loan.counterparty
                            editAmountText = formatAmount(loan.principalCents)
                            editNotes = loan.notes ?: ""
                            editAccountId = loan.defaultAccountId ?: ""
                            editCounterpartyError = null
                            editAmountError = null
                            editAccountError = null
                            showEditLoan = true
                        },
                        onArchiveLoan = {
                            viewModel.archiveLoan(loan.loanId)
                        },
                        onTopUp = {
                            topUpLoanId = loan.loanId
                            topUpAccountId = loan.defaultAccountId ?: ""
                            topUpAmountText = ""
                            topUpNote = ""
                            viewModel.prepareTopUp(loan.loanId)
                            showTopUp = true
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
            },
            title = { Text(dialogTitle) },
            containerColor = colors.surface,
            confirmButton = {
                Button(onClick = {
                    // Protección adicional contra doble clic
                    if (state.isCreatingLoan) return@Button

                    val cents = MoneyInputFormatter.parseToCents(amountText)

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
                                counterparty = ""
                                amountText = ""
                                notes = ""
                            }
                        }
                    }
                },
                    enabled = !state.isCreatingLoan,
                    shape = RoundedCornerShape(shapes.extraLarge),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.brand)
                ) {
                    if (state.isCreatingLoan) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing.s),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(spacing.xl),
                                strokeWidth = elevation.level1,
                                color = colors.onBrand
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
                    // Mostrar error si existe
                    state.createLoanError?.let { error ->
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

                    MoneyInputField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = {
                            Text(if (accountCurrency.isBlank()) "Monto" else "Monto ($accountCurrency)")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(shapes.extraLarge),
                        variant = MoneyInputFieldVariant.OUTLINED,
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

        val summary = state.selectedPaymentSummary
        val loanCurrency = summary?.currency.orEmpty()
        val totalDebtCents = summary?.principalCents ?: 0L
        val alreadyPaidCents = summary?.totalPaidCents ?: 0L
        val remainingDebtCents = summary?.pendingCents?.coerceAtLeast(0L) ?: 0L

        val enteredCentsPreview = MoneyInputFormatter.parseToCents(paymentAmountText)

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
                    
                    val cents = MoneyInputFormatter.parseToCents(paymentAmountText)
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
                            amountCents = cents,
                            note = null
                        )
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
                                color = colors.onBrand
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
                    LaunchedEffect(state.paidLoanSnapshot) {
                        state.paidLoanSnapshot?.let {
                            showPayment = false
                            paymentAmountText = ""
                        }
                    }

                    state.paymentError?.let { error ->
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

                    if (summary != null && loanCurrency.isNotBlank()) {
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
                    MoneyInputField(
                        value = paymentAmountText,
                        onValueChange = { paymentAmountText = it },
                        label = {
                            Text(
                                if (accountCurrency.isBlank()) "Monto" else "Monto ($accountCurrency)"
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        variant = MoneyInputFieldVariant.OUTLINED,
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

    if (showTopUp) {
        val topUpSummary = state.selectedTopUpSummary
        val topUpCurrency = topUpSummary?.currency.orEmpty()
        val isLentTopUp = topUpSummary?.loanType == com.jcadenas.xpendz.domain.loan.journal.LoanType.LENT
        val baseColor = if (isLentTopUp) Income else Expense
        val enteredCents = MoneyInputFormatter.parseToCents(topUpAmountText)

        LaunchedEffect(showTopUp, state.accounts) {
            if (showTopUp && topUpAccountId.isBlank() && state.accounts.size == 1) {
                topUpAccountId = state.accounts.first().id
            }
        }

        LaunchedEffect(state.toppedUpLoanSnapshot) {
            state.toppedUpLoanSnapshot?.let {
                showTopUp = false
                topUpAmountText = ""
                topUpNote = ""
            }
        }

        AlertDialog(
            onDismissRequest = { showTopUp = false },
            title = { Text("Agregar capital") },
            containerColor = colors.surface,
            confirmButton = {
                Button(onClick = {
                    // Protección adicional contra doble clic
                    if (state.isSavingTopUp) return@Button

                    val cents = MoneyInputFormatter.parseToCents(topUpAmountText)
                    if (
                        topUpLoanId.isNotBlank() &&
                        topUpAccountId.isNotBlank() &&
                        cents != null &&
                        cents > 0
                    ) {
                        viewModel.topUpLoan(
                            loanId = topUpLoanId,
                            accountId = topUpAccountId,
                            amountCents = cents,
                            note = topUpNote.takeIf { it.isNotBlank() }
                        )
                    }
                },
                    enabled = !state.isSavingTopUp,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.brand)
                ) {
                    if (state.isSavingTopUp) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = colors.onBrand
                            )
                            Text("Guardando...")
                        }
                    } else {
                        Text("Confirmar agregado")
                    }
                }
            },
            dismissButton = {
                FilledTonalButton(
                    onClick = { showTopUp = false },
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
                    state.topUpError?.let { error ->
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

                    if (topUpSummary != null && topUpCurrency.isNotBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = baseColor.copy(alpha = 0.08f),
                            shape = MaterialTheme.shapes.extraLarge
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Préstamo",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        topUpSummary.counterparty,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Saldo actual",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        formatMoney(topUpSummary.principalCents, topUpCurrency),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = baseColor
                                    )
                                }
                                if (enteredCents != null && enteredCents > 0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Nuevo total",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            formatMoney(topUpSummary.principalCents + enteredCents, topUpCurrency),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = baseColor
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentSize(Alignment.TopStart)
                            .clickable { topUpAccountExpanded = true }
                    ) {
                        var anchorSize by remember { mutableStateOf(IntSize.Zero) }
                        val density = LocalDensity.current
                        val accountName = state.accounts.firstOrNull { it.id == topUpAccountId }?.name ?: "Selecciona cuenta"
                        val selectedBalance = state.accountBalancesCents[topUpAccountId]
                        OutlinedTextField(
                            value = accountName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Cuenta") },
                            supportingText = {
                                if (topUpAccountId.isNotBlank() && selectedBalance != null) {
                                    Text("Disponible: ${formatAmount(selectedBalance)}")
                                }
                            },
                            trailingIcon = {
                                IconButton(onClick = { topUpAccountExpanded = true }) {
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = "Ver cuentas",
                                        tint = colors.onSurfaceVariant
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { anchorSize = it.size },
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
                            expanded = topUpAccountExpanded,
                            onDismissRequest = { topUpAccountExpanded = false },
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
                                        onClick = { topUpAccountExpanded = false }
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
                                                topUpAccountId = a.id
                                                topUpAccountExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val topUpAccountCurrency = state.accounts.firstOrNull { it.id == topUpAccountId }?.currency.orEmpty()
                    MoneyInputField(
                        value = topUpAmountText,
                        onValueChange = { topUpAmountText = it },
                        label = {
                            Text(
                                if (topUpAccountCurrency.isBlank()) "Monto" else "Monto ($topUpAccountCurrency)"
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        variant = MoneyInputFieldVariant.OUTLINED,
                        textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface,
                            disabledContainerColor = colors.surface,
                            focusedBorderColor = colors.brand,
                            unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.3f)
                        ),
                        supportingText = {
                            if (enteredCents != null && enteredCents <= 0) {
                                Text("El monto debe ser mayor a $0", color = Expense)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = topUpNote,
                        onValueChange = { topUpNote = it },
                        label = { Text("Nota (opcional)") },
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
        val payments = state.paymentProjections[historyLoanId] ?: emptyList()
        val loadError = state.paymentProjectionsError[historyLoanId]
        val sortedPayments = payments
        val loanTypeLabel = if (state.selectedTab == "LENT") "ME DEBEN" else "YO DEBO"

        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = {
                Column {
                    Text(
                        text = "Historial de pagos",
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
                    state.reversePaymentError?.let { error ->
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
                    } else if (sortedPayments.isEmpty()) {
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
                                    text = "No hay pagos registrados",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        sortedPayments.forEach { payment ->
                            PaymentProjectionItem(
                                payment = payment,
                                currency = historyLoanCurrency,
                                isReversing = state.isReversingPayment,
                                isReversingThis = state.reversingPaymentEventId == payment.sourceEventId,
                                onReverse = { selected ->
                                    viewModel.selectPaymentForReverse(historyLoanId, selected.sourceEventId)
                                    paymentToReverse = selected
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        )
    }

    paymentToReverse?.let { pending ->
        val reversingThis = state.isReversingPayment &&
                state.reversingPaymentEventId == pending.sourceEventId
        // Snapshot presente al abrir el modal: solo se cierra cuando llega uno nuevo.
        val snapshotBaseline = remember(pending.sourceEventId) { state.reversedLoanSnapshot }
        LaunchedEffect(state.reversedLoanSnapshot) {
            if (state.reversedLoanSnapshot != null && state.reversedLoanSnapshot != snapshotBaseline) {
                paymentToReverse = null
            }
        }
        AlertDialog(
            onDismissRequest = { if (!state.isReversingPayment) paymentToReverse = null },
            title = { Text("Revertir pago") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
                    Text(
                        "¿Revertir este pago de ${formatMoney(pending.amountCents, historyLoanCurrency)}?"
                    )
                    Text(
                        "El monto volverá al saldo pendiente del préstamo.",
                        style = typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    state.reversePaymentError?.let { error ->
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
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.reversePayment(
                            loanId = historyLoanId,
                            paymentEventId = pending.sourceEventId,
                            reason = "Reversión desde historial",
                            note = null
                        )
                    },
                    enabled = !state.isReversingPayment
                ) {
                    if (reversingThis) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            text = "Revertir",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { paymentToReverse = null },
                    enabled = !state.isReversingPayment
                ) {
                    Text(
                        text = "Cancelar",
                        color = colors.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            containerColor = colors.surface
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
                (MoneyInputFormatter.parseToCents(editAmountText)?.let { it > 0 } ?: false) &&
                editAccountId.isNotBlank()

        AlertDialog(
            onDismissRequest = { showEditLoan = false },
            title = { Text("Editar préstamo") },
            containerColor = colors.surface,
            confirmButton = {
                val currentCents = MoneyInputFormatter.parseToCents(editAmountText)
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
                        val cents = MoneyInputFormatter.parseToCents(editAmountText)

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
                                color = colors.onBrand
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
                val currentCents = MoneyInputFormatter.parseToCents(editAmountText)
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

                    MoneyInputField(
                        value = editAmountText,
                        onValueChange = {
                            editAmountText = it
                            editAmountError = MoneyInputFormatter.parseToCents(it)?.let { cents ->
                                if (cents <= 0) "El monto debe ser mayor a cero" else null
                            } ?: if (it.trim().isBlank()) "El monto es obligatorio" else null
                        },
                        label = { Text("Monto principal *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                        variant = MoneyInputFieldVariant.OUTLINED,
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
                color = colors.onSurface
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
            color = if (emphasize) colors.onSurfaceVariant else colors.onSurfaceVariant.copy(alpha = 0.85f),
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
    loan: com.jcadenas.xpendz.domain.loan.projection.LoanSummaryProjection,
    isLent: Boolean,
    onRegisterPayment: () -> Unit,
    onViewHistory: () -> Unit = {},
    onEditLoan: () -> Unit = {},
    onArchiveLoan: () -> Unit = {},
    onTopUp: () -> Unit = {}
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val elevation = XpendzThemeTokens.elevation
    val typography = XpendzThemeTokens.typography

    val remainingCents = loan.pendingCents
    val remainingText = formatMoney(remainingCents, loan.currency)

    val progress = loan.progressPercent / 100f
    val percent = loan.progressPercent

    val visualState = when {
        remainingCents <= 0L -> LoanVisualState.Paid
        loan.totalPaidCents > 0L -> LoanVisualState.Partial
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
                        text = loan.counterparty,
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
                var showArchiveDialog by remember { mutableStateOf(false) }
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
                            text = { Text("Agregar capital") },
                            onClick = {
                                showMenu = false
                                onTopUp()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Archivar préstamo") },
                            leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showArchiveDialog = true
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

                if (showArchiveDialog) {
                    AlertDialog(
                        onDismissRequest = { showArchiveDialog = false },
                        icon = {
                            Surface(
                                shape = RoundedCornerShape(shapes.extraLarge),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Archive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(spacing.s)
                                )
                            }
                        },
                        title = { Text("Archivar préstamo") },
                        text = {
                            Text(
                                "Este préstamo dejará de mostrarse en la lista activa.\n\nNo se eliminarán los pagos, movimientos, transacciones ni el historial financiero.\n\n¿Deseas continuar?"
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showArchiveDialog = false
                                onArchiveLoan()
                            }) {
                                Text(
                                    text = "Archivar",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showArchiveDialog = false }) {
                                Text(
                                    text = "Cancelar",
                                    color = colors.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        },
                        containerColor = colors.surface,
                        shape = RoundedCornerShape(shapes.extraLarge)
                    )
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
    return MoneyInputFormatter.formatFromCents(amountCents)
}

@Composable
private fun PaymentProjectionItem(
    payment: com.jcadenas.xpendz.domain.loan.projection.LoanPaymentProjection,
    currency: String,
    isReversing: Boolean,
    isReversingThis: Boolean,
    onReverse: (com.jcadenas.xpendz.domain.loan.projection.LoanPaymentProjection) -> Unit
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val elevation = XpendzThemeTokens.elevation
    val typography = XpendzThemeTokens.typography

    val typeColor = when (payment.direction) {
        com.jcadenas.xpendz.domain.loan.projection.LoanPaymentDirection.IN -> Color(0xFF10B981)
        com.jcadenas.xpendz.domain.loan.projection.LoanPaymentDirection.OUT -> Color(0xFF3B82F6)
        null -> colors.onSurfaceVariant
    }

    val typeLabel = when (payment.direction) {
        com.jcadenas.xpendz.domain.loan.projection.LoanPaymentDirection.IN -> "Pago recibido"
        com.jcadenas.xpendz.domain.loan.projection.LoanPaymentDirection.OUT -> "Pago realizado"
        null -> "Pago"
    }

    val typeSymbol = when (payment.direction) {
        com.jcadenas.xpendz.domain.loan.projection.LoanPaymentDirection.IN -> "↓"
        com.jcadenas.xpendz.domain.loan.projection.LoanPaymentDirection.OUT -> "↑"
        null -> "•"
    }

    val dateFormat = remember {
        java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
    }
    val occurredAtFormatted = dateFormat.format(java.util.Date(payment.occurredAt * 1000))
    val amountFormatted = formatMoney(payment.amountCents, currency)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(shapes.extraLarge),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(
            elevation.level1,
            colors.onSurfaceVariant.copy(alpha = 0.05f)
        )
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
                    text = occurredAtFormatted,
                    style = typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                if (!payment.note.isNullOrBlank()) {
                    Text(
                        text = payment.note,
                        style = typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Amount and reverse action
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(spacing.xxs)
            ) {
                Text(
                    text = amountFormatted,
                    style = typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = typeColor
                )
                IconButton(
                    onClick = { onReverse(payment) },
                    enabled = !isReversing,
                    modifier = Modifier.size(spacing.s + spacing.xxs)
                ) {
                    if (isReversingThis) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(spacing.s),
                            strokeWidth = 2.dp,
                            color = colors.negative
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Revertir pago",
                            tint = if (isReversing) colors.negative.copy(alpha = 0.4f) else colors.negative,
                            modifier = Modifier.size(spacing.s)
                        )
                    }
                }
            }
        }
    }
}
