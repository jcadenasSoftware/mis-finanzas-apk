package com.myfinances.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myfinances.ui.screens.categories.CategoriesScreen
import com.myfinances.ui.screens.charts.ChartsScreen
import com.myfinances.ui.screens.dashboard.DashboardScreen
import com.myfinances.ui.screens.budget.BudgetScreen
import com.myfinances.ui.screens.loans.LoansScreen
import com.myfinances.ui.screens.login.LoginScreen
import com.myfinances.ui.screens.onboarding.OnboardingScreen
import com.myfinances.ui.screens.reports.ReportsScreen
import com.myfinances.ui.screens.settings.BackupSettingsScreen
import com.myfinances.ui.screens.settings.PrivacyAndDataScreen
import com.myfinances.ui.screens.settings.PrivacyPolicyScreen
import com.myfinances.ui.screens.settings.SettingsScreen
import com.myfinances.ui.screens.transactions.AddTransactionScreen
import com.myfinances.ui.screens.transactions.TransactionsScreen
import com.myfinances.ui.screens.transfers.AddTransferScreen
import com.myfinances.ui.screens.transfers.TransfersScreen
import com.myfinances.ui.components.HamburgerMenu
import com.myfinances.ui.components.HamburgerMenuButton
import com.myfinances.ui.viewmodel.AuthViewModel
import com.myfinances.ui.viewmodel.OnboardingViewModel
import com.myfinances.work.BudgetAlertHelper

