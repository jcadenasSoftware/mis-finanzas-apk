package com.jcadenas.xpendz.ui.screens.obligations

import android.app.DatePickerDialog
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jcadenas.xpendz.application.obligation.ObligationResolvedStatus
import com.jcadenas.xpendz.application.obligation.ResolvedObligationState
import com.jcadenas.xpendz.data.local.entity.AccountEntity
import com.jcadenas.xpendz.data.local.entity.CategoryEntity
import com.jcadenas.xpendz.data.local.entity.ObligationEntity
import com.jcadenas.xpendz.data.local.entity.ObligationSettlementEntity
import com.jcadenas.xpendz.ui.components.CompactHeader
import com.jcadenas.xpendz.ui.components.HamburgerMenu
import com.jcadenas.xpendz.ui.components.HamburgerMenuButton
import com.jcadenas.xpendz.ui.components.MoneyInputField
import com.jcadenas.xpendz.ui.components.MoneyInputFieldVariant
import com.jcadenas.xpendz.ui.components.MoneyInputFormatter
import com.jcadenas.xpendz.ui.theme.XpendzThemeTokens
import com.jcadenas.xpendz.ui.viewmodel.ObligationsViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val ReceivableColor = Color(0xFF16A34A)
private val PayableColor = Color(0xFF2563EB)
private val OverdueColor = Color(0xFFDC2626)
private val PartialColor = Color(0xFFD97706)
private val NeutralColor = Color(0xFF64748B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObligationsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCharts: () -> Unit,
    onNavigateToBudget: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToObligations: () -> Unit = {},
    onLogout: () -> Unit,
    viewModel: ObligationsViewModel = hiltViewModel()
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val typography = XpendzThemeTokens.typography

    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var showHamburgerMenu by remember { mutableStateOf(false) }

    var showObligationForm by remember { mutableStateOf(false) }
    var editingObligation by remember { mutableStateOf<ObligationEntity?>(null) }
    var showSettlementForm by remember { mutableStateOf(false) }
    var editingSettlement by remember { mutableStateOf<ObligationSettlementEntity?>(null) }
    var confirmCancelObligation by remember { mutableStateOf<ObligationEntity?>(null) }
    var confirmDeleteSettlement by remember { mutableStateOf<ObligationSettlementEntity?>(null) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(state.obligationSaved) {
        if (state.obligationSaved) {
            showObligationForm = false
            editingObligation = null
            viewModel.consumeObligationSaved()
            snackbarHostState.showSnackbar("Obligación guardada")
        }
    }
    LaunchedEffect(state.settlementSaved) {
        if (state.settlementSaved) {
            showSettlementForm = false
            editingSettlement = null
            viewModel.consumeSettlementSaved()
            snackbarHostState.showSnackbar("Abono guardado")
        }
    }
    LaunchedEffect(state.settlementDeleted) {
        if (state.settlementDeleted) {
            confirmDeleteSettlement = null
            viewModel.consumeSettlementDeleted()
            snackbarHostState.showSnackbar("Abono eliminado")
        }
    }
    LaunchedEffect(state.obligationCancelled) {
        if (state.obligationCancelled) {
            confirmCancelObligation = null
            viewModel.closeDetail()
            viewModel.consumeObligationCancelled()
            snackbarHostState.showSnackbar("Obligación cancelada")
        }
    }

    Scaffold(
        topBar = {
            CompactHeader(
                title = {
                    Text(
                        text = "Obligaciones",
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
                            onNavigateToObligations = onNavigateToObligations,
                            onLogout = onLogout,
                            currentScreen = "obligations"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingObligation = null
                    showObligationForm = true
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nueva obligación")
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = spacing.m)
        ) {
            ObligationsSummaryCard(
                receivableCents = state.receivablePendingCents,
                payableCents = state.payablePendingCents,
                overdueCents = state.overduePendingCents
            )

            Spacer(modifier = Modifier.height(spacing.m))

            ObligationsSegmentedTabs(
                selectedTab = state.selectedTab,
                onSelectTab = { viewModel.selectTab(it) }
            )

            Spacer(modifier = Modifier.height(spacing.s))

            val filtered = state.obligations.filter { it.type == state.selectedTab }
            val (active, cancelled) = splitObligationsByCancellation(filtered, state.resolvedStates)
            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator(color = colors.brand)
                    }
                }
                filtered.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Receipt,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(spacing.xs))
                            Text(
                                text = if (state.selectedTab == ObligationEntity.TYPE_RECEIVABLE) "No hay cuentas por cobrar" else "No hay cuentas por pagar",
                                style = typography.bodyMedium,
                                color = colors.onSurfaceVariant
                            )
                            Text(
                                text = "Toca + para registrar la primera",
                                style = typography.bodySmall,
                                color = colors.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
                else -> {
                    var cancelledExpanded by remember { mutableStateOf(false) }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(spacing.xs),
                        contentPadding = PaddingValues(bottom = spacing.xxxl * 2)
                    ) {
                        item(key = "header-active") {
                            Text(
                                text = "Activas",
                                style = typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = spacing.xxs)
                            )
                        }
                        items(active, key = { it.id }) { obligation ->
                            ObligationCard(
                                obligation = obligation,
                                resolved = state.resolvedStates[obligation.id],
                                onClick = { viewModel.openDetail(obligation.id) }
                            )
                        }
                        // Sección "Canceladas" — mismo patrón que Metas → Archivadas:
                        // contraída por defecto, expandible, detalle accesible.
                        if (cancelled.isNotEmpty()) {
                            item(key = "header-cancelled") {
                                Text(
                                    text = "Canceladas (${cancelled.size}) ${if (cancelledExpanded) "▾" else "▸"}",
                                    style = typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { cancelledExpanded = !cancelledExpanded }
                                        .padding(vertical = spacing.xs)
                                )
                            }
                            if (cancelledExpanded) {
                                items(cancelled, key = { "cancelled-${it.id}" }) { obligation ->
                                    ObligationCard(
                                        obligation = obligation,
                                        resolved = state.resolvedStates[obligation.id],
                                        onClick = { viewModel.openDetail(obligation.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Diálogos ───────────────────────────────────────────────────
    if (showObligationForm) {
        ObligationFormDialog(
            existing = editingObligation,
            categories = state.categories,
            isSaving = state.isSavingObligation,
            onDismiss = {
                if (!state.isSavingObligation) {
                    showObligationForm = false
                    editingObligation = null
                }
            },
            onSubmit = { type, title, counterparty, cents, issuedAt, dueAt, catId, ref, notes ->
                val existing = editingObligation
                if (existing != null) {
                    viewModel.updateObligationMetadata(
                        existing.id, type, title, counterparty, cents, issuedAt, dueAt, catId, ref, notes
                    )
                } else {
                    viewModel.createObligation(
                        type, title, counterparty, cents, issuedAt, dueAt, catId, ref, notes
                    )
                }
            }
        )
    }

    state.selectedObligation?.let { selected ->
        ObligationDetailDialog(
            obligation = selected,
            resolved = state.selectedResolvedState,
            settlements = state.selectedSettlements,
            transactions = state.selectedTransactions,
            categoryName = state.selectedCategoryName,
            accountNameFor = { accountId -> state.accounts.firstOrNull { it.id == accountId }?.name ?: accountId },
            isCancelling = state.isCancellingObligation,
            isDeletingSettlement = state.isDeletingSettlement,
            onDismiss = { viewModel.closeDetail() },
            onRegisterSettlement = {
                editingSettlement = null
                showSettlementForm = true
            },
            onEditObligation = {
                editingObligation = selected
                showObligationForm = true
            },
            onCancelObligation = { confirmCancelObligation = selected },
            onEditSettlement = { editingSettlement = it; showSettlementForm = true },
            onDeleteSettlement = { confirmDeleteSettlement = it }
        )
    }

    if (showSettlementForm) {
        state.selectedObligation?.let { selected ->
            SettlementFormDialog(
                obligation = selected,
                pendingCents = state.selectedResolvedState?.pendingAmountCents ?: 0L,
                existing = editingSettlement,
                accounts = state.accounts,
                balances = state.accountBalancesCents,
                categories = state.categories,
                existingCategoryId = editingSettlement?.let { state.selectedTransactions[it.id]?.categoryId },
                isSaving = state.isSavingSettlement,
                serviceError = state.settlementFormError,
                onDismiss = {
                    if (!state.isSavingSettlement) {
                        showSettlementForm = false
                        editingSettlement = null
                        viewModel.clearSettlementFormError()
                    }
                },
                onSubmit = { accountId, finCatId, cents, occurredAt, note ->
                    val existing = editingSettlement
                    if (existing != null) {
                        viewModel.updateSettlement(existing.id, accountId, finCatId, cents, occurredAt, note)
                    } else {
                        viewModel.registerSettlement(selected.id, accountId, finCatId, cents, occurredAt, note)
                    }
                }
            )
        }
    }

    confirmCancelObligation?.let { obligation ->
        AlertDialog(
            onDismissRequest = { if (!state.isCancellingObligation) confirmCancelObligation = null },
            containerColor = colors.surface,
            shape = RoundedCornerShape(shapes.extraLarge),
            title = { Text("Cancelar obligación") },
            text = {
                Text(
                    "Cancelar \"${obligation.title}\" NO registra un pago ni un cobro. " +
                        "No se creará ninguna transacción ni se modificará el saldo de tus cuentas. " +
                        "La obligación quedará marcada como CANCELADA."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.cancelObligation(obligation.id) },
                    enabled = !state.isCancellingObligation
                ) {
                    Text(if (state.isCancellingObligation) "Cancelando..." else "Cancelar obligación", color = OverdueColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancelObligation = null }, enabled = !state.isCancellingObligation) {
                    Text("Volver")
                }
            }
        )
    }

    confirmDeleteSettlement?.let { settlement ->
        AlertDialog(
            onDismissRequest = { if (!state.isDeletingSettlement) confirmDeleteSettlement = null },
            containerColor = colors.surface,
            shape = RoundedCornerShape(shapes.extraLarge),
            title = { Text("Eliminar abono") },
            text = {
                Text(
                    "Se eliminará el abono de ${formatMoney(settlement.amountCents)} y también se eliminará " +
                        "el movimiento financiero asociado. El saldo de la cuenta se revertirá. " +
                        "Esta acción no se puede deshacer."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteSettlement(settlement.id) },
                    enabled = !state.isDeletingSettlement
                ) {
                    Text(if (state.isDeletingSettlement) "Eliminando..." else "Eliminar", color = OverdueColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSettlement = null }, enabled = !state.isDeletingSettlement) {
                    Text("Volver")
                }
            }
        )
    }
}

// ══════════════════════════════════════════════════════════════════
//  Tarjeta resumen: Por cobrar / Por pagar / Vencido
// ══════════════════════════════════════════════════════════════════
// ── Tabs segmentados (mismo patrón que Préstamos) ─────────────────
@Composable
private fun ObligationsSegmentedTabs(
    selectedTab: String,
    onSelectTab: (String) -> Unit
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes

    val receivableSelected = selectedTab != ObligationEntity.TYPE_PAYABLE
    val receivableBg by animateColorAsState(
        if (receivableSelected) ReceivableColor else colors.surfaceVariant.copy(alpha = 0.55f),
        label = "oblReceivableBg"
    )
    val payableBg by animateColorAsState(
        if (!receivableSelected) PayableColor else colors.surfaceVariant.copy(alpha = 0.55f),
        label = "oblPayableBg"
    )
    val receivableFg = if (receivableSelected) Color.White else colors.onSurfaceVariant
    val payableFg = if (!receivableSelected) Color.White else colors.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(shapes.extraLarge))
            .background(colors.surfaceVariant.copy(alpha = 0.55f))
            .padding(spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs)
    ) {
        ObligationSegmentTab(
            label = "Por cobrar",
            icon = Icons.Default.TrendingUp,
            background = receivableBg,
            foreground = receivableFg,
            onClick = { onSelectTab(ObligationEntity.TYPE_RECEIVABLE) },
            modifier = Modifier.weight(1f)
        )
        ObligationSegmentTab(
            label = "Por pagar",
            icon = Icons.Default.TrendingDown,
            background = payableBg,
            foreground = payableFg,
            onClick = { onSelectTab(ObligationEntity.TYPE_PAYABLE) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ObligationSegmentTab(
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
private fun ObligationsSummaryCard(
    receivableCents: Long,
    payableCents: Long,
    overdueCents: Long
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val elevation = XpendzThemeTokens.elevation
    val typography = XpendzThemeTokens.typography

    val balanceCents = receivableCents - payableCents
    val balancePositive = balanceCents >= 0

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                text = "Balance de obligaciones",
                style = typography.titleMedium,
                color = colors.onSurface
            )
            Spacer(modifier = Modifier.height(spacing.m))
            Surface(
                color = colors.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(shapes.extraLarge),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.m, vertical = spacing.m),
                    verticalArrangement = Arrangement.spacedBy(spacing.m)
                ) {
                    ObligationSummaryRow("Por cobrar", formatMoney(receivableCents).trim(), ReceivableColor, labelBold = true)
                    ObligationSummaryRow("Por pagar", formatMoney(payableCents).trim(), PayableColor, labelBold = true)
                    ObligationSummaryRow("Vencido", formatMoney(overdueCents).trim(), OverdueColor)
                    ObligationSummaryRow(
                        label = "Balance",
                        value = (if (balancePositive) "+" else "-") + formatMoney(kotlin.math.abs(balanceCents)).trim(),
                        valueColor = if (balancePositive) ReceivableColor else PayableColor,
                        emphasize = true,
                        labelBold = true
                    )
                }
            }
        }
    }
}

@Composable
private fun ObligationSummaryRow(
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

// ══════════════════════════════════════════════════════════════════
//  Tarjeta de obligación
// ══════════════════════════════════════════════════════════════════
@Composable
private fun ObligationCard(
    obligation: ObligationEntity,
    resolved: ResolvedObligationState?,
    onClick: () -> Unit
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val elevation = XpendzThemeTokens.elevation
    val typography = XpendzThemeTokens.typography
    val receivable = obligation.type == ObligationEntity.TYPE_RECEIVABLE
    val accent = if (receivable) ReceivableColor else PayableColor
    val statusColor = when (resolved?.status) {
        ObligationResolvedStatus.VENCIDA -> OverdueColor
        ObligationResolvedStatus.PAGADA -> ReceivableColor
        ObligationResolvedStatus.PARCIAL -> PartialColor
        else -> accent
    }

    val original = obligation.originalAmountCents
    val settled = resolved?.totalSettledCents ?: 0L
    val pending = resolved?.pendingAmountCents ?: original
    val progress = if (original > 0) (settled.toFloat() / original.toFloat()).coerceIn(0f, 1f) else 0f
    val percent = (progress * 100).toInt()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                        text = obligation.counterpartyName,
                        style = typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(spacing.xxs))
                    Text(
                        text = obligation.title,
                        style = typography.bodySmall,
                        color = colors.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = formatMoney(pending),
                    style = typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }

            Spacer(modifier = Modifier.height(spacing.s + spacing.xxs / 2))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pendiente de ${formatMoney(original)}",
                    style = typography.bodySmall,
                    color = colors.onSurfaceVariant.copy(alpha = 0.85f)
                )
                StatusChip(resolved?.status)
            }

            Spacer(modifier = Modifier.height(spacing.s + spacing.xxs / 2))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = statusColor,
                trackColor = colors.onSurfaceVariant.copy(alpha = 0.12f)
            )

            Spacer(modifier = Modifier.height(spacing.xs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$percent% ${if (receivable) "cobrado" else "pagado"}",
                    style = typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Text(
                    text = obligation.dueAtEpochSec?.let { "Vence ${formatDate(it)}" } ?: "Sin vencimiento",
                    style = typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun StatusChip(status: ObligationResolvedStatus?) {
    val (text, color) = when (status) {
        ObligationResolvedStatus.PAGADA -> "PAGADA" to ReceivableColor
        ObligationResolvedStatus.VENCIDA -> "VENCIDA" to OverdueColor
        ObligationResolvedStatus.PARCIAL -> "PARCIAL" to PartialColor
        ObligationResolvedStatus.CANCELADA -> "CANCELADA" to NeutralColor
        else -> "PENDIENTE" to NeutralColor
    }
    Surface(
        color = color.copy(alpha = 0.13f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text = text,
            style = XpendzThemeTokens.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

// ══════════════════════════════════════════════════════════════════
//  Diálogo: Crear / Editar obligación (solo metadata — nunca Transaction)
// ══════════════════════════════════════════════════════════════════
@Composable
private fun ObligationFormDialog(
    existing: ObligationEntity?,
    categories: List<CategoryEntity>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, Long, Long, Long?, String?, String?, String?) -> Unit
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val context = LocalContext.current
    val editing = existing != null

    var type by remember { mutableStateOf(existing?.type ?: ObligationEntity.TYPE_RECEIVABLE) }
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var counterparty by remember { mutableStateOf(existing?.counterpartyName.orEmpty()) }
    var amountText by remember {
        mutableStateOf(existing?.originalAmountCents?.let { MoneyInputFormatter.formatFromCents(it) }.orEmpty())
    }
    var issuedAt by remember { mutableStateOf(existing?.issuedAtEpochSec ?: System.currentTimeMillis() / 1000) }
    var dueAt by remember { mutableStateOf(existing?.dueAtEpochSec) }
    var categoryId by remember { mutableStateOf(existing?.obligationCategoryId) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var reference by remember { mutableStateOf(existing?.reference.orEmpty()) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var formError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = colors.surface,
        shape = RoundedCornerShape(shapes.extraLarge),
        title = { Text(if (editing) "Editar obligación" else "Nueva obligación") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .imePadding()
            ) {
                Text(
                    text = if (editing) "Modifica la metadata de la obligación"
                    else "Una obligación no mueve dinero: registra una cuenta por cobrar o por pagar",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                formError?.let {
                    Spacer(modifier = Modifier.height(spacing.xs))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = colors.negative.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(shapes.medium)
                    ) {
                        Text(
                            text = it,
                            color = colors.negative,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(spacing.m)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(spacing.s))

                // Tipo (mismo segmented que la lista)
                ObligationsSegmentedTabs(
                    selectedTab = type,
                    onSelectTab = { type = it }
                )
                Spacer(modifier = Modifier.height(spacing.xs))

                OutlinedTextField(
                        shape = RoundedCornerShape(shapes.extraLarge),
                        colors = obligationFieldColors(),
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(spacing.xs))
                OutlinedTextField(
                        shape = RoundedCornerShape(shapes.extraLarge),
                        colors = obligationFieldColors(),
                    value = counterparty,
                    onValueChange = { counterparty = it },
                    label = { Text("Contraparte") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(spacing.xs))
                MoneyInputField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Monto original") },
                    variant = MoneyInputFieldVariant.OUTLINED,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(spacing.xs))

                // Fecha emisión
                val issuedCal = Calendar.getInstance().apply { timeInMillis = issuedAt * 1000 }
                OutlinedTextField(
                        shape = RoundedCornerShape(shapes.extraLarge),
                        colors = obligationFieldColors(),
                    value = formatDate(issuedAt),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Fecha de emisión") },
                    trailingIcon = {
                        IconButton(onClick = {
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    Calendar.getInstance().apply {
                                        set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0)
                                    }.let { issuedAt = it.timeInMillis / 1000 }
                                },
                                issuedCal.get(Calendar.YEAR),
                                issuedCal.get(Calendar.MONTH),
                                issuedCal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }) {
                            Icon(Icons.Default.DateRange, null, tint = colors.brand)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(spacing.xs))

                // Vencimiento opcional
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val dueCal = dueAt?.let { Calendar.getInstance().apply { timeInMillis = it * 1000 } }
                    OutlinedTextField(
                        shape = RoundedCornerShape(shapes.extraLarge),
                        colors = obligationFieldColors(),
                        value = dueAt?.let { formatDate(it) } ?: "Sin vencimiento",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Vencimiento (opcional)") },
                        trailingIcon = {
                            IconButton(onClick = {
                                val base = dueCal ?: Calendar.getInstance()
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        Calendar.getInstance().apply {
                                            set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0)
                                        }.let { dueAt = it.timeInMillis / 1000 }
                                    },
                                    base.get(Calendar.YEAR),
                                    base.get(Calendar.MONTH),
                                    base.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }) {
                                Icon(Icons.Default.DateRange, null, tint = colors.brand)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    if (dueAt != null) {
                        TextButton(onClick = { dueAt = null }) { Text("Quitar") }
                    }
                }
                Spacer(modifier = Modifier.height(spacing.xs))

                // Categoría de obligación opcional
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentSize(Alignment.TopStart)
                        .clickable { categoryExpanded = true }
                ) {
                    var anchorSize by remember { mutableStateOf(IntSize.Zero) }
                    val density = LocalDensity.current
                    val catName = categories.firstOrNull { it.id == categoryId }?.name ?: "Sin categoría"
                    OutlinedTextField(
                        shape = RoundedCornerShape(shapes.extraLarge),
                        colors = obligationFieldColors(),
                        value = catName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Categoría de obligación (opcional)") },
                        trailingIcon = {
                            IconButton(onClick = { categoryExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, null, tint = colors.onSurfaceVariant)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { anchorSize = it.size }
                    )
                    DropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false },
                        modifier = Modifier
                            .width(with(density) { anchorSize.width.toDp() })
                            .clip(RoundedCornerShape(shapes.extraLarge))
                            .background(colors.surface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sin categoría") },
                            onClick = { categoryId = null; categoryExpanded = false }
                        )
                        categories.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.name) },
                                onClick = { categoryId = c.id; categoryExpanded = false }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(spacing.xs))

                OutlinedTextField(
                        shape = RoundedCornerShape(shapes.extraLarge),
                        colors = obligationFieldColors(),
                    value = reference,
                    onValueChange = { reference = it },
                    label = { Text("Referencia (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(spacing.xs))
                OutlinedTextField(
                        shape = RoundedCornerShape(shapes.extraLarge),
                        colors = obligationFieldColors(),
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cents = MoneyInputFormatter.parseToCents(amountText)
                    formError = null
                    when {
                        title.isBlank() -> formError = "Ingresa un título"
                        counterparty.isBlank() -> formError = "Ingresa la contraparte"
                        cents == null || cents <= 0 -> formError = "El monto debe ser mayor a $0"
                        else -> onSubmit(
                            type,
                            title.trim(),
                            counterparty.trim(),
                            cents,
                            issuedAt,
                            dueAt,
                            categoryId,
                            reference.ifBlank { null },
                            notes.ifBlank { null }
                        )
                    }
                },
                enabled = !isSaving,
                shape = RoundedCornerShape(shapes.extraLarge),
                colors = ButtonDefaults.buttonColors(containerColor = colors.brand)
            ) {
                Text(if (isSaving) "Guardando..." else if (editing) "Guardar cambios" else "Crear obligación")
            }
        },
        dismissButton = {
            FilledTonalButton(
                onClick = onDismiss,
                enabled = !isSaving,
                shape = RoundedCornerShape(shapes.extraLarge)
            ) { Text("Cancelar") }
        }
    )
}

// ══════════════════════════════════════════════════════════════════
//  Diálogo: Detalle de obligación + historial de settlements
// ══════════════════════════════════════════════════════════════════
@Composable
private fun ObligationDetailDialog(
    obligation: ObligationEntity,
    resolved: ResolvedObligationState?,
    settlements: List<ObligationSettlementEntity>,
    transactions: Map<String, com.jcadenas.xpendz.data.local.entity.TransactionEntity>,
    categoryName: String?,
    accountNameFor: (String) -> String,
    isCancelling: Boolean,
    isDeletingSettlement: Boolean,
    onDismiss: () -> Unit,
    onRegisterSettlement: () -> Unit,
    onEditObligation: () -> Unit,
    onCancelObligation: () -> Unit,
    onEditSettlement: (ObligationSettlementEntity) -> Unit,
    onDeleteSettlement: (ObligationSettlementEntity) -> Unit
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val typography = XpendzThemeTokens.typography
    val receivable = obligation.type == ObligationEntity.TYPE_RECEIVABLE
    val canSettle = resolved?.status != ObligationResolvedStatus.CANCELADA &&
        resolved?.status != ObligationResolvedStatus.PAGADA

    val baseColor = if (receivable) ReceivableColor else PayableColor
    val progress = if (obligation.originalAmountCents > 0 && resolved != null) {
        (resolved.totalSettledCents.toFloat() / obligation.originalAmountCents.toFloat()).coerceIn(0f, 1f)
    } else 0f

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(XpendzThemeTokens.shapes.extraLarge),
        title = {
            Column {
                Text(obligation.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    obligation.counterpartyName,
                    style = typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(resolved?.status)
                    Spacer(modifier = Modifier.width(spacing.xs))
                    Text(
                        text = if (receivable) "Por cobrar" else "Por pagar",
                        style = typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = baseColor
                    )
                }
                Spacer(modifier = Modifier.height(spacing.s))

                // Bloque de progreso — mismo patrón que el resumen de Préstamos.
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = baseColor.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(XpendzThemeTokens.shapes.extraLarge)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(spacing.m),
                        verticalArrangement = Arrangement.spacedBy(spacing.s)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Progreso",
                                style = typography.labelMedium,
                                color = colors.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "${(progress * 100).toInt()}%",
                                style = typography.labelLarge,
                                color = baseColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = baseColor,
                            trackColor = baseColor.copy(alpha = 0.2f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Pendiente",
                                style = typography.labelSmall,
                                color = colors.onSurfaceVariant
                            )
                            Text(
                                formatMoney(resolved?.pendingAmountCents ?: obligation.originalAmountCents),
                                style = typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = baseColor
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (receivable) "Abonado" else "Pagado",
                                style = typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                            Text(
                                formatMoney(resolved?.totalSettledCents ?: 0L),
                                style = typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurface
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Total original",
                                style = typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                            Text(
                                formatMoney(obligation.originalAmountCents),
                                style = typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(spacing.s))

                InfoLine("Emitida", formatDate(obligation.issuedAtEpochSec))
                InfoLine("Vencimiento", obligation.dueAtEpochSec?.let { formatDate(it) } ?: "—")
                InfoLine("Categoría", categoryName ?: "—")
                obligation.reference?.takeIf { it.isNotBlank() }?.let { InfoLine("Referencia", it) }
                obligation.notes?.takeIf { it.isNotBlank() }?.let { InfoLine("Notas", it) }

                Spacer(modifier = Modifier.height(spacing.m))
                Text("Abonos registrados", style = typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(spacing.xs))

                if (settlements.isEmpty()) {
                    Text(
                        "Sin abonos todavía",
                        style = typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                } else {
                    settlements.forEach { s ->
                        SettlementRow(
                            settlement = s,
                            accountName = accountNameFor(s.accountId),
                            tx = transactions[s.id],
                            isDeleting = isDeletingSettlement,
                            onEdit = { onEditSettlement(s) },
                            onDelete = { onDeleteSettlement(s) }
                        )
                        Spacer(modifier = Modifier.height(spacing.xs))
                    }
                }

                Spacer(modifier = Modifier.height(spacing.s))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onRegisterSettlement,
                        enabled = canSettle,
                        shape = RoundedCornerShape(XpendzThemeTokens.shapes.extraLarge),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.brand)
                    ) {
                        Icon(Icons.Default.Payments, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (receivable) "Registrar cobro" else "Registrar pago")
                    }
                    IconButton(onClick = onEditObligation) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar obligación", tint = colors.brand)
                    }
                    IconButton(
                        onClick = onCancelObligation,
                        enabled = resolved?.status != ObligationResolvedStatus.CANCELADA && !isCancelling
                    ) {
                        Icon(Icons.Default.Block, contentDescription = "Cancelar obligación", tint = OverdueColor)
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(XpendzThemeTokens.shapes.extraLarge)
            ) { Text("Cerrar") }
        }
    )
}

@Composable
private fun SettlementRow(
    settlement: ObligationSettlementEntity,
    accountName: String,
    tx: com.jcadenas.xpendz.data.local.entity.TransactionEntity?,
    isDeleting: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val typography = XpendzThemeTokens.typography

    Surface(
        color = colors.onSurfaceVariant.copy(alpha = 0.06f),
        shape = RoundedCornerShape(shapes.medium),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.s, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    formatMoney(settlement.amountCents),
                    style = typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    formatDate(settlement.occurredAtEpochSec),
                    style = typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Receipt, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(10.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (tx != null) "Movimiento ${if (tx.kind == "EXPENSE") "de egreso" else "de ingreso"} · $accountName"
                        else "Movimiento enlazado · $accountName",
                        style = typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                settlement.note?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = typography.labelSmall, color = colors.onSurfaceVariant.copy(alpha = 0.8f), maxLines = 2)
                }
            }
            IconButton(onClick = onEdit, enabled = !isDeleting) {
                Icon(Icons.Default.Edit, contentDescription = "Editar abono", tint = colors.brand, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, enabled = !isDeleting) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar abono", tint = OverdueColor, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val typography = XpendzThemeTokens.typography
    val colors = XpendzThemeTokens.colors
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.width(IntrinsicSize.Max).weight(0.4f)
        )
        Text(value, style = typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.6f))
    }
}

// Campos de los diálogos del módulo — mismo estilo que Nueva transacción/Préstamos.
@Composable
private fun obligationFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = XpendzThemeTokens.colors.surfaceVariant.copy(alpha = 0.30f),
    unfocusedContainerColor = XpendzThemeTokens.colors.surfaceVariant.copy(alpha = 0.30f),
    disabledContainerColor = XpendzThemeTokens.colors.surfaceVariant.copy(alpha = 0.30f),
    focusedBorderColor = XpendzThemeTokens.colors.brand,
    unfocusedBorderColor = XpendzThemeTokens.colors.onSurfaceVariant.copy(alpha = 0.30f)
)

// ══════════════════════════════════════════════════════════════════
//  Filtro Categoría → Subcategoría para la Transaction del abono
//  (mismo concepto que Nueva transacción: raíces compatibles con el
//  kind del movimiento; subcategorías compatibles de la raíz elegida).
// ══════════════════════════════════════════════════════════════════
internal fun splitObligationsByCancellation(
    obligations: List<ObligationEntity>,
    resolvedStates: Map<String, ResolvedObligationState>
): Pair<List<ObligationEntity>, List<ObligationEntity>> {
    val active = mutableListOf<ObligationEntity>()
    val cancelled = mutableListOf<ObligationEntity>()
    for (obligation in obligations) {
        if (resolvedStates[obligation.id]?.status == ObligationResolvedStatus.CANCELADA) {
            cancelled.add(obligation)
        } else {
            active.add(obligation)
        }
    }
    return active to cancelled
}

internal fun settlementCompatibleRoots(
    categories: List<CategoryEntity>,
    wantedKind: String
): List<CategoryEntity> = categories.filter {
    it.parentId.isNullOrBlank()
        && (it.kind.equals(wantedKind, ignoreCase = true) || it.kind.equals("BOTH", ignoreCase = true))
        && !it.id.startsWith("system-")
}

internal fun settlementCompatibleSubcategories(
    categories: List<CategoryEntity>,
    rootId: String?,
    wantedKind: String
): List<CategoryEntity> {
    if (rootId.isNullOrBlank()) return emptyList()
    return categories.filter {
        it.parentId == rootId
            && (it.kind.equals(wantedKind, ignoreCase = true) || it.kind.equals("BOTH", ignoreCase = true))
            && !it.id.startsWith("system-")
    }
}

// ══════════════════════════════════════════════════════════════════
//  Diálogo: Registrar / Editar settlement (vía ObligationService)
// ══════════════════════════════════════════════════════════════════
@Composable
private fun SettlementFormDialog(
    obligation: ObligationEntity,
    pendingCents: Long,
    existing: ObligationSettlementEntity?,
    existingCategoryId: String?,
    accounts: List<AccountEntity>,
    balances: Map<String, Long>,
    categories: List<CategoryEntity>,
    isSaving: Boolean,
    serviceError: String?,
    onDismiss: () -> Unit,
    onSubmit: (String, String, Long, Long, String?) -> Unit
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val context = LocalContext.current
    val editing = existing != null
    val receivable = obligation.type == ObligationEntity.TYPE_RECEIVABLE
    val action = if (receivable) "cobro" else "pago"

    var accountId by remember { mutableStateOf(existing?.accountId ?: accounts.firstOrNull()?.id ?: "") }
    var accountExpanded by remember { mutableStateOf(false) }
    var amountText by remember {
        mutableStateOf(existing?.amountCents?.let { MoneyInputFormatter.formatFromCents(it) }.orEmpty())
    }
    // La categoría financiera pertenece a la Transaction, no a la obligación.
    // Mismo concepto que Nueva transacción: Categoría → Subcategoría.
    val wantedKind = if (receivable) "INCOME" else "EXPENSE"
    val rootCategories = settlementCompatibleRoots(categories, wantedKind)
    var rootCategoryId by remember { mutableStateOf("") }
    var finCategoryId by remember { mutableStateOf("") }
    var finCatInitialized by remember { mutableStateOf(false) }
    var rootCatExpanded by remember { mutableStateOf(false) }
    var subCatExpanded by remember { mutableStateOf(false) }
    var occurredAt by remember { mutableStateOf(existing?.occurredAtEpochSec ?: System.currentTimeMillis() / 1000) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var formError by remember { mutableStateOf<String?>(null) }

    if (!finCatInitialized) {
        finCatInitialized = true
        // Al editar, la categoría vive en la Transaction enlazada: se restaura
        // raíz (y subcategoría si la tx apuntaba a una hija).
        val saved = existingCategoryId?.let { id -> categories.firstOrNull { it.id == id } }
        if (saved != null) {
            val savedRootId = saved.parentId?.takeIf { it.isNotBlank() } ?: saved.id
            if (rootCategories.any { it.id == savedRootId }) {
                rootCategoryId = savedRootId
                finCategoryId = saved.id
            }
        }
        if (rootCategoryId.isBlank()) {
            rootCategoryId = rootCategories.firstOrNull()?.id ?: ""
            finCategoryId = if (settlementCompatibleSubcategories(categories, rootCategoryId, wantedKind).isEmpty()) {
                rootCategoryId
            } else {
                ""
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = colors.surface,
        shape = RoundedCornerShape(shapes.extraLarge),
        title = {
            Column {
                Text(if (editing) "Editar $action" else "Registrar $action")
                Text(
                    "${obligation.title} · ${obligation.counterpartyName}",
                    style = XpendzThemeTokens.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .imePadding()
            ) {
                (formError ?: serviceError)?.let {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = colors.negative.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(shapes.medium)
                    ) {
                        Text(
                            text = it,
                            color = colors.negative,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(spacing.m)
                        )
                    }
                    Spacer(modifier = Modifier.height(spacing.xs))
                }
                // Cuenta
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentSize(Alignment.TopStart)
                        .clickable { accountExpanded = true }
                ) {
                    var anchorSize by remember { mutableStateOf(IntSize.Zero) }
                    val density = LocalDensity.current
                    val acc = accounts.firstOrNull { it.id == accountId }
                    OutlinedTextField(
                        shape = RoundedCornerShape(shapes.extraLarge),
                        colors = obligationFieldColors(),
                        value = acc?.let { "${it.name} · ${it.currency}" } ?: "Selecciona cuenta",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Cuenta") },
                        supportingText = {
                            val bal = balances[accountId]
                            if (acc != null && bal != null) {
                                Text("Disponible: ${formatMoney(bal, acc.currency)}")
                            }
                        },
                        trailingIcon = {
                            IconButton(onClick = { accountExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, null, tint = colors.onSurfaceVariant)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { anchorSize = it.size }
                    )
                    DropdownMenu(
                        expanded = accountExpanded,
                        onDismissRequest = { accountExpanded = false },
                        modifier = Modifier
                            .width(with(density) { anchorSize.width.toDp() })
                            .clip(RoundedCornerShape(shapes.extraLarge))
                            .background(colors.surface)
                    ) {
                        if (accounts.isEmpty()) {
                            DropdownMenuItem(text = { Text("No hay cuentas registradas") }, onClick = { accountExpanded = false })
                        }
                        accounts.forEach { a ->
                            val bal = balances[a.id] ?: 0L
                            DropdownMenuItem(
                                text = {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(a.name, modifier = Modifier.weight(1f))
                                        Text(formatMoney(bal, a.currency))
                                    }
                                },
                                onClick = { accountId = a.id; accountExpanded = false }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(spacing.xs))

                MoneyInputField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Monto") },
                    supportingText = { Text("Pendiente: ${formatMoney(pendingCents)}") },
                    variant = MoneyInputFieldVariant.OUTLINED,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(spacing.xs))

                // Categoría financiera → Subcategoría (mismo concepto que Nueva transacción)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentSize(Alignment.TopStart)
                        .clickable { rootCatExpanded = true }
                ) {
                    var anchorSize by remember { mutableStateOf(IntSize.Zero) }
                    val density = LocalDensity.current
                    OutlinedTextField(
                        shape = RoundedCornerShape(shapes.extraLarge),
                        colors = obligationFieldColors(),
                        value = rootCategories.firstOrNull { it.id == rootCategoryId }?.name ?: "Selecciona categoría",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Categoría financiera") },
                        trailingIcon = {
                            IconButton(onClick = { rootCatExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, null, tint = colors.onSurfaceVariant)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { anchorSize = it.size }
                    )
                    DropdownMenu(
                        expanded = rootCatExpanded,
                        onDismissRequest = { rootCatExpanded = false },
                        modifier = Modifier
                            .width(with(density) { anchorSize.width.toDp() })
                            .clip(RoundedCornerShape(shapes.extraLarge))
                            .background(colors.surface)
                    ) {
                        if (rootCategories.isEmpty()) {
                            DropdownMenuItem(text = { Text("Sin categorías compatibles") }, onClick = { rootCatExpanded = false })
                        }
                        rootCategories.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.name) },
                                onClick = {
                                    rootCategoryId = c.id
                                    // Sin hijas compatibles → la raíz misma es la categoría.
                                    finCategoryId = if (settlementCompatibleSubcategories(categories, c.id, wantedKind).isEmpty()) {
                                        c.id
                                    } else {
                                        ""
                                    }
                                    rootCatExpanded = false
                                }
                            )
                        }
                    }
                }

                val subCategories = settlementCompatibleSubcategories(categories, rootCategoryId, wantedKind)
                if (subCategories.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(spacing.xs))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentSize(Alignment.TopStart)
                            .clickable { subCatExpanded = true }
                    ) {
                        var anchorSize by remember { mutableStateOf(IntSize.Zero) }
                        val density = LocalDensity.current
                        OutlinedTextField(
                        shape = RoundedCornerShape(shapes.extraLarge),
                        colors = obligationFieldColors(),
                            value = subCategories.firstOrNull { it.id == finCategoryId }?.name ?: "Selecciona subcategoría",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Subcategoría") },
                            trailingIcon = {
                                IconButton(onClick = { subCatExpanded = true }) {
                                    Icon(Icons.Default.ArrowDropDown, null, tint = colors.onSurfaceVariant)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { anchorSize = it.size }
                        )
                        DropdownMenu(
                            expanded = subCatExpanded,
                            onDismissRequest = { subCatExpanded = false },
                            modifier = Modifier
                                .width(with(density) { anchorSize.width.toDp() })
                                .clip(RoundedCornerShape(shapes.extraLarge))
                                .background(colors.surface)
                        ) {
                            subCategories.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c.name) },
                                    onClick = { finCategoryId = c.id; subCatExpanded = false }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(spacing.xs))

                // Fecha
                val dateCal = Calendar.getInstance().apply { timeInMillis = occurredAt * 1000 }
                OutlinedTextField(
                        shape = RoundedCornerShape(shapes.extraLarge),
                        colors = obligationFieldColors(),
                    value = formatDate(occurredAt),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Fecha") },
                    trailingIcon = {
                        IconButton(onClick = {
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    // El picker cambia solo el día: conserva la hora del
                                    // timestamp actual (hora real para hoy, hora original al editar).
                                    val cur = Calendar.getInstance().apply { timeInMillis = occurredAt * 1000 }
                                    Calendar.getInstance().apply {
                                        set(y, m, d,
                                            cur.get(Calendar.HOUR_OF_DAY),
                                            cur.get(Calendar.MINUTE),
                                            cur.get(Calendar.SECOND))
                                        set(Calendar.MILLISECOND, 0)
                                    }.let { occurredAt = it.timeInMillis / 1000 }
                                },
                                dateCal.get(Calendar.YEAR),
                                dateCal.get(Calendar.MONTH),
                                dateCal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }) {
                            Icon(Icons.Default.DateRange, null, tint = colors.brand)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(spacing.xs))

                OutlinedTextField(
                        shape = RoundedCornerShape(shapes.extraLarge),
                        colors = obligationFieldColors(),
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Nota (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cents = MoneyInputFormatter.parseToCents(amountText)
                    formError = null
                    when {
                        accountId.isBlank() -> formError = "Selecciona una cuenta"
                        cents == null || cents <= 0 -> formError = "El monto debe ser mayor a $0"
                        !editing && cents > pendingCents -> formError = "El monto excede el saldo pendiente"
                        finCategoryId.isBlank() -> formError =
                            if (settlementCompatibleSubcategories(categories, rootCategoryId, wantedKind).isNotEmpty())
                                "Selecciona la subcategoría"
                            else "Selecciona la categoría financiera"
                        else -> {
                            // Alta: si la fecha elegida es hoy, el movimiento lleva la
                            // fecha/hora real del registro. Edición: conserva el timestamp.
                            val effectiveOccurredAt =
                                if (!editing && isSameDayAsNow(occurredAt)) System.currentTimeMillis() / 1000
                                else occurredAt
                            onSubmit(accountId, finCategoryId, cents, effectiveOccurredAt, note.ifBlank { null })
                        }
                    }
                },
                enabled = !isSaving,
                shape = RoundedCornerShape(shapes.extraLarge),
                colors = ButtonDefaults.buttonColors(containerColor = colors.brand)
            ) {
                Text(if (isSaving) "Guardando..." else if (editing) "Guardar $action" else "Confirmar $action")
            }
        },
        dismissButton = {
            FilledTonalButton(
                onClick = onDismiss,
                enabled = !isSaving,
                shape = RoundedCornerShape(shapes.extraLarge)
            ) { Text("Cancelar") }
        }
    )
}

// ══════════════════════════════════════════════════════════════════
//  Utilidades
// ══════════════════════════════════════════════════════════════════
private fun formatMoney(amountCents: Long, currency: String = ""): String {
    val amount = BigDecimal(amountCents).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
    val nf = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }
    return if (currency.isBlank()) nf.format(amount) else "${nf.format(amount)} $currency"
}

private fun formatDate(epochSec: Long): String {
    return SimpleDateFormat("dd/MM/yyyy", Locale("es", "CO")).format(Date(epochSec * 1000))
}

private fun isSameDayAsNow(epochSec: Long): Boolean {
    val a = Calendar.getInstance().apply { timeInMillis = epochSec * 1000 }
    val b = Calendar.getInstance()
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

