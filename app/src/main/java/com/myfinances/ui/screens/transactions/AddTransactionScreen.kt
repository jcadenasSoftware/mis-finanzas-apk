package com.myfinances.ui.screens.transactions

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.myfinances.data.local.entity.AccountEntity
import com.myfinances.data.local.entity.CategoryEntity
import com.myfinances.ui.components.CompactHeader
import com.myfinances.ui.theme.Income
import com.myfinances.ui.theme.Expense
import com.myfinances.ui.viewmodel.TransactionsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    transactionId: String? = null,
    initialKind: String? = null,
    onNavigateBack: () -> Unit,
    onTransactionSaved: () -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val formState by viewModel.formState.collectAsState()
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale("es")) }
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("es", "CO")) }

    var initialKindApplied by remember(transactionId, initialKind) { mutableStateOf(false) }

    LaunchedEffect(transactionId) {
        viewModel.initForm(transactionId)
    }

    LaunchedEffect(formState.isLoading, transactionId, initialKind) {
        if (!initialKindApplied && !formState.isLoading && transactionId == null && !initialKind.isNullOrBlank()) {
            initialKindApplied = true
            if (formState.kind != initialKind) {
                viewModel.updateFormKind(initialKind)
            }
        }
    }

    LaunchedEffect(formState.isSaved) {
        if (formState.isSaved) {
            onTransactionSaved()
        }
    }

    Scaffold(
        topBar = {
            CompactHeader(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = com.myfinances.R.drawable.ic_launcher),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            if (transactionId != null) "Editar transacción" else "Nueva transacción",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (formState.isLoading && formState.accounts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            AddTransactionFormContent(
                formState = formState,
                dateFormat = dateFormat,
                currencyFormat = currencyFormat,
                onKind = { viewModel.updateFormKind(it) },
                onAmount = { viewModel.updateFormAmount(it) },
                onAccount = { viewModel.updateFormAccount(it) },
                onRootCategory = { viewModel.updateFormRootCategory(it) },
                onCategory = { viewModel.updateFormCategory(it) },
                onDateChange = { viewModel.updateFormDate(it) },
                onNote = { viewModel.updateFormNote(it) },
                onSubmit = { viewModel.saveTransaction() },
                showStickySave = true,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(
    sessionId: Int,
    initialKind: String? = null,
    onDismiss: () -> Unit,
    onTransactionSaved: () -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val amountFocusRequester = remember { FocusRequester() }
    var amountAutoFocused by remember(sessionId) { mutableStateOf(false) }
    val formState by viewModel.formState.collectAsState()
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale("es")) }
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("es", "CO")) }
    var initialKindApplied by remember(sessionId) { mutableStateOf(false) }
    var sawLoading by remember(sessionId) { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val prefs = remember {
        context.getSharedPreferences("myfinances_prefs", android.content.Context.MODE_PRIVATE)
    }
    var restoreApplied by remember(sessionId) { mutableStateOf(false) }
    var restorePendingCategoryId by remember(sessionId) { mutableStateOf<String?>(null) }

    LaunchedEffect(sessionId, initialKind) {
        initialKindApplied = false
        sawLoading = false
        viewModel.prepareNewForm()
        viewModel.initForm(null)
    }

    LaunchedEffect(formState.isLoading, initialKind) {
        if (formState.isLoading) {
            sawLoading = true
        }
        if (!initialKindApplied && sawLoading && !formState.isLoading && !initialKind.isNullOrBlank()) {
            initialKindApplied = true
            if (formState.kind != initialKind) {
                viewModel.updateFormKind(initialKind)
            }
        }
    }

    LaunchedEffect(formState.isSaved) {
        if (formState.isSaved) {
            val kindKey = formState.kind.trim().uppercase()
            val rootId = formState.selectedRootCategoryId
            val categoryId = formState.categoryId
            if (!rootId.isNullOrBlank() && categoryId.isNotBlank()) {
                prefs.edit()
                    .putString("last_root_$kindKey", rootId)
                    .putString("last_category_$kindKey", categoryId)
                    .apply()
            }
            viewModel.consumeSaved()
            onTransactionSaved()
        }
    }

    LaunchedEffect(formState.error) {
        val msg = formState.error
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message = msg)
            viewModel.clearError()
        }
    }

    // Restore last used category after form is initialized and kind is applied
    LaunchedEffect(formState.isLoading, formState.kind, formState.rootCategories, initialKindApplied, sawLoading) {
        if (restoreApplied) return@LaunchedEffect
        if (!sawLoading || formState.isLoading) return@LaunchedEffect
        if (formState.rootCategories.isEmpty()) return@LaunchedEffect
        if (!initialKindApplied && !initialKind.isNullOrBlank()) return@LaunchedEffect

        val kindKey = formState.kind.trim().uppercase()
        val lastRoot = prefs.getString("last_root_$kindKey", null)
        val lastCategory = prefs.getString("last_category_$kindKey", null)

        if (!lastRoot.isNullOrBlank() && formState.rootCategories.any { it.id == lastRoot }) {
            restoreApplied = true
            restorePendingCategoryId = lastCategory
            viewModel.updateFormRootCategory(lastRoot)
        } else {
            restoreApplied = true
        }
    }

    LaunchedEffect(formState.subCategories, restorePendingCategoryId, formState.selectedRootCategoryId) {
        val pending = restorePendingCategoryId ?: return@LaunchedEffect
        if (pending.isBlank()) return@LaunchedEffect
        // If root has children and pending is one of them, select it.
        if (formState.subCategories.any { it.id == pending }) {
            viewModel.updateFormCategory(pending)
        }
        restorePendingCategoryId = null
    }

    // Auto-focus amount and show numeric keyboard once the sheet is ready.
    LaunchedEffect(formState.isLoading, sawLoading) {
        if (amountAutoFocused) return@LaunchedEffect
        if (!sawLoading || formState.isLoading) return@LaunchedEffect
        amountAutoFocused = true
        delay(120)
        amountFocusRequester.requestFocus()
        keyboardController?.show()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = { viewModel.saveTransaction() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = !formState.isLoading,
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (formState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Guardar transacción")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Nueva transacción",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar")
                }
            }

            Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))

            if (formState.isLoading && formState.accounts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                AddTransactionFormContent(
                    modifier = Modifier.weight(1f),
                    formState = formState,
                    dateFormat = dateFormat,
                    currencyFormat = currencyFormat,
                    onKind = { viewModel.updateFormKind(it) },
                    onAmount = { viewModel.updateFormAmount(it) },
                    onAccount = { viewModel.updateFormAccount(it) },
                    onRootCategory = { viewModel.updateFormRootCategory(it) },
                    onCategory = { viewModel.updateFormCategory(it) },
                    onNote = { viewModel.updateFormNote(it) },
                    onDateChange = { viewModel.updateFormDate(it) },
                    onSubmit = { viewModel.saveTransaction() },
                    showStickySave = false,
                    amountFocusRequester = amountFocusRequester
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionFormContent(
    formState: com.myfinances.ui.viewmodel.TransactionFormState,
    dateFormat: SimpleDateFormat,
    currencyFormat: NumberFormat,
    onKind: (String) -> Unit,
    onAmount: (String) -> Unit,
    onAccount: (String) -> Unit,
    onRootCategory: (String) -> Unit,
    onCategory: (String) -> Unit,
    onNote: (String) -> Unit,
    onDateChange: (Long) -> Unit,
    onSubmit: () -> Unit,
    showStickySave: Boolean,
    modifier: Modifier = Modifier,
    amountFocusRequester: FocusRequester? = null
) {
    fun isCompatibleCategoryKind(categoryKind: String, txKind: String): Boolean {
        val k = categoryKind.trim().uppercase()
        val t = txKind.trim().uppercase()
        return k == "BOTH" || k == t
    }

    fun sanitizeAmountInput(input: String): String {
        return input.filter { it.isDigit() || it == '.' || it == ',' }
    }

    val compatibleRootCategories = remember(formState.rootCategories, formState.kind) {
        formState.rootCategories.filter { isCompatibleCategoryKind(it.kind, formState.kind) }
    }
    val quickCategories = remember(compatibleRootCategories) { compatibleRootCategories.take(6) }

    var rootCategoryExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Transaction Type
        Text("Tipo de transacción", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilterChip(
                selected = formState.kind == "EXPENSE",
                onClick = { onKind("EXPENSE") },
                label = { Text("Gasto") },
                leadingIcon = {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = if (formState.kind == "EXPENSE") Expense else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                shape = MaterialTheme.shapes.extraLarge,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Expense.copy(alpha = 0.18f),
                    selectedLabelColor = Expense,
                    selectedLeadingIconColor = Expense,
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = if (formState.kind == "EXPENSE") null else BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                ),
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = formState.kind == "INCOME",
                onClick = { onKind("INCOME") },
                label = { Text("Ingreso") },
                leadingIcon = {
                    Icon(
                        Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = if (formState.kind == "INCOME") Income else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                shape = MaterialTheme.shapes.extraLarge,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Income.copy(alpha = 0.18f),
                    selectedLabelColor = Income,
                    selectedLeadingIconColor = Income,
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = if (formState.kind == "INCOME") null else BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                ),
                modifier = Modifier.weight(1f)
            )
        }

        // Amount (hero)
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$",
                    style = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Spacer(modifier = Modifier.width(10.dp))
                BasicTextField(
                    value = formState.amountText,
                    onValueChange = { onAmount(sanitizeAmountInput(it)) },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .then(if (amountFocusRequester != null) Modifier.focusRequester(amountFocusRequester) else Modifier)
                )
            }
        }

        // Category

        // Root Category Dropdown
        ExposedDropdownMenuBox(
            expanded = rootCategoryExpanded,
            onExpandedChange = { rootCategoryExpanded = it }
        ) {
            OutlinedTextField(
                value = formState.rootCategories.find { it.id == formState.selectedRootCategoryId }?.name ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Categoría") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = rootCategoryExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = rootCategoryExpanded,
                onDismissRequest = { rootCategoryExpanded = false },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                compatibleRootCategories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = {
                            onRootCategory(category.id)
                            rootCategoryExpanded = false
                        }
                    )
                }
            }
        }

        // Subcategory Dropdown (if available)
        if (formState.subCategories.isNotEmpty()) {
            var subCategoryExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = subCategoryExpanded,
                onExpandedChange = { subCategoryExpanded = it }
            ) {
                OutlinedTextField(
                    value = formState.subCategories.find { it.id == formState.categoryId }?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Subcategoría") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subCategoryExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = subCategoryExpanded,
                    onDismissRequest = { subCategoryExpanded = false },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    formState.subCategories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                onCategory(category.id)
                                subCategoryExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Account
        Text("Cuenta", style = MaterialTheme.typography.labelLarge)
        var accountExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = accountExpanded,
            onExpandedChange = { accountExpanded = it }
        ) {
            val selectedAccount = formState.accounts.firstOrNull { it.id == formState.accountId }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                shape = MaterialTheme.shapes.extraLarge,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { accountExpanded = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedAccount?.name ?: "",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        formState.accountBalanceCents?.let { balanceCents ->
                            Text(
                                text = "Saldo: ${currencyFormat.format(balanceCents / 100.0)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = if (accountExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            ExposedDropdownMenu(
                expanded = accountExpanded,
                onDismissRequest = { accountExpanded = false },
                modifier = Modifier
                    .background(Color.White)
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                formState.accounts.forEach { account ->
                    DropdownMenuItem(
                        text = { Text(account.name) },
                        onClick = {
                            onAccount(account.id)
                            accountExpanded = false
                        }
                    )
                }
            }
        }

        // Date
        val context = LocalContext.current
        var showDatePicker by remember { mutableStateOf(false) }
        if (showDatePicker) {
            val cal = Calendar.getInstance().apply { timeInMillis = formState.occurredAtEpochSec * 1000 }
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
                    onDateChange(c.timeInMillis / 1000)
                    showDatePicker = false
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).apply {
                setOnCancelListener { showDatePicker = false }
            }.show()
        }

        OutlinedTextField(
            value = dateFormat.format(Date(formState.occurredAtEpochSec * 1000)),
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
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
            )
        )

        // Note
        OutlinedTextField(
            value = formState.note,
            onValueChange = { onNote(it) },
            label = { Text("Nota (opcional)") },
            leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth()
        )

        if (showStickySave) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !formState.isLoading,
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (formState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (formState.id != null) "Actualizar" else "Guardar")
                }
            }
        }
    }
}
