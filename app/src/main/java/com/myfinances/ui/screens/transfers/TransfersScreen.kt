package com.jcadenas.xpendz.ui.screens.transfers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jcadenas.xpendz.data.local.dao.TransferWithDetails
import com.jcadenas.xpendz.data.local.entity.AccountEntity
import com.jcadenas.xpendz.ui.components.CompactHeader
import com.jcadenas.xpendz.ui.components.SyncSwipeRefresh
import com.jcadenas.xpendz.ui.theme.Transfer
import com.jcadenas.xpendz.ui.viewmodel.SyncViewModel
import com.jcadenas.xpendz.ui.viewmodel.TransfersViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

private fun getDateGroupHeader(occurredAtEpochSec: Long): String {
    val zone = ZoneId.systemDefault()
    val transferDate = Instant.ofEpochSecond(occurredAtEpochSec).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val yesterday = today.minusDays(1)
    
    return when {
        transferDate.isEqual(today) -> "Hoy"
        transferDate.isEqual(yesterday) -> "Ayer"
        else -> {
            val dayFormat = SimpleDateFormat("dd MMM yyyy", Locale("es"))
            dayFormat.format(Date(occurredAtEpochSec * 1000))
        }
    }
}

private fun accountDefaultIconKey(accountType: String?): String {
    return when (accountType?.trim()?.uppercase()) {
        "CASH" -> "cash"
        "SAVINGS" -> "savings"
        "VIRTUAL_WALLET" -> "wallet"
        "DIGITAL_ACCOUNT" -> "digital"
        "CREDIT" -> "card"
        else -> "bank"
    }
}

private fun accountIconForKey(iconKey: String?, accountType: String?): ImageVector {
    return when (iconKey?.lowercase() ?: accountDefaultIconKey(accountType)) {
        "bank" -> Icons.Default.AccountBalance
        "wallet" -> Icons.Default.AccountBalanceWallet
        "cash" -> Icons.Default.Money
        "card" -> Icons.Default.CreditCard
        "savings" -> Icons.Default.Savings
        "digital" -> Icons.Default.PhoneAndroid
        else -> Icons.Default.AccountBalance
    }
}

private fun accountAccentColor(account: AccountEntity?): Color {
    val fallback = when (account?.type?.trim()?.uppercase()) {
        "CASH" -> Color(0xFF059669)
        "SAVINGS" -> Color(0xFFD97706)
        "VIRTUAL_WALLET" -> Color(0xFF0891B2)
        "DIGITAL_ACCOUNT" -> Color(0xFFE11D48)
        "CREDIT" -> Color(0xFF7C3AED)
        else -> Color(0xFF2463EB)
    }

    val stored = account?.colorHex?.takeIf { it.isNotBlank() }?.let { hex ->
        runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
    }

    return stored ?: fallback
}

