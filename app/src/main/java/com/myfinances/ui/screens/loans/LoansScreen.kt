package com.myfinances.ui.screens.loans

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import com.myfinances.ui.viewmodel.LoansViewModel
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
            TopAppBar(
                title = { Text("Préstamos") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refrescar")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                loanType = state.selectedTab
                selectedAccountId = ""
                counterparty = ""
                amountText = ""
                notes = ""
                loanDateEpochSec = System.currentTimeMillis() / 1000
                showCreateLoan = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Nuevo préstamo")
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { idx, (key, label) ->
                    Tab(
                        selected = idx == selectedTabIndex,
                        onClick = { viewModel.setTab(key) },
                        text = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn {
                items(state.loans) { loan ->
                    val paidCents = state.loanPaidCents[loan.id] ?: 0L
                    val remainingCents = (loan.principalCents - paidCents).coerceAtLeast(0L)
                    val totalText = formatMoney(loan.principalCents, loan.currency)
                    val paidText = formatMoney(paidCents, loan.currency)
                    val remainingText = formatMoney(remainingCents, loan.currency)

                    val totalColor = MaterialTheme.colorScheme.onSurfaceVariant
                    val paidColor = MaterialTheme.colorScheme.tertiary
                    val remainingColor = if (state.selectedTab == "LENT") {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(loan.counterpartyName)
                                    val subtitle = if (state.selectedTab == "LENT") "Te deben" else "Tú debes"
                                    Text(subtitle)

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Total: $totalText", color = totalColor)
                                    Text(
                                        text = if (state.selectedTab == "LENT") "Abonado: $paidText" else "Pagado: $paidText",
                                        color = paidColor
                                    )
                                    Text(
                                        text = if (state.selectedTab == "LENT") "Pendiente: $remainingText" else "Saldo: $remainingText",
                                        color = remainingColor
                                    )
                                }
                                Text(remainingText, color = remainingColor)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                val paymentLabel = if (state.selectedTab == "LENT") "Registrar abono" else "Registrar pago"
                                TextButton(onClick = {
                                    paymentLoanId = loan.id
                                    paymentAccountId = loan.accountId ?: ""
                                    paymentAmountText = ""
                                    paymentDateEpochSec = System.currentTimeMillis() / 1000
                                    showPayment = true
                                }) {
                                    Icon(Icons.Default.AttachMoney, contentDescription = null)
                                    Spacer(modifier = Modifier.height(0.dp))
                                    Text(paymentLabel)
                                }
                            }
                        }
                    }
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
            confirmButton = {
                TextButton(onClick = {
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
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateLoan = false }) { Text("Cancelar") }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                ) {
                    Text("Tipo")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = loanType == "LENT",
                            onClick = { loanType = "LENT" },
                            label = { Text("Presto") }
                        )
                        FilterChip(
                            selected = loanType == "BORROWED",
                            onClick = { loanType = "BORROWED" },
                            label = { Text("Pido prestado") }
                        )
                    }

                    Text("Cuenta")
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
                            value = if (accountCurrency.isBlank()) accountName else "$accountName ($accountCurrency)",
                            onValueChange = {},
                            readOnly = true,
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
                        )
                        DropdownMenu(
                            expanded = loanAccountExpanded,
                            onDismissRequest = { loanAccountExpanded = false }
                            ,
                            modifier = Modifier.width(with(density) { anchorSize.width.toDp() }),
                            properties = PopupProperties(focusable = true)
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
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(a.name)
                                                    Text(a.currency)
                                                }
                                                Text(formatMoney(bal, a.currency))
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
                    )

                    OutlinedTextField(
                        value = counterparty,
                        onValueChange = { counterparty = it },
                        label = { Text("Persona/Entidad") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    val accountCurrency = state.accounts.firstOrNull { it.id == selectedAccountId }?.currency.orEmpty()
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = {
                            Text(
                                if (accountCurrency.isBlank()) "Monto" else "Monto ($accountCurrency)"
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Nota") },
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
            confirmButton = {
                TextButton(onClick = {
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
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPayment = false }) { Text("Cancelar") }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                ) {
                    if (selectedLoan != null && loanCurrency.isNotBlank()) {
                        val totalColor = MaterialTheme.colorScheme.onSurfaceVariant
                        val paidColor = MaterialTheme.colorScheme.tertiary
                        val remainingColor = if (state.selectedTab == "LENT") {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total")
                            Text(formatMoney(totalDebtCents, loanCurrency), color = totalColor)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (state.selectedTab == "LENT") "Abonado" else "Pagado")
                            Text(formatMoney(alreadyPaidCents, loanCurrency), color = paidColor)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (state.selectedTab == "LENT") "Pendiente" else "Saldo")
                            Text(formatMoney(remainingDebtCents, loanCurrency), color = remainingColor)
                        }
                        if (remainingAfterPreview != null && enteredCentsPreview != null && enteredCentsPreview > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Después de este movimiento")
                                Text(formatMoney(remainingAfterPreview, loanCurrency), color = remainingColor)
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
                    )

                    Text("Cuenta")
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
                            supportingText = {
                                if (paymentAccountId.isNotBlank() && selectedBalance != null && accountCurrency.isNotBlank()) {
                                    Text("Disponible: ${formatMoney(selectedBalance, accountCurrency)}")
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
                        )
                        DropdownMenu(
                            expanded = paymentAccountExpanded,
                            onDismissRequest = { paymentAccountExpanded = false }
                            ,
                            modifier = Modifier.width(with(density) { anchorSize.width.toDp() }),
                            properties = PopupProperties(focusable = true)
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
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(a.name)
                                                    Text(a.currency)
                                                }
                                                Text(formatMoney(bal, a.currency))
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
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (enteredCentsPreview != null && enteredCentsPreview > remainingDebtCents && loanCurrency.isNotBlank()) {
                        Text("El monto excede lo pendiente (${formatMoney(remainingDebtCents, loanCurrency)})")
                    }

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

private fun formatMoney(amountCents: Long, currency: String): String {
    val amount = BigDecimal(amountCents).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
    val nf = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }
    return "${nf.format(amount)} $currency"
}
