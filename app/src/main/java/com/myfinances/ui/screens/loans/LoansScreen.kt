package com.myfinances.ui.screens.loans

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import com.myfinances.ui.components.CompactHeader
import com.myfinances.ui.components.SyncSwipeRefresh
import com.myfinances.ui.theme.Expense
import com.myfinances.ui.theme.Income
import com.myfinances.ui.viewmodel.LoansViewModel
import com.myfinances.ui.viewmodel.SyncViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansScreen(
    onNavigateBack: () -> Unit,
    viewModel: LoansViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

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


    val context = LocalContext.current

    val tabs = listOf("LENT" to "Me deben", "BORROWED" to "Yo debo")
    val selectedTabIndex = tabs.indexOfFirst { it.first == state.selectedTab }.coerceAtLeast(0)

    Scaffold(
        topBar = {
            CompactHeader(
                title = {
                    Text(
                        text = "Préstamos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
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
                .padding(16.dp)
        ) {
            LoansSummaryCard(
                lentRemainingCents = state.totalLentRemainingCents,
                borrowedRemainingCents = state.totalBorrowedRemainingCents
            )

            Spacer(modifier = Modifier.height(14.dp))

            LoansSegmentedTabs(
                selectedTab = state.selectedTab,
                onSelectTab = { viewModel.setTab(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.loans) { loan ->
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
            onDismissRequest = { showCreateLoan = false },
            title = { Text(dialogTitle) },
            containerColor = Color.White,
            confirmButton = {
                Button(onClick = {
                    val currency = state.accounts.firstOrNull { it.id == selectedAccountId }?.currency.orEmpty()
                    val cents = runCatching {
                        val normalized = amountText.trim().replace(',', '.')
                        BigDecimal(normalized).multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()
                    }.getOrNull()

                    if (selectedAccountId.isNotBlank() && !counterparty.isBlank() && cents != null) {
                        viewModel.createLoan(
                            type = loanType,
                            accountId = selectedAccountId,
                            counterparty = counterparty.trim(),
                            principalCents = cents,
                            occurredAtEpochSec = loanDateEpochSec,
                            notes = notes.takeIf { it.isNotBlank() }
                        )
                        showCreateLoan = false
                        counterparty = ""
                        amountText = ""
                        notes = ""
                    }
                },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2463EB))
                ) { Text("Guardar") }
            },
            dismissButton = {
                FilledTonalButton(
                    onClick = { showCreateLoan = false },
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
                    LoanTypeSegmentedTabs(
                        selectedType = loanType,
                        onSelect = { loanType = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

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
                                        contentDescription = "Ver cuentas"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { anchorSize = it.size }
                            ,
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
                            expanded = loanAccountExpanded,
                            onDismissRequest = { loanAccountExpanded = false }
                            ,
                            modifier = Modifier
                                .width(with(density) { anchorSize.width.toDp() })
                                .clip(MaterialTheme.shapes.extraLarge)
                                .background(Color.White),
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
                                        onClick = { loanAccountExpanded = false }
                                    )
                                } else {
                                    state.accounts.forEach { a ->
                                        DropdownMenuItem(
                                            text = { Text(a.name) },
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
                                Icon(Icons.Default.DateRange, contentDescription = "Elegir fecha")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showLoanDatePicker = true
                            }
                        ,
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            disabledContainerColor = Color.White,
                            focusedBorderColor = Color(0xFF2463EB),
                            unfocusedBorderColor = Color(0xFFD8DFEA)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LoanModernTextField(
                        value = counterparty,
                        onValueChange = { counterparty = it },
                        label = "Persona o entidad",
                        modifier = Modifier.fillMaxWidth()
                    )
                    val accountCurrency = state.accounts.firstOrNull { it.id == selectedAccountId }?.currency.orEmpty()

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = {
                            Text(if (accountCurrency.isBlank()) "Monto" else "Monto ($accountCurrency)")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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

                    Spacer(modifier = Modifier.height(12.dp))

                    LoanModernTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = "Nota (opcional)",
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
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
            containerColor = Color.White,
            confirmButton = {
                Button(onClick = {
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
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2463EB))
                ) { Text("Guardar") }
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

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = baseColor.copy(alpha = 0.08f),
                            shape = MaterialTheme.shapes.extraLarge
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        formatMoney(totalDebtCents, loanCurrency),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
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
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        if (state.selectedTab == "LENT") "Pendiente" else "Saldo",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        formatMoney(remainingDebtCents, loanCurrency),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = baseColor
                                    )
                                }

                                if (remainingAfterPreview != null && enteredCentsPreview != null && enteredCentsPreview > 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Después de este movimiento",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            formatMoney(remainingAfterPreview, loanCurrency),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = baseColor
                                        )
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
                                Icon(Icons.Default.DateRange, contentDescription = "Elegir fecha")
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
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            disabledContainerColor = Color.White,
                            focusedBorderColor = Color(0xFF2463EB),
                            unfocusedBorderColor = Color(0xFFD8DFEA)
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
                                        contentDescription = "Ver cuentas"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { anchorSize = it.size }
                            ,
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
                            expanded = paymentAccountExpanded,
                            onDismissRequest = { paymentAccountExpanded = false }
                            ,
                            modifier = Modifier
                                .width(with(density) { anchorSize.width.toDp() })
                                .clip(MaterialTheme.shapes.extraLarge)
                                .background(Color.White),
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
                                                    Text(formatAmount(bal))
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
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            disabledContainerColor = Color.White,
                            focusedBorderColor = Color(0xFF2463EB),
                            unfocusedBorderColor = Color(0xFFD8DFEA)
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
}

@Composable
private fun LoansSummaryCard(
    lentRemainingCents: Long,
    borrowedRemainingCents: Long
) {
    val currency = ""
    val teDebenText = formatMoney(lentRemainingCents, currency).trim()
    val debesText = formatMoney(borrowedRemainingCents, currency).trim()
    val balanceCents = lentRemainingCents - borrowedRemainingCents
    val balanceText = formatMoney(kotlin.math.abs(balanceCents), currency).trim()
    val balancePositive = balanceCents >= 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
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
                text = "Balance de préstamos",
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
                    LoansSummaryBackgroundGraph(
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
private fun SummaryRow(
    label: String,
    value: String,
    valueColor: Color,
    emphasize: Boolean = false,
    labelBold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (emphasize) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (labelBold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = value,
            style = if (emphasize) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
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
    val lentSelected = selectedTab != "BORROWED"
    val lentBg by animateColorAsState(if (lentSelected) Color(0xFF2463EB) else Color(0xFFF1F3F7), label = "lentBg")
    val borrowedBg by animateColorAsState(if (!lentSelected) Color(0xFF2463EB) else Color(0xFFF1F3F7), label = "borrowedBg")
    val lentFg = if (lentSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val borrowedFg = if (!lentSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Color(0xFFF1F3F7))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
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
    Surface(
        modifier = modifier
            .height(44.dp)
            .clickable(onClick = onClick),
        color = background,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = foreground,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun LoanTypeSegmentedTabs(
    selectedType: String,
    onSelect: (String) -> Unit
) {
    val isLent = selectedType != "BORROWED"
    val lentBg by animateColorAsState(
        targetValue = if (isLent) Income else Color(0xFFF1F3F7),
        label = "lentTypeBg"
    )
    val borrowedBg by animateColorAsState(
        targetValue = if (!isLent) Expense else Color(0xFFF1F3F7),
        label = "borrowedTypeBg"
    )
    val lentFg = if (isLent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val borrowedFg = if (!isLent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Color(0xFFF1F3F7))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        placeholder = { Text(label) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color(0xFFF5F7FA),
            unfocusedContainerColor = Color(0xFFF5F7FA),
            disabledContainerColor = Color(0xFFF5F7FA),
            focusedBorderColor = Color(0xFF2463EB),
            unfocusedBorderColor = Color(0xFFD8DFEA)
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
    loan: com.myfinances.data.local.entity.LoanEntity,
    paidCents: Long,
    isLent: Boolean,
    onRegisterPayment: () -> Unit
) {
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
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = loan.counterpartyName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isLent) "Te deben" else "Tú debes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = remainingText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = baseColor
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pendiente: ${formatMoney(remainingCents, loan.currency)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = statusLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = statusColor,
                trackColor = statusColor.copy(alpha = 0.18f)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "$percent% pagado",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            FilledTonalButton(
                onClick = onRegisterPayment,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = baseColor.copy(alpha = 0.12f))
            ) {
                Icon(Icons.Default.AttachMoney, contentDescription = null, tint = baseColor)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isLent) "+ Abono" else "+ Pago",
                    color = baseColor,
                    fontWeight = FontWeight.SemiBold
                )
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