@Composable
private fun TransferSummaryCard(
    transferCount: Int,
    totalTransferredCents: Long,
    currencyFormat: NumberFormat,
    modifier: Modifier = Modifier
) {
    val transferTotalText = currencyFormat.format(totalTransferredCents / 100.0)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = Transfer.copy(alpha = 0.12f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = Transfer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Transferencias del período",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$transferCount movimientos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = transferTotalText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Transfer
                )
            }

            Text(
                text = "$transferTotalText transferidos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TransferAccountBadge(
    label: String,
    account: AccountEntity?,
    fallbackName: String,
    modifier: Modifier = Modifier
) {
    val accent = remember(account?.colorHex, account?.type) { accountAccentColor(account) }
    val icon = remember(account?.iconKey, account?.type) {
        accountIconForKey(account?.iconKey, account?.type)
    }
    val name = account?.name?.takeIf { it.isNotBlank() } ?: fallbackName

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = accent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = accent.copy(alpha = 0.18f),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransfersScreen(
    onNavigateBack: () -> Unit,
    onAddTransfer: () -> Unit,
    onEditTransfer: (String) -> Unit,
    viewModel: TransfersViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("es", "CO")) }
    val monthLabelFormat = remember { SimpleDateFormat("MMMM", Locale("es")) }
    val accountsById = remember(state.accounts) { state.accounts.associateBy { it.id } }

    val syncViewModel: SyncViewModel = hiltViewModel()
    val syncVersion by syncViewModel.syncVersion.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadTransfers()
    }

    LaunchedEffect(syncVersion) {
        viewModel.loadTransfers()
    }

    Scaffold(
        topBar = {
            CompactHeader(
                title = {
                    Text(
                        text = "Transferencias",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTransfer) {
                Icon(Icons.Default.Add, contentDescription = "Nueva transferencia")
            }
        }
    ) { paddingValues ->
        SyncSwipeRefresh(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TransfersFiltersHeader(
                    state = state,
                    monthLabelFormat = monthLabelFormat,
                    onSearch = { viewModel.setSearchQuery(it) },
                    onMonthSelected = { y, m -> viewModel.setMonth(y, m) },
                    onAccountSelected = { viewModel.filterByAccount(it) }
                )

                if (!state.isLoading || state.transfers.isNotEmpty()) {
                    TransferSummaryCard(
                        transferCount = state.transfers.size,
                        totalTransferredCents = state.totalTransferredCents,
                        currencyFormat = currencyFormat,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    when {
                        state.isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        state.transfers.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Surface(
                                        shape = MaterialTheme.shapes.extraLarge,
                                        color = Transfer.copy(alpha = 0.10f),
                                        modifier = Modifier.size(72.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.SwapHoriz,
                                                contentDescription = null,
                                                modifier = Modifier.size(36.dp),
                                                tint = Transfer
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "No hay transferencias",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Ajusta filtros o crea una nueva transferencia.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                var lastHeader: String? = null
                                itemsIndexed(state.transfers) { index, transfer ->
                                    val header = getDateGroupHeader(transfer.occurredAtEpochSec)
                                    if (lastHeader != header) {
                                        lastHeader = header
                                        DateGroupHeader(text = header)
                                    }

                                    val alternate = index % 2 == 1
                                    val containerColor = if (alternate) {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }

                                    TransferItem(
                                        transfer = transfer,
                                        fromAccount = accountsById[transfer.fromAccountId],
                                        toAccount = accountsById[transfer.toAccountId],
                                        currencyFormat = currencyFormat,
                                        containerColor = containerColor,
                                        onEdit = { onEditTransfer(transfer.id) },
                                        onDelete = { viewModel.deleteTransfer(transfer.id) }
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

@Composable
private fun TransfersFiltersHeader(
    state: com.jcadenas.xpendz.ui.viewmodel.TransfersState,
    monthLabelFormat: SimpleDateFormat,
    onSearch: (String) -> Unit,
    onMonthSelected: (Int, Int) -> Unit,
    onAccountSelected: (String?) -> Unit
) {
    var showMonthSheet by remember { mutableStateOf(false) }
    var showAccountSheet by remember { mutableStateOf(false) }
    val monthOptions = remember(state.availableMonthsYearMonth) { state.availableMonthsYearMonth }

    val selectedAccountLabel = remember(state.selectedAccountId, state.accounts) {
        if (state.selectedAccountId.isNullOrBlank()) {
            "Todas las cuentas"
        } else {
            state.accounts.find { it.id == state.selectedAccountId }?.name ?: "Cuenta"
        }
    }

    val monthLabel = remember(state.selectedYear, state.selectedMonth) {
        val y = state.selectedYear
        val m = state.selectedMonth
        if (y == null || m == null) {
            "Mes"
        } else {
            val cal = Calendar.getInstance()
            cal.set(Calendar.YEAR, y)
            cal.set(Calendar.MONTH, m - 1)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            val monthName = monthLabelFormat
                .format(cal.time)
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            "$monthName $y"
        }
    }

    if (showMonthSheet) {
        MonthPickerBottomSheet(
            monthOptions = monthOptions,
            monthLabelFormat = monthLabelFormat,
            onDismiss = { showMonthSheet = false },
            onSelected = { y, m ->
                showMonthSheet = false
                onMonthSelected(y, m)
            }
        )
    }

    if (showAccountSheet) {
        AccountPickerBottomSheet(
            title = "Cuenta",
            accounts = state.accounts,
            selectedAccountId = state.selectedAccountId,
            onDismiss = { showAccountSheet = false },
            onSelected = {
                showAccountSheet = false
                onAccountSelected(it)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                onClick = { showMonthSheet = true },
                label = { Text(monthLabel) },
                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                modifier = Modifier.heightIn(min = 34.dp)
            )

            AssistChip(
                onClick = { showAccountSheet = true },
                label = {
                    Text(
                        selectedAccountLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                modifier = Modifier
                    .heightIn(min = 34.dp)
                    .weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearch,
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Buscar transferencia...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountPickerBottomSheet(
    title: String,
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
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(null) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            if (selectedAccountId.isNullOrBlank()) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (selectedAccountId.isNullOrBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("Todas las cuentas", style = MaterialTheme.typography.bodyLarge)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))

                    accounts.forEach { account ->
                        val selected = selectedAccountId == account.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelected(account.id) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(account.name, style = MaterialTheme.typography.bodyLarge)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DateGroupHeader(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun TransferItem(
    transfer: TransferWithDetails,
    fromAccount: AccountEntity?,
    toAccount: AccountEntity?,
    currencyFormat: NumberFormat,
    containerColor: Color,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateTimeFormat = remember { SimpleDateFormat("dd MMM yyyy · hh:mm a", Locale("es")) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = currencyFormat.format(transfer.amountCents / 100.0),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Transfer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = dateTimeFormat.format(Date(transfer.occurredAtEpochSec * 1000)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menú de transferencia")
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            },
                            text = { Text("Editar") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            text = { Text("Eliminar") },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TransferAccountBadge(
                    label = "Desde",
                    account = fromAccount,
                    fallbackName = transfer.fromAccountName,
                    modifier = Modifier.fillMaxWidth()
                )

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                TransferAccountBadge(
                    label = "Hacia",
                    account = toAccount,
                    fallbackName = transfer.toAccountName,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (!transfer.note.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Notes,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = transfer.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
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
            Text("Seleccionar mes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                        val monthName = monthLabelFormat
                            .format(cal.time)
                            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                        val selectedLabel = "$monthName $year"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelected(year, month) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null)
                            Text(selectedLabel, style = MaterialTheme.typography.bodyLarge)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
