package com.myfinances.ui.navigation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myfinances.ui.screens.categories.CategoriesScreen
import com.myfinances.ui.screens.charts.ChartsScreen
import com.myfinances.ui.screens.dashboard.DashboardScreen
import com.myfinances.ui.screens.budget.BudgetScreen
import com.myfinances.ui.screens.loans.LoansScreen
import com.myfinances.ui.screens.login.LoginScreen
import com.myfinances.ui.screens.reports.ReportsScreen
import com.myfinances.ui.screens.settings.PrivacyPolicyScreen
import com.myfinances.ui.screens.settings.SettingsScreen
import com.myfinances.ui.screens.transactions.AddTransactionScreen
import com.myfinances.ui.screens.transactions.TransactionsScreen
import com.myfinances.ui.screens.transfers.AddTransferScreen
import com.myfinances.ui.screens.transfers.TransfersScreen
import com.myfinances.ui.viewmodel.AuthViewModel

@Composable
fun AppNavHost(
    navController: NavHostController,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val authState by authViewModel.authState.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var bottomBarVisible by remember { mutableStateOf(true) }
    
    val startDestination = if (authState.isLoggedIn) {
        NavRoutes.Dashboard.route
    } else {
        NavRoutes.Login.route
    }

    val isAuthRoute = currentRoute == NavRoutes.Login.route
    val isModalRoute = currentRoute == NavRoutes.AddTransaction.route ||
        currentRoute == NavRoutes.EditTransaction.route ||
        currentRoute == NavRoutes.AddTransfer.route ||
        currentRoute == NavRoutes.EditTransfer.route

    val showBottomBar = authState.isLoggedIn && !isAuthRoute && !isModalRoute && bottomBarVisible

    val isTopRouteDashboard = currentRoute == NavRoutes.Dashboard.route
    val isTopRouteTransactions = currentRoute?.startsWith("transactions") == true
    val isTopRouteCategories = currentRoute == NavRoutes.Categories.route
    val isTopRouteLoans = currentRoute == NavRoutes.Loans.route

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                Surface(
                    color = Color.White,
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                ) {
                NavigationBar(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 0.dp,
                    containerColor = Color.White
                ) {
                    val itemColors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color.Transparent,
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    NavigationBarItem(
                        selected = isTopRouteDashboard,
                        onClick = {
                            navController.navigate(NavRoutes.Dashboard.route) {
                                launchSingleTop = true
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
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(imageVector = Icons.Default.MoneyOff, contentDescription = "Préstamos") },
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

        composable(NavRoutes.Budget.route) {
            BudgetScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.Loans.route) {
            LoansScreen(
                onNavigateBack = { navController.popBackStack() }
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
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.Charts.route) {
            ChartsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTransactions = { route -> navController.navigate(route) },
                onNavigateToAddTransaction = { navController.navigate(NavRoutes.AddTransaction.route) }
            )
        }

        composable(NavRoutes.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPrivacyPolicy = {
                    navController.navigate("privacy_policy")
                }
            )
        }

        composable("privacy_policy") {
            PrivacyPolicyScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.Reports.route) {
            ReportsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        }
    }
}
