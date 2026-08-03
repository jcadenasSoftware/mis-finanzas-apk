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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            icon = {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "¿Seguro que deseas cerrar sesión?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Volverás a la pantalla de acceso y podrás iniciar sesión nuevamente cuando quieras.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    Scaffold(
        topBar = {
            CompactHeader(
                title = {
                    Column {
                        Text(
                            text = if (greetingName.isNotBlank()) "Hola, $greetingName 👋" else "Hola 👋",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = financialSummaryLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
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
                contentPadding = PaddingValues(top = 8.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isSyncing) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.extraLarge,
                            tonalElevation = 2.dp,
                            shadowElevation = 2.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (syncProgress.isCancelling) "Cancelando sincronización" else "Sincronizando datos",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = syncProgress.message ?: syncStatus.orEmpty(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    TextButton(
                                        onClick = { syncViewModel.cancelSync() },
                                        enabled = !syncProgress.isCancelling
                                    ) {
                                        Text(if (syncProgress.isCancelling) "Cancelando..." else "Cancelar")
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                LinearProgressIndicator(
                                    progress = { syncProgress.progress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = if (syncProgress.totalSteps > 0) {
                                        "Paso ${syncProgress.currentStep.coerceAtMost(syncProgress.totalSteps)} de ${syncProgress.totalSteps}"
                                    } else {
                                        "Preparando sincronización"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Tus cuentas",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Ordenadas por saldo (de mayor a menor)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (state.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
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
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "No tienes cuentas",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Agrega tu primera cuenta para comenzar",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            modifier = Modifier.padding(horizontal = 16.dp)
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
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 0.dp,
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
        color = Color.White,
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (top != null && totalBalanceCents > 0L) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(28.dp),
                            shape = MaterialTheme.shapes.large,
                            color = Color(0xFFE8F0FF)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = Color(0xFF2463EB),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            text = "La mayor parte de tu dinero está en ${top.account.name} (${topPercentText}%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            .height(52.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Nueva cuenta", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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
    val cardElevation = if (isTop) 8.dp else 4.dp
    val verticalPadding = if (isTop) 14.dp else 10.dp

    val mainIcon = remember(accountWithBalance.account.iconKey, accountWithBalance.account.type) {
        accountIconForKey(accountWithBalance.account.iconKey, accountWithBalance.account.type)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
        shape = MaterialTheme.shapes.extraLarge,
        border = CardDefaults.outlinedCardBorder().copy(
            width = 1.dp,
            brush = androidx.compose.ui.graphics.SolidColor(accountAccent.copy(alpha = if (isTop) 0.14f else 0.08f))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(if (isTop) 52.dp else 46.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = accountAccent.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = mainIcon,
                            contentDescription = null,
                            tint = accountAccent,
                            modifier = Modifier.size(if (isTop) 26.dp else 22.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = accountWithBalance.account.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2
                    )
                    Text(
                        text = "${accountTypeLabel(accountWithBalance.account.type)}  •  ${accountWithBalance.account.currency}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currencyFormat.format(accountWithBalance.balanceCents / 100.0),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (accountWithBalance.balanceCents >= 0) Income else Expense
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = Color.White,
                        tonalElevation = 0.dp
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
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LinearProgressIndicator(
                    progress = { pct },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp),
                    color = accountAccent,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    text = "${pctText}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = "Personaliza la información de esta cuenta",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        border = CardDefaults.outlinedCardBorder().copy(
                            width = 1.dp,
                            brush = androidx.compose.ui.graphics.SolidColor(animatedBorderColor)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(46.dp),
                                shape = MaterialTheme.shapes.extraLarge,
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
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }

                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = newName.ifBlank { accountWithBalance.account.name },
                                    style = MaterialTheme.typography.titleSmall,
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
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

                    Text(text = "Icono", style = MaterialTheme.typography.labelLarge)
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = accountAccent.copy(alpha = 0.12f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = accountIconForKey(selectedIconKey, selectedType),
                                contentDescription = null,
                                tint = accountAccent,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Text(text = "Color", style = MaterialTheme.typography.labelLarge)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                targetValue = if (selected) 3.dp else 1.dp,
                                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                                label = "borderWidth"
                            )

                            val animatedBorderColor by animateColorAsState(
                                targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                                label = "borderColor"
                            )

                            Surface(
                                modifier = Modifier
                                    .size(40.dp)
                                    .graphicsLayer {
                                        scaleX = animatedScale
                                        scaleY = animatedScale
                                    }
                                    .clip(MaterialTheme.shapes.extraLarge)
                                    .border(
                                        width = animatedBorderWidth,
                                        color = animatedBorderColor,
                                        shape = MaterialTheme.shapes.extraLarge
                                    )
                                    .clickable { selectedColorHex = hex },
                                color = c,
                                tonalElevation = if (selected) 1.dp else 0.dp
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
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
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
            shape = MaterialTheme.shapes.extraLarge
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
    val periodLabel = "$monthLabel actual"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Este mes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = periodLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onToggleHistory,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (showHistory) "Ocultar" else "Ver meses",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    MonthlyCardBackgroundGraph(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    previousMonths.forEach { item ->
                        PreviousMonthRow(
                            item = item,
                            balanceColor = if (item.balanceCents >= 0) Income else Expense
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Total período",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = totalIncomeFormatted,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Income,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = totalExpenseFormatted,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Expense,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = totalBalanceFormatted,
                                    style = MaterialTheme.typography.titleSmall,
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
    Column(
        modifier = modifier.padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            fontSize = 16.sp,
            lineHeight = 17.sp,
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
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = value,
            fontSize = 16.sp,
            lineHeight = 17.sp,
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
    val primaryOverlay = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
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
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale("es", "CO")) }
    Surface(
        color = Color.White,
        shape = MaterialTheme.shapes.large,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(62.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Ing. ${currencyFormatter.format(item.incomeCents / 100.0)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Income
                )
                Text(
                    text = "Gas. ${currencyFormatter.format(item.expenseCents / 100.0)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Expense
                )
            }
            Text(
                text = currencyFormatter.format(item.balanceCents / 100.0),
                fontSize = 12.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Bold,
                color = balanceColor,
                textAlign = TextAlign.End,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier.width(84.dp)
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onToggleDetail() },
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        val gradient = Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.primaryContainer
            )
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(gradient)
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Saldo total · $balancePeriodLabel",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f)
                )
                IconButton(
                    onClick = onToggleBalanceVisibility,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (showTotalBalance) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (showTotalBalance) "Ocultar saldo" else "Mostrar saldo"
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                totalBalanceFormatted,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.16f),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = realTrendIcon,
                        contentDescription = null,
                        tint = realTrendAccentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = realTrendText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (realTrendIsPositive) "🟢" else "🔴",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            if (monthlyPoints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(1.dp))
                BalanceSparkline(
                    points = monthlyPoints,
                    lineColor = Color(0xFF7DFFB3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                )
            }

            if (showBalanceDetail) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.14f),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Detalle rápido",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = balanceDetailText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f)
                        )
                        BalanceMetricChip(
                            title = "Variación vs periodo anterior",
                            value = trendFormatted,
                            accentColor = if (trendPositive) Income else Expense,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Toca esta tarjeta para ocultar o mostrar este desglose.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
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
    var selected by rememberSaveable { mutableStateOf("Categorías") }

    Surface(
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
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
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)

    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
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
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.14f),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
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
    val isPrimary = emphasis == ActionButtonEmphasis.Primary
    val containerColor = if (isPrimary) {
        accentColor
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isPrimary) Color.White else MaterialTheme.colorScheme.onSurface
    val supportingColor = if (isPrimary) {
        Color.White.copy(alpha = 0.95f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
    }

    ElevatedCard(
        onClick = onClick,
        modifier = modifier.heightIn(min = if (isPrimary) 108.dp else 0.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (isPrimary) 8.dp else 2.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(
                    horizontal = if (isPrimary) 10.dp else 12.dp,
                    vertical = if (isPrimary) 12.dp else 12.dp
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
                        color = Color.White.copy(alpha = 0.92f),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(18.dp),
                            tint = accentColor
                        )
                    }
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = contentColor,
                        textAlign = TextAlign.Center
                    )
                    if (supportingText.isNotBlank()) {
                        Text(
                            supportingText,
                            style = MaterialTheme.typography.bodySmall,
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
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(22.dp),
                            tint = accentColor
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = contentColor
                    )
                    Text(
                        supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = supportingColor
                    )
                }
            }
        }
    }
}
