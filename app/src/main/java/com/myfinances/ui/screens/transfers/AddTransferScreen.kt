package com.myfinances.ui.screens.transfers

import android.app.DatePickerDialog
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.myfinances.data.local.entity.AccountEntity
import com.myfinances.ui.components.CompactHeader
import com.myfinances.ui.theme.Expense
import com.myfinances.ui.theme.Income
import com.myfinances.ui.theme.Transfer
import com.myfinances.ui.viewmodel.TransfersViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

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

private fun accountIconForKey(iconKey: String?, accountType: String?): androidx.compose.ui.graphics.vector.ImageVector {
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

private fun accountAccentColor(account: AccountEntity?): androidx.compose.ui.graphics.Color {
    val fallback = when (account?.type?.trim()?.uppercase()) {
        "CASH" -> androidx.compose.ui.graphics.Color(0xFF059669)
        "SAVINGS" -> androidx.compose.ui.graphics.Color(0xFFD97706)
        "VIRTUAL_WALLET" -> androidx.compose.ui.graphics.Color(0xFF0891B2)
        "DIGITAL_ACCOUNT" -> androidx.compose.ui.graphics.Color(0xFFE11D48)
        "CREDIT" -> androidx.compose.ui.graphics.Color(0xFF7C3AED)
        else -> androidx.compose.ui.graphics.Color(0xFF2463EB)
    }

    val stored = account?.colorHex?.takeIf { it.isNotBlank() }?.let { hex ->
        runCatching { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
    }

    return stored ?: fallback
}

@Composable
private fun TransferPreviewCard(
    amountCents: Long?,
    amountText: String,
    fromAccount: AccountEntity?,
    toAccount: AccountEntity?,
    fromProjectedBalanceCents: Long?,
    toProjectedBalanceCents: Long?,
    currencyFormat: NumberFormat,
    modifier: Modifier = Modifier
) {
    val accent = Transfer
    val previewAmountText = amountCents?.let { currencyFormat.format(it / 100.0) }
    val fromLabel = fromAccount?.name?.takeIf { it.isNotBlank() } ?: "Seleccionar origen"
    val toLabel = toAccount?.name?.takeIf { it.isNotBlank() } ?: "Seleccionar destino"

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = accent.copy(alpha = 0.12f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Resumen antes de guardar",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (!amountText.isBlank()) "Revisa antes de confirmar" else "Completa el monto para ver el resumen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Transferir",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = previewAmountText ?: "—",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TransferAccountSummaryPill(
                    label = "Desde",
                    account = fromAccount,
                    fallbackName = fromLabel,
                    amountLabel = if (fromAccount != null && fromProjectedBalanceCents != null && fromAccount.name.isNotBlank()) {
                        "Después: ${currencyFormat.format(fromProjectedBalanceCents / 100.0)}"
                    } else {
                        "Saldo disponible"
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                TransferAccountSummaryPill(
                    label = "Hacia",
                    account = toAccount,
                    fallbackName = toLabel,
                    amountLabel = if (toAccount != null && toProjectedBalanceCents != null && toAccount.name.isNotBlank()) {
                        "Después: ${currencyFormat.format(toProjectedBalanceCents / 100.0)}"
                    } else {
                        "Saldo destino"
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun TransferAccountSummaryPill(
    label: String,
    account: AccountEntity?,
    fallbackName: String,
    amountLabel: String,
    modifier: Modifier = Modifier
) {
    val accent = remember(account?.colorHex, account?.type) { accountAccentColor(account) }
    val icon = remember(account?.iconKey, account?.type) { accountIconForKey(account?.iconKey, account?.type) }
    val name = account?.name?.takeIf { it.isNotBlank() } ?: fallbackName

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = accent.copy(alpha = 0.12f),
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

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = amountLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
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
fun AddTransferScreen(
    transferId: String? = null,
    onNavigateBack: () -> Unit,
    onTransferSaved: () -> Unit,
    viewModel: TransfersViewModel = hiltViewModel()
) {
    val formState by viewModel.formState.collectAsState()
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale("es")) }
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("es", "CO")) }

    val snackbarHostState = remember { SnackbarHostState() }

    val fromAccount = remember(formState.accounts, formState.fromAccountId) {
        formState.accounts.find { it.id == formState.fromAccountId }
    }
    val toAccount = remember(formState.accounts, formState.toAccountId) {
        formState.accounts.find { it.id == formState.toAccountId }
    }

    val amountCents = remember(formState.amountText) {
        val raw = formState.amountText.trim()
        if (raw.isBlank()) {
            null
        } else {
            val cleaned = run {
                val noSpaces = raw.replace(" ", "")
                if (noSpaces.contains(',')) {
                    noSpaces.replace(".", "").replace(',', '.')
                } else if (noSpaces.count { it == '.' } >= 1) {
                    val parts = noSpaces.split('.')
                    val last = parts.lastOrNull()
                    if (parts.size > 1 && last != null && last.length == 3) {
                        noSpaces.replace(".", "")
                    } else {
                        noSpaces
                    }
                } else {
                    noSpaces
                }
            }
            cleaned.toDoubleOrNull()?.let { (it * 100).toLong() }
        }
    }
    val fromAfterCents = remember(formState.fromAccountBalanceCents, amountCents) {
        val bal = formState.fromAccountBalanceCents
        if (bal == null || amountCents == null) null else bal - amountCents
    }
    val toAfterCents = remember(formState.toAccountBalanceCents, amountCents) {
        val bal = formState.toAccountBalanceCents
        if (bal == null || amountCents == null) null else bal + amountCents
    }

    var showFromSheet by remember { mutableStateOf(false) }
    var showToSheet by remember { mutableStateOf(false) }
    var swapFlip by remember { mutableStateOf(false) }
    val swapRotation by animateFloatAsState(
        targetValue = if (swapFlip) 180f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "swapRotation"
    )

    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(transferId) {
        viewModel.initForm(transferId)
    }

    LaunchedEffect(showDatePicker, formState.occurredAtEpochSec) {
        if (!showDatePicker) return@LaunchedEffect

        val cal = Calendar.getInstance()
        cal.timeInMillis = formState.occurredAtEpochSec * 1000L

        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val picked = Calendar.getInstance()
                picked.set(Calendar.YEAR, year)
                picked.set(Calendar.MONTH, month)
                picked.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                picked.set(Calendar.HOUR_OF_DAY, 0)
                picked.set(Calendar.MINUTE, 0)
                picked.set(Calendar.SECOND, 0)
                picked.set(Calendar.MILLISECOND, 0)
                viewModel.updateFormDate(picked.timeInMillis / 1000L)
                showDatePicker = false
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setOnCancelListener { showDatePicker = false }
        }.show()
    }

    LaunchedEffect(formState.isSaved) {
        if (formState.isSaved) {
            onTransferSaved()
        }
    }

    LaunchedEffect(formState.error) {
        val msg = formState.error
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message = msg)
            viewModel.clearError()
        }
    }

    if (showFromSheet) {
        AccountPickerBottomSheet(
            title = "Cuenta origen",
            accounts = formState.accounts,
            selectedAccountId = formState.fromAccountId,
            onDismiss = { showFromSheet = false },
            onSelected = {
                showFromSheet = false
                viewModel.updateFormFromAccount(it)
            }
        )
    }

    if (showToSheet) {
        AccountPickerBottomSheet(
            title = "Cuenta destino",
            accounts = formState.accounts,
            selectedAccountId = formState.toAccountId,
            onDismiss = { showToSheet = false },
            onSelected = {
                showToSheet = false
                viewModel.updateFormToAccount(it)
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CompactHeader(
                title = {
                    Text(
                        text = if (transferId != null) "Editar transferencia" else "Nueva transferencia",
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Desde (cuenta origen)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AccountSelectorRow(
                    account = fromAccount,
                    title = fromAccount?.name ?: "Seleccionar",
                    supporting = "Disponible: ${formState.fromAccountBalanceCents?.let { currencyFormat.format(it / 100.0) } ?: "-"}",
                    trailing = formState.fromAccountBalanceCents?.let { currencyFormat.format(it / 100.0) } ?: "-",
                    trailingColor = if ((formState.fromAccountBalanceCents ?: 0L) >= 0) Income else Expense,
                    onClick = { showFromSheet = true }
                )

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    FilledTonalButton(
                        onClick = {
                            swapFlip = !swapFlip
                            viewModel.swapAccounts()
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.SwapVert,
                            contentDescription = null,
                            modifier = Modifier.graphicsLayer(rotationZ = swapRotation)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Intercambiar")
                    }
                }

                Text(
                    text = "Hacia (cuenta destino)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AccountSelectorRow(
                    account = toAccount,
                    title = toAccount?.name ?: "Seleccionar",
                    supporting = "Recibe la transferencia",
                    trailing = formState.toAccountBalanceCents?.let { currencyFormat.format(it / 100.0) } ?: "-",
                    trailingColor = MaterialTheme.colorScheme.onSurface,
                    onClick = { showToSheet = true }
                )

                Text(
                    text = "Monto",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = formState.amountText,
                    onValueChange = { viewModel.updateFormAmount(it) },
                    isError = !formState.error.isNullOrBlank(),
                    leadingIcon = {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                        ) {
                            Text(
                                "$",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    placeholder = { Text("") },
                    textStyle = MaterialTheme.typography.headlineLarge.copy(textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    supportingText = {
                        Text(
                            text = formState.error ?: "Escribe el monto exacto a transferir",
                            color = if (formState.error.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                val quickAmounts = remember {
                    listOf(
                        "\$ 10.000" to "10000",
                        "\$ 50.000" to "50000",
                        "\$ 100.000" to "100000"
                    )
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(quickAmounts) { (label, value) ->
                        QuickAmountChip(label = label) { viewModel.updateFormAmount(value) }
                    }
                    item {
                        QuickAmountChip(label = "Máximo") {
                            val max = formState.fromAccountBalanceCents?.let { (it / 100.0).toString() } ?: ""
                            if (max.isNotBlank()) viewModel.updateFormAmount(max)
                        }
                    }
                }

                TransferPreviewCard(
                    amountCents = amountCents,
                    amountText = formState.amountText,
                    fromAccount = fromAccount,
                    toAccount = toAccount,
                    fromProjectedBalanceCents = fromAfterCents,
                    toProjectedBalanceCents = toAfterCents,
                    currencyFormat = currencyFormat
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                ) {
                    OutlinedTextField(
                        value = dateFormat.format(Date(formState.occurredAtEpochSec * 1000)),
                        onValueChange = {},
                        enabled = false,
                        readOnly = true,
                        label = { Text("Fecha") },
                        leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                        trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledContainerColor = MaterialTheme.colorScheme.surface,
                            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = formState.note,
                    onValueChange = { viewModel.updateFormNote(it) },
                    label = { Text("Nota (opcional)") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Transferencia segura. Solo entre tus cuentas.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "No afecta tus ingresos ni gastos.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Save Button
                Button(
                    onClick = { viewModel.saveTransfer() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !formState.isLoading
                ) {
                    if (formState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(if (transferId != null) "Actualizar" else "Transferir")
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountSelectorRow(
    account: AccountEntity?,
    title: String,
    supporting: String,
    trailing: String,
    trailingColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    val accent = remember(account?.colorHex, account?.type) { accountAccentColor(account) }
    val icon = remember(account?.iconKey, account?.type) { accountIconForKey(account?.iconKey, account?.type) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.16f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = accent.copy(alpha = 0.14f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (supporting.isNotBlank()) {
                    Text(
                        supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = trailingColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun QuickAmountChip(
    label: String,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        modifier = Modifier.heightIn(min = 34.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountPickerBottomSheet(
    title: String,
    accounts: List<com.myfinances.data.local.entity.AccountEntity>,
    selectedAccountId: String?,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit
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
