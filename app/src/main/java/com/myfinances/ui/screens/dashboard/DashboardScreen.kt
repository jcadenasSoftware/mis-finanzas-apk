package com.jcadenas.xpendz.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.jcadenas.xpendz.R
import com.jcadenas.xpendz.diagnostics.AppIdentityLogger
import com.jcadenas.xpendz.ui.components.AddAccountDialog
import com.jcadenas.xpendz.ui.components.CompactHeader
import com.jcadenas.xpendz.ui.components.HamburgerMenu
import com.jcadenas.xpendz.ui.components.HamburgerMenuButton
import com.jcadenas.xpendz.ui.components.SyncSwipeRefresh
import com.jcadenas.xpendz.ui.theme.Income
import com.jcadenas.xpendz.ui.theme.Expense
import com.jcadenas.xpendz.ui.theme.XpendzThemeTokens
import com.jcadenas.xpendz.ui.screens.transactions.AddTransactionSheet
import com.jcadenas.xpendz.ui.viewmodel.DashboardViewModel
import com.jcadenas.xpendz.ui.viewmodel.DashboardBalancePoint
import com.jcadenas.xpendz.ui.viewmodel.DashboardMonthlyHistoryItem
import com.jcadenas.xpendz.ui.viewmodel.SyncViewModel
import java.text.NumberFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToTransactions: (String?) -> Unit,
    onNavigateToTransfers: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToCharts: () -> Unit,
    onNavigateToLoans: () -> Unit,
    onNavigateToBudget: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToReports: () -> Unit,
    onBottomBarVisibilityChange: (Boolean) -> Unit,
    onLogout: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val elevation = XpendzThemeTokens.elevation
    val typography = XpendzThemeTokens.typography
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("es", "CO")) }
    var showTotalBalance by rememberSaveable { mutableStateOf(false) }
    var showBalanceDetail by rememberSaveable { mutableStateOf(false) }
    var showMonthlyHistory by rememberSaveable { mutableStateOf(false) }
    var showLogoutConfirmation by rememberSaveable { mutableStateOf(false) }
    var showHamburgerMenu by remember { mutableStateOf(false) }
    val balancePeriodLabel = remember {
        val month = SimpleDateFormat("MMMM", Locale("es", "CO")).format(Date())
        month.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("es", "CO")) else it.toString() }
    }
    val financialSummaryLabel = remember {
        val monthYear = SimpleDateFormat("MMMM yyyy", Locale("es", "CO")).format(Date())
        "Resumen financiero $monthYear"
    }
    val greetingName = remember(state.userDisplayName, state.userEmail) {
        val displayName = state.userDisplayName.trim()
        if (displayName.isNotBlank()) {
            displayName.substringBefore(" ").trim()
        } else {
            state.userEmail
            .substringBefore("@")
            .substringBefore(".")
            .substringBefore("_")
            .substringBefore("-")
            .trim()
            .replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale("es", "CO")) else it.toString()
            }
            .ifBlank { "" }
        }
    }
    val realTrendIsPositive = state.monthlySummary.trendDeltaCents >= 0
    val realTrendIcon = if (realTrendIsPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown
    val realTrendText = if (realTrendIsPositive) "Tendencia al alza" else "Tendencia a la baja"
    val realTrendAccentColor = if (realTrendIsPositive) Income else Expense
    val balanceDetailText = if (realTrendIsPositive) {
        "Tus movimientos reales del mes muestran una evolución favorable frente al periodo anterior."
    } else {
        "Tus movimientos reales del mes están por debajo del periodo anterior. Revisa gastos y movimientos recientes."
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showAddTransactionSheet by rememberSaveable { mutableStateOf(false) }
    var addTransactionKind by rememberSaveable { mutableStateOf("EXPENSE") }
    var showTransactionSavedSnack by remember { mutableStateOf(false) }
    var addTransactionSessionId by rememberSaveable { mutableIntStateOf(0) }
    val addTransactionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp

    LaunchedEffect(showAddTransactionSheet) {
        onBottomBarVisibilityChange(!showAddTransactionSheet)
    }

    DisposableEffect(Unit) {
        onDispose {
            onBottomBarVisibilityChange(true)
        }
    }

    val syncViewModel: SyncViewModel = hiltViewModel()
    val isSyncing by syncViewModel.isSyncing.collectAsState()
    val syncStatus by syncViewModel.status.collectAsState()
    val syncError by syncViewModel.error.collectAsState()
    val syncProgress by syncViewModel.progress.collectAsState()
    val baseDataVersion by syncViewModel.baseDataVersion.collectAsState()
    val syncVersion by syncViewModel.syncVersion.collectAsState()
    var lastAutoSyncedUserEmail by rememberSaveable { mutableStateOf<String?>(null) }
    var previousIsSyncing by remember { mutableStateOf(false) }

    LaunchedEffect(state.userEmail) {
        val userEmail = state.userEmail
        if (userEmail.isNotBlank() && lastAutoSyncedUserEmail != userEmail) {
            lastAutoSyncedUserEmail = userEmail
            syncViewModel.syncAll()
        }
    }

    LaunchedEffect(isSyncing, syncVersion) {
        if (previousIsSyncing && !isSyncing) {
            viewModel.refreshBalances()
        }
        previousIsSyncing = isSyncing
    }

    LaunchedEffect(baseDataVersion) {
        if (baseDataVersion > 0) {
            viewModel.refreshBalances()
        }
    }

    if (showLogoutConfirmation) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmation = false },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirmation = false
                    // TEMP DIAGNOSTIC
                    AppIdentityLogger.logGoogleSignInBuilder(
                        source = "DashboardScreen.logout",
                        defaultWebClientId = context.getString(R.string.default_web_client_id),
                        requestEmail = true,
                        requestIdToken = true,
                        extraConfig = listOf(
                            "GoogleSignInOptions.DEFAULT_SIGN_IN",
                            "requestEmail()",
                            "requestIdToken(default_web_client_id)",
                            "signOut().addOnCompleteListener { onLogout }"
                        )
                    )
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(context.getString(R.string.default_web_client_id))
                        .requestEmail()
                        .build()
                    val googleSignInClient = GoogleSignIn.getClient(context, gso)
                    googleSignInClient.signOut().addOnCompleteListener {
                        onLogout()
                    }
                }) {
                    Text(
                        text = "Cerrar sesión",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmation = false }) {
                    Text(
                        text = "Cancelar",
                        color = colors.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            icon = {
                Surface(
                    shape = RoundedCornerShape(shapes.extraLarge),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(spacing.s - spacing.xxs / 2)
                    )
                }
            },
            title = {
                Text(
                    text = "¿Seguro que deseas cerrar sesión?",
                    style = typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Volverás a la pantalla de acceso y podrás iniciar sesión nuevamente cuando quieras.",
                    style = typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            },
            containerColor = colors.surface,
            tonalElevation = elevation.level4,
            shape = RoundedCornerShape(shapes.extraLarge)
        )
    }

    Scaffold(
        topBar = {
            CompactHeader(
                title = {
                    Column {
                        Text(
                            text = if (greetingName.isNotBlank()) "Hola, $greetingName 👋" else "Hola 👋",
                            style = typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        Text(
                            text = financialSummaryLabel,
                            style = typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.brand.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = spacing.xxs / 2)
                        )
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
                            onLogout = { showLogoutConfirmation = true },
                            currentScreen = "dashboard"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        SyncSwipeRefresh(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = spacing.xs, bottom = spacing.l),
                verticalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                if (isSyncing) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.m, vertical = spacing.xs),
                            color = colors.surface,
                            shape = RoundedCornerShape(shapes.extraLarge),
                            tonalElevation = elevation.level1 + elevation.level1,
                            shadowElevation = elevation.level1 + elevation.level1
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = spacing.m, vertical = spacing.s + spacing.xxs / 2)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(spacing.s)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (syncProgress.isCancelling) "Cancelando sincronización" else "Sincronizando datos",
                                            style = typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(spacing.xxs / 2))
                                        Text(
                                            text = syncProgress.message ?: syncStatus.orEmpty(),
                                            style = typography.bodySmall,
                                            color = colors.onSurfaceVariant
                                        )
                                    }

                                    TextButton(
                                        onClick = { syncViewModel.cancelSync() },
                                        enabled = !syncProgress.isCancelling
                                    ) {
                                        Text(if (syncProgress.isCancelling) "Cancelando..." else "Cancelar")
                                    }
                                }

                                Spacer(modifier = Modifier.height(spacing.s - spacing.xxs / 2))

                                LinearProgressIndicator(
                                    progress = { syncProgress.progress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(spacing.s - spacing.xxs - spacing.xxs / 2))

                                Text(
                                    text = if (syncProgress.totalSteps > 0) {
                                        "Paso ${syncProgress.currentStep.coerceAtMost(syncProgress.totalSteps)} de ${syncProgress.totalSteps}"
                                    } else {
                                        "Preparando sincronización"
                                    },
                                    style = typography.labelSmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    BalanceSummaryCard(
                        balancePeriodLabel = balancePeriodLabel,
                        showBalanceDetail = showBalanceDetail,
                        onToggleDetail = { showBalanceDetail = !showBalanceDetail },
                        showTotalBalance = showTotalBalance,
                        onToggleBalanceVisibility = { showTotalBalance = !showTotalBalance },
                        totalBalanceFormatted = if (showTotalBalance) currencyFormat.format(state.totalBalanceCents / 100.0) else "••••••",
                        realTrendIcon = realTrendIcon,
                        realTrendAccentColor = realTrendAccentColor,
                        realTrendText = realTrendText,
                        realTrendIsPositive = realTrendIsPositive,
                        monthlyPoints = state.monthlySummary.points,
                        balanceDetailText = balanceDetailText,
                        trendFormatted = currencyFormat.format(state.monthlySummary.trendDeltaCents / 100.0),
                        trendPositive = state.monthlySummary.trendDeltaCents >= 0
                    )
                }

                item {
                    MonthlyOverviewCard(
                        monthLabel = balancePeriodLabel,
                        incomeFormatted = currencyFormat.format(state.monthlySummary.incomeCents / 100.0),
                        expenseFormatted = currencyFormat.format(state.monthlySummary.expenseCents / 100.0),
                        balanceFormatted = currencyFormat.format(state.monthlySummary.balanceCents / 100.0),
                        balancePositive = state.monthlySummary.balanceCents >= 0,
                        previousMonths = state.monthlySummary.previousMonths,
                        showHistory = showMonthlyHistory,
                        onToggleHistory = { showMonthlyHistory = !showMonthlyHistory },
                        totalIncomeFormatted = currencyFormat.format(state.monthlySummary.periodTotalIncomeCents / 100.0),
                        totalExpenseFormatted = currencyFormat.format(state.monthlySummary.periodTotalExpenseCents / 100.0),
                        totalBalanceFormatted = currencyFormat.format(state.monthlySummary.periodTotalBalanceCents / 100.0),
                        totalBalancePositive = state.monthlySummary.periodTotalBalanceCents >= 0
                    )
                }

                item {
                    Text(
                        text = "Acciones principales",
                        style = typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = spacing.m)
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .padding(horizontal = spacing.m),
                        horizontalArrangement = Arrangement.spacedBy(spacing.s)
                    ) {
                        ActionButton(
                            icon = Icons.Default.RemoveCircle,
                            label = "Gastos",
                            supportingText = "",
                            onClick = {
                                val missing = buildList {
                                    if (!state.hasAccounts) add("una cuenta")
                                    if (!state.hasRootCategories) add("una categoría")
                                    if (!state.hasSubCategories) add("una subcategoría")
                                }

                                if (missing.isNotEmpty()) {
                                    val message = "Para agregar un gasto primero crea ${missing.joinToString(", ")}."
                                    val actionLabel = if (!state.hasAccounts) "Crear cuenta" else "Categorías"
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = message,
                                            actionLabel = actionLabel
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            if (!state.hasAccounts) {
                                                viewModel.showAddAccountDialog()
                                            } else {
                                                onNavigateToCategories()
                                            }
                                        }
                                    }
                                } else {
                                    addTransactionKind = "EXPENSE"
                                    addTransactionSessionId += 1
                                    showAddTransactionSheet = true
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            emphasis = ActionButtonEmphasis.Primary,
                            accentColor = Color(0xFFFF8A3D)
                        )
                        ActionButton(
                            icon = Icons.Default.AddCircle,
                            label = "Ingresos",
                            supportingText = "",
                            onClick = {
                                val missing = buildList {
                                    if (!state.hasAccounts) add("una cuenta")
                                    if (!state.hasRootCategories) add("una categoría")
                                    if (!state.hasSubCategories) add("una subcategoría")
                                }

                                if (missing.isNotEmpty()) {
                                    val message = "Para agregar un ingreso primero crea ${missing.joinToString(", ")}."
                                    val actionLabel = if (!state.hasAccounts) "Crear cuenta" else "Categorías"
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = message,
                                            actionLabel = actionLabel
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            if (!state.hasAccounts) {
                                                viewModel.showAddAccountDialog()
                                            } else {
                                                onNavigateToCategories()
                                            }
                                        }
                                    }
                                } else {
                                    addTransactionKind = "INCOME"
                                    addTransactionSessionId += 1
                                    showAddTransactionSheet = true
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            emphasis = ActionButtonEmphasis.Primary,
                            accentColor = Income
                        )
                        ActionButton(
                            icon = Icons.Default.SwapHoriz,
                            label = "Transferir",
                            supportingText = "",
                            onClick = {
                                if (!state.hasAccounts || !state.hasTwoAccounts) {
                                    val message = if (!state.hasAccounts) {
                                        "Para hacer una transferencia primero crea una cuenta."
                                    } else {
                                        "Para hacer una transferencia necesitas al menos 2 cuentas."
                                    }
                                    val actionLabel = "Crear cuenta"
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = message,
                                            actionLabel = actionLabel
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.showAddAccountDialog()
                                        }
                                    }
                                } else {
                                    onNavigateToTransfers()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            emphasis = ActionButtonEmphasis.Primary,
                            accentColor = Color(0xFF3D7BFF)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.m, vertical = spacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Tus cuentas",
                                style = typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Ordenadas por saldo (de mayor a menor)",
                                style = typography.labelSmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }

                if (state.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = spacing.xl),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (state.accounts.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(spacing.xxl),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    modifier = Modifier.size(spacing.xxxl + spacing.m),
                                    tint = colors.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(spacing.m))
                                Text(
                                    "No tienes cuentas",
                                    style = typography.bodyLarge,
                                    color = colors.onSurfaceVariant
                                )
                                Text(
                                    "Agrega tu primera cuenta para comenzar",
                                    style = typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    item {
                        AccountsScrollPanel(
                            accounts = state.accounts,
                            onOpenAccount = { accountId -> onNavigateToTransactions(accountId) },
                            onAddAccount = { viewModel.showAddAccountDialog() },
                            onRename = { accountId, name, type, iconKey, colorHex ->
                                viewModel.updateAccountDetails(accountId, name, type, iconKey, colorHex)
                            },
                            onDelete = { accountId ->
                                viewModel.deleteAccount(accountId)
                            },
                            modifier = Modifier.padding(horizontal = spacing.m)
                        )
                    }
                }
            }
        }

        // Add Account Dialog
        if (state.showAddAccountDialog) {
            AddAccountDialog(
                onDismiss = { viewModel.hideAddAccountDialog() },
                onConfirm = { name, type, currency, iconKey, colorHex ->
                    viewModel.createAccount(name, type, currency, iconKey, colorHex)
                }
            )
        }

        state.error?.let { error ->
            LaunchedEffect(error) {
                snackbarHostState.showSnackbar(message = error)
                viewModel.clearError()
            }
        }

        if (showTransactionSavedSnack) {
            LaunchedEffect(Unit) {
                snackbarHostState.showSnackbar(message = "Gasto/ingreso registrado")
                showTransactionSavedSnack = false
            }
        }

        syncError?.let { error ->
            LaunchedEffect(error) {
                snackbarHostState.showSnackbar(message = error)
                syncViewModel.clearError()
            }
        }

        if (showAddTransactionSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAddTransactionSheet = false },
                sheetState = addTransactionSheetState,
                containerColor = colors.surface,
                contentColor = colors.onSurface,
                shape = RoundedCornerShape(shapes.extraLarge),
                tonalElevation = elevation.level0,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = screenHeightDp * 0.90f)
                ) {
                    AddTransactionSheet(
                        sessionId = addTransactionSessionId,
                        initialKind = addTransactionKind,
                        onDismiss = { showAddTransactionSheet = false },
                        onTransactionSaved = {
                            showAddTransactionSheet = false
                            showTransactionSavedSnack = true
                            viewModel.refreshBalances()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountsScrollPanel(
    accounts: List<com.jcadenas.xpendz.ui.viewmodel.AccountWithBalance>,
    onOpenAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onRename: (String, String, String, String?, String?) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val elevation = XpendzThemeTokens.elevation
    val typography = XpendzThemeTokens.typography
    val sortedAccounts = remember(accounts) {
        accounts.sortedByDescending { it.balanceCents }
    }
    val totalBalanceCents = remember(sortedAccounts) {
        sortedAccounts.sumOf { it.balanceCents.coerceAtLeast(0L) }
    }
    val top = sortedAccounts.firstOrNull()
    val topPercentText = remember(top, totalBalanceCents) {
        if (top == null || totalBalanceCents <= 0L) {
            "0.0"
        } else {
            String.format(
                Locale.US,
                "%.1f",
                (top.balanceCents.coerceAtLeast(0L) * 100.0) / totalBalanceCents
            )
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.surface,
        shape = RoundedCornerShape(shapes.extraLarge),
        tonalElevation = elevation.level0,
        shadowElevation = elevation.level3
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.s, vertical = spacing.s - spacing.xxs / 2),
            verticalArrangement = Arrangement.spacedBy(spacing.s - spacing.xxs / 2)
        ) {
            if (top != null && totalBalanceCents > 0L) {
                Surface(
                    color = colors.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(shapes.extraLarge),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.s, vertical = spacing.s - spacing.xxs / 2),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.s - spacing.xxs / 2)
                    ) {
                        Surface(
                            modifier = Modifier.size(spacing.xl + spacing.xxs),
                            shape = RoundedCornerShape(shapes.large),
                            color = Color(0xFFE8F0FF)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = Color(0xFF2463EB),
                                    modifier = Modifier.size(spacing.m)
                                )
                            }
                        }
                        Text(
                            text = "La mayor parte de tu dinero está en ${top.account.name} (${topPercentText}%)",
                            style = typography.bodySmall,
                            color = colors.onSurface
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = spacing.xxxl * 8 + spacing.xxl + spacing.xxs),
                contentPadding = PaddingValues(horizontal = elevation.level0, vertical = elevation.level0),
                verticalArrangement = Arrangement.spacedBy(spacing.s - spacing.xxs / 2)
            ) {
                items(sortedAccounts) { accountWithBalance ->
                    val isTop = accountWithBalance.account.id == top?.account?.id
                    RankedAccountCard(
                        accountWithBalance = accountWithBalance,
                        totalBalanceCents = totalBalanceCents,
                        isTop = isTop,
                        onOpen = { onOpenAccount(accountWithBalance.account.id) },
                        onRename = { name, type, iconKey, colorHex ->
                            onRename(accountWithBalance.account.id, name, type, iconKey, colorHex)
                        },
                        onDelete = { onDelete(accountWithBalance.account.id) }
                    )
                }

                item {
                    OutlinedButton(
                        onClick = onAddAccount,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(spacing.xxxl + spacing.xxs),
                        shape = RoundedCornerShape(shapes.extraLarge),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = colors.surface)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(spacing.xs))
                        Text("Nueva cuenta", style = typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private fun accountIconForKey(
    iconKey: String?,
    accountType: String
): androidx.compose.ui.graphics.vector.ImageVector {
    return when (iconKey?.lowercase()) {
        "bank" -> Icons.Default.AccountBalance
        "wallet" -> Icons.Default.AccountBalanceWallet
        "cash" -> Icons.Default.Money
        "card" -> Icons.Default.CreditCard
        "savings" -> Icons.Default.Savings
        "digital" -> Icons.Default.PhoneAndroid
        "mobile" -> Icons.Default.Smartphone
        "store" -> Icons.Default.Store
        "investment" -> Icons.Default.TrendingUp
        "vault" -> Icons.Default.Lock
        else -> when (accountType) {
            "CASH" -> Icons.Default.Money
            "CREDIT" -> Icons.Default.CreditCard
            "SAVINGS" -> Icons.Default.Savings
            "VIRTUAL_WALLET" -> Icons.Default.AccountBalanceWallet
            "DIGITAL_ACCOUNT" -> Icons.Default.PhoneAndroid
            else -> Icons.Default.AccountBalance
        }
    }
}

private fun defaultIconKeyForType(accountType: String): String {
    return when (accountType) {
        "BANK" -> "bank"
        "CASH" -> "cash"
        "SAVINGS" -> "savings"
        "VIRTUAL_WALLET" -> "wallet"
        "DIGITAL_ACCOUNT" -> "digital"
        else -> "bank"
    }
}

private fun accountTypeLabel(raw: String?): String {
    val t = raw?.trim()?.uppercase().orEmpty()
    return when (t) {
        "BANK" -> "Banco"
        "CASH" -> "Efectivo"
        "SAVINGS" -> "Ahorro"
        "VIRTUAL_WALLET" -> "Billetera virtual"
        "DIGITAL_ACCOUNT" -> "Cuenta digital"
        "CREDIT" -> "Banco"
        "INVESTMENT" -> "Ahorro"
        "OTHER" -> "Banco"
        "CHECKING" -> "Banco"
        else -> if (t.isBlank()) "Cuenta" else t
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RankedAccountCard(
    accountWithBalance: com.jcadenas.xpendz.ui.viewmodel.AccountWithBalance,
    totalBalanceCents: Long,
    isTop: Boolean,
    onOpen: () -> Unit,
    onRename: (String, String, String?, String?) -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val elevation = XpendzThemeTokens.elevation
    val typography = XpendzThemeTokens.typography
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("es", "CO")) }
    val safeTotal = totalBalanceCents.coerceAtLeast(0L)
    val safeBalance = accountWithBalance.balanceCents.coerceAtLeast(0L)
    val pct = remember(safeBalance, safeTotal) {
        if (safeTotal <= 0L) 0f else (safeBalance.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)
    }
    val pctText = remember(pct) { String.format(Locale.US, "%.1f", pct * 100f) }

    val accent = Color(0xFF2463EB)
    val accountAccent = remember(accountWithBalance.account.colorHex) {
        accountWithBalance.account.colorHex?.let { hex ->
            runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
        } ?: accent
    }
    val cardElevation = if (isTop) elevation.level4 else elevation.level2 + elevation.level1
    val verticalPadding = if (isTop) spacing.s + spacing.xxs / 2 else spacing.s - spacing.xxs / 2

    val mainIcon = remember(accountWithBalance.account.iconKey, accountWithBalance.account.type) {
        accountIconForKey(accountWithBalance.account.iconKey, accountWithBalance.account.type)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
        shape = RoundedCornerShape(shapes.extraLarge),
        border = CardDefaults.outlinedCardBorder().copy(
            width = elevation.level1,
            brush = androidx.compose.ui.graphics.SolidColor(accountAccent.copy(alpha = if (isTop) 0.14f else 0.08f))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.s + spacing.xxs / 2, vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.s)
            ) {
                Surface(
                    modifier = Modifier.size(if (isTop) spacing.xxxl + spacing.xxs else spacing.xxxl - spacing.xxs / 2),
                    shape = RoundedCornerShape(shapes.extraLarge),
                    color = accountAccent.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = mainIcon,
                            contentDescription = null,
                            tint = accountAccent,
                            modifier = Modifier.size(if (isTop) spacing.xl + spacing.xxs / 2 else spacing.l + spacing.xxs / 2)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs / 2)) {
                    Text(
                        text = accountWithBalance.account.name,
                        style = typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2
                    )
                    Text(
                        text = "${accountTypeLabel(accountWithBalance.account.type)}  •  ${accountWithBalance.account.currency}",
                        style = typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currencyFormat.format(accountWithBalance.balanceCents / 100.0),
                        style = typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (accountWithBalance.balanceCents >= 0) Income else Expense
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(spacing.xxl + spacing.xxs / 2)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = colors.surface,
                        tonalElevation = elevation.level0
                    ) {
                        DropdownMenuItem(
                            text = { Text("Editar") },
                            onClick = {
                                showMenu = false
                                showRenameDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Eliminar") },
                            onClick = {
                                showMenu = false
                                showDeleteDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.s - spacing.xxs / 2)
            ) {
                LinearProgressIndicator(
                    progress = { pct },
                    modifier = Modifier
                        .weight(1f)
                        .height(spacing.xs),
                    color = accountAccent,
                    trackColor = colors.surfaceVariant
                )
                Text(
                    text = "${pctText}%",
                    style = typography.labelMedium,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }

    if (showRenameDialog) {
        var newName by remember { mutableStateOf(accountWithBalance.account.name) }
        var selectedType by remember { mutableStateOf(accountWithBalance.account.type) }
        var selectedIconKey by remember { mutableStateOf(accountWithBalance.account.iconKey ?: defaultIconKeyForType(accountWithBalance.account.type)) }
        var selectedColorHex by remember { mutableStateOf(accountWithBalance.account.colorHex ?: "") }
        var typeExpanded by remember { mutableStateOf(false) }

        val accountTypes = listOf(
            "BANK" to "Banco",
            "CASH" to "Efectivo",
            "SAVINGS" to "Ahorro",
            "VIRTUAL_WALLET" to "Billetera virtual",
            "DIGITAL_ACCOUNT" to "Cuenta digital"
        )
        val colorOptions = listOf(
            "#8A05BE" to "Nu",
            "#FF6B6B" to "Rojo claro",
            "#D32F2F" to "Rojo oscuro",
            "#2463EB" to "Azul",
            "#10B981" to "Verde",
            "#0EA5E9" to "Celeste",
            "#14B8A6" to "Turquesa",
            "#F59E0B" to "Ámbar",
            "#F97316" to "Naranja",
            "#EC4899" to "Rosa",
            "#6366F1" to "Índigo",
            "#111827" to "Negro",
            "#6B7280" to "Gris"
        )

        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Editar cuenta") },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing.l)
                ) {
                    Text(
                        text = "Personaliza la información de esta cuenta",
                        style = typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )

                    // Vista previa de cuenta
                    val previewColor = remember(selectedColorHex) {
                        if (selectedColorHex.isNotBlank()) {
                            runCatching { Color(android.graphics.Color.parseColor(selectedColorHex)) }.getOrNull() ?: accent
                        } else {
                            accent
                        }
                    }
                    val previewIcon = remember(selectedIconKey, selectedType) {
                        accountIconForKey(selectedIconKey, selectedType)
                    }
                    val previewTypeLabel = remember(selectedType) {
                        accountTypeLabel(selectedType)
                    }

                    // Animaciones sutiles
                    val animatedPreviewColor by animateColorAsState(
                        targetValue = previewColor,
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                        label = "previewColor"
                    )
                    val animatedBorderColor by animateColorAsState(
                        targetValue = animatedPreviewColor.copy(alpha = 0.14f),
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                        label = "borderColor"
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        elevation = CardDefaults.cardElevation(defaultElevation = elevation.level1 + elevation.level1),
                        shape = RoundedCornerShape(shapes.extraLarge),
                        border = CardDefaults.outlinedCardBorder().copy(
                            width = elevation.level1,
                            brush = androidx.compose.ui.graphics.SolidColor(animatedBorderColor)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.m, vertical = spacing.s + spacing.xxs / 2),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.s)
                        ) {
                            Surface(
                                modifier = Modifier.size(spacing.xxxl - spacing.xxs / 2),
                                shape = RoundedCornerShape(shapes.extraLarge),
                                color = animatedPreviewColor.copy(alpha = 0.12f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Crossfade(
                                        targetState = previewIcon,
                                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                                        label = "iconCrossfade"
                                    ) { icon ->
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = animatedPreviewColor,
                                            modifier = Modifier.size(spacing.l + spacing.xxs / 2)
                                        )
                                    }
                                }
                            }

                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs / 2)) {
                                Text(
                                    text = newName.ifBlank { accountWithBalance.account.name },
                                    style = typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                AnimatedContent(
                                    targetState = previewTypeLabel,
                                    transitionSpec = {
                                        fadeIn(animationSpec = tween(150)) togetherWith
                                            fadeOut(animationSpec = tween(150)) using
                                            SizeTransform(clip = false)
                                    },
                                    label = "typeLabel"
                                ) { label ->
                                    Text(
                                        text = "$label  •  ${accountWithBalance.account.currency}",
                                        style = typography.labelSmall,
                                        color = colors.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nombre de la cuenta") },
                        placeholder = { Text("Ej: Mi cuenta principal") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    ExposedDropdownMenuBox(
                        expanded = typeExpanded,
                        onExpandedChange = { typeExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = accountTypes.find { it.first == selectedType }?.second ?: "",
                            onValueChange = {} ,
                            readOnly = true,
                            label = { Text("Tipo de cuenta") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = typeExpanded,
                            onDismissRequest = { typeExpanded = false }
                        ) {
                            accountTypes.forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        selectedType = value
                                        selectedIconKey = defaultIconKeyForType(value)
                                        typeExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Text(text = "Icono", style = typography.labelLarge)
                    Surface(
                        modifier = Modifier.size(spacing.xxxl + spacing.xs),
                        shape = RoundedCornerShape(shapes.extraLarge),
                        color = accountAccent.copy(alpha = 0.12f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = accountIconForKey(selectedIconKey, selectedType),
                                contentDescription = null,
                                tint = accountAccent,
                                modifier = Modifier.size(spacing.xl + spacing.xxs / 2)
                            )
                        }
                    }

                    Text(text = "Color", style = typography.labelLarge)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.s)
                    ) {
                        items(colorOptions) { (hex, _) ->
                            val c = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull() ?: accent
                            val selected = selectedColorHex.equals(hex, ignoreCase = true)

                            val animatedScale by animateFloatAsState(
                                targetValue = if (selected) 1.05f else 1f,
                                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                                label = "colorScale"
                            )

                            val animatedBorderWidth by animateDpAsState(
                                targetValue = if (selected) elevation.level2 else elevation.level1,
                                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                                label = "borderWidth"
                            )

                            val animatedBorderColor by animateColorAsState(
                                targetValue = if (selected) colors.brand else colors.onSurface.copy(alpha = 0.12f),
                                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                                label = "borderColor"
                            )

                            Surface(
                                modifier = Modifier
                                    .size(spacing.xxl + spacing.xs)
                                    .graphicsLayer {
                                        scaleX = animatedScale
                                        scaleY = animatedScale
                                    }
                                    .clip(RoundedCornerShape(shapes.extraLarge))
                                    .border(
                                        width = animatedBorderWidth,
                                        color = animatedBorderColor,
                                        shape = RoundedCornerShape(shapes.extraLarge)
                                    )
                                    .clickable { selectedColorHex = hex },
                                color = c,
                                tonalElevation = if (selected) elevation.level1 else elevation.level0
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = selected,
                                        enter = fadeIn(animationSpec = tween(150)) + scaleIn(
                                            animationSpec = tween(200, easing = FastOutSlowInEasing),
                                            initialScale = 0.8f
                                        ),
                                        exit = fadeOut(animationSpec = tween(150)) + scaleOut(
                                            animationSpec = tween(150, easing = FastOutSlowInEasing),
                                            targetScale = 0.8f
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = colors.onBrand,
                                            modifier = Modifier.size(spacing.l)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            val iconKey = selectedIconKey.ifBlank { null }
                            val colorHex = selectedColorHex.ifBlank { null }
                            onRename(newName, selectedType, iconKey, colorHex)
                            showRenameDialog = false
                        }
                    },
                    enabled = newName.isNotBlank()
                ) { Text("Guardar cambios") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancelar") }
            },
            shape = RoundedCornerShape(shapes.extraLarge)
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar cuenta") },
            text = { Text("¿Estás seguro de que deseas eliminar la cuenta \"${accountWithBalance.account.name}\"? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun MonthlyOverviewCard(
    monthLabel: String,
    incomeFormatted: String,
    expenseFormatted: String,
    balanceFormatted: String,
    balancePositive: Boolean,
    previousMonths: List<DashboardMonthlyHistoryItem>,
    showHistory: Boolean,
    onToggleHistory: () -> Unit,
    totalIncomeFormatted: String,
    totalExpenseFormatted: String,
    totalBalanceFormatted: String,
    totalBalancePositive: Boolean
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val elevation = XpendzThemeTokens.elevation
    val typography = XpendzThemeTokens.typography
    val periodLabel = "$monthLabel actual"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.m, vertical = spacing.xxs),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation.level2 + elevation.level1),
        shape = RoundedCornerShape(shapes.extraLarge)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.m, vertical = spacing.s - spacing.xxs / 2),
            verticalArrangement = Arrangement.spacedBy(spacing.s - spacing.xxs - spacing.xxs / 2)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs / 2)) {
                    Text(
                        text = "Este mes",
                        style = typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    Text(
                        text = periodLabel,
                        style = typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onToggleHistory,
                    contentPadding = PaddingValues(horizontal = spacing.xs, vertical = spacing.xxs / 2)
                ) {
                    Text(
                        text = if (showHistory) "Ocultar" else "Ver meses",
                        style = typography.labelLarge
                    )
                }
            }

            Surface(
                color = colors.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(shapes.extraLarge),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    MonthlyCardBackgroundGraph(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(horizontal = spacing.s - spacing.xxs - spacing.xxs / 2, vertical = spacing.s - spacing.xxs - spacing.xxs / 2)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.s - spacing.xxs / 2, vertical = spacing.s - spacing.xxs / 2),
                        verticalArrangement = Arrangement.spacedBy(spacing.s - spacing.xxs - spacing.xxs / 2)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing.s - spacing.xxs / 2),
                            verticalAlignment = Alignment.Top
                        ) {
                            MonthlyMetricColumn(
                                title = "Ingresos:",
                                value = incomeFormatted,
                                accentColor = Income,
                                modifier = Modifier.weight(1f)
                            )
                            MonthlyMetricColumn(
                                title = "Gastos:",
                                value = expenseFormatted,
                                accentColor = Expense,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        MonthlyInlineBalanceMetric(
                            title = "Balance:",
                            value = balanceFormatted,
                            accentColor = if (balancePositive) Income else Expense,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (showHistory && previousMonths.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.s - spacing.xxs - spacing.xxs / 2)) {
                    previousMonths.forEach { item ->
                        PreviousMonthRow(
                            item = item,
                            balanceColor = if (item.balanceCents >= 0) Income else Expense
                        )
                    }

                    Surface(
                        color = colors.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(shapes.large),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.s, vertical = spacing.xs),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Total período",
                                style = typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = totalIncomeFormatted,
                                    style = typography.labelMedium,
                                    color = Income,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = totalExpenseFormatted,
                                    style = typography.labelMedium,
                                    color = Expense,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = totalBalanceFormatted,
                                    style = typography.titleSmall,
                                    color = if (totalBalancePositive) Income else Expense,
                                    fontWeight = FontWeight.Bold
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
private fun MonthlyMetricColumn(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val typography = XpendzThemeTokens.typography
    Column(
        modifier = modifier.padding(horizontal = spacing.xxs / 2),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs / 2)
    ) {
        Text(
            text = title,
            style = typography.labelMedium,
            fontWeight = FontWeight.Normal,
            color = colors.onSurfaceVariant
        )
        Text(
            text = value,
            style = typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 17.sp),
            fontWeight = FontWeight.Bold,
            color = accentColor,
            maxLines = 2,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun MonthlyInlineBalanceMetric(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val typography = XpendzThemeTokens.typography
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = title,
            style = typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
            modifier = Modifier.padding(end = spacing.xs)
        )
        Text(
            text = value,
            style = typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 17.sp),
            fontWeight = FontWeight.Bold,
            color = accentColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun MonthlyCardBackgroundGraph(
    modifier: Modifier = Modifier
) {
    val primaryOverlay = XpendzThemeTokens.colors.brand.copy(alpha = 0.08f)
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val linePath = Path().apply {
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
private fun PreviousMonthRow(
    item: DashboardMonthlyHistoryItem,
    balanceColor: Color
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val elevation = XpendzThemeTokens.elevation
    val typography = XpendzThemeTokens.typography
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale("es", "CO")) }
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(shapes.large),
        shadowElevation = elevation.level1,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.s, vertical = spacing.s - spacing.xxs / 2),
            horizontalArrangement = Arrangement.spacedBy(spacing.s - spacing.xxs / 2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.label,
                style = typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(spacing.xxxl + spacing.s + spacing.xxs / 2)
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(spacing.xxs / 2)
            ) {
                Text(
                    text = "Ing. ${currencyFormatter.format(item.incomeCents / 100.0)}",
                    style = typography.bodySmall,
                    color = Income
                )
                Text(
                    text = "Gas. ${currencyFormatter.format(item.expenseCents / 100.0)}",
                    style = typography.bodySmall,
                    color = Expense
                )
            }
            Text(
                text = currencyFormatter.format(item.balanceCents / 100.0),
                style = typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 13.sp),
                fontWeight = FontWeight.Bold,
                color = balanceColor,
                textAlign = TextAlign.End,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier.width(spacing.xxxl + spacing.xxl + spacing.xxs)
            )
        }
    }
}

@Composable
private fun BalanceSummaryCard(
    balancePeriodLabel: String,
    showBalanceDetail: Boolean,
    onToggleDetail: () -> Unit,
    showTotalBalance: Boolean,
    onToggleBalanceVisibility: () -> Unit,
    totalBalanceFormatted: String,
    realTrendIcon: androidx.compose.ui.graphics.vector.ImageVector,
    realTrendAccentColor: Color,
    realTrendText: String,
    realTrendIsPositive: Boolean,
    monthlyPoints: List<DashboardBalancePoint>,
    balanceDetailText: String,
    trendFormatted: String,
    trendPositive: Boolean
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val elevation = XpendzThemeTokens.elevation
    val typography = XpendzThemeTokens.typography
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.m, vertical = spacing.xxs)
            .clickable { onToggleDetail() },
        elevation = CardDefaults.cardElevation(defaultElevation = elevation.level3)
    ) {
        val gradient = Brush.linearGradient(
            colors = listOf(
                colors.brand,
                MaterialTheme.colorScheme.primaryContainer
            )
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(shapes.medium))
                .background(gradient)
                .padding(horizontal = spacing.m, vertical = spacing.xxs / 2),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Saldo total · $balancePeriodLabel",
                    style = typography.labelMedium,
                    color = colors.onBrand.copy(alpha = 0.92f)
                )
                IconButton(
                    onClick = onToggleBalanceVisibility,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = colors.onBrand
                    )
                ) {
                    Icon(
                        imageVector = if (showTotalBalance) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (showTotalBalance) "Ocultar saldo" else "Mostrar saldo"
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.xxs / 2))
            Text(
                totalBalanceFormatted,
                style = typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = colors.onBrand
            )

            Spacer(modifier = Modifier.height(spacing.xxs))

            Surface(
                color = colors.surface.copy(alpha = 0.16f),
                shape = RoundedCornerShape(shapes.extraLarge)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = spacing.s, vertical = spacing.xs - spacing.xxs / 2 - elevation.level1),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    Icon(
                        imageVector = realTrendIcon,
                        contentDescription = null,
                        tint = realTrendAccentColor,
                        modifier = Modifier.size(spacing.l - spacing.xxs / 2)
                    )
                    Text(
                        text = realTrendText,
                        style = typography.labelLarge,
                        color = colors.onBrand,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (realTrendIsPositive) "🟢" else "🔴",
                        style = typography.labelLarge
                    )
                }
            }

            if (monthlyPoints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(elevation.level1))
                BalanceSparkline(
                    points = monthlyPoints,
                    lineColor = Color(0xFF7DFFB3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(spacing.xxl + spacing.xs - spacing.xxs / 2)
                )
            }

            if (showBalanceDetail) {
                Spacer(modifier = Modifier.height(spacing.xs))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.surface.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(shapes.large)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = spacing.s + spacing.xxs / 2, vertical = spacing.s - spacing.xxs / 2),
                        verticalArrangement = Arrangement.spacedBy(spacing.xxs)
                    ) {
                        Text(
                            text = "Detalle rápido",
                            style = typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onBrand
                        )
                        Text(
                            text = balanceDetailText,
                            style = typography.bodySmall,
                            color = colors.onBrand.copy(alpha = 0.92f)
                        )
                        BalanceMetricChip(
                            title = "Variación vs periodo anterior",
                            value = trendFormatted,
                            accentColor = if (trendPositive) Income else Expense,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Toca esta tarjeta para ocultar o mostrar este desglose.",
                            style = typography.bodySmall,
                            color = colors.onBrand.copy(alpha = 0.78f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomToolsPanel(
    onNavigateToTransactions: (String?) -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToCharts: () -> Unit,
    onNavigateToBudget: () -> Unit,
    onNavigateToLoans: () -> Unit
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val elevation = XpendzThemeTokens.elevation
    var selected by rememberSaveable { mutableStateOf("Categorías") }

    Surface(
        color = colors.surface,
        tonalElevation = elevation.level0,
        shadowElevation = elevation.level4 + elevation.level1 + elevation.level1,
        shape = RoundedCornerShape(shapes.extraLarge),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = spacing.s, vertical = spacing.xs)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.s - spacing.xxs - spacing.xxs / 2, vertical = spacing.s - spacing.xxs / 2),
            horizontalArrangement = Arrangement.spacedBy(elevation.level0),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomToolItem(
                icon = Icons.Default.GridView,
                label = "Categorías",
                selected = selected == "Categorías",
                modifier = Modifier.weight(1f),
                onClick = {
                    selected = "Categorías"
                    onNavigateToCategories()
                }
            )
            BottomToolItem(
                icon = Icons.Default.Insights,
                label = "Gráficos",
                selected = selected == "Gráficos",
                modifier = Modifier.weight(1f),
                onClick = {
                    selected = "Gráficos"
                    onNavigateToCharts()
                }
            )
            BottomToolItem(
                icon = Icons.Default.ListAlt,
                label = "Transacciones",
                selected = selected == "Transacciones",
                modifier = Modifier.weight(1f),
                onClick = {
                    selected = "Transacciones"
                    onNavigateToTransactions(null)
                }
            )
            BottomToolItem(
                icon = Icons.Default.Payments,
                label = "Presupuesto",
                selected = selected == "Presupuesto",
                modifier = Modifier.weight(1f),
                onClick = {
                    selected = "Presupuesto"
                    onNavigateToBudget()
                }
            )
            BottomToolItem(
                icon = Icons.Default.RequestQuote,
                label = "Préstamos",
                selected = selected == "Préstamos",
                modifier = Modifier.weight(1f),
                onClick = {
                    selected = "Préstamos"
                    onNavigateToLoans()
                }
            )
        }
    }
}

@Composable
private fun BottomToolItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val typography = XpendzThemeTokens.typography
    val tint = if (selected) colors.brand else colors.onSurfaceVariant.copy(alpha = 0.75f)

    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.xxs / 2, vertical = spacing.s - spacing.xxs - spacing.xxs / 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xxs)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(spacing.xl)
        )
        Text(
            text = label,
            style = typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BalanceMetricChip(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val typography = XpendzThemeTokens.typography
    Surface(
        modifier = modifier,
        color = colors.surface.copy(alpha = 0.14f),
        shape = RoundedCornerShape(shapes.medium)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.s - spacing.xxs / 2, vertical = spacing.xs),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs / 2)
        ) {
            Text(
                text = title,
                style = typography.labelSmall,
                color = colors.onBrand.copy(alpha = 0.78f)
            )
            Text(
                text = value,
                style = typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }
    }
}

@Composable
private fun BalanceSparkline(
    points: List<DashboardBalancePoint>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        return
    }

    Canvas(modifier = modifier) {
        if (points.size == 1) {
            drawCircle(color = lineColor, radius = 6f, center = Offset(size.width / 2f, size.height / 2f))
            return@Canvas
        }

        val minValue = points.minOf { it.balanceCents }.toFloat()
        val maxValue = points.maxOf { it.balanceCents }.toFloat()
        val range = (maxValue - minValue).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (points.size - 1).coerceAtLeast(1)

        val path = Path()
        points.forEachIndexed { index, point ->
            val x = stepX * index
            val normalizedY = (point.balanceCents.toFloat() - minValue) / range
            val y = size.height - (normalizedY * size.height)
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = lineColor,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f)
        )

        val lastPoint = points.last()
        val lastX = stepX * (points.size - 1)
        val lastY = size.height - (((lastPoint.balanceCents.toFloat() - minValue) / range) * size.height)
        drawCircle(color = lineColor, radius = 7f, center = Offset(lastX, lastY))
    }
}