@Composable
fun AppNavHost(
    navController: NavHostController,
    authViewModel: AuthViewModel = hiltViewModel(),
    onboardingViewModel: OnboardingViewModel = hiltViewModel()
) {
    val authState by authViewModel.authState.collectAsState()
    val onboardingCompleted by onboardingViewModel.onboardingCompleted.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var bottomBarVisible by remember { mutableStateOf(true) }

    // Esperar a que DataStore resuelva el valor antes de decidir la ruta inicial
    if (onboardingCompleted == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator(color = androidx.compose.ui.graphics.Color(0xFF1E6DFF))
        }
        return
    }

    val startDestination = when {
        authState.isLoggedIn -> NavRoutes.Dashboard.route
        onboardingCompleted == false -> NavRoutes.Onboarding.route
        else -> NavRoutes.Login.route
    }

    val isAuthRoute = currentRoute == NavRoutes.Login.route
            || currentRoute == NavRoutes.Onboarding.route
    val isModalRoute = currentRoute == NavRoutes.AddTransaction.route ||
        currentRoute == NavRoutes.EditTransaction.route ||
        currentRoute == NavRoutes.AddTransfer.route ||
        currentRoute == NavRoutes.EditTransfer.route

    val showBottomBar = authState.isLoggedIn && !isAuthRoute && !isModalRoute && bottomBarVisible

    val isTopRouteDashboard = currentRoute == NavRoutes.Dashboard.route
    val isTopRouteTransactions = currentRoute?.startsWith("transactions") == true
    val isTopRouteCategories = currentRoute == NavRoutes.Categories.route
    val isTopRouteLoans = currentRoute == NavRoutes.Loans.route

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        BudgetAlertHelper.alerts.collect { alert ->
            val result = snackbarHostState.showSnackbar(
                message = alert.message,
                actionLabel = "Ver",
                withDismissAction = true
            )
            if (result == SnackbarResult.ActionPerformed) {
                navController.navigate(NavRoutes.Budget.createRoute(tab = "monthly")) {
                    launchSingleTop = true
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    shadowElevation = 0.dp,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                ) {
                    NavigationBar(
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = 0.dp,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        val itemColors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        NavigationBarItem(
                            selected = isTopRouteDashboard,
                            onClick = {
                                val popped = navController.popBackStack(NavRoutes.Dashboard.route, inclusive = false)
                                if (!popped) {
                                    navController.navigate(NavRoutes.Dashboard.route) {
                                        popUpTo(startDestination) {
                                            saveState = true
                                        }
                                        restoreState = true
                                        launchSingleTop = true
                                    }
                                }
                            },
                            icon = { Icon(imageVector = Icons.Default.Home, contentDescription = "Inicio") },
                            label = { Text("Inicio", maxLines = 1, fontSize = 10.sp) },
                            colors = itemColors
                        )
                        NavigationBarItem(
                            selected = isTopRouteTransactions,
                            onClick = {
                                navController.navigate(NavRoutes.Transactions.createRoute()) {
                                    popUpTo(startDestination) {
                                        saveState = true
                                    }
                                    restoreState = true
                                    launchSingleTop = true
                                }
                            },
                            icon = { Icon(imageVector = Icons.Default.ReceiptLong, contentDescription = "Transacciones") },
                            label = { Text("Transacciones", maxLines = 1, fontSize = 10.sp) },
                            colors = itemColors
                        )
                        NavigationBarItem(
                            selected = isTopRouteCategories,
                            onClick = {
                                navController.navigate(NavRoutes.Categories.route) {
                                    popUpTo(startDestination) {
                                        saveState = true
                                    }
                                    restoreState = true
                                    launchSingleTop = true
                                }
                            },
                            icon = { Icon(imageVector = Icons.Default.Category, contentDescription = "Categorías") },
                            label = { Text("Categorías", maxLines = 1, fontSize = 10.sp) },
                            colors = itemColors
                        )
                        NavigationBarItem(
                            selected = isTopRouteLoans,
                            onClick = {
                                navController.navigate(NavRoutes.Loans.route) {
                                    popUpTo(startDestination) {
                                        saveState = true
                                    }
                                    restoreState = true
                                    launchSingleTop = true
                                }
                            },
                            icon = { Icon(imageVector = Icons.Default.AccountBalanceWallet, contentDescription = "Préstamos") },
                            label = { Text("Préstamos", maxLines = 1, fontSize = 10.sp) },
                            colors = itemColors
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = androidx.compose.ui.Modifier.padding(paddingValues)
        ) {
        composable(NavRoutes.Onboarding.route) {
            OnboardingScreen(
                onFinish = {
                    navController.navigate(NavRoutes.Login.route) {
                        popUpTo(NavRoutes.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(NavRoutes.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(NavRoutes.Dashboard.route) {
                        popUpTo(NavRoutes.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(NavRoutes.Dashboard.route) {
            DashboardScreen(
                onNavigateToTransactions = { accountId ->
                    navController.navigate(NavRoutes.Transactions.createRoute(accountId = accountId))
                },
                onNavigateToTransfers = {
                    navController.navigate(NavRoutes.Transfers.route)
                },
                onNavigateToCategories = {
                    navController.navigate(NavRoutes.Categories.route)
                },
                onNavigateToCharts = {
                    navController.navigate(NavRoutes.Charts.route)
                },
                onNavigateToLoans = {
                    navController.navigate(NavRoutes.Loans.route)
                },
                onNavigateToBudget = {
                    navController.navigate(NavRoutes.Budget.route)
                },
                onNavigateToSettings = {
                    navController.navigate(NavRoutes.Settings.route)
                },
                onNavigateToReports = {
                    navController.navigate(NavRoutes.Reports.route)
                },
                onBottomBarVisibilityChange = { visible ->
                    bottomBarVisible = visible
                },
                onLogout = {
                    authViewModel.signOut()
                    navController.navigate(NavRoutes.Login.route) {
                        popUpTo(NavRoutes.Dashboard.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = NavRoutes.Budget.route,
            arguments = listOf(navArgument("tab") { type = NavType.StringType; defaultValue = "" })
        ) { backStackEntry ->
            val tab = backStackEntry.arguments?.getString("tab") ?: ""
            BudgetScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCharts = { navController.navigate(NavRoutes.Charts.route) },
                onNavigateToReports = { navController.navigate(NavRoutes.Reports.route) },
                onNavigateToSettings = { navController.navigate(NavRoutes.Settings.route) },
                onLogout = {
                    authViewModel.signOut()
                    navController.navigate(NavRoutes.Login.route) {
                        popUpTo(NavRoutes.Dashboard.route) { inclusive = true }
                    }
                },
                initialTab = tab
            )
        }

        composable(NavRoutes.Loans.route) {
            LoansScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCharts = { navController.navigate(NavRoutes.Charts.route) },
                onNavigateToBudget = { navController.navigate(NavRoutes.Budget.route) },
                onNavigateToReports = { navController.navigate(NavRoutes.Reports.route) },
                onNavigateToSettings = { navController.navigate(NavRoutes.Settings.route) },
                onLogout = {
                    authViewModel.signOut()
                    navController.navigate(NavRoutes.Login.route) {
                        popUpTo(NavRoutes.Dashboard.route) { inclusive = true }
                    }
                },
                onEditTransaction = { id ->
                    navController.navigate(NavRoutes.EditTransaction.createRoute(id))
                }
            )
        }

        composable(
            route = NavRoutes.Transactions.route,
            arguments = listOf(
                navArgument("accountId") { type = NavType.StringType; defaultValue = "" },
                navArgument("categoryId") { type = NavType.StringType; defaultValue = "" },
                navArgument("from") { type = NavType.StringType; defaultValue = "" },
                navArgument("to") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getString("accountId")?.takeIf { it.isNotBlank() }
            val categoryId = backStackEntry.arguments?.getString("categoryId")?.takeIf { it.isNotBlank() }
            val fromEpochSec = backStackEntry.arguments?.getString("from")?.toLongOrNull()
            val toEpochSec = backStackEntry.arguments?.getString("to")?.toLongOrNull()

            TransactionsScreen(
                onNavigateBack = { navController.popBackStack() },
                onAddTransaction = { navController.navigate(NavRoutes.AddTransaction.route) },
                onEditTransaction = { id ->
                    navController.navigate(NavRoutes.EditTransaction.createRoute(id))
                },
                onNavigateToCharts = { navController.navigate(NavRoutes.Charts.route) },
                onNavigateToBudget = { navController.navigate(NavRoutes.Budget.route) },
                onNavigateToReports = { navController.navigate(NavRoutes.Reports.route) },
                onNavigateToSettings = { navController.navigate(NavRoutes.Settings.route) },
                onLogout = {
                    authViewModel.signOut()
                    navController.navigate(NavRoutes.Login.route) {
                        popUpTo(NavRoutes.Dashboard.route) { inclusive = true }
                    }
                },
                initialAccountId = accountId,
                initialCategoryId = categoryId,
                initialFromEpochSec = fromEpochSec,
                initialToEpochSec = toEpochSec
            )
        }

        composable(NavRoutes.AddTransaction.route) {
            AddTransactionScreen(
                onNavigateBack = { navController.popBackStack() },
                onTransactionSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = NavRoutes.EditTransaction.route,
            arguments = listOf(navArgument("transactionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val transactionId = backStackEntry.arguments?.getString("transactionId") ?: ""
            AddTransactionScreen(
                transactionId = transactionId,
                onNavigateBack = { navController.popBackStack() },
                onTransactionSaved = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.Transfers.route) {
            TransfersScreen(
                onNavigateBack = { navController.popBackStack() },
                onAddTransfer = { navController.navigate(NavRoutes.AddTransfer.route) },
                onEditTransfer = { id ->
                    navController.navigate(NavRoutes.EditTransfer.createRoute(id))
                }
            )
        }

        composable(NavRoutes.AddTransfer.route) {
            AddTransferScreen(
                onNavigateBack = { navController.popBackStack() },
                onTransferSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = NavRoutes.EditTransfer.route,
            arguments = listOf(navArgument("transferId") { type = NavType.StringType })
        ) { backStackEntry ->
            val transferId = backStackEntry.arguments?.getString("transferId") ?: ""
            AddTransferScreen(
                transferId = transferId,
                onNavigateBack = { navController.popBackStack() },
                onTransferSaved = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.Categories.route) {
            CategoriesScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCharts = { navController.navigate(NavRoutes.Charts.route) },
                onNavigateToBudget = { navController.navigate(NavRoutes.Budget.route) },
                onNavigateToReports = { navController.navigate(NavRoutes.Reports.route) },
                onNavigateToSettings = { navController.navigate(NavRoutes.Settings.route) },
                onLogout = {
                    authViewModel.signOut()
                    navController.navigate(NavRoutes.Login.route) {
                        popUpTo(NavRoutes.Dashboard.route) { inclusive = true }
                    }
                }
            )
        }

        composable(NavRoutes.Charts.route) {
            ChartsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTransactions = { route -> navController.navigate(route) },
                onNavigateToAddTransaction = { navController.navigate(NavRoutes.AddTransaction.route) },
                onNavigateToBudget = { navController.navigate(NavRoutes.Budget.route) },
                onNavigateToReports = { navController.navigate(NavRoutes.Reports.route) },
                onNavigateToSettings = { navController.navigate(NavRoutes.Settings.route) },
                onLogout = {
                    authViewModel.signOut()
                    navController.navigate(NavRoutes.Login.route) {
                        popUpTo(NavRoutes.Dashboard.route) { inclusive = true }
                    }
                }
            )
        }

        composable(NavRoutes.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPrivacyAndData = {
                    navController.navigate(NavRoutes.PrivacyAndData.route)
                },
                onNavigateToBackupSettings = { userUid ->
                    navController.navigate(NavRoutes.BackupSettings.createRoute(userUid))
                },
                onNavigateToPrivacyPolicy = {
                    navController.navigate("privacy_policy")
                },
                onNavigateToCharts = { navController.navigate(NavRoutes.Charts.route) },
                onNavigateToBudget = { navController.navigate(NavRoutes.Budget.route) },
                onNavigateToReports = { navController.navigate(NavRoutes.Reports.route) },
                onLogout = {
                    authViewModel.signOut()
                    navController.navigate(NavRoutes.Login.route) {
                        popUpTo(NavRoutes.Dashboard.route) { inclusive = true }
                    }
                }
            )
        }

        composable("privacy_policy") {
            PrivacyPolicyScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.PrivacyAndData.route) {
            PrivacyAndDataScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPrivacyPolicy = {
                    navController.navigate("privacy_policy")
                }
            )
        }

        composable(NavRoutes.BackupSettings.route,
            arguments = listOf(navArgument("userUid") { type = NavType.StringType })
        ) { backStackEntry ->
            val userUid = backStackEntry.arguments?.getString("userUid") ?: ""
            BackupSettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                userUid = userUid
            )
        }

        composable(NavRoutes.Reports.route) {
            ReportsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCharts = { navController.navigate(NavRoutes.Charts.route) },
                onNavigateToBudget = { navController.navigate(NavRoutes.Budget.route) },
                onNavigateToSettings = { navController.navigate(NavRoutes.Settings.route) },
                onLogout = {
                    authViewModel.signOut()
                    navController.navigate(NavRoutes.Login.route) {
                        popUpTo(NavRoutes.Dashboard.route) { inclusive = true }
                    }
                }
            )
        }
        }
    }
}