private enum class ActionButtonEmphasis {
    Primary,
    Secondary
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    supportingText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasis: ActionButtonEmphasis = ActionButtonEmphasis.Secondary,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val elevation = XpendzThemeTokens.elevation
    val typography = XpendzThemeTokens.typography
    val isPrimary = emphasis == ActionButtonEmphasis.Primary
    val containerColor = if (isPrimary) {
        accentColor
    } else {
        colors.surface
    }
    val contentColor = if (isPrimary) colors.onBrand else colors.onSurface
    val supportingColor = if (isPrimary) {
        colors.onBrand.copy(alpha = 0.95f)
    } else {
        colors.onSurfaceVariant.copy(alpha = 0.82f)
    }

    ElevatedCard(
        onClick = onClick,
        modifier = modifier.heightIn(min = if (isPrimary) spacing.xxxl * 2 + spacing.s else elevation.level0),
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (isPrimary) elevation.level4 else elevation.level1 + elevation.level1
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(
                    horizontal = if (isPrimary) spacing.s - spacing.xxs / 2 else spacing.s,
                    vertical = spacing.s
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = com.jcadenas.xpendz.R.drawable.ic_launcher),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer(alpha = if (isPrimary) 0.03f else 0.04f)
            )

            if (isPrimary) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        color = colors.onBrand.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(shapes.large)
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(spacing.xs)
                                .size(spacing.l - spacing.xxs / 2),
                            tint = accentColor
                        )
                    }
                    Text(
                        label,
                        style = typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = contentColor,
                        textAlign = TextAlign.Center
                    )
                    if (supportingText.isNotBlank()) {
                        Text(
                            supportingText,
                            style = typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = supportingColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Surface(
                        color = accentColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(shapes.large)
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(spacing.s - spacing.xxs / 2)
                                .size(spacing.l + spacing.xxs / 2),
                            tint = accentColor
                        )
                    }
                    Spacer(modifier = Modifier.height(spacing.s - spacing.xxs / 2))
                    Text(
                        label,
                        style = typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = contentColor
                    )
                    Text(
                        supportingText,
                        style = typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = supportingColor
                    )
                }
            }
        }
    }
}
